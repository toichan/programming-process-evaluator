package control.teacher;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import control.evaluation.EvaluationProviderException;
import control.evaluation.GeminiEvaluationClient;
import control.evaluation.GeminiModelCatalog;
import control.evaluation.EvaluationRetryPolicy;
import entity.StandardRubric;
import entity.StandardRubric.Criterion;
import entity.StandardRubric.Dimension;
import entity.TeacherPromptVersion.FluctuationItem;

public final class TeacherPromptAiClient {
	private final StructuredGenerator generator;

	public TeacherPromptAiClient() {
		this(new GeminiEvaluationClient()::generateStructuredOutput);
	}

	TeacherPromptAiClient(StructuredGenerator generator) {
		this.generator = Objects.requireNonNull(generator);
	}

	public List<FluctuationItem> generateFluctuations(
			String modelId,
			JsonObject taskContext,
			String commonPrompt,
			String additionalInstruction,
			StandardRubric rubric) throws EvaluationProviderException {
		requireModel(modelId);
		JsonObject input = new JsonObject();
		input.add("task_context", taskContext.deepCopy());
		input.addProperty("evaluation_prompt", commonPrompt);
		input.addProperty("additional_instructions", additionalInstruction == null ? "" : additionalInstruction);
		input.add("standard_rubric", rubricJson(rubric));
		input.add("synthetic_evaluation_samples", TeacherPromptExampleFactory.createSyntheticSamples(taskContext));
		return generate(
				modelId,
				"プログラミング課題の評価ルールを分析し、評価者間で解釈が分かれる点を抽出してください。"
						+ "各項目にタイトル、曖昧さの理由、risk_level（high/medium/low）、明確化の質問、"
						+ "推奨ルール案を含めてください。入力は合成シナリオであり生徒データは含まれません。"
						+ "指定JSONだけを返してください。",
				input,
				fluctuationSchema(),
				TeacherPromptAiClient::parseFluctuations);
	}

	public JsonObject generateEvaluationExamples(
			String modelId,
			JsonObject taskContext,
			String commonPrompt,
			String additionalInstruction,
			StandardRubric rubric,
			List<FluctuationItem> fluctuationItems,
			JsonArray samples) throws EvaluationProviderException {
		requireModel(modelId);
		if (fluctuationItems == null || fluctuationItems.isEmpty() || samples == null || samples.isEmpty()) {
			throw new IllegalArgumentException("Resolved fluctuation guidance and synthetic samples are required.");
		}
		JsonObject input = new JsonObject();
		input.add("task_context", taskContext.deepCopy());
		input.addProperty("evaluation_prompt", commonPrompt);
		input.addProperty("additional_instructions", additionalInstruction == null ? "" : additionalInstruction);
		input.add("standard_rubric", rubricJson(rubric));
		input.add("ambiguity_resolutions", fluctuationResolutionsJson(fluctuationItems));
		input.add("synthetic_submission_samples", samples.deepCopy());
		return generate(
				modelId,
				"あなたは評価ルールの確認用シミュレーターです。入力された架空シナリオごとに課題に沿う"
						+ "合成コード例を作成して採点してください。実在の生徒データを使わず、コードを実行したと"
						+ "主張せず、テスト結果は課題仕様から推定した内容と明記してください。"
						+ "各例に本番の評価入力と同じ形式のsynthetic_logsを複数作成し、各ログにlog_id、"
						+ "event_type、observed_at、snapshotを含めてください。少なくとも1件は実行記録として"
						+ "execution_id、execution_status、error_type、standard_input、standard_output、"
						+ "standard_errorを含めてください。これらは合成された記録であり実際の実行結果ではありません。"
						+ "evaluationには本番AI評価と同じscores、reasons、process_analysis、confidence、"
						+ "evidence_refs、warningsをすべて含め、評価根拠のlog_idはsynthetic_logs内のIDを参照してください。"
						+ "入力のsample_idをそのまま使い、指定JSONだけを返してください。",
				input,
				exampleSchema(),
				output -> {
					validateExamples(output, samples);
					return output;
				});
	}

