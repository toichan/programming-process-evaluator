package entity;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

class TeacherPromptVersionTest {
	private static final TeacherPromptVersion.FluctuationItem RESOLVED =
			new TeacherPromptVersion.FluctuationItem(1, "Title", "low", "Description", "Example", "Response", "resolved", 1);
	private static final TeacherPromptVersion.EvaluationExample ACTIVE =
			new TeacherPromptVersion.EvaluationExample(1, new JsonObject(), new JsonObject(), "active");

	@Test
	void configuredAndVersionedPromptsWithCompletedArtifactsAreReady() {
		assertTrue(version("configured", "completed", "completed", List.of(RESOLVED), List.of(ACTIVE)).isReadyForApplication());
		assertTrue(version("versioned", "completed", "completed", List.of(RESOLVED), List.of(ACTIVE)).isReadyForApplication());
	}

	@Test
	void incompleteOrInactiveArtifactsCannotBeApplied() {
		assertFalse(version("draft", "completed", "completed", List.of(RESOLVED), List.of(ACTIVE)).isReadyForApplication());
		assertFalse(version("configured", "in_progress", "completed", List.of(RESOLVED), List.of(ACTIVE)).isReadyForApplication());
		assertFalse(version("configured", "completed", "failed", List.of(RESOLVED), List.of(ACTIVE)).isReadyForApplication());
		assertFalse(version("configured", "completed", "completed", List.of(), List.of(ACTIVE)).isReadyForApplication());
		var pending = new TeacherPromptVersion.FluctuationItem(1, "Title", "low", "Description", "Example", "", "pending", 1);
		assertFalse(version("configured", "completed", "completed", List.of(pending), List.of(ACTIVE)).isReadyForApplication());
		assertFalse(version("configured", "completed", "completed", List.of(RESOLVED), List.of()).isReadyForApplication());
		var inactive = new TeacherPromptVersion.EvaluationExample(1, new JsonObject(), new JsonObject(), "inactive");
		assertFalse(version("configured", "completed", "completed", List.of(RESOLVED), List.of(inactive)).isReadyForApplication());
	}

	@Test
	void exposesCompleteSyntheticLogsAndProductionEvaluationForPromptPreview() {
		JsonObject output = JsonParser.parseString("""
				{
				  "simulated_results":[{
				    "sample_id":"synthetic-1",
				    "synthetic_code":"print(1)",
				    "scenario":"合成シナリオ",
				    "predicted_testcase_result":"実行していない",
				    "synthetic_logs":[{
				      "log_id":1,"event_type":"edit","observed_at":"2026-10-14T01:00:00",
				      "snapshot":"print(1)","execution_id":2,"execution_status":"succeeded",
				      "error_type":"","standard_input":"","standard_output":"1","standard_error":""
				    }],
				    "evaluation":{
				      "scores":{"thinking_expression_level":4,"proactive_attitude_level":3},
				      "reasons":{"thinking_expression_reason":"根拠A","proactive_attitude_reason":"根拠B"},
				      "process_analysis":{"pattern_label":"iterative","turning_points":[],"stagnation_points":[],
				        "teacher_support_suggestions":[]},
				      "confidence":0.8,"evidence_refs":["log_1","run_2"],"warnings":[]
				    }
				  }],
				  "variance_alerts":[],
				  "pre_reeval_checklist":[]
				}
				""").getAsJsonObject();
		var example = new TeacherPromptVersion.EvaluationExample(2, new JsonObject(), output, "active");

		var result = example.getSimulatedResults().getFirst();
		assertEquals(4, result.thinkingExpressionLevel());
		assertEquals(1, result.getSyntheticLogJson().size());
		assertTrue(result.getSyntheticLogJson().getFirst().contains("\"standard_output\": \"1\""));
		assertTrue(result.getPrettyEvaluation().contains("\"process_analysis\""));
		assertTrue(example.getPrettyOutput().contains("\"synthetic_logs\""));
	}

	private static TeacherPromptVersion version(String status, String fluctuations, String examples,
			List<TeacherPromptVersion.FluctuationItem> items, List<TeacherPromptVersion.EvaluationExample> outputs) {
		return new TeacherPromptVersion(1, 1, "v1", "gemini-3.1-pro-preview", "Prompt", null, null, "", status,
				fluctuations, examples, 1, "teacher", null, "teacher", null, items, outputs);
	}
}
