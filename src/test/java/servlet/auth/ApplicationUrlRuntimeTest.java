package servlet.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.Statement;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import control.admin.TeacherAccountControl;
import control.auth.AuthenticatedUser;
import control.auth.PasswordHasher;
import control.auth.RequestMetadata;
import control.teacher.TeacherSelfAccountControl;
import dao.StudentDao;
import entity.ConsentStatus;
import entity.TeacherAccountInput;
import entity.UserCredential.UserType;
import lib.mysql.Client;

@TestMethodOrder(OrderAnnotation.class)
class ApplicationUrlRuntimeTest {
	private static final String SYNTHETIC_PASSWORD = "SyntheticUrl2026!Aa";
	private static final String CHANGED_PASSWORD = "ChangedUrl2026!Aa";
	private static final String OPTIONAL_PASSWORD = "OptionalUrl2026!Aa";
	private static final String TEACHER_LOGIN = "synthetic-url-teacher";
	private static final String STUDENT_LOGIN = "synthetic-url-student";
	private static final Browser teacher = new Browser("teacher.localhost:8080");
	private static final Browser student = new Browser("student.localhost:8080");
	private static final Browser admin = new Browser("teacher.localhost:8080");
	private static long studentId;
	private static long teacherId;
	private static AuthenticatedUser fixtureAdmin;

	@BeforeAll
	static void installIsolatedSyntheticFixture() throws Exception {
		Assumptions.assumeTrue("true".equals(System.getenv("URL_ROUTING_HTTP_TEST")));
		assertTrue(System.getenv("DB_NAME").matches("ppe_url_routing_test_[a-z0-9_]+"));
		assertEquals("http://ppe-url-routing-runtime:8080", System.getenv("URL_ROUTING_HTTP_BASE"),
				"Use the isolated application container, never a shared application.");
		long adminId;
		long schoolId;
		try (Connection connection = Client.createConnection(); Statement statement = connection.createStatement()) {
			assertEquals(System.getenv("DB_NAME"), connection.getCatalog());
			assertEquals(0, scalar(connection, "SELECT COUNT(*) FROM users"));
			String hash = new PasswordHasher().hash(SYNTHETIC_PASSWORD.toCharArray());
			try (var insert = connection.prepareStatement("""
					INSERT INTO users (user_type,login_id,password_hash,display_name,account_status,created_at)
					VALUES (?, ?, ?, 'Synthetic URL fixture', 'active', CURRENT_TIMESTAMP)
					""", Statement.RETURN_GENERATED_KEYS)) {
				insert.setString(1, "admin"); insert.setString(2, "admin"); insert.setString(3, hash);
				insert.executeUpdate();
				try (var keys = insert.getGeneratedKeys()) { assertTrue(keys.next()); adminId = keys.getLong(1); }
				insert.setString(1, "student"); insert.setString(2, STUDENT_LOGIN);
				insert.executeUpdate();
				try (var keys = insert.getGeneratedKeys()) { assertTrue(keys.next()); studentId = keys.getLong(1); }
			}
			statement.executeUpdate("""
					INSERT INTO schools (school_code,name,security_level,school_status,created_at)
					VALUES ('SYNTHETIC-URL','Synthetic URL school',2,'active',CURRENT_TIMESTAMP)
					""");
			schoolId = scalar(connection, "SELECT school_id FROM schools");
			try (var insert = connection.prepareStatement("""
					INSERT INTO student_profiles
					(user_id,student_code,security_level,first_login_status,must_change_password,school_id)
					VALUES (?, 'synthetic-url-student', 2, 'password_change_required', TRUE, ?)
					""")) {
				insert.setLong(1, studentId); insert.setLong(2, schoolId); insert.executeUpdate();
			}
		}
		var management = new TeacherAccountControl();
		var authenticatedAdmin = new AuthenticatedUser(adminId, "admin", "Synthetic", UserType.ADMIN, false, "test");
		fixtureAdmin = authenticatedAdmin;
		String initial = management.create(authenticatedAdmin, new TeacherAccountInput(
				TEACHER_LOGIN, Set.of(schoolId), Set.of("account-management", "task-management", "teacher-prompt-design")));
		var created = management.list(authenticatedAdmin).getFirst();
		teacherId = created.userId();
		var authenticatedTeacher = new AuthenticatedUser(
				created.userId(), TEACHER_LOGIN, "Synthetic", UserType.TEACHER, true, "test");
		assertEquals(TeacherSelfAccountControl.ChangeResult.SUCCESS,
				new TeacherSelfAccountControl().changePassword(authenticatedTeacher, 1,
						initial.toCharArray(), SYNTHETIC_PASSWORD.toCharArray(), SYNTHETIC_PASSWORD.toCharArray(),
						RequestMetadata.from("127.0.0.1", "synthetic-url-runtime")));
	}

