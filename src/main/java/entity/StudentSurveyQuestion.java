package entity;

import java.util.List;

public final class StudentSurveyQuestion {
	private final long questionId;
	private final String code;
	private final String type;
	private final String prompt;
	private final String reasonPrompt;
	private final boolean required;
	private final boolean reasonRequired;
	private final int sortOrder;
	private final List<Option> options;
	private final List<String> selectedValues;
	private final String answerReason;

	public StudentSurveyQuestion(
			long questionId,
			String code,
			String type,
			String prompt,
			String reasonPrompt,
			boolean required,
			boolean reasonRequired,
			int sortOrder,
			List<Option> options,
			List<String> selectedValues,
			String answerReason) {
		this.questionId = questionId;
		this.code = code;
		this.type = type;
		this.prompt = prompt;
		this.reasonPrompt = reasonPrompt;
		this.required = required;
		this.reasonRequired = reasonRequired;
		this.sortOrder = sortOrder;
		this.options = List.copyOf(options);
		this.selectedValues = List.copyOf(selectedValues);
		this.answerReason = answerReason == null ? "" : answerReason;
	}

	public long getQuestionId() { return questionId; }
	public String getCode() { return code; }
	public String getType() { return type; }
	public String getPrompt() { return prompt; }
	public String getReasonPrompt() { return reasonPrompt; }
	public String getDisplayReasonPrompt() {
		if (reasonPrompt != null && !reasonPrompt.isBlank()) {
			return reasonPrompt;
		}
		return switch (code == null ? "" : code) {
			case "q1ThinkingValidity" -> "システムの評価を妥当だと考えた理由";
			case "q1ThinkingScore" -> "思考力・判断力・表現力の自己評価の理由";
			case "q2AttitudeValidity" -> "システムの評価を妥当だと考えた理由";
			case "q2AttitudeScore" -> "主体的に取り組む態度の自己評価の理由";
			case "q3ProcessResistanceScore" -> "試行錯誤の過程の評価についてそう考えた理由";
			case "q4UsabilityScore" -> "使いやすかった点、改善してほしい点";
			default -> "回答理由";
		};
	}
	public boolean isStandardMetric() {
		return switch (code == null ? "" : code) {
			case "q1ThinkingValidity", "q1ThinkingScore", "q2AttitudeValidity", "q2AttitudeScore",
					"q3ProcessResistanceScore", "q4UsabilityScore" -> true;
			default -> false;
		};
	}
	public boolean isRequired() { return required; }
	public boolean isReasonRequired() { return reasonRequired; }
	public int getSortOrder() { return sortOrder; }
	public List<Option> getOptions() { return options; }
	public List<Option> getDisplayOptions() {
		return options.stream().map(option -> new Option(option.value(), getOptionLabel(option))).toList();
	}
	public List<String> getSelectedValues() { return selectedValues; }
	public String getAnswerReason() { return answerReason; }
	public String getAnswerValue() { return selectedValues.isEmpty() ? "" : selectedValues.get(0); }
	public boolean isMultipleChoice() { return "multiple_choice".equals(type); }
	public boolean isText() { return "text".equals(type); }

	public String getOptionLabel(Option option) {
		if ("q1ThinkingValidity".equals(code) || "q2AttitudeValidity".equals(code)) {
			return switch (option.value()) {
				case "1" -> "妥当でない";
				case "2" -> "あまり妥当でない";
				case "3" -> "どちらともいえない";
				case "4" -> "おおむね妥当";
				case "5" -> "妥当";
				default -> option.label();
			};
		}
		if ("q3ProcessResistanceScore".equals(code)) {
			return switch (option.value()) {
				case "1" -> "とても感じる";
				case "2" -> "やや感じる";
				case "3" -> "どちらともいえない";
				case "4" -> "あまり感じない";
				case "5" -> "全く感じない";
				default -> option.label();
			};
		}
		if ("q4UsabilityScore".equals(code)) {
			return switch (option.value()) {
				case "1" -> "とても使いにくい";
				case "2" -> "やや使いにくい";
				case "3" -> "どちらともいえない";
				case "4" -> "やや使いやすい";
				case "5" -> "とても使いやすい";
				default -> option.label();
			};
		}
		return option.label();
	}

	public StudentSurveyQuestion withAnswer(SavedAnswer answer) {
		return answer == null ? this : new StudentSurveyQuestion(
				questionId,
				code,
				type,
				prompt,
				reasonPrompt,
				required,
				reasonRequired,
				sortOrder,
				options,
				answer.values(),
				answer.reason());
	}

	public record Option(String value, String label) {
		public String getValue() { return value; }
		public String getLabel() { return label; }
	}

	public record SavedAnswer(List<String> values, String reason) {
		public SavedAnswer {
			values = List.copyOf(values);
			reason = reason == null ? "" : reason;
		}
	}
}
