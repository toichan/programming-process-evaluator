package control.teacher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import entity.StandardRubric;
import entity.StandardRubric.Criterion;
import entity.StandardRubric.Dimension;
import entity.StandardRubric.Level;
import entity.TeacherPromptVersion.FluctuationItem;

class TeacherPromptAiClientTest {
	private static final String VALID_PROMPT = "{\"ambiguity_items\":[{\"title\":\"明確化\","
			+ "\"ambiguity_reason\":\"判断が分かれる\",\"risk_level\":\"medium\","
			+ "\"clarification_question\":\"境界値をどう扱いますか\","
			+ "\"recommended_rule_text\":\"境界値テストを確認する\"}]}";

	@Test
	void retriesInvalidStructuredOutputBeforeReturningValidatedFluctuations() throws Exception {
		AtomicInteger calls = new AtomicInteger();
		TeacherPromptAiClient client = new TeacherPromptAiClient((model, system, input, schema) -> {
			assertEquals("gemini-3.7-flash", model);
			if (calls.getAndIncrement() == 0) {
				return response("{\"ambiguity_items\":[{\"title\":\"欠落\",\"risk_level\":\"medium\"}]}");
			}
			return response(VALID_PROMPT);
		});

		List<FluctuationItem> items = client.generateFluctuations(
				"gemini-3.7-flash", taskContext(), "評価指示", null, standardRubric());

		assertEquals(2, calls.get());
		assertEquals(1, items.size());
		assertEquals("明確化", items.getFirst().title());
		assertEquals("中", items.getFirst().level());
		assertTrue(items.getFirst().example().contains("推奨ルール案"));
		assertEquals("pending", items.getFirst().resolutionStatus());
	}

	@Test
	void evaluationExamplesUseTaskSpecificSyntheticProfilesAndValidateEverySample() throws Exception {
		List<String> requests = new ArrayList<>();
		TeacherPromptAiClient client = new TeacherPromptAiClient((model, system, input, schema) -> {
			requests.add(input);
			JsonObject request = JsonParser.parseString(input).getAsJsonObject();
			JsonArray samples = request.getAsJsonArray("synthetic_submission_samples");
			JsonObject criterion = request.getAsJsonObject("standard_rubric")
					.getAsJsonArray("dimensions").get(0).getAsJsonObject()
					.getAsJsonArray("criteria").get(0).getAsJsonObject();
			assertEquals("文法デバッグ能力", criterion.get("name").getAsString());
			JsonArray levels = criterion.getAsJsonArray("levels");
			assertEquals(5, levels.size());
			assertEquals(5, levels.get(0).getAsJsonObject().get("value").getAsInt());
			assertEquals("説明", levels.get(0).getAsJsonObject().get("description").getAsString());
			JsonArray results = new JsonArray();
			for (var sample : samples) {
				JsonObject result = new JsonObject();
				result.add("sample_id", sample.getAsJsonObject().get("sample_id"));
				result.addProperty("synthetic_code", "print('fictional sample')");
				result.addProperty("scenario", sample.getAsJsonObject().get("description").getAsString());
				result.addProperty("predicted_testcase_result", "仕様からの推定。実行していない。");
				JsonObject scores = new JsonObject();
				scores.addProperty("thinking_expression_level", 3);
				scores.addProperty("proactive_attitude_level", 4);
				result.add("scores", scores);
				JsonObject reasons = new JsonObject();
				reasons.addProperty("thinking_expression_reason", "合成例の理由");
				reasons.addProperty("proactive_attitude_reason", "合成例の理由");
				result.add("reasons", reasons);
				results.add(result);
			}
			JsonObject output = new JsonObject();
			output.add("simulated_results", results);
			output.add("variance_alerts", new JsonArray());
			output.add("pre_reeval_checklist", new JsonArray());
			return response(output.toString());
		});

		JsonObject output = client.generateEvaluationExamples(
				"gemini-3.7-flash", taskContext(), "評価指示", null, standardRubric(),
				List.of(new FluctuationItem(
						8, "境界条件", "高", "範囲外の扱い", "確認質問と明確化ルール",
						"境界値の扱いを明記する", "resolved", 1)),
				TeacherPromptExampleFactory.createSyntheticSamples(taskContext()));

		assertEquals(1, requests.size());
		assertEquals(2, output.getAsJsonArray("simulated_results").size());
		JsonObject request = JsonParser.parseString(requests.getFirst()).getAsJsonObject();
		JsonArray samples = request.getAsJsonArray("synthetic_submission_samples");
		assertEquals("task-compliant", samples.get(0).getAsJsonObject().get("profile").getAsString());
		assertEquals("boundary-case", samples.get(1).getAsJsonObject().get("profile").getAsString());
		assertFalse(requests.getFirst().contains("student_id"));
		assertTrue(requests.getFirst().contains("task_context"));
		assertTrue(request.getAsJsonArray("ambiguity_resolutions").get(0).getAsJsonObject()
				.get("teacher_resolution").getAsString().contains("境界値"));
	}

	@Test
	void rejectsUnapprovedModelsBeforeCallingProvider() {
		AtomicInteger calls = new AtomicInteger();
		TeacherPromptAiClient client = new TeacherPromptAiClient((model, system, input, schema) -> {
			calls.incrementAndGet();
			return response(VALID_PROMPT);
		});

		assertThrows(IllegalArgumentException.class, () -> client.generateFluctuations(
				"untrusted-model", taskContext(), "評価指示", null, standardRubric()));
		assertThrows(IllegalArgumentException.class, () -> client.generateFluctuations(
				"gemini-2.5-pro", taskContext(), "評価指示", null, standardRubric()));
		assertThrows(IllegalArgumentException.class, () -> client.generateFluctuations(
				"gemini-2.5-flash", taskContext(), "評価指示", null, standardRubric()));
		assertEquals(0, calls.get());
	}

	private static JsonObject response(String text) {
		JsonObject response = new JsonObject();
		response.addProperty("output_text", text);
		return response;
	}

	private static JsonObject taskContext() {
		JsonObject task = new JsonObject();
		task.addProperty("title", "合成課題");
		task.addProperty("description", "入力を処理する");
		return task;
	}

	private static StandardRubric standardRubric() {
		List<Level> levels = List.of(
				new Level(5, "レベル5", "説明"),
				new Level(4, "レベル4", "説明"),
				new Level(3, "レベル3", "説明"),
				new Level(2, "レベル2", "説明"),
				new Level(1, "レベル1", "説明"));
		List<Criterion> thinking = List.of(
				new Criterion("文法デバッグ能力", levels),
				new Criterion("論理デバッグ能力", levels),
				new Criterion("アルゴリズムの設計と実装", levels),
				new Criterion("コードの可読性", levels));
		List<Criterion> attitude = List.of(
				new Criterion("課題への粘り強さ", levels),
				new Criterion("課題解決への意欲", levels));
		return new StandardRubric(
				StandardRubric.TITLE,
				StandardRubric.VERSION,
				List.of(
						new Dimension("thinking", "思考力・判断力・表現力", thinking),
						new Dimension("attitude", "主体的に学習に取り組む態度", attitude)));
	}
}
