package servlet.student;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import control.teacher.TeacherReviewFixture;
import java.net.http.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

class StudentTaskMenuRuntimeTest {
	@Test void taskCardsAreRealAndTheirLinksResolveAcrossStudentPages() throws Exception {
		Assumptions.assumeTrue("true".equals(System.getenv("UI160_RUNTIME_TEST")));
		TeacherReviewFixture.guard(System.getenv());
		System.setProperty("jdk.httpclient.allowRestrictedHeaders", "host");
		String base = System.getenv("EVALUATION_SURVEY_HTTP_BASE");
		assertEquals("http://ppe-159-runtime:8080", base);
		var client = HttpClient.newBuilder().cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL)).build();
		var login = client.send(request(base, "/student/account/login").GET().build(), HttpResponse.BodyHandlers.ofString());
		var csrf = Pattern.compile("name=\"csrfToken\"\\s+value=\"([^\"]+)\"").matcher(login.body());
		assertTrue(csrf.find());
		String fields = "loginId=review_student&password=" + URLEncoder.encode(TeacherReviewFixture.PASSWORD, StandardCharsets.UTF_8)
				+ "&csrfToken=" + csrf.group(1);
		assertEquals(302, client.send(request(base, "/student/account/login").header("Content-Type", "application/x-www-form-urlencoded")
				.POST(HttpRequest.BodyPublishers.ofString(fields)).build(), HttpResponse.BodyHandlers.ofString()).statusCode());
		for (String path : new String[] { "/student/home", "/student/account", "/student/consent", "/student/exercise" }) {
			var page = client.send(request(base, path).GET().build(), HttpResponse.BodyHandlers.ofString());
			assertEquals(200, page.statusCode());
			assertTrue(page.body().contains("task-shortcut-card"), path);
			assertTrue(page.body().contains("Synthetic Review Task"), path);
			assertFalse(page.body().contains("課題画面の実装後"), path);
			var links = Pattern.compile("class=\"task-shortcut-link[^\"]*\" href=\"([^\"]+)\"").matcher(page.body());
			int count = 0;
			while (links.find()) {
				String link = links.group(1).replace("&amp;", "&");
				assertEquals(200, client.send(request(base, link).GET().build(), HttpResponse.BodyHandlers.ofString()).statusCode(), link);
				count++;
			}
			assertTrue(count >= 2);
		}
		assertEquals(302, HttpClient.newHttpClient().send(request(base, "/student/home").GET().build(), HttpResponse.BodyHandlers.ofString()).statusCode());
	}
	private HttpRequest.Builder request(String base, String path) {
		return HttpRequest.newBuilder(URI.create(base + path)).header("Host", "student.localhost:18092");
	}
}
