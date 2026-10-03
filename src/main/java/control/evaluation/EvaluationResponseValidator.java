package control.evaluation;

import java.util.HashSet;
import java.util.Set;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

public final class EvaluationResponseValidator {
	private EvaluationResponseValidator() {
	}

	public static JsonObject parseAndValidate(String rawJson, Set<Long> knownLogIds) {
		return parseAndValidate(rawJson, knownLogIds, Set.of());
	}

	public static JsonObject parseAndValidate(
			String rawJson,
			Set<Long> knownLogIds,
			Set<Long> knownExecutionIds) {
		JsonElement parsed;
		try {
			parsed = JsonParser.parseString(rawJson);
		} catch (JsonParseException e) {
			throw new IllegalArgumentException("The AI response is not valid JSON.", e);
		}
		if (!parsed.isJsonObject()) {
			throw new IllegalArgumentException("The AI response must be a JSON object.");
		}
		JsonObject root = parsed.getAsJsonObject();
		JsonObject scores = object(root, "scores");
		numberInRange(scores, "thinking_expression_level", 1, 5);
		numberInRange(scores, "proactive_attitude_level", 1, 5);

		JsonObject reasons = object(root, "reasons");
		requiredText(reasons, "thinking_expression_reason", 5000);
		requiredText(reasons, "proactive_attitude_reason", 5000);

		JsonObject analysis = object(root, "process_analysis");
		requiredText(analysis, "pattern_label", 120);
		validatePoints(analysis, "turning_points", knownLogIds);
		validatePoints(analysis, "stagnation_points", knownLogIds);
		JsonArray suggestions = array(analysis, "teacher_support_suggestions");
		if (suggestions.size() > 3) {
			throw new IllegalArgumentException("At most three teacher support suggestions are allowed.");
		}
		for (JsonElement suggestion : suggestions) {
			text(suggestion, "teacher_support_suggestions[]", 2000);
		}

		double confidence = decimal(root, "confidence");
		if (!Double.isFinite(confidence) || confidence < 0 || confidence > 1) {
			throw new IllegalArgumentException("The confidence must be between 0 and 1.");
		}
		validateEvidenceRefs(array(root, "evidence_refs"), knownLogIds, knownExecutionIds);
		JsonArray warnings = array(root, "warnings");
		if (warnings.size() > 50) {
			throw new IllegalArgumentException("Too many warnings were returned.");
		}
		for (JsonElement warning : warnings) {
			text(warning, "warnings[]", 2000);
		}
		return root;
	}

	private static void validatePoints(JsonObject parent, String field, Set<Long> knownLogIds) {
		JsonArray points = array(parent, field);
		if (points.size() > 100) {
			throw new IllegalArgumentException("Too many process analysis points were returned.");
		}
		for (JsonElement element : points) {
			if (!element.isJsonObject()) {
				throw new IllegalArgumentException(field + " entries must be objects.");
			}
			JsonObject point = element.getAsJsonObject();
			long logId = logId(point.get("log_id"), field + "[].log_id");
			if (!knownLogIds.contains(logId)) {
				throw new IllegalArgumentException("The response refers to an unknown code log.");
			}
			requiredText(point, "summary", 2000);
		}
	}

	private static void validateEvidenceRefs(
			JsonArray refs,
			Set<Long> knownLogIds,
			Set<Long> knownExecutionIds) {
		if (refs.size() > 100) {
			throw new IllegalArgumentException("Too many evidence references were returned.");
		}
		Set<String> seen = new HashSet<>();
		for (JsonElement element : refs) {
			EvidenceReference reference = evidenceReference(element);
			Set<Long> knownIds = reference.execution() ? knownExecutionIds : knownLogIds;
			String uniqueId = (reference.execution() ? "run:" : "log:") + reference.id();
			if (!knownIds.contains(reference.id()) || !seen.add(uniqueId)) {
				throw new IllegalArgumentException("The response refers to an unknown or duplicate evidence item.");
			}
		}
	}

	private static long logId(JsonElement value, String field) {
		EvidenceReference reference = evidenceReference(value);
		if (reference.execution()) {
			throw new IllegalArgumentException(field + " must refer to a code log.");
		}
		return reference.id();
	}

	private static EvidenceReference evidenceReference(JsonElement value) {
		if (value == null || !value.isJsonPrimitive()) {
			throw new IllegalArgumentException("An evidence reference must be a code log or execution ID.");
		}
		String text = value.getAsString();
		boolean execution = text.startsWith("run_");
		if (execution) {
			text = text.substring(4);
		} else if (text.startsWith("log_")) {
			text = text.substring(4);
		}
		if (!text.matches("[0-9]{1,19}")) {
			throw new IllegalArgumentException("An evidence reference must be a code log or execution ID.");
		}
		try {
			return new EvidenceReference(execution, Long.parseLong(text));
		} catch (NumberFormatException e) {
			throw new IllegalArgumentException("An evidence reference must be a code log or execution ID.", e);
		}
	}

	private static JsonObject object(JsonObject parent, String field) {
		JsonElement value = parent.get(field);
		if (value == null || !value.isJsonObject()) {
			throw new IllegalArgumentException(field + " must be an object.");
		}
		return value.getAsJsonObject();
	}

	private static JsonArray array(JsonObject parent, String field) {
		JsonElement value = parent.get(field);
		if (value == null || !value.isJsonArray()) {
			throw new IllegalArgumentException(field + " must be an array.");
		}
		return value.getAsJsonArray();
	}

	private static int numberInRange(JsonObject parent, String field, int minimum, int maximum) {
		long value = integer(parent, field);
		if (value < minimum || value > maximum) {
			throw new IllegalArgumentException(field + " is outside the allowed range.");
		}
		return (int) value;
	}

	private static long integer(JsonObject parent, String field) {
		JsonElement value = parent.get(field);
		if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
			throw new IllegalArgumentException(field + " must be a number.");
		}
		double number = value.getAsDouble();
		if (!Double.isFinite(number) || number != Math.rint(number)
				|| number < Long.MIN_VALUE || number > Long.MAX_VALUE) {
			throw new IllegalArgumentException(field + " must be an integer.");
		}
		return value.getAsLong();
	}

	private static double decimal(JsonObject parent, String field) {
		JsonElement value = parent.get(field);
		if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
			throw new IllegalArgumentException(field + " must be a number.");
		}
		return value.getAsDouble();
	}

	private static String requiredText(JsonObject parent, String field, int maxLength) {
		String value = text(parent.get(field), field, maxLength);
		if (value.isBlank()) {
			throw new IllegalArgumentException(field + " must not be blank.");
		}
		return value;
	}

	private static String text(JsonElement value, String field, int maxLength) {
		if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
			throw new IllegalArgumentException(field + " must be a string.");
		}
		String text = value.getAsString();
		if (text.length() > maxLength) {
			throw new IllegalArgumentException(field + " exceeds the maximum length.");
		}
		return text;
	}

	private record EvidenceReference(boolean execution, long id) {
	}
}
