package control.evaluation;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

class GeminiClassicApiDiagnosticTest {
	@Test
	void comparesTheSameModelAndSyntheticInputAgainstGenerateContent() throws Exception {
		GeminiApiDiagnosticSupport.requireEnabled();
		var response = GeminiApiDiagnosticSupport.classicGeneration();
		var parts = response.getAsJsonArray("candidates").get(0).getAsJsonObject()
				.getAsJsonObject("content").getAsJsonArray("parts");
		StringBuilder text = new StringBuilder();
		for (var value : parts) {
			var part = value.getAsJsonObject();
			if (part.has("text") && (!part.has("thought") || !part.get("thought").getAsBoolean())) {
				text.append(part.get("text").getAsString());
			}
		}
		JsonObject normalized = new JsonObject();
		normalized.addProperty("output_text", text.toString());
		var output = GeminiApiDiagnosticSupport.saveOutput("minimal-generate-content", normalized);
		assertTrue(JsonParser.parseString(Files.readString(output)).getAsJsonObject().get("ok").getAsBoolean());
	}
}
