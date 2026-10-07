package control.evaluation;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.Set;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

class GeminiEvaluationSmokeTest {
	private static final String SMOKE_TEST_MODEL = GeminiModelCatalog.DEFAULT_MODEL;

	@Test
	void evaluatesSyntheticPayloadAgainstGeminiOnlyWhenExplicitlyEnabled() throws Exception {
		Assumptions.assumeTrue(Boolean.parseBoolean(System.getenv("GEMINI_API_SMOKE_TEST")),
				"Set GEMINI_API_SMOKE_TEST=true to make a billable Gemini API request.");
		String apiKey = System.getenv("GEMINI_API_KEY");
		assertNotNull(apiKey, "GEMINI_API_KEY must be set for the explicit API smoke test.");
		assertFalse(apiKey.isBlank(), "GEMINI_API_KEY must not be blank.");

		JsonObject payload = new JsonObject();
		JsonObject metadata = new JsonObject();
		metadata.addProperty("request_id", "synthetic-smoke-test");
		metadata.addProperty("feature_name", "synthetic_evaluation_smoke_test");
		metadata.addProperty("task_id", "synthetic");
		payload.add("metadata", metadata);

		JsonObject task = new JsonObject();
		task.addProperty("task_title", "合成データ疎通確認");
		task.addProperty("task_description", "入力された整数を表示する短い練習課題です。");
		task.addProperty("constraints", "個人情報や実在の課題データは含みません。");
		task.addProperty("language", "python");
		JsonArray testCases = new JsonArray();
		JsonObject testCase = new JsonObject();
		testCase.addProperty("input", "1");
		testCase.addProperty("expected_output", "1");
		testCases.add(testCase);
		task.add("testcases", testCases);
		payload.add("task", task);

		JsonObject rubric = new JsonObject();
		rubric.addProperty("viewpoint_definitions",
				"思考力・判断力・表現力、および主体的に学習に取り組む態度を各1〜5で評価します。");
		payload.add("rubric", rubric);

		payload.add("timeline_logs", new JsonArray());
		JsonObject submission = new JsonObject();
		submission.addProperty("submitted_code", "print(input())");
		submission.addProperty("submitted_at", "2026-01-01T00:00:00");
		payload.add("submission", submission);

		JsonObject promptSettings = new JsonObject();
		promptSettings.addProperty("common_prompt",
				"合成データを用いたAPI疎通確認です。簡潔に根拠を説明してください。");
		promptSettings.addProperty("additional_evaluation_instruction", "");
		promptSettings.add("ambiguity_item_instructions", new JsonArray());
		promptSettings.add("evaluation_examples", new JsonArray());
		payload.add("prompt_evaluation_settings", promptSettings);

		GeminiEvaluationClient client = new GeminiEvaluationClient();
		com.google.gson.JsonObject rawResponse = client.generate(SMOKE_TEST_MODEL, payload);
		String output = client.extractOutputText(rawResponse);
		EvaluationResponseValidator.parseAndValidate(output, Set.of(), Set.of());
	}
}
