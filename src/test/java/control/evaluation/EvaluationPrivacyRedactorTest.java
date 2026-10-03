package control.evaluation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

class EvaluationPrivacyRedactorTest {
	@Test
	void redactsKnownIdentifiersAndCommonContactInformationRecursively() {
		JsonObject source = JsonParser.parseString("""
				{
				  "code":"# 葵\nprint('aoi@example.jp')",
				  "logs":[{"text":"電話 090-1234-5678"}]
				}
				""").getAsJsonObject();

		JsonObject result = EvaluationPrivacyRedactor.redact(source, List.of("葵", "student-17"));

		String redacted = result.toString();
		assertFalse(redacted.contains("葵"));
		assertFalse(redacted.contains("aoi@example.jp"));
		assertFalse(redacted.contains("090-1234-5678"));
		assertEquals("# [REDACTED]\nprint('[REDACTED_EMAIL]')", result.get("code").getAsString());
	}
}