	private <T> T generate(
			String modelId,
			String systemInstruction,
			JsonObject input,
			JsonObject schema,
			Function<JsonObject, T> validator) throws EvaluationProviderException {
		EvaluationProviderException lastProviderFailure = null;
		IllegalArgumentException lastOutputFailure = null;
		EvaluationRetryPolicy retries = new EvaluationRetryPolicy();
		for (int attempt = 0; attempt < EvaluationRetryPolicy.MAX_ATTEMPTS; attempt++) {
			JsonObject requestInput = attempt < 2 ? input : simplified(input);
			try {
				JsonObject raw = generator.generate(modelId, systemInstruction, requestInput.toString(), schema);
				String response = new GeminiEvaluationClient().extractOutputText(raw);
				return validator.apply(JsonParser.parseString(response).getAsJsonObject());
			} catch (EvaluationProviderException failure) {
				lastProviderFailure = failure;
				if (!failure.isRetryable() || attempt == 2) {
					throw failure;
				}
				retries.awaitNext(attempt, failure);
			} catch (RuntimeException failure) {
				lastOutputFailure = new IllegalArgumentException(
						"AI returned an invalid structured prompt-design response.", failure);
				if (attempt == 2) {
					throw lastOutputFailure;
				}
				retries.awaitNext(attempt, lastOutputFailure);
			}
		}
		if (lastProviderFailure != null) {
			throw lastProviderFailure;
		}
		throw Objects.requireNonNull(lastOutputFailure);
	}

	private static JsonObject simplified(JsonObject input) {
		JsonObject result = input.deepCopy();
		JsonObject task = result.getAsJsonObject("task_context");
		if (task != null) {
			task.remove("features");
			task.remove("test_cases");
			task.remove("initial_code");
		}
		return result;
	}

	private static List<FluctuationItem> parseFluctuations(JsonObject output) {
		if (!output.has("ambiguity_items") || !output.get("ambiguity_items").isJsonArray()) {
			throw new IllegalArgumentException("AI did not return fluctuation items.");
		}
		JsonArray items = output.getAsJsonArray("ambiguity_items");
		if (items.size() < 1 || items.size() > 20) {
			throw new IllegalArgumentException("AI returned an invalid fluctuation item count.");
		}
		List<FluctuationItem> parsed = new ArrayList<>();
		for (int index = 0; index < items.size(); index++) {
			JsonElement element = items.get(index);
			if (!element.isJsonObject()) {
				throw new IllegalArgumentException("AI returned an invalid fluctuation item.");
			}
			JsonObject item = element.getAsJsonObject();
			String title = requiredText(item, "title", 200);
			String riskLevel = requiredText(item, "risk_level", 20);
			String level = switch (riskLevel) {
				case "low" -> "低";
				case "medium" -> "中";
				case "high" -> "高";
				default -> throw new IllegalArgumentException("AI returned an unsupported fluctuation level.");
			};
			String ambiguityReason = requiredText(item, "ambiguity_reason", 2000);
			String question = requiredText(item, "clarification_question", 600);
			String rule = requiredText(item, "recommended_rule_text", 1300);
			String example = "明確化の質問: " + question + "\n推奨ルール案: " + rule;
			parsed.add(new FluctuationItem(
					0, title, level, ambiguityReason, example, null, "pending", index + 1));
		}
		return List.copyOf(parsed);
	}

