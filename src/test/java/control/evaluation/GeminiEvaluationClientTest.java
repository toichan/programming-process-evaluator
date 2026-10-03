package control.evaluation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import com.sun.net.httpserver.HttpServer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

class GeminiEvaluationClientTest {
	private HttpServer server;

	@AfterEach
	void stopServer() {
		if (server != null) {
			server.stop(0);
		}
	}

	@Test
	void missingApiKeyFailsBeforeMakingARequestAndIsNotRetryable() {
		GeminiEvaluationClient client = new GeminiEvaluationClient(HttpClient.newHttpClient(), null);
		EvaluationProviderException error = assertThrows(
				EvaluationProviderException.class,
				() -> client.generate("gemini-test", new JsonObject()));
		assertFalse(error.isRetryable());
		assertTrue(error.getMessage().contains("GEMINI_API_KEY"));
	}

	@Test
	void sendsGeminiRequestWithKeyOnlyInHeaderAndParsesProviderResponse() throws Exception {
		AtomicInteger requestCount = new AtomicInteger();
		JsonObject captured = new JsonObject();
		server = startServer(exchange -> {
			requestCount.incrementAndGet();
			assertEquals("POST", exchange.getRequestMethod());
			assertEquals("/v1beta/interactions", exchange.getRequestURI().getPath());
			assertEquals("test-key", exchange.getRequestHeaders().getFirst("x-goog-api-key"));
			assertEquals("2026-05-20", exchange.getRequestHeaders().getFirst("Api-Revision"));
			String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
			captured.add("body", JsonParser.parseString(body));
			assertFalse(exchange.getRequestURI().toString().contains("test-key"));
			assertFalse(body.contains("test-key"));
			writeResponse(exchange, 200, """
					{"status":"completed","steps":[{"type":"model_output","content":[{"type":"text","text":"{\\"ok\\":true}"}]}]}
					""");
		});

		GeminiEvaluationClient client = newClient("test-key");
		JsonObject payload = new JsonObject();
		payload.addProperty("synthetic", true);
		JsonObject response = client.generate("gemini-test", payload);

		assertEquals(1, requestCount.get());
		JsonObject requestBody = captured.getAsJsonObject("body");
		assertEquals("gemini-test", requestBody.get("model").getAsString());
		assertTrue(requestBody.get("system_instruction").getAsString().contains("untrusted evidence"));
		assertTrue(requestBody.get("input").getAsString().contains("\"synthetic\":true"));
		assertEquals("application/json",
				requestBody.getAsJsonObject("response_format").get("mime_type").getAsString());
		assertTrue(requestBody.getAsJsonObject("response_format").getAsJsonObject("schema")
				.getAsJsonObject("properties").has("process_analysis"));
		assertTrue(client.extractOutputText(response).contains("\"ok\":true"));
	}

	@Test
	void reportsHttpFailuresWithoutIncludingProviderResponseOrApiKey() throws Exception {
		server = startServer(exchange -> writeResponse(exchange, 429,
				"{\"error\":{\"message\":\"sensitive provider detail\",\"status\":\"RESOURCE_EXHAUSTED\"}}"));
		String testKey = "private-test-key";
		EvaluationProviderException error = assertThrows(
				EvaluationProviderException.class,
				() -> newClient(testKey).generate("gemini-test", new JsonObject()));

		assertTrue(error.isRetryable());
		assertTrue(error.getMessage().contains("429"));
		assertTrue(error.getMessage().contains("RESOURCE_EXHAUSTED"));
		assertFalse(error.getMessage().contains(testKey));
		assertFalse(error.getMessage().contains("sensitive provider detail"));
	}

	@Test
	void doesNotRetryBillingOrAuthenticationFailures() throws Exception {
		server = startServer(exchange -> writeResponse(exchange, 402,
				"{\"error\":{\"message\":\"private billing detail\",\"status\":\"RESOURCE_EXHAUSTED\"}}"));
		String testKey = "private-test-key";
		EvaluationProviderException error = assertThrows(
				EvaluationProviderException.class,
				() -> newClient(testKey).generate("gemini-test", new JsonObject()));

		assertFalse(error.isRetryable());
		assertTrue(error.getMessage().contains("RESOURCE_EXHAUSTED"));
		assertFalse(error.getMessage().contains(testKey));
		assertFalse(error.getMessage().contains("private billing detail"));
	}

	@Test
	void malformedProviderJsonIsARecoverableProviderError() throws Exception {
		server = startServer(exchange -> writeResponse(exchange, 200, "not-json"));
		EvaluationProviderException error = assertThrows(
				EvaluationProviderException.class,
				() -> newClient("test-key").generate("gemini-test", new JsonObject()));
		assertTrue(error.isRetryable());
		assertFalse(error.getMessage().contains("test-key"));
	}

	@Test
	void extractsJsonFromInteractionModelOutputAndRemovesOptionalMarkdownFence() throws EvaluationProviderException {
		GeminiEvaluationClient client = new GeminiEvaluationClient(HttpClient.newHttpClient(), "test-key");
		JsonObject response = new JsonObject();
		com.google.gson.JsonArray steps = new com.google.gson.JsonArray();
		JsonObject nonModelStep = new JsonObject();
		nonModelStep.addProperty("type", "thought");
		steps.add(nonModelStep);
		JsonObject modelOutput = new JsonObject();
		modelOutput.addProperty("type", "model_output");
		com.google.gson.JsonArray content = new com.google.gson.JsonArray();
		JsonObject textBlock = new JsonObject();
		textBlock.addProperty("type", "text");
		textBlock.addProperty("text", "```json\n{\"ok\":true}\n```");
		content.add(textBlock);
		modelOutput.add("content", content);
		steps.add(modelOutput);
		response.add("steps", steps);

		assertTrue(client.extractOutputText(response).contains("\"ok\":true"));
	}

	@Test
	void extractsConvenienceOutputTextWhenInteractionStepsAreAbsent() throws EvaluationProviderException {
		JsonObject response = new JsonObject();
		response.addProperty("output_text", "{\"ok\":true}");

		assertEquals("{\"ok\":true}",
				new GeminiEvaluationClient(HttpClient.newHttpClient(), "test-key").extractOutputText(response));
	}

	private GeminiEvaluationClient newClient(String apiKey) {
		return new GeminiEvaluationClient(
				HttpClient.newHttpClient(),
				apiKey,
				URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/"));
	}

	private HttpServer startServer(HttpServerHandler handler) throws IOException {
		HttpServer testServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		testServer.createContext("/", exchange -> {
			try {
				handler.handle(exchange);
			} catch (Exception error) {
				exchange.close();
				throw new IOException(error);
			} catch (AssertionError error) {
				exchange.close();
				throw error;
			}
		});
		testServer.start();
		return testServer;
	}

	private static void writeResponse(
			com.sun.net.httpserver.HttpExchange exchange,
			int status,
			String body) throws IOException {
		byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
		exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
		exchange.sendResponseHeaders(status, bytes.length);
		try (var output = exchange.getResponseBody()) {
			output.write(bytes);
		}
	}

	@FunctionalInterface
	private interface HttpServerHandler {
		void handle(com.sun.net.httpserver.HttpExchange exchange) throws Exception;
	}
}
