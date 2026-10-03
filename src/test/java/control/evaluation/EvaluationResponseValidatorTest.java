package control.evaluation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Set;

import org.junit.jupiter.api.Test;

class EvaluationResponseValidatorTest {
	private static final Set<Long> LOG_IDS = Set.of(12L, 13L);

	@Test
	void acceptsOnlyScoresAndEvidenceThatMatchThePinnedInput() {
		var response = EvaluationResponseValidator.parseAndValidate("""
				{
				  "scores":{"thinking_expression_level":4,"proactive_attitude_level":3},
				  "reasons":{"thinking_expression_reason":"根拠A","proactive_attitude_reason":"根拠B"},
				  "process_analysis":{
				    "pattern_label":"iterative_refinement",
				    "turning_points":[{"log_id":12,"summary":"改善した"}],
				    "stagnation_points":[],
				    "teacher_support_suggestions":["境界値を確認する"]
				  },
				  "confidence":0.81,
				  "evidence_refs":["13"],
				  "warnings":[]
				}
				""", LOG_IDS);

		assertEquals(4, response.getAsJsonObject("scores").get("thinking_expression_level").getAsInt());
	}

	@Test
	void acceptsDocumentedLogPrefixesForEvidenceReferences() {
		String prefixedIds = validResponse()
				.replace("\"evidence_refs\":[\"13\"]", "\"evidence_refs\":[\"log_013\"]")
				.replace("\"turning_points\":[]",
						"\"turning_points\":[{\"log_id\":\"log_012\",\"summary\":\"改善した\"}]");
		assertEquals(4, EvaluationResponseValidator.parseAndValidate(prefixedIds, LOG_IDS)
				.getAsJsonObject("scores").get("thinking_expression_level").getAsInt());
	}

	@Test
	void acceptsOnlyExecutionReferencesPresentInThePinnedInput() {
		String responseWithRunReference = validResponse()
				.replace("\"evidence_refs\":[\"13\"]", "\"evidence_refs\":[\"run_24\"]");
		assertEquals(4, EvaluationResponseValidator.parseAndValidate(
				responseWithRunReference, LOG_IDS, Set.of(24L))
				.getAsJsonObject("scores").get("thinking_expression_level").getAsInt());
		assertThrows(IllegalArgumentException.class, () -> EvaluationResponseValidator.parseAndValidate(
				responseWithRunReference, LOG_IDS, Set.of(25L)));
	}

	@Test
	void rejectsOutOfRangeScoresAndUnknownEvidence() {
		String invalidScore = validResponse().replace("\"thinking_expression_level\":4",
				"\"thinking_expression_level\":6");
		assertThrows(IllegalArgumentException.class,
				() -> EvaluationResponseValidator.parseAndValidate(invalidScore, LOG_IDS));

		String unknownEvidence = validResponse().replace("\"evidence_refs\":[\"13\"]",
				"\"evidence_refs\":[\"99\"]");
		assertThrows(IllegalArgumentException.class,
				() -> EvaluationResponseValidator.parseAndValidate(unknownEvidence, LOG_IDS));
	}

	@Test
	void rejectsMalformedOrNonObjectResponses() {
		assertThrows(IllegalArgumentException.class,
				() -> EvaluationResponseValidator.parseAndValidate("not json", LOG_IDS));
		assertThrows(IllegalArgumentException.class,
				() -> EvaluationResponseValidator.parseAndValidate("[]", LOG_IDS));
	}

	private static String validResponse() {
		return """
				{
				  "scores":{"thinking_expression_level":4,"proactive_attitude_level":3},
				  "reasons":{"thinking_expression_reason":"根拠A","proactive_attitude_reason":"根拠B"},
				  "process_analysis":{
				    "pattern_label":"iterative_refinement",
				    "turning_points":[],
				    "stagnation_points":[],
				    "teacher_support_suggestions":[]
				  },
				  "confidence":0.81,
				  "evidence_refs":["13"],
				  "warnings":[]
				}
				""";
	}
}