	private static void validateExamples(JsonObject output, JsonArray samples) {
		if (!output.has("simulated_results") || !output.get("simulated_results").isJsonArray()
				|| output.getAsJsonArray("simulated_results").size() != samples.size()
				|| !output.has("variance_alerts") || !output.get("variance_alerts").isJsonArray()
				|| !output.has("pre_reeval_checklist") || !output.get("pre_reeval_checklist").isJsonArray()) {
			throw new IllegalArgumentException("AI returned an invalid evaluation example response.");
		}
		java.util.Set<String> expectedIds = new java.util.HashSet<>();
		for (JsonElement sample : samples) {
			expectedIds.add(sample.getAsJsonObject().get("sample_id").getAsString());
		}
		java.util.Set<String> actualIds = new java.util.HashSet<>();
		for (JsonElement element : output.getAsJsonArray("simulated_results")) {
			if (!element.isJsonObject()) {
				throw new IllegalArgumentException("AI returned an invalid simulated result.");
			}
			JsonObject result = element.getAsJsonObject();
			String sampleId = requiredText(result, "sample_id", 40);
			if (!actualIds.add(sampleId) || !result.has("synthetic_logs")
					|| !result.get("synthetic_logs").isJsonArray() || !result.has("evaluation")
					|| !result.get("evaluation").isJsonObject()) {
				throw new IllegalArgumentException("AI returned an invalid simulated result.");
			}
			requiredText(result, "synthetic_code", 12_000);
			requiredText(result, "scenario", 1_000);
			requiredText(result, "predicted_testcase_result", 2_000);
			SyntheticEvidenceIds evidenceIds = validateSyntheticLogs(result.getAsJsonArray("synthetic_logs"));
			validateProductionEvaluation(result.getAsJsonObject("evaluation"), evidenceIds);
		}
		if (!actualIds.equals(expectedIds)) {
			throw new IllegalArgumentException("AI returned results for unexpected synthetic samples.");
		}
		validateTextArray(output.getAsJsonArray("variance_alerts"), 20);
		validateTextArray(output.getAsJsonArray("pre_reeval_checklist"), 20);
	}

	private static SyntheticEvidenceIds validateSyntheticLogs(JsonArray logs) {
		if (logs.size() < 2 || logs.size() > 100) {
			throw new IllegalArgumentException("AI returned an invalid synthetic log count.");
		}
		java.util.Set<Integer> logIds = new java.util.HashSet<>();
		java.util.Set<Integer> executionIds = new java.util.HashSet<>();
		String[] executionFields = {
				"execution_id", "execution_status", "error_type",
				"standard_input", "standard_output", "standard_error"
		};
		boolean hasExecution = false;
		for (JsonElement element : logs) {
			if (!element.isJsonObject()) {
				throw new IllegalArgumentException("AI returned an invalid synthetic log.");
			}
			JsonObject log = element.getAsJsonObject();
			int logId = requiredInteger(log, "log_id", 1, Integer.MAX_VALUE);
			if (!logIds.add(logId)) {
				throw new IllegalArgumentException("AI returned duplicate synthetic log IDs.");
			}
			requiredText(log, "event_type", 40);
			if (!List.of("periodic_snapshot", "manual_save", "submission", "edit")
					.contains(log.get("event_type").getAsString())) {
				throw new IllegalArgumentException("AI returned an unsupported synthetic log event type.");
			}
			requiredText(log, "observed_at", 80);
			requiredText(log, "snapshot", 12_000);
			boolean hasAnyExecutionField = java.util.Arrays.stream(executionFields).anyMatch(log::has);
			if (hasAnyExecutionField) {
				int executionId = requiredInteger(log, "execution_id", 1, Integer.MAX_VALUE);
				if (!executionIds.add(executionId)) {
					throw new IllegalArgumentException("AI returned duplicate synthetic execution IDs.");
				}
				String executionStatus = requiredText(log, "execution_status", 40);
				if (!List.of("not_run", "succeeded", "failed", "timed_out").contains(executionStatus)) {
					throw new IllegalArgumentException("AI returned an unsupported synthetic execution status.");
				}
				requiredTextAllowEmpty(log, "error_type", 200);
				requiredTextAllowEmpty(log, "standard_input", 12_000);
				requiredTextAllowEmpty(log, "standard_output", 12_000);
				requiredTextAllowEmpty(log, "standard_error", 12_000);
				hasExecution = true;
			}
		}
		if (!hasExecution) {
			throw new IllegalArgumentException("AI omitted execution input/output from synthetic logs.");
		}
		return new SyntheticEvidenceIds(logIds, executionIds);
	}

