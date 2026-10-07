package control.evaluation;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Assumptions;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public final class GeminiApiDiagnosticSupport {
	public static final String INSTRUCTION = "Return the JSON object {\"ok\":true}, with no other text.";
	public static final String INPUT = "Synthetic availability check.";
	private static final int MAX_GENERATION_REQUESTS = 4;
	private static final AtomicInteger GENERATIONS = new AtomicInteger();
	private static final HttpClient HTTP = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(10)).build();

	private GeminiApiDiagnosticSupport() {}

	public static void requireEnabled() {
		Assumptions.assumeTrue("true".equals(System.getenv("GEMINI_API_DIAGNOSTIC_TEST"))
				&& "true".equals(System.getenv("GEMINI_API_SMOKE_TEST")),
				"Explicitly enable synthetic, billable API diagnostics.");
		if (System.getenv("GEMINI_API_KEY") == null || System.getenv("GEMINI_API_KEY").isBlank()) {
			throw new IllegalStateException("GEMINI_API_KEY must be configured.");
		}
	}

	public static JsonObject modelMetadata() throws Exception {
		return sendRaw("model-metadata", HttpRequest.newBuilder(URI.create(
				"https://generativelanguage.googleapis.com/v1beta/models/" + GeminiModelCatalog.DEFAULT_MODEL))
				.timeout(Duration.ofSeconds(30))
				.header("x-goog-api-key", System.getenv("GEMINI_API_KEY"))
				.GET().build(), 0, 0);
	}

	public static JsonObject availableModels() throws Exception {
		JsonObject response = sendRaw("available-models", HttpRequest.newBuilder(URI.create(
				"https://generativelanguage.googleapis.com/v1beta/models?pageSize=100"))
				.timeout(Duration.ofSeconds(30))
				.header("x-goog-api-key", System.getenv("GEMINI_API_KEY")).GET().build(), 0, 0);
		JsonArray names = new JsonArray();
		for (var model : response.getAsJsonArray("models")) {
			names.add(model.getAsJsonObject().get("name"));
		}
		JsonObject catalog = new JsonObject();
		catalog.add("names", names);
		catalog.addProperty("has_more", response.has("nextPageToken"));
		record("available-model-names", catalog);
		return catalog;
	}

	public static JsonObject plainGeneration() throws Exception {
		reserveGeneration();
		JsonObject body = new JsonObject();
		body.addProperty("model", GeminiModelCatalog.DEFAULT_MODEL);
		body.addProperty("system_instruction", INSTRUCTION);
		body.addProperty("input", INPUT);
		body.addProperty("store", false);
		JsonObject config = new JsonObject();
		config.addProperty("max_output_tokens", 4096);
		body.add("generation_config", config);
		return sendRaw("minimal-plain", HttpRequest.newBuilder(
				URI.create("https://generativelanguage.googleapis.com/v1beta/interactions"))
				.timeout(Duration.ofSeconds(60))
				.header("Content-Type", "application/json")
				.header("x-goog-api-key", System.getenv("GEMINI_API_KEY"))
				.header("Api-Revision", "2026-05-20")
				.POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
				.build(), bytes(INPUT), 0);
	}

	public static JsonObject classicGeneration() throws Exception {
		reserveGeneration();
		JsonObject body = new JsonObject();
		body.add("systemInstruction", textContent(INSTRUCTION));
		JsonArray contents = new JsonArray();
		JsonObject user = textContent(INPUT);
		user.addProperty("role", "user");
		contents.add(user);
		body.add("contents", contents);
		JsonObject config = new JsonObject();
		config.addProperty("maxOutputTokens", 4096);
		body.add("generationConfig", config);
		return sendRaw("minimal-generate-content", HttpRequest.newBuilder(URI.create(
				"https://generativelanguage.googleapis.com/v1beta/models/"
						+ GeminiModelCatalog.DEFAULT_MODEL + ":generateContent"))
				.timeout(Duration.ofSeconds(60))
				.header("Content-Type", "application/json")
				.header("x-goog-api-key", System.getenv("GEMINI_API_KEY"))
				.POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
				.build(), bytes(INPUT), 0);
	}

	public static JsonObject structured(
			String label, String model, String instruction, String input, JsonObject schema)
			throws EvaluationProviderException, java.io.IOException {
		reserveGeneration();
		long started = System.nanoTime();
		JsonObject observation = observation(label, bytes(input), bytes(schema.toString()));
		observation.addProperty("model", model);
		try {
			JsonObject response = new GeminiEvaluationClient().generateStructuredOutput(
					model, instruction, input, schema);
			observation.addProperty("http_status", 200);
			observation.addProperty("elapsed_ms", elapsed(started));
			record(label, observation);
			return response;
		} catch (EvaluationProviderException failure) {
			if (failure.getHttpStatusCode() != null) {
				observation.addProperty("http_status", failure.getHttpStatusCode());
			}
			observation.addProperty("elapsed_ms", elapsed(started));
			observation.addProperty("high_demand", failure.getMessage().contains("high demand"));
			observation.addProperty("retryable", failure.isRetryable());
			if (failure.getRetryAfter() != null) {
				observation.addProperty("retry_after_seconds", failure.getRetryAfter().toSeconds());
			}
			record(label, observation);
			throw failure;
		}
	}

	public static Path saveOutput(String label, JsonObject response)
			throws EvaluationProviderException, java.io.IOException {
		String text = new GeminiEvaluationClient().extractOutputText(response);
		Path output = directory().resolve(label + "-output.txt");
		Files.writeString(output, text);
		if (!Files.readString(output).equals(text)) {
			throw new IllegalStateException("Synthetic output did not survive readback.");
		}
		return output;
	}

	private static JsonObject sendRaw(
			String label, HttpRequest request, int inputBytes, int schemaBytes) throws Exception {
		long started = System.nanoTime();
		JsonObject observation = observation(label, inputBytes, schemaBytes);
		HttpResponse<String> response;
		try {
			response = HTTP.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
		} catch (java.io.IOException | InterruptedException failure) {
			observation.addProperty("elapsed_ms", elapsed(started));
			observation.addProperty("exception_class", failure.getClass().getSimpleName());
			record(label, observation);
			if (failure instanceof InterruptedException) Thread.currentThread().interrupt();
			throw failure;
		}
		observation.addProperty("http_status", response.statusCode());
		observation.addProperty("elapsed_ms", elapsed(started));
		observation.addProperty("response_bytes", bytes(response.body()));
		JsonObject body = JsonParser.parseString(response.body()).getAsJsonObject();
		if (body.has("error") && body.get("error").isJsonObject()) {
			JsonObject error = body.getAsJsonObject("error");
			observation.addProperty("high_demand",
					error.has("message") && error.get("message").getAsString().contains("high demand"));
			if (error.has("code") && error.get("code").isJsonPrimitive()
					&& error.get("code").getAsString().matches("[A-Za-z0-9_]{1,64}")) {
				observation.add("error_code", error.get("code"));
			}
		}
		Duration retryAfter = EvaluationRetryPolicy.parseRetryAfter(
				response.headers().firstValue("Retry-After").orElse(null), Instant.now());
		if (retryAfter != null) observation.addProperty("retry_after_seconds", retryAfter.toSeconds());
		if ("model-metadata".equals(label) && response.statusCode() == 200) {
			for (String key : new String[] {"name", "version", "supportedGenerationMethods",
					"inputTokenLimit", "outputTokenLimit"}) {
				if (body.has(key)) observation.add(key, body.get(key));
			}
		}
		record(label, observation);
		if (response.statusCode() != 200) {
			throw new EvaluationProviderException("Diagnostic " + label + " HTTP " + response.statusCode(),
					false, response.statusCode());
		}
		return body;
	}

	private static void reserveGeneration() {
		if (GENERATIONS.incrementAndGet() > MAX_GENERATION_REQUESTS) {
			throw new IllegalStateException("Diagnostic generation request budget exceeded.");
		}
	}

	private static JsonObject textContent(String text) {
		JsonObject part = new JsonObject();
		part.addProperty("text", text);
		JsonArray parts = new JsonArray();
		parts.add(part);
		JsonObject content = new JsonObject();
		content.add("parts", parts);
		return content;
	}

	private static JsonObject observation(String label, int inputBytes, int schemaBytes) {
		JsonObject result = new JsonObject();
		result.addProperty("case", label);
		result.addProperty("model", GeminiModelCatalog.DEFAULT_MODEL);
		result.addProperty("observed_at", Instant.now().toString());
		result.addProperty("input_bytes", inputBytes);
		result.addProperty("schema_bytes", schemaBytes);
		return result;
	}

	private static void record(String label, JsonObject observation) throws java.io.IOException {
		Files.writeString(directory().resolve(label + ".json"), observation.toString());
		System.out.println("DIAGNOSTIC " + observation);
	}

	private static Path directory() throws java.io.IOException {
		Path directory = Path.of(System.getProperty("ppe.diagnostic.output",
				"build/reports/gemini-diagnostics"));
		Files.createDirectories(directory);
		return directory;
	}

	private static int bytes(String value) { return value.getBytes(StandardCharsets.UTF_8).length; }
	private static long elapsed(long started) { return (System.nanoTime() - started) / 1_000_000; }
}
