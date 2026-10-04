package entity;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public record TeacherPromptVersion(
		long promptVersionId,
		long taskId,
		String version,
		String aiModel,
		String commonPrompt,
		String additionalInstruction,
		String promptStatus,
		String fluctuationGenerationStatus,
		String evaluationExamplesStatus,
		long rowVersion,
		String createdByLoginId,
		LocalDateTime createdAt,
		String updatedByLoginId,
		LocalDateTime updatedAt,
		List<FluctuationItem> fluctuationItems,
		List<EvaluationExample> evaluationExamples) {

	public TeacherPromptVersion {
		fluctuationItems = List.copyOf(fluctuationItems);
		evaluationExamples = List.copyOf(evaluationExamples);
	}

	public long getPromptVersionId() { return promptVersionId; }
	public long getTaskId() { return taskId; }
	public String getVersion() { return version; }
	public String getAiModel() { return aiModel; }
	public String getCommonPrompt() { return commonPrompt; }
	public String getAdditionalInstruction() { return additionalInstruction; }
	public String getPromptStatus() { return promptStatus; }
	public String getFluctuationGenerationStatus() { return fluctuationGenerationStatus; }
	public String getEvaluationExamplesStatus() { return evaluationExamplesStatus; }
	public long getRowVersion() { return rowVersion; }
	public String getCreatedByLoginId() { return createdByLoginId; }
	public LocalDateTime getCreatedAt() { return createdAt; }
	public String getUpdatedByLoginId() { return updatedByLoginId; }
	public LocalDateTime getUpdatedAt() { return updatedAt; }
	public List<FluctuationItem> getFluctuationItems() { return fluctuationItems; }
	public List<EvaluationExample> getEvaluationExamples() { return evaluationExamples; }

	public record FluctuationItem(
			long id,
			String title,
			String level,
			String description,
			String example,
			String teacherResolution,
			String resolutionStatus,
			int sortOrder) {
		public long getId() { return id; }
		public String getTitle() { return title; }
		public String getLevel() { return level; }
		public String getDescription() { return description; }
		public String getExample() { return example; }
		public String getTeacherResolution() { return teacherResolution; }
		public String getResolutionStatus() { return resolutionStatus; }
		public int getSortOrder() { return sortOrder; }
	}

	public record EvaluationExample(long id, JsonObject input, JsonObject output, String status) {
		public EvaluationExample {
			input = input.deepCopy();
			output = output.deepCopy();
		}
		public long getId() { return id; }
		public JsonObject getInput() { return input.deepCopy(); }
		public JsonObject getOutput() { return output.deepCopy(); }
		public String getStatus() { return status; }
		public List<EvaluationExampleResult> getSimulatedResults() {
			JsonArray values = output.getAsJsonArray("simulated_results");
			if (values == null) {
				throw new IllegalStateException("Stored evaluation example is missing simulated results.");
			}
			List<EvaluationExampleResult> results = new ArrayList<>(values.size());
			for (JsonElement value : values) {
				if (!value.isJsonObject()) {
					throw new IllegalStateException("Stored evaluation example result is invalid.");
				}
				JsonObject result = value.getAsJsonObject();
				JsonObject scores = requiredObject(result, "scores");
				JsonObject reasons = requiredObject(result, "reasons");
				results.add(new EvaluationExampleResult(
						requiredText(result, "sample_id"),
						requiredText(result, "synthetic_code"),
						requiredText(result, "scenario"),
						requiredText(result, "predicted_testcase_result"),
						scores.get("thinking_expression_level").getAsInt(),
						scores.get("proactive_attitude_level").getAsInt(),
						requiredText(reasons, "thinking_expression_reason"),
						requiredText(reasons, "proactive_attitude_reason")));
			}
			return List.copyOf(results);
		}
		public List<String> getVarianceAlerts() { return textArray("variance_alerts"); }
		public List<String> getPreReevalChecklist() { return textArray("pre_reeval_checklist"); }

		private List<String> textArray(String name) {
			JsonArray values = output.getAsJsonArray(name);
			if (values == null) {
				throw new IllegalStateException("Stored evaluation example is missing " + name + ".");
			}
			List<String> result = new ArrayList<>(values.size());
			for (JsonElement value : values) {
				if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
					throw new IllegalStateException("Stored evaluation example contains an invalid " + name + ".");
				}
				result.add(value.getAsString());
			}
			return List.copyOf(result);
		}

		private static JsonObject requiredObject(JsonObject object, String name) {
			if (!object.has(name) || !object.get(name).isJsonObject()) {
				throw new IllegalStateException("Stored evaluation example is missing " + name + ".");
			}
			return object.getAsJsonObject(name);
		}

		private static String requiredText(JsonObject object, String name) {
			if (!object.has(name) || !object.get(name).isJsonPrimitive()
					|| !object.getAsJsonPrimitive(name).isString()
					|| object.get(name).getAsString().isBlank()) {
				throw new IllegalStateException("Stored evaluation example is missing " + name + ".");
			}
			return object.get(name).getAsString();
		}
	}

	public record EvaluationExampleResult(
			String sampleId,
			String syntheticCode,
			String scenario,
			String predictedTestcaseResult,
			int thinkingExpressionLevel,
			int proactiveAttitudeLevel,
			String thinkingExpressionReason,
			String proactiveAttitudeReason) {
		public String getSampleId() { return sampleId; }
		public String getSyntheticCode() { return syntheticCode; }
		public String getScenario() { return scenario; }
		public String getPredictedTestcaseResult() { return predictedTestcaseResult; }
		public int getThinkingExpressionLevel() { return thinkingExpressionLevel; }
		public int getProactiveAttitudeLevel() { return proactiveAttitudeLevel; }
		public String getThinkingExpressionReason() { return thinkingExpressionReason; }
		public String getProactiveAttitudeReason() { return proactiveAttitudeReason; }
	}
}