	@Test @Order(1)
	void portalRootsHostSeparationAndAnonymousRoutesWorkOverHttp() throws Exception {
		assertRedirect(teacher.get("/"), 302, "/teacher/account/login");
		assertRedirect(student.get("/"), 302, "/student/account/login");
		assertRedirect(teacher.get("/teacher"), 302, "/teacher/account/login");
		assertRedirect(teacher.get("/admin/"), 302, "/teacher/account/login");
		assertRedirect(student.get("/student/"), 302, "/student/account/login");
		assertRedirect(teacher.get("/student/account"), 302, "http://student.localhost:8080/student/account/login");
		assertRedirect(student.get("/teacher/account/account"), 302, "http://teacher.localhost:8080/teacher/account/login");
		assertRedirect(teacher.post("/auth/logout", Map.of()), 302, "/teacher/account/login");
	}

	@Test @Order(2)
	void teacherManagementAliasesCanonicalPagesAndInvalidCsrfRemainConsistent() throws Exception {
		assertRedirect(teacher.login(TEACHER_LOGIN, SYNTHETIC_PASSWORD), 302, "/teacher/students");
		assertEquals(200, teacher.get("/teacher/students").statusCode());
		assertTrue(teacher.get("/teacher/students").body().contains("data-endpoint=\"/teacher/students\""));
		for (String path : new String[] { "/teacher/account/account", "/teacher/accounts" }) {
			assertRedirect(teacher.get(path + "?view=list&q=synthetic-url"), 302,
					"/teacher/students?view=list&q=synthetic-url");
			assertEquals(200, teacher.get("/teacher/students?view=list&q=synthetic-url").statusCode());
		}
		assertRedirect(teacher.get("/teacher/account/profile"), 302, "/teacher/account");
		assertEquals(200, teacher.get("/teacher/account").statusCode());
		assertEquals(200, teacher.get("/teacher/account/password").statusCode());
		assertRedirect(teacher.get("/teacher/"), 302, "/teacher/home");
		assertEquals(403, teacher.get("/admin").statusCode());
		assertEquals(403, teacher.get("/admin/management").statusCode());
		Map<String, String> rejected = Map.of("action", "reveal", "userId", Long.toString(studentId),
				"version", "1", "changeConfirmed", "yes", "csrfToken", "invalid");
		assertRedirect(teacher.post("/teacher/account/account?view=detail", rejected), 307,
				"/teacher/students?view=detail");
		assertEquals(403, teacher.post("/teacher/students?view=detail", rejected).statusCode());
		String reset = new TeacherAccountControl().change(fixtureAdmin, teacherId, 2, "reset", null);
		assertRedirect(teacher.get("/teacher/students"), 302, "/teacher/account/login");
		assertRedirect(teacher.login(TEACHER_LOGIN, reset), 302, "/teacher/account/password");
		assertRedirect(teacher.get("/teacher/account/profile"), 302, "/teacher/account/password");
		assertRedirect(teacher.get("/teacher"), 302, "/teacher/account/password");
		var password = teacher.get("/teacher/account/password");
		assertEquals(200, password.statusCode());
		var change = new HashMap<>(passwordForm(hidden(password.body(), "csrfToken"), reset, SYNTHETIC_PASSWORD));
		change.put("version", hidden(password.body(), "version"));
		change.put("changeConfirmed", "yes");
		assertRedirect(teacher.post("/teacher/account/password", change), 303, "/teacher/account/login");
		assertRedirect(teacher.login(TEACHER_LOGIN, SYNTHETIC_PASSWORD), 302, "/teacher/students");
	}

