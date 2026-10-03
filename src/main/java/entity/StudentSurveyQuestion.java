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
	public boolean isRequired() { return required; }
	public boolean isReasonRequired() { return reasonRequired; }
	public int getSortOrder() { return sortOrder; }
	public List<Option> getOptions() { return options; }
	public List<String> getSelectedValues() { return selectedValues; }
	public String getAnswerReason() { return answerReason; }
	public String getAnswerValue() { return selectedValues.isEmpty() ? "" : selectedValues.get(0); }
	public boolean isMultipleChoice() { return "multiple_choice".equals(type); }
	public boolean isText() { return "text".equals(type); }

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
	}

	public record SavedAnswer(List<String> values, String reason) {
		public SavedAnswer {
			values = List.copyOf(values);
			reason = reason == null ? "" : reason;
		}
	}
}
