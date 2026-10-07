package control.evaluation;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import com.google.gson.JsonParser;

class GeminiModelComparisonDiagnosticTest {
	@Test
	void comparesApprovedAlternateModelWithoutChangingApplicationConfiguration() throws Exception {
		Assumptions.assumeTrue("true".equals(System.getenv("GEMINI_API_MODEL_COMPARISON_TEST")),
				"Separately authorize one synthetic request to gemini-3.8-flash.");
		GeminiApiDiagnosticSupport.requireEnabled();
		var schema = JsonParser.parseString("""
				{"type":"object","properties":{"ok":{"type":"boolean"}},"required":["ok"]}
				""").getAsJsonObject();
		var response = GeminiApiDiagnosticSupport.structured("minimal-structured-model-comparison",
				"gemini-3.8-flash", GeminiApiDiagnosticSupport.INSTRUCTION,
				GeminiApiDiagnosticSupport.INPUT, schema);
		var output = GeminiApiDiagnosticSupport.saveOutput("minimal-structured-model-comparison", response);
		assertTrue(JsonParser.parseString(Files.readString(output)).getAsJsonObject().get("ok").getAsBoolean());
	}
}