	@Test @Order(3)
	void administratorUsesCanonicalManagementButCannotUseTeacherSelfService() throws Exception {
		assertRedirect(admin.login("admin", SYNTHETIC_PASSWORD), 302, "/admin/home");
		assertRedirect(admin.get("/admin/home"), 302, "/admin/teachers");
		assertEquals(200, admin.get("/admin/teachers").statusCode());
		assertEquals(200, admin.get("/admin/schools").statusCode());
		assertRedirect(admin.get("/admin/management?q=synthetic-url"), 302, "/admin/teachers?q=synthetic-url");
		assertRedirect(admin.post("/admin/management", Map.of("csrfToken", "invalid")), 307, "/admin/teachers");
		assertEquals(403, admin.post("/admin/teachers", Map.of("csrfToken", "invalid")).statusCode());
		assertEquals(403, admin.get("/teacher/account").statusCode());
	}

	@Test @Order(4)
	void mandatoryLegacyPasswordPostPreservesBodyAndOptionalChangeReturnsToCanonicalAccount() throws Exception {
		assertRedirect(student.login(STUDENT_LOGIN, SYNTHETIC_PASSWORD), 302, "/student/account/password");
		assertRedirect(student.get("/student"), 302, "/student/account/password");
		assertRedirect(student.get("/student/account/account"), 302, "/student/account/password");
		assertRedirect(student.get("/student/account/change-password?source=legacy"), 302,
				"/student/account/password?source=legacy");
		var page = student.get("/student/account/password");
		assertEquals(200, page.statusCode());
		assertTrue(page.body().contains("action=\"/student/account/password\""));
		Map<String, String> form = passwordForm(hidden(page.body(), "csrfToken"), SYNTHETIC_PASSWORD, CHANGED_PASSWORD);
		var invalid = new HashMap<>(form);
		invalid.put("csrfToken", "invalid");
		assertRedirect(student.post("/student/account/change-password", invalid), 307, "/student/account/password");
		assertEquals(403, student.post("/student/account/password", invalid).statusCode());
		assertStudentCredential(SYNTHETIC_PASSWORD, true, 1);
		assertRedirect(student.post("/student/account/change-password", form), 307, "/student/account/password");
		assertRedirect(student.post("/student/account/password", form), 302, "/student/home");
		assertStudentCredential(CHANGED_PASSWORD, false, 2);
		assertEquals(200, student.get("/student/home").statusCode());
		assertRedirect(student.get("/student/account/account"), 302, "/student/account");
		assertEquals(200, student.get("/student/account").statusCode());
		var optional = student.get("/student/account/password");
		assertRedirect(student.post("/student/account/password",
				passwordForm(hidden(optional.body(), "csrfToken"), CHANGED_PASSWORD, OPTIONAL_PASSWORD)),
				302, "/student/account");
		assertStudentCredential(OPTIONAL_PASSWORD, false, 3);
		assertRedirect(student.get("/teacher"), 302, "http://teacher.localhost:8080/teacher/account/login");
		assertEquals(403, student.getOnHost("/teacher", "teacher.localhost:8080").statusCode());
	}

