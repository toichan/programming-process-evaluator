package entity;

public final class StudentSurveyNavigation {
	private final Long surveyId;
	private final Long evaluationId;
	private final String unavailableReason;

	private StudentSurveyNavigation(Long surveyId, Long evaluationId, String unavailableReason) {
		this.surveyId = surveyId;
		this.evaluationId = evaluationId;
		this.unavailableReason = unavailableReason;
	}

	public static StudentSurveyNavigation forEvaluation(
			StudentHomePage home, long assignmentId, Long evaluationId, boolean completed) {
		if (home == null) {
			return new StudentSurveyNavigation(null, null, "課題情報を読み込めません。画面を再読み込みしてください。");
		}
		if (home.getConsentStatus() != ConsentStatus.AGREED) {
			return new StudentSurveyNavigation(null, null, "アンケートの回答には研究同意が必要です。研究同意画面で現在の選択を確認してください。");
		}
		if (!completed || evaluationId == null) {
			return new StudentSurveyNavigation(null, null, "この提出版の評価完了後にアンケートへ回答できます。");
		}
		var task = home.getTasks().stream()
				.filter(item -> item.getTaskClassAssignmentId() == assignmentId).findFirst().orElse(null);
		if (task == null || task.getActiveSurveyId() == null) {
			return new StudentSurveyNavigation(null, null, "この課題には現在有効なアンケートが設定されていません。先生に確認してください。");
		}
		if (!evaluationId.equals(task.getLatestCompletedEvaluationId())) {
			return new StudentSurveyNavigation(null, null, "最新の完了済み評価からアンケートを開いてください。");
		}
		return new StudentSurveyNavigation(task.getActiveSurveyId(), evaluationId, "");
	}

	public boolean isAvailable() { return surveyId != null; }
	public Long getSurveyId() { return surveyId; }
	public Long getEvaluationId() { return evaluationId; }
	public String getUnavailableReason() { return unavailableReason; }
}
