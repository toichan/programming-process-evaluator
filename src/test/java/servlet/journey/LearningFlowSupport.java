package servlet.journey;

import static org.junit.jupiter.api.Assertions.*;

import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.time.Duration;
import java.util.*;
import java.util.regex.*;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Assumptions;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import lib.mysql.Client;

final class LearningFlowSupport {
	static final String PASSWORD = "SyntheticLearning2026!Aa";
	static final String STUDENT_PASSWORD = "SyntheticLearningChanged2026!Aa";
	static final String TITLE = "Synthetic learning flow: double an integer";

	static void guard() throws Exception {
		Assumptions.assumeTrue("true".equals(System.getenv("LEARNING_FLOW_HTTP_TEST")));
		assertTrue(System.getenv("DB_NAME").matches("ppe_learning_flow_test_[a-z0-9_]+"));
		assertEquals("http://ppe-learning-flow-runtime:8080", System.getenv("LEARNING_FLOW_HTTP_BASE"));
		try (var connection = Client.createConnection()) {
			assertEquals(System.getenv("DB_NAME"), connection.getCatalog());
		}
	}

	static long number(String sql) throws Exception {
		try (var connection = Client.createConnection(); var statement = connection.createStatement();
				var result = statement.executeQuery(sql)) {
			assertTrue(result.next(), "Expected a synthetic fixture row.");
			return result.getLong(1);
		}
	}

	static String text(String sql) throws Exception {
		try (var connection = Client.createConnection(); var statement = connection.createStatement();
				var result = statement.executeQuery(sql)) {
			assertTrue(result.next(), "Expected a persisted synthetic row.");
			return result.getString(1);
		}
	}

	static String input(String html, String name) {
		var matcher = Pattern.compile("<input\\b[^>]*\\bname=\"" + Pattern.quote(name)
				+ "\"[^>]*\\bvalue=\"([^\"]*)\"").matcher(html);
		assertTrue(matcher.find(), "Missing form field " + name);
		return decode(matcher.group(1));
	}

	static List<String> inputs(String html, String name) {
		var matcher = Pattern.compile("<input\\b[^>]*\\bname=\"" + Pattern.quote(name)
				+ "\"[^>]*\\bvalue=\"([^\"]*)\"").matcher(html);
		List<String> values = new ArrayList<>();
		while (matcher.find()) values.add(decode(matcher.group(1)));
		return values;
	}

	static String form(String html, String id) {
		var matcher = Pattern.compile("<form\\b[^>]*\\bid=\"" + Pattern.quote(id)
				+ "\"[^>]*>(.*?)</form>", Pattern.DOTALL).matcher(html);
		assertTrue(matcher.find(), "Missing form " + id);
		return matcher.group(1);
	}

	static String decode(String value) {
		return value.replace("&amp;", "&").replace("&quot;", "\"")
				.replace("&#039;", "'").replace("&#39;", "'").replace("&lt;", "<").replace("&gt;", ">");
	}

	static Map<String, List<String>> fields(String... pairs) {
		Map<String, List<String>> values = new LinkedHashMap<>();
		for (int i = 0; i < pairs.length; i += 2) values.put(pairs[i], List.of(pairs[i + 1]));
		return values;
	}

	static JsonObject json(HttpResponse<String> response) {
		assertEquals(200, response.statusCode(), response.body());
		return JsonParser.parseString(response.body()).getAsJsonObject();
	}

	static void redirect(HttpResponse<String> response, String path) {
		assertTrue(response.statusCode() == 302 || response.statusCode() == 303,
				"Expected redirect; status=" + response.statusCode());
		assertTrue(response.headers().firstValue("location").orElseThrow().startsWith(path));
	}

	static final class Browser {
		private final String host;
		private final HttpClient client = HttpClient.newBuilder()
				.cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
				.connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NEVER).build();

		Browser(String role) { host = role + ".localhost:18082"; }

		HttpResponse<String> get(String path) throws Exception {
			return client.send(request(path).GET().build(), HttpResponse.BodyHandlers.ofString());
		}

		HttpResponse<String> post(String path, Map<String, List<String>> fields) throws Exception {
			String body = fields.entrySet().stream().flatMap(entry -> entry.getValue().stream().map(value ->
					URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8) + "="
							+ URLEncoder.encode(value, StandardCharsets.UTF_8))).collect(Collectors.joining("&"));
			return client.send(request(path).header("Content-Type", "application/x-www-form-urlencoded")
					.POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
		}

		void login(String login, String role) throws Exception {
			login(login, role, PASSWORD);
		}

		void login(String login, String role, String password) throws Exception {
			String path = "/" + role + "/account/login";
			var page = get(path);
			assertEquals(200, page.statusCode());
			redirect(post(path, fields("loginId", login, "password", password,
					"csrfToken", input(page.body(), "csrfToken"))), "/" + role + "/");
		}

		void completeStudentInitialPasswordChange(String login) throws Exception {
			login(login, "student");
			var page = get("/student/account/password");
			assertEquals(200, page.statusCode());
			redirect(post("/student/account/password", fields("csrfToken", input(page.body(), "csrfToken"),
					"currentPassword", PASSWORD, "newPassword", STUDENT_PASSWORD, "confirmPassword", STUDENT_PASSWORD)),
					"/student/home");
		}

		void logout(String role) throws Exception {
			var home = get("/" + role + "/home");
			assertEquals(200, home.statusCode());
			redirect(post("/auth/logout", fields("csrfToken", input(home.body(), "csrfToken"))),
					"/" + role + "/account/login");
		}

		private HttpRequest.Builder request(String path) {
			return HttpRequest.newBuilder(URI.create(System.getenv("LEARNING_FLOW_HTTP_BASE") + path))
					.header("Host", host).timeout(Duration.ofMinutes(13));
		}
	}
}
