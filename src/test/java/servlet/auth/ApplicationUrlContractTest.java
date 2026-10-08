package servlet.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import javax.servlet.annotation.WebServlet;

import org.junit.jupiter.api.Test;

import servlet.RootServlet;
import servlet.admin.TeacherAccountServlet;
import servlet.student.StudentAccountServlet;
import servlet.student.StudentConsentServlet;
import servlet.teacher.StudentAccountManagementServlet;
import servlet.teacher.TeacherSelfAccountServlet;

class ApplicationUrlContractTest {
	private static final Set<String> LEGACY_PATHS = Set.of(
			"/teacher/account/account", "/teacher/accounts", "/student/account/account",
			"/teacher/account/profile", "/student/account/change-password",
			"/student/survey/consent", "/admin/management");

	@Test
	void mapsEachAccountAndConsentFunctionToItsCanonicalUrl() {
		Map<Class<?>, Set<String>> expected = Map.of(
				StudentAccountManagementServlet.class, Set.of("/teacher/students"),
				StudentAccountServlet.class, Set.of("/student/account"),
				TeacherSelfAccountServlet.class, Set.of("/teacher/account", "/teacher/account/password"),
				StudentPasswordChangeServlet.class, Set.of("/student/account/password"),
				StudentConsentServlet.class, Set.of("/student/consent"),
				TeacherAccountServlet.class, Set.of("/admin/teachers"),
				RootServlet.class, Set.of("", "/student", "/student/", "/teacher", "/teacher/", "/admin", "/admin/"));
		expected.forEach((servlet, paths) -> assertEquals(paths, mapping(servlet), servlet.getName()));
	}

	@Test
	void everyServletMappingIsUniqueAndOnlyCanonical() throws Exception {
		Set<String> paths = new HashSet<>();
		for (Class<?> servlet : servlets()) {
			for (String path : mapping(servlet)) {
				assertTrue(paths.add(path), "Duplicate servlet URL: " + path);
				assertFalse(LEGACY_PATHS.contains(path), "Legacy URL still executes a servlet: " + path);
			}
		}
		assertTrue(paths.contains("/health"), "Readiness must have a dedicated endpoint.");
		assertTrue(paths.containsAll(Set.of("/teacher/submissions", "/teacher/evaluations")));
		assertTrue(paths.contains("/teacher/exercises"));
		assertEquals(27, servlets().size(), "Review the whole URL inventory when adding a servlet.");
	}

	@Test
	void allJspBusinessLinksFormsAndEndpointsUseMappedCanonicalUrls() throws Exception {
		Set<String> mapped = new HashSet<>();
		for (Class<?> servlet : servlets()) mapped.addAll(mapping(servlet));
		Pattern url = Pattern.compile("<c:url\\s+value=['\"]([^'\"]+)['\"]");
		try (var files = Files.walk(Path.of("src/main/webapp/WEB-INF"))) {
			for (Path file : files.filter(path -> path.toString().endsWith(".jsp")
					|| path.toString().endsWith(".jspf")).toList()) {
				var matcher = url.matcher(Files.readString(file));
				while (matcher.find()) {
					String path = matcher.group(1).split("[?#]", 2)[0];
					if (!path.matches("/(?:student|teacher|admin|auth)(?:/.*)?")) continue;
					assertFalse(LEGACY_PATHS.contains(path), "Legacy generated URL in " + file + ": " + path);
					assertTrue(mapped.contains(path), "Unmapped generated URL in " + file + ": " + path);
				}
			}
		}
	}

	private static Set<String> mapping(Class<?> servlet) {
		WebServlet annotation = servlet.getAnnotation(WebServlet.class);
		Set<String> paths = new HashSet<>(Arrays.asList(annotation.value()));
		paths.addAll(Arrays.asList(annotation.urlPatterns()));
		return paths;
	}

	private static Set<Class<?>> servlets() throws Exception {
		Set<Class<?>> servlets = new HashSet<>();
		Pattern packageName = Pattern.compile("(?m)^package ([\\w.]+);");
		try (var files = Files.walk(Path.of("src/main/java/servlet"))) {
			for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
				String source = Files.readString(file);
				if (!source.contains("@WebServlet")) continue;
				var matcher = packageName.matcher(source);
				assertTrue(matcher.find(), "Servlet must have a package: " + file);
				String name = file.getFileName().toString().replaceFirst("\\.java$", "");
				servlets.add(Class.forName(matcher.group(1) + "." + name,
						false, ApplicationUrlContractTest.class.getClassLoader()));
			}
		}
		return servlets;
	}
}
