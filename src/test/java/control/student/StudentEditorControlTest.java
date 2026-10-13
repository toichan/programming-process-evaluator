package control.student;

import static org.junit.jupiter.api.Assertions.*;

import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpServer;

import control.auth.AuthenticatedUser;
import dao.StudentEditorDao;
import entity.EditorTestCase;
import entity.StudentEditorPage;
import entity.UserCredential.UserType;

class StudentEditorControlTest {
	private static final AuthenticatedUser STUDENT =
			new AuthenticatedUser(5, "synthetic-student", "synthetic", UserType.STUDENT, false, "test");
	private static final String SOURCE =
			"value = input('入力してください>'); print(value) if value else None";
	private static final String INPUT = "パー\n";

	@Test
	void submissionCheckKeepsInteractiveInputSeparateFromProgramOutput() throws Exception {
		var testCases = List.of(
				new EditorTestCase(1, "prompt only", "\n", "", 1),
				new EditorTestCase(2, "program prints input", INPUT, "パー", 2));
		LocalDateTime updatedAt = LocalDateTime.of(2026, 10, 13, 10, 15);
		var page = new StudentEditorPage(
				31, 41, 51, "課題", null, "beginner", "説明", null, null, null, SOURCE,
				"not_started", "not_started", "unsaved", "deny", null, updatedAt,
				null, null, null, null, null, null, List.of(), testCases, List.of(), List.of());

		var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		var calls = new AtomicInteger();
		var requestBodies = new CopyOnWriteArrayList<String>();
		var handlerFailure = new AtomicReference<Throwable>();
		server.createContext("/execute", exchange -> {
			try {
				requestBodies.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
				int call = calls.getAndIncrement();
				String output = call == 0 ? "" : "パー";
				byte[] body = ("{\"status\":\"succeeded\",\"exitCode\":0,\"standardOutput\":\""
						+ output + "\",\"standardError\":\"\"}").getBytes(StandardCharsets.UTF_8);
				exchange.sendResponseHeaders(200, body.length);
				try (var response = exchange.getResponseBody()) {
					response.write(body);
				}
			} catch (Throwable failure) {
				handlerFailure.set(failure);
				exchange.close();
			}
		});
		server.start();
		try {
			var client = new PythonRunnerClient(
					URI.create("http://127.0.0.1:" + server.getAddress().getPort()), "synthetic-test-token");
			var control = new StudentEditorControl(new StudentEditorDao(), client,
					(studentId, assignmentId) -> Optional.of(page));
			var check = control.checkSubmission(STUDENT, 31, SOURCE, updatedAt.toString());

			assertNull(handlerFailure.get());
			assertEquals(2, requestBodies.size());
			for (int index = 0; index < requestBodies.size(); index++) {
				var request = JsonParser.parseString(requestBodies.get(index)).getAsJsonObject();
				assertEquals(index == 0 ? "\n" : INPUT, request.get("standardInput").getAsString());
				assertEquals(SOURCE, request.get("source").getAsString());
				assertTrue(request.get("suppressInputPrompt").getAsBoolean());
			}

			assertEquals(2, check.getResults().size());
			assertEquals("\n", check.getResults().get(0).getInput());
			assertEquals("", check.getResults().get(0).getActualOutput());
			assertTrue(check.getResults().get(0).isPassed(),
					"the input prompt must not be compared as program output");
			assertEquals(INPUT, check.getResults().get(1).getInput());
			assertEquals("パー", check.getResults().get(1).getActualOutput());
			assertTrue(check.getResults().get(1).isPassed(),
					"explicit output that includes the typed value remains program output");
		} finally {
			server.stop(0);
		}
	}
}
