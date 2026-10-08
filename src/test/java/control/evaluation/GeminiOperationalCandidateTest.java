package control.evaluation;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import com.google.gson.JsonParser;

class GeminiOperationalCandidateTest {
	@Test
	void readsAvailableModelNamesWithoutAGenerationRequest() throws Exception {
		GeminiApiDiagnosticSupport.requireEnabled();
		assertTrue(GeminiApiDiagnosticSupport.availableModels().getAsJsonArray("names").size() > 0);
	}

	@Test
	void validatesFlashLiteCandidateWithOneStructuredRequestAndReadback() throws Exception {
		validateCandidate("gemini-3.5-flash-lite", "flash-lite");
	}

	@Test
	void validatesProCandidateWithOneStructuredRequestAndReadback() throws Exception {
		validateCandidate("gemini-2.5-pro", "pro");
	}

	@Test
	void validatesCurrentProCandidateWithOneStructuredRequestAndReadback() throws Exception {
		validateCandidate("gemini-3.1-pro-preview", "current-pro");
	}

	private void validateCandidate(String model, String label) throws Exception {
		Assumptions.assumeTrue("true".equals(System.getenv("GEMINI_API_OPERATION_CANDIDATE_TEST")),
				"Explicitly authorize synthetic operational candidate requests.");
		GeminiApiDiagnosticSupport.requireEnabled();
		var schema = JsonParser.parseString("""
				{"type":"object","properties":{"ok":{"type":"boolean"}},"required":["ok"]}
				""").getAsJsonObject();
		var response = GeminiApiDiagnosticSupport.structured(
				label, model, GeminiApiDiagnosticSupport.INSTRUCTION, GeminiApiDiagnosticSupport.INPUT, schema);
		var output = GeminiApiDiagnosticSupport.saveOutput(label, response);
		assertTrue(JsonParser.parseString(Files.readString(output)).getAsJsonObject().get("ok").getAsBoolean());
	}
}