	@Test @Order(5)
	void legacyConsentPostSavesExactlyOnceAndReloadsThroughCanonicalUrl() throws Exception {
		assertRedirect(student.get("/student/survey/consent"), 302, "/student/consent");
		var page = student.get("/student/consent");
		assertEquals(200, page.statusCode());
		assertTrue(page.body().contains("action=\"/student/consent\""));
		Map<String, String> form = Map.of("csrfToken", hidden(page.body(), "csrfToken"),
				"documentVersionId", hidden(page.body(), "documentVersionId"),
				"responseId", hidden(page.body(), "responseId"), "confirmRead", "yes",
				"consentDecision", "decline", "changeConfirmed", "yes");
		assertRedirect(student.post("/student/survey/consent", form), 307, "/student/consent");
		assertEquals(ConsentStatus.UNCONFIRMED, new StudentDao().findConsentStatus(studentId));
		assertRedirect(student.post("/student/consent", form), 302, "/student/home");
		assertEquals(ConsentStatus.DECLINED, new StudentDao().findConsentStatus(studentId));
		var reloaded = student.get("/student/consent");
		assertEquals(200, reloaded.statusCode());
		assertEquals("DECLINED", new StudentDao().findConsentPage(studentId).getStatus().name());
	}

	private static Map<String, String> passwordForm(String csrf, String current, String next) {
		return Map.of("csrfToken", csrf, "currentPassword", current, "newPassword", next, "confirmPassword", next);
	}

	private static void assertStudentCredential(String password, boolean required, long version) throws Exception {
		try (Connection connection = Client.createConnection(); var query = connection.prepareStatement("""
				SELECT u.password_hash,u.account_version,p.must_change_password,p.first_login_status
				FROM users u JOIN student_profiles p ON p.user_id=u.user_id WHERE u.user_id=?
				""")) {
			query.setLong(1, studentId);
			try (var rows = query.executeQuery()) {
				assertTrue(rows.next());
				assertTrue(new PasswordHasher().matches(password.toCharArray(), rows.getString(1)));
				assertEquals(version, rows.getLong(2));
				assertEquals(required, rows.getBoolean(3));
				assertEquals(required ? "password_change_required" : "completed", rows.getString(4));
			}
		}
	}

	private static long scalar(Connection connection, String sql) throws Exception {
		try (var query = connection.createStatement(); var rows = query.executeQuery(sql)) {
			assertTrue(rows.next());
			return rows.getLong(1);
		}
	}

	private static String hidden(String html, String name) {
		var matcher = Pattern.compile("name=\"" + Pattern.quote(name) + "\"\\s+value=\"([^\"]+)\"").matcher(html);
		assertTrue(matcher.find(), "Expected hidden input: " + name);
		return matcher.group(1);
	}

	private static void assertRedirect(HttpResponse<String> response, int status, String destination) {
		assertEquals(status, response.statusCode());
		String location = response.headers().firstValue("Location").orElseThrow();
		assertEquals(destination, destination.startsWith("/")
				? URI.create(location).getRawPath() + (URI.create(location).getRawQuery() == null
						? "" : "?" + URI.create(location).getRawQuery()) : location);
	}

	private static final class Browser {
		private final String host;
		private final HttpClient client = HttpClient.newBuilder()
				.cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
				.connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build();

		private Browser(String host) { this.host = host; }

		private HttpResponse<String> get(String path) throws Exception {
			return getOnHost(path, host);
		}

		private HttpResponse<String> getOnHost(String path, String requestedHost) throws Exception {
			return client.send(request(path).setHeader("Host", requestedHost).GET().build(),
					HttpResponse.BodyHandlers.ofString());
		}

		private HttpResponse<String> post(String path, Map<String, String> form) throws Exception {
			String body = form.entrySet().stream().map(entry -> encode(entry.getKey()) + "=" + encode(entry.getValue()))
					.collect(Collectors.joining("&"));
			return client.send(request(path).header("Content-Type", "application/x-www-form-urlencoded")
					.POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
		}

		private HttpResponse<String> login(String loginId, String password) throws Exception {
			String path = host.startsWith("student.") ? "/student/account/login" : "/teacher/account/login";
			var login = get(path);
			assertEquals(200, login.statusCode());
			return post(path, Map.of("loginId", loginId, "password", password, "csrfToken", hidden(login.body(), "csrfToken")));
		}

		private HttpRequest.Builder request(String path) {
			return HttpRequest.newBuilder(URI.create(System.getenv("URL_ROUTING_HTTP_BASE") + path))
					.header("Host", host).timeout(Duration.ofSeconds(20));
		}

		private static String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
	}
}
