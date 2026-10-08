package control.student;

import static org.junit.jupiter.api.Assertions.*;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpServer;

class PythonRunnerPreviewTest {
	@Test void previewUsesAuthenticatedExecuteWithInputAndPreservesCaps() throws Exception {
		var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		var calls = new java.util.concurrent.atomic.AtomicInteger();
		server.createContext("/execute", exchange -> {
			assertEquals("Bearer synthetic-preview-token", exchange.getRequestHeaders().getFirst("Authorization"));
			var request = JsonParser.parseString(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
			assertEquals("print(input())", request.get("source").getAsString());
			assertEquals("dummy\n", request.get("standardInput").getAsString());
			calls.incrementAndGet();
			byte[] body = "{\"status\":\"succeeded\",\"exitCode\":0,\"standardOutput\":\"dummy\\n\",\"standardError\":\"\"}".getBytes(StandardCharsets.UTF_8);
			exchange.sendResponseHeaders(200, body.length);
			try (var out = exchange.getResponseBody()) { out.write(body); }
		});
		server.start();
		try {
			var client = new PythonRunnerClient(URI.create("http://127.0.0.1:" + server.getAddress().getPort()), "synthetic-preview-token");
			assertEquals("dummy\n", client.executePreview("print(input())", "dummy\n").getStandardOutput());
			assertThrows(IllegalArgumentException.class, () -> client.executePreview("x".repeat(65537), ""));
			assertThrows(IllegalArgumentException.class, () -> client.executePreview("", "x".repeat(8193)));
			assertEquals(1, calls.get());
		} finally { server.stop(0); }
	}
}
