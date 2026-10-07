package control.evaluation;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import com.google.gson.JsonParser;

class GeminiStructuredApiDiagnosticTest {
	@Test
	void generatesTheSameMinimalInputWithApplicationStructuredTransport() throws Exception {
		GeminiApiDiagnosticSupport.requireEnabled();
		var schema = JsonParser.parseString("""
				{"type":"object","properties":{"ok":{"type":"boolean"}},"required":["ok"]}
				""").getAsJsonObject();
		var response = GeminiApiDiagnosticSupport.structured("minimal-structured",
				GeminiModelCatalog.DEFAULT_MODEL, GeminiApiDiagnosticSupport.INSTRUCTION,
				GeminiApiDiagnosticSupport.INPUT, schema);
		var output = GeminiApiDiagnosticSupport.saveOutput("minimal-structured", response);
		assertTrue(JsonParser.parseString(Files.readString(output)).getAsJsonObject().get("ok").getAsBoolean());
	}

	@Test
	void rechecksPrimaryModelAfterTheSeparatelyApprovedComparison() throws Exception {
		Assumptions.assumeTrue("true".equals(System.getenv("GEMINI_API_PRIMARY_RECHECK_TEST")),
				"Explicitly authorize one primary-model recheck after model comparison.");
		GeminiApiDiagnosticSupport.requireEnabled();
		var schema = JsonParser.parseString("""
				{"type":"object","properties":{"ok":{"type":"boolean"}},"required":["ok"]}
				""").getAsJsonObject();
		var response = GeminiApiDiagnosticSupport.structured("minimal-structured-primary-recheck",
				GeminiModelCatalog.DEFAULT_MODEL, GeminiApiDiagnosticSupport.INSTRUCTION,
				GeminiApiDiagnosticSupport.INPUT, schema);
		var output = GeminiApiDiagnosticSupport.saveOutput("minimal-structured-primary-recheck", response);
		assertTrue(JsonParser.parseString(Files.readString(output)).getAsJsonObject().get("ok").getAsBoolean());
	}
}
