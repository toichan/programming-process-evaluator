package entity;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public final class SurveyAnswerValidator {
	private static final int MAX_ANSWER_LENGTH = 5000;

	private SurveyAnswerValidator() {
	}

	public static Optional<String> validate(
			StudentSurveyQuestion question,
			List<String> values,
			String reason,
			boolean submitting) {
		List<String> answers = values == null ? List.of() : values;
		String normalizedReason = reason == null ? "" : reason;
		if (normalizedReason.length() > MAX_ANSWER_LENGTH) {
			return Optional.of("理由は5,000文字以内で入力してください。");
		}
		if ("text".equals(question.getType())) {
			if (answers.size() > 1
					|| answers.stream().anyMatch(value -> value == null || value.length() > MAX_ANSWER_LENGTH)) {
				return Optional.of("回答は5,000文字以内で入力してください。");
			}
			if (submitting && question.isRequired()
					&& (answers.isEmpty() || answers.get(0).isBlank())) {
				return Optional.of("必須項目に回答してください。");
			}
			return Optional.empty();
		}

		if (submitting && question.isReasonRequired() && normalizedReason.isBlank()) {
			return Optional.of("理由・補足を入力してください。");
		}

		if (!answers.isEmpty()) {
			boolean allowsMultiple = "multiple_choice".equals(question.getType());
			if (!allowsMultiple && answers.size() != 1) {
				return Optional.of("選択肢を1つ選んでください。");
			}
			Set<String> allowedValues = question.getOptions().stream()
					.map(StudentSurveyQuestion.Option::value)
					.collect(Collectors.toSet());
			if (answers.stream().anyMatch(value -> !allowedValues.contains(value))) {
				return Optional.of("選択肢を確認して、もう一度回答してください。");
			}
			if (allowsMultiple && new HashSet<>(answers).size() != answers.size()) {
				return Optional.of("同じ選択肢を重複して送信できません。");
			}
		}
		if (submitting && question.isRequired() && answers.isEmpty()) {
			return Optional.of("必須項目に回答してください。");
		}
		return Optional.empty();
	}
}