	private static void validateProductionEvaluation(JsonObject evaluation, SyntheticEvidenceIds evidenceIds) {
		JsonObject scores = requiredObject(evaluation, "scores");
		for (String key : List.of("thinking_expression_level", "proactive_attitude_level")) {
			requiredInteger(scores, key, 1, 5);
		}
		JsonObject reasons = requiredObject(evaluation, "reasons");
		requiredText(reasons, "thinking_expression_reason", 5_000);
		requiredText(reasons, "proactive_attitude_reason", 5_000);
		JsonObject analysis = requiredObject(evaluation, "process_analysis");
		requiredText(analysis, "pattern_label", 120);
		validateProcessPoints(requiredArray(analysis, "turning_points"), evidenceIds.logIds());
		validateProcessPoints(requiredArray(analysis, "stagnation_points"), evidenceIds.logIds());
		validateTextArray(requiredArray(analysis, "teacher_support_suggestions"), 3, 2_000);
		if (!evaluation.has("confidence") || !evaluation.get("confidence").isJsonPrimitive()
				|| !evaluation.getAsJsonPrimitive("confidence").isNumber()) {
			throw new IllegalArgumentException("AI returned an invalid evaluation confidence.");
		}
		double confidence = evaluation.get("confidence").getAsDouble();
		if (!Double.isFinite(confidence) || confidence < 0 || confidence > 1) {
			throw new IllegalArgumentException("AI returned an invalid evaluation confidence.");
		}
		validateEvidenceReferences(requiredArray(evaluation, "evidence_refs"), evidenceIds);
		validateTextArray(requiredArray(evaluation, "warnings"), 50, 2_000);
	}

