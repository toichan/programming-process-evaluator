package control.evaluation;

import static org.junit.jupiter.api.Assertions.*;

import java.net.http.HttpHeaders;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;

class GeminiApiDiagnosticSupportTest {
	@Test
	void recordsProviderFailureMetadataWithoutCredentialsOrControlCharacters() {
		JsonObject error = new JsonObject();
		error.addProperty("code", 404);
		error.addProperty("status", "NOT_FOUND");
		error.addProperty("message", "Model unavailable for synthetic-secret\nRetry only after configuration.");
		JsonObject body = new JsonObject();
		body.add("error", error);
		JsonObject observation = new JsonObject();

		GeminiApiDiagnosticSupport.appendErrorDetails(observation, body,
				HttpHeaders.of(Map.of("x-goog-request-id", List.of("synthetic-request-42")), (key, value) -> true),
				"synthetic-secret");

		assertEquals(404, observation.get("provider_code").getAsInt());
		assertEquals("NOT_FOUND", observation.get("provider_status").getAsString());
		assertEquals("synthetic-request-42", observation.get("request_id").getAsString());
		assertFalse(observation.toString().contains("synthetic-secret"));
		assertFalse(observation.get("provider_message").getAsString().contains("\n"));
		assertTrue(observation.get("provider_message").getAsString().contains("[REDACTED]"));
	}

	@Test
	void boundsMessagesAndDoesNotInventMissingRequestIdsOrErrors() {
		JsonObject observation = new JsonObject();
		HttpHeaders emptyHeaders = HttpHeaders.of(Map.of(), (key, value) -> true);
		GeminiApiDiagnosticSupport.appendErrorDetails(observation, new JsonObject(), emptyHeaders, null);
		assertTrue(observation.isEmpty());
		JsonObject error = new JsonObject();
		error.addProperty("message", "x".repeat(2000));
		JsonObject body = new JsonObject();
		body.add("error", error);
		GeminiApiDiagnosticSupport.appendErrorDetails(observation, body, emptyHeaders, null);
		assertEquals(1000, observation.get("provider_message").getAsString().length());
		assertFalse(observation.has("request_id"));
	}
}
