package control.evaluation;

import java.util.Collection;
import java.util.Comparator;
import java.util.regex.Pattern;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

public final class EvaluationPrivacyRedactor {
	private static final Pattern EMAIL = Pattern.compile(
			"(?i)\\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}\\b");
	private static final Pattern PHONE = Pattern.compile(
			"(?<!\\d)(?:\\+?\\d{1,3}[- .]?)?(?:\\(?0\\d{1,4}\\)?[- .]?)?\\d{1,4}[- .]\\d{4}(?!\\d)");

	private EvaluationPrivacyRedactor() {
	}

	public static JsonObject redact(JsonObject input, Collection<String> identifyingValues) {
		JsonElement redacted = redactValue(input, identifyingValues.stream()
				.filter(value -> value != null && !value.isBlank())
				.sorted(Comparator.comparingInt(String::length).reversed())
				.toList());
		return redacted.getAsJsonObject();
	}

	private static JsonElement redactValue(JsonElement input, Collection<String> identifyingValues) {
		if (input.isJsonObject()) {
			JsonObject redacted = new JsonObject();
			for (var entry : input.getAsJsonObject().entrySet()) {
				redacted.add(entry.getKey(), redactValue(entry.getValue(), identifyingValues));
			}
			return redacted;
		}
		if (input.isJsonArray()) {
			JsonArray redacted = new JsonArray();
			for (JsonElement element : input.getAsJsonArray()) {
				redacted.add(redactValue(element, identifyingValues));
			}
			return redacted;
		}
		if (input.isJsonPrimitive() && input.getAsJsonPrimitive().isString()) {
			String value = input.getAsString();
			for (String identifyingValue : identifyingValues) {
				value = value.replace(identifyingValue, "[REDACTED]");
			}
			value = EMAIL.matcher(value).replaceAll("[REDACTED_EMAIL]");
			value = PHONE.matcher(value).replaceAll("[REDACTED_PHONE]");
			return new JsonPrimitive(value);
		}
		return input.deepCopy();
	}
}
