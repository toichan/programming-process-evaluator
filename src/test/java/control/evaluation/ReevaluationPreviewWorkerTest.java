package control.evaluation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.SQLException;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import dao.ReevaluationPreviewDao.PreviewWorkItem;
import dao.ReevaluationPreviewWorkRepository;

class ReevaluationPreviewWorkerTest {
	@Test
	void validatesThePreviewResponseAndPersistsOnlyTheSuccessfulResult() throws SQLException {
		FakeRepository repository = new FakeRepository();
		EvaluationProvider provider = new EvaluationProvider() {
			@Override
			public JsonObject generate(String modelId, JsonObject payload) {
				assertEquals("request-current", payload.getAsJsonObject("metadata").get("request_id").getAsString());
				JsonObject raw = new JsonObject();
				raw.addProperty("response", "synthetic");
				return raw;
			}

			@Override
			public String extractOutputText(JsonObject response) {
				return validOutput();
			}
		};

		assertTrue(new ReevaluationPreviewWorker(repository, provider).processNext());
		assertEquals(1, repository.successCount);
		assertEquals(1, repository.successfulAttempts);
		assertEquals(0, repository.failedCount);
		assertTrue(repository.savedResponse.has("response"));
		assertEquals(4, repository.savedResult.getAsJsonObject("scores")
				.get("thinking_expression_level").getAsInt());
	}

	@Test
	void failedValidationRetriesTheSameTargetAndKeepsFailuresSafe() throws SQLException {
		FakeRepository repository = new FakeRepository();
		int[] calls = { 0 };
		EvaluationProvider provider = new EvaluationProvider() {
			@Override
			public JsonObject generate(String modelId, JsonObject payload) {
				calls[0]++;
				return new JsonObject();
			}

			@Override
			public String extractOutputText(JsonObject response) {
				return "{}";
			}
		};

		assertTrue(new ReevaluationPreviewWorker(repository, provider).processNext());
		assertEquals(3, calls[0]);
		assertEquals(0, repository.successCount);
		assertEquals(1, repository.failedCount);
		assertEquals(3, repository.failedAttempts);
		assertEquals("invalid_response", repository.safeErrorCode);
		assertFalse(repository.safeErrorMessage.contains("synthetic"));
	}

	private static String validOutput() {
		return """
				{
				  "scores": {
				    "thinking_expression_level": 4,
				    "proactive_attitude_level": 3
				  },
				  "reasons": {
				    "thinking_expression_reason": "根拠のある説明です。",
				    "proactive_attitude_reason": "継続した改善が見られます。"
				  },
				  "process_analysis": {
				    "pattern_label": "iterative",
				    "turning_points": [{"log_id": 5, "summary": "修正しました。"}],
				    "stagnation_points": [],
				    "teacher_support_suggestions": []
				  },
				  "confidence": 0.9,
				  "evidence_refs": ["log_5", "run_7"],
				  "warnings": []
				}
				""";
	}

	private static final class FakeRepository implements ReevaluationPreviewWorkRepository {
		private boolean claimed;
		private int successCount;
		private int failedCount;
		private int failedAttempts;
		private String safeErrorCode;
		private String safeErrorMessage;
		private JsonObject savedResponse;
		private JsonObject savedResult;
		private int successfulAttempts;

		@Override
		public Optional<PreviewWorkItem> claimNextTarget() {
			if (claimed) {
				return Optional.empty();
			}
			claimed = true;
			JsonObject payload = new JsonObject();
			JsonObject metadata = new JsonObject();
			metadata.addProperty("request_id", "request-original");
			payload.add("metadata", metadata);
			JsonArray logs = new JsonArray();
			JsonObject log = new JsonObject();
			log.addProperty("log_id", 5);
			log.addProperty("execution_id", 7);
			log.addProperty("observed_at", "2026-10-05T10:00:00");
			log.addProperty("snapshot", "synthetic code");
			logs.add(log);
			payload.add("timeline_logs", logs);
			JsonObject prompt = new JsonObject();
			prompt.add("evaluation_examples", new JsonArray());
			payload.add("prompt_evaluation_settings", prompt);
			return Optional.of(new PreviewWorkItem(
					11, 12, "preview-code", 13, "model", 14, 15, 16, 17, 18,
					"rubric-v1", "prompt-v1", payload, "request-current", 0));
		}

		@Override
		public void markSucceeded(
				long targetId, long previewId, int attempts, JsonObject rawResponse, JsonObject result) {
			successCount++;
			successfulAttempts = attempts;
			savedResponse = rawResponse;
			savedResult = result;
		}

		@Override
		public void markFailed(long targetId, long previewId, int attempts, String safeCode, String safeMessage) {
			failedCount++;
			failedAttempts = attempts;
			safeErrorCode = safeCode;
			safeErrorMessage = safeMessage;
		}

		@Override
		public int cleanupExpired() {
			return 0;
		}
	}
}
