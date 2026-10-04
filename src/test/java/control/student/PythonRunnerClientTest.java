package control.student;

import static org.junit.jupiter.api.Assertions.*;

import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import com.sun.net.httpserver.HttpServer;

class PythonRunnerClientTest {
	@Test
	void preservesRunnerInputRejectionCodes() throws Exception {
		for (int status : new int[] {409, 413}) {
			String code = status == 413 ? "input_too_large" : "execution_not_running";
			HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
			server.createContext("/sessions", exchange -> {
				byte[] body = ("{\"error\":\"" + code + "\"}").getBytes(StandardCharsets.UTF_8);
				exchange.sendResponseHeaders(status, body.length);
				try (var output = exchange.getResponseBody()) { output.write(body); }
			});
			server.start();
			try {
				var client = new PythonRunnerClient(URI.create("http://127.0.0.1:" + server.getAddress().getPort()));
				var failure = assertThrows(PythonRunnerRequestException.class,
						() -> client.sendSessionInput("a".repeat(32), "synthetic"));
				assertEquals(status, failure.statusCode());
				assertEquals(code, failure.errorCode());
			} finally { server.stop(0); }
		}
	}

	@Test
	void preservesExplicitRunnerFailureCode() throws Exception {
		HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/sessions", exchange -> {
			byte[] body = "{\"status\":\"unavailable\",\"errorCode\":\"runner_cleanup_failed\"}"
					.getBytes(StandardCharsets.UTF_8);
			exchange.sendResponseHeaders(503, body.length);
			try (var output = exchange.getResponseBody()) { output.write(body); }
		});
		server.start();
		try {
			var client = new PythonRunnerClient(URI.create("http://127.0.0.1:" + server.getAddress().getPort()));
			var failure = assertThrows(PythonRunnerRequestException.class,
					() -> client.pollSession("a".repeat(32), 0));
			assertEquals(503, failure.statusCode());
			assertEquals("runner_cleanup_failed", failure.errorCode());
		} finally { server.stop(0); }
	}
}