	private static void validateEvidenceReferences(JsonArray references, SyntheticEvidenceIds evidenceIds) {
		if (references.size() > 100) {
			throw new IllegalArgumentException("AI returned too many evaluation evidence references.");
		}
		java.util.Set<String> seen = new java.util.HashSet<>();
		for (JsonElement element : references) {
			if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
				throw new IllegalArgumentException("AI returned an invalid evaluation evidence reference.");
			}
			String reference = element.getAsString();
			boolean execution = reference.startsWith("run_");
			String idText = execution ? reference.substring(4)
					: reference.startsWith("log_") ? reference.substring(4) : reference;
			if (!idText.matches("[0-9]{1,10}")) {
				throw new IllegalArgumentException("AI returned an invalid evaluation evidence reference.");
			}
			int id = Integer.parseInt(idText);
			java.util.Set<Integer> knownIds = execution ? evidenceIds.executionIds() : evidenceIds.logIds();
			String uniqueId = (execution ? "run_" : "log_") + id;
			if (!knownIds.contains(id) || !seen.add(uniqueId)) {
				throw new IllegalArgumentException("AI referenced unknown or duplicate synthetic evidence.");
			}
		}
	}

	private static void validateProcessPoints(JsonArray points, java.util.Set<Integer> logIds) {
		if (points == null || points.size() > 100) {
			throw new IllegalArgumentException("AI returned invalid process-analysis points.");
		}
		for (JsonElement element : points) {
			if (!element.isJsonObject()) {
				throw new IllegalArgumentException("AI returned invalid process-analysis points.");
			}
			JsonObject point = element.getAsJsonObject();
			int logId = requiredInteger(point, "log_id", 1, Integer.MAX_VALUE);
			if (!logIds.contains(logId)) {
				throw new IllegalArgumentException("AI referenced an unknown synthetic log.");
			}
			requiredText(point, "summary", 2_000);
		}
	}

	private static int requiredInteger(JsonObject object, String name, int minimum, int maximum) {
		if (!object.has(name) || !object.get(name).isJsonPrimitive()
				|| !object.getAsJsonPrimitive(name).isNumber()) {
			throw new IllegalArgumentException("AI output is missing an integer field.");
		}
		double value = object.get(name).getAsDouble();
		if (!Double.isFinite(value) || value != Math.rint(value) || value < minimum || value > maximum) {
			throw new IllegalArgumentException("AI output contains an integer outside the supported range.");
		}
		return (int) value;
	}

	private static JsonObject requiredObject(JsonObject object, String name) {
		if (!object.has(name) || !object.get(name).isJsonObject()) {
			throw new IllegalArgumentException("AI output is missing an object field.");
		}
		return object.getAsJsonObject(name);
	}

	private static JsonArray requiredArray(JsonObject object, String name) {
		if (!object.has(name) || !object.get(name).isJsonArray()) {
			throw new IllegalArgumentException("AI output is missing an array field.");
		}
		return object.getAsJsonArray(name);
	}

	private static void validateTextArray(JsonArray values, int maximum) {
		validateTextArray(values, maximum, 1_000);
	}

	private static void validateTextArray(JsonArray values, int maximum, int maximumTextLength) {
		if (values == null || values.size() > maximum) {
			throw new IllegalArgumentException("AI returned too many evaluation notes.");
		}
		for (JsonElement value : values) {
			if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()
					|| value.getAsString().isBlank() || value.getAsString().length() > maximumTextLength) {
				throw new IllegalArgumentException("AI returned an invalid evaluation note.");
			}
		}
	}

	private record SyntheticEvidenceIds(java.util.Set<Integer> logIds, java.util.Set<Integer> executionIds) { }

	private static String requiredText(JsonObject object, String name, int maximumLength) {
		if (!object.has(name) || !object.get(name).isJsonPrimitive()
				|| !object.getAsJsonPrimitive(name).isString()) {
			throw new IllegalArgumentException("AI output is missing a text field.");
		}
		String value = object.get(name).getAsString().trim();
		if (value.isEmpty() || value.length() > maximumLength) {
			throw new IllegalArgumentException("AI output contains invalid text.");
		}
		return value;
	}

	private static String requiredTextAllowEmpty(JsonObject object, String name, int maximumLength) {
		if (!object.has(name) || !object.get(name).isJsonPrimitive()
				|| !object.getAsJsonPrimitive(name).isString()
				|| object.get(name).getAsString().length() > maximumLength) {
			throw new IllegalArgumentException("AI output contains invalid text.");
		}
		return object.get(name).getAsString();
	}

	private static JsonObject rubricJson(StandardRubric rubric) {
		JsonArray dimensions = new JsonArray();
		for (Dimension dimension : rubric.dimensions()) {
			JsonObject dimensionJson = new JsonObject();
			dimensionJson.addProperty("code", dimension.code());
			dimensionJson.addProperty("label", dimension.label());
			JsonArray criteria = new JsonArray();
			for (Criterion criterion : dimension.criteria()) {
				JsonObject criterionJson = new JsonObject();
				criterionJson.addProperty("name", criterion.name());
				JsonArray levels = new JsonArray();
				for (var level : criterion.levels()) {
					JsonObject levelJson = new JsonObject();
					levelJson.addProperty("value", level.value());
					levelJson.addProperty("label", level.label());
					levelJson.addProperty("description", level.description());
					levels.add(levelJson);
				}
				criterionJson.add("levels", levels);
				criteria.add(criterionJson);
			}
			dimensionJson.add("criteria", criteria);
			dimensions.add(dimensionJson);
		}
		JsonObject result = new JsonObject();
		result.addProperty("title", rubric.title());
		result.addProperty("version", rubric.version());
		result.add("dimensions", dimensions);
		return result;
	}

	private static JsonArray fluctuationResolutionsJson(List<FluctuationItem> items) {
		JsonArray resolutions = new JsonArray();
		for (FluctuationItem item : items) {
			JsonObject resolution = new JsonObject();
			resolution.addProperty("title", item.title());
			resolution.addProperty("risk_level", item.level());
			resolution.addProperty("ambiguity_reason", item.description());
			resolution.addProperty("clarification_and_recommended_rule", item.example());
			resolution.addProperty("teacher_resolution", item.teacherResolution());
			resolution.addProperty("resolution_status", item.resolutionStatus());
			resolutions.add(resolution);
		}
		return resolutions;
	}

	private static JsonObject fluctuationSchema() {
		JsonObject item = objectSchema(
				properties(Map.of(
						"title", textSchema(),
						"ambiguity_reason", textSchema(),
						"risk_level", enumSchema("high", "medium", "low"),
						"clarification_question", textSchema(),
						"recommended_rule_text", textSchema())),
				"title", "ambiguity_reason", "risk_level", "clarification_question", "recommended_rule_text");
		return objectSchema(properties(Map.of("ambiguity_items", arraySchema(item))), "ambiguity_items");
	}

	private static JsonObject exampleSchema() {
		JsonObject scores = objectSchema(
				properties(Map.of(
						"thinking_expression_level", integerSchema(),
						"proactive_attitude_level", integerSchema())),
				"thinking_expression_level", "proactive_attitude_level");
		JsonObject reasons = objectSchema(
				properties(Map.of(
						"thinking_expression_reason", textSchema(),
						"proactive_attitude_reason", textSchema())),
				"thinking_expression_reason", "proactive_attitude_reason");
		JsonObject processPoint = objectSchema(
				properties(Map.of("log_id", integerSchema(), "summary", textSchema())),
				"log_id", "summary");
		JsonObject processAnalysis = objectSchema(
				properties(Map.of(
						"pattern_label", textSchema(),
						"turning_points", arraySchema(processPoint),
						"stagnation_points", arraySchema(processPoint),
						"teacher_support_suggestions", arraySchema(textSchema()))),
				"pattern_label", "turning_points", "stagnation_points", "teacher_support_suggestions");
		JsonObject confidence = new JsonObject();
		confidence.addProperty("type", "number");
		JsonObject evaluation = objectSchema(
				properties(Map.of(
						"scores", scores,
						"reasons", reasons,
						"process_analysis", processAnalysis,
						"confidence", confidence,
						"evidence_refs", arraySchema(textSchema()),
						"warnings", arraySchema(textSchema()))),
				"scores", "reasons", "process_analysis", "confidence", "evidence_refs", "warnings");
		JsonObject logProperties = properties(Map.of(
				"log_id", integerSchema(),
				"event_type", enumSchema("periodic_snapshot", "manual_save", "submission", "edit"),
				"observed_at", textSchema(),
				"snapshot", textSchema(),
				"execution_id", integerSchema(),
				"execution_status", enumSchema("not_run", "succeeded", "failed", "timed_out"),
				"error_type", textSchema(),
				"standard_input", textSchema(),
				"standard_output", textSchema(),
				"standard_error", textSchema()));
		JsonObject syntheticLog = objectSchema(
				logProperties, "log_id", "event_type", "observed_at", "snapshot");
		JsonObject result = objectSchema(
				properties(Map.of(
						"sample_id", textSchema(),
						"synthetic_code", textSchema(),
						"scenario", textSchema(),
						"predicted_testcase_result", textSchema(),
						"synthetic_logs", arraySchema(syntheticLog),
						"evaluation", evaluation)),
				"sample_id", "synthetic_code", "scenario", "predicted_testcase_result", "synthetic_logs", "evaluation");
		return objectSchema(properties(Map.of(
				"simulated_results", arraySchema(result),
				"variance_alerts", arraySchema(textSchema()),
				"pre_reeval_checklist", arraySchema(textSchema()))),
				"simulated_results", "variance_alerts", "pre_reeval_checklist");
	}

	private static JsonObject properties(Map<String, JsonObject> fields) {
		JsonObject properties = new JsonObject();
		fields.forEach(properties::add);
		return properties;
	}

	private static JsonObject objectSchema(JsonObject properties, String... required) {
		JsonObject schema = new JsonObject();
		schema.addProperty("type", "object");
		schema.add("properties", properties);
		JsonArray fields = new JsonArray();
		for (String field : required) {
			fields.add(field);
		}
		schema.add("required", fields);
		schema.addProperty("additionalProperties", false);
		return schema;
	}

	private static JsonObject textSchema() {
		JsonObject schema = new JsonObject();
		schema.addProperty("type", "string");
		return schema;
	}

	private static JsonObject integerSchema() {
		JsonObject schema = new JsonObject();
		schema.addProperty("type", "integer");
		return schema;
	}

	private static JsonObject enumSchema(String... values) {
		JsonObject schema = textSchema();
		JsonArray allowed = new JsonArray();
		for (String value : values) {
			allowed.add(value);
		}
		schema.add("enum", allowed);
		return schema;
	}

	private static JsonObject arraySchema(JsonObject items) {
		JsonObject schema = new JsonObject();
		schema.addProperty("type", "array");
		schema.add("items", items);
		return schema;
	}

	private static void requireModel(String modelId) {
		GeminiModelCatalog.requireSelectable(modelId);
	}

	@FunctionalInterface
	interface StructuredGenerator {
		JsonObject generate(String modelId, String systemInstruction, String input, JsonObject schema)
				throws EvaluationProviderException;
	}
}
