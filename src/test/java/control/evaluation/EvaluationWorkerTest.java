package control.evaluation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import dao.EvaluationWorkRepository;
import dao.EvaluationWorkerDao.EvaluationInput;
import dao.EvaluationWorkerDao.EvaluationJob;

class EvaluationWorkerTest {
	@Test
	void storageRuntimeFailureIsNotRetriedAsInvalidAiOutput() {
		FakeRepository repository = new FakeRepository();
		repository.completionFailure = new IllegalArgumentException("Synthetic storage failure.");
		List<JsonObject> payloads = new ArrayList<>();
		EvaluationProvider provider = new EvaluationProvider() {
			@Override
			public JsonObject generate(String modelId, JsonObject payload) {
				payloads.add(payload.deepCopy());
				return new JsonObject();
			}

			@Override
			public String extractOutputText(JsonObject response) {
				return validOutput();
			}
		};

		assertThrows(IllegalArgumentException.class,
				() -> new EvaluationWorker(repository, provider).processNext());
		assertEquals(1, payloads.size());
		assertEquals(1, repository.recordedResponses);
		assertEquals(1, repository.validatedResponses);
		assertTrue(repository.retryCounts.isEmpty());
		assertEquals(1, repository.failures);
		assertFalse(repository.completed);
	}

	@Test
	void retriesTwiceAndSimplifiesOnlyTheFinalPayload() throws SQLException {
		FakeRepository repository = new FakeRepository();
		List<JsonObject> payloads = new ArrayList<>();
		EvaluationProvider provider = new EvaluationProvider() {
			private int calls;

			@Override
			public JsonObject generate(String modelId, JsonObject payload) {
				payloads.add(payload.deepCopy());
				JsonObject response = new JsonObject();
				response.addProperty("call", ++calls);
				return response;
			}

			@Override
			public String extractOutputText(JsonObject response) {
				return response.get("call").getAsInt() < 3 ? "{}" : validOutput();
			}
		};

		assertTrue(new EvaluationWorker(repository, provider).processNext());
		assertEquals(3, payloads.size());
		assertEquals(payloads.get(0), payloads.get(1));
		assertTrue(payloads.get(0).getAsJsonObject("prompt_evaluation_settings")
				.has("evaluation_examples"));
		assertFalse(payloads.get(2).getAsJsonObject("prompt_evaluation_settings")
				.has("evaluation_examples"));
		assertFalse(payloads.get(2).getAsJsonArray("timeline_logs").get(0)
				.getAsJsonObject().has("snapshot"));
		assertEquals(List.of(1, 2), repository.retryCounts);
		assertEquals(3, repository.recordedResponses);
		assertEquals(1, repository.validatedResponses);
		assertTrue(repository.completed);
		assertEquals(0, repository.failures);
	}

	@Test
	void missingProviderConfigurationFailsWithoutRetrying() throws SQLException {
		FakeRepository repository = new FakeRepository();
		EvaluationProvider provider = new EvaluationProvider() {
			@Override
			public JsonObject generate(String modelId, JsonObject payload) throws EvaluationProviderException {
				throw new EvaluationProviderException("GEMINI_API_KEY is not configured.", false);
			}

			@Override
			public String extractOutputText(JsonObject response) {
				throw new AssertionError("No response should be extracted.");
			}
		};

		new EvaluationWorker(repository, provider).processNext();
		assertEquals(0, repository.recordedResponses);
		assertTrue(repository.retryCounts.isEmpty());
		assertEquals(1, repository.failures);
		assertEquals(0, repository.failureRetryCount);
		assertFalse(repository.completed);
	}

	@Test
	void recordsFinalFailureAfterInitialSamePayloadRetryAndSimplifiedRetry() throws SQLException {
		FakeRepository repository = new FakeRepository();
		List<JsonObject> payloads = new ArrayList<>();
		EvaluationProvider provider = new EvaluationProvider() {
			@Override
			public JsonObject generate(String modelId, JsonObject payload) {
				payloads.add(payload.deepCopy());
				return new JsonObject();
			}

			@Override
			public String extractOutputText(JsonObject response) {
				return "{}";
			}
		};

		new EvaluationWorker(repository, provider).processNext();
		assertEquals(3, payloads.size());
		assertEquals(payloads.get(0), payloads.get(1));
		assertFalse(payloads.get(0).equals(payloads.get(2)));
		assertEquals(List.of(1, 2), repository.retryCounts);
		assertEquals(3, repository.recordedResponses);
		assertEquals(0, repository.validatedResponses);
		assertEquals(1, repository.failures);
		assertEquals(2, repository.failureRetryCount);
		assertFalse(repository.completed);
	}

	private static String validOutput() {
		return """
				{
				  "scores": {
				    "thinking_expression_level": 4,
				    "proactive_attitude_level": 3
				  },
				  "reasons": {
				    "thinking_expression_reason": "根拠あり",
				    "proactive_attitude_reason": "根拠あり"
				  },
				  "process_analysis": {
				    "pattern_label": "iterative",
				    "turning_points": [],
				    "stagnation_points": [],
				    "teacher_support_suggestions": []
				  },
				  "confidence": 0.8,
				  "evidence_refs": [],
				  "warnings": []
				}
				""";
	}

	private static final class FakeRepository implements EvaluationWorkRepository {
		private final EvaluationJob job = new EvaluationJob(
				1, 2, 3, 4, 5, 6, "gemini-test", "p1", "r1", "unconfirmed", "request-1");
		private final JsonObject payload;
		private final List<Integer> retryCounts = new ArrayList<>();
		private int recordedResponses;
		private int validatedResponses;
		private int failures;
		private int failureRetryCount = -1;
		private boolean completed;
		private RuntimeException completionFailure;

		private FakeRepository() {
			payload = new JsonObject();
			JsonObject prompt = new JsonObject();
			prompt.add("evaluation_examples", new JsonArray());
			payload.add("prompt_evaluation_settings", prompt);
			JsonArray logs = new JsonArray();
			JsonObject log = new JsonObject();
			log.addProperty("snapshot", "student code");
			log.addProperty("standard_output", "execution output");
			logs.add(log);
			payload.add("timeline_logs", logs);
		}

		@Override
		public Optional<EvaluationJob> claimNext() {
			return Optional.of(job);
		}

		@Override
		public EvaluationInput loadAndPersistInput(EvaluationJob ignored) {
			return new EvaluationInput(payload.deepCopy(), Set.of(), Set.of());
		}

		@Override
		public void recordResponse(long requestId, JsonObject rawResponse, JsonObject validatedOutput) {
			recordedResponses++;
			if (validatedOutput != null) {
				validatedResponses++;
			}
		}

		@Override
		public void complete(EvaluationJob ignored, JsonObject result) {
			assertNotNull(result);
			if (completionFailure != null) {
				throw completionFailure;
			}
			completed = true;
		}

		@Override
		public void fail(EvaluationJob ignored, String safeErrorDetail, int retryCount) {
			failures++;
			failureRetryCount = retryCount;
		}

		@Override
		public void setRetryCount(long requestId, int retryCount) {
			retryCounts.add(retryCount);
		}
	}
}
