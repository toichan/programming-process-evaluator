package control.evaluation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonParser;

class GeminiMinimalApiDiagnosticTest {
	@Test
	void checksAuthenticatedModelMetadata() throws Exception {
		GeminiApiDiagnosticSupport.requireEnabled();
		var metadata = GeminiApiDiagnosticSupport.modelMetadata();
		assertEquals("models/" + GeminiModelCatalog.DEFAULT_MODEL, metadata.get("name").getAsString());
	}

	@Test
	void generatesMinimalSyntheticJsonWithoutResponseSchema() throws Exception {
		GeminiApiDiagnosticSupport.requireEnabled();
		var response = GeminiApiDiagnosticSupport.plainGeneration();
		var output = GeminiApiDiagnosticSupport.saveOutput("minimal-plain", response);
		assertTrue(JsonParser.parseString(Files.readString(output)).getAsJsonObject().get("ok").getAsBoolean());
	}
}
