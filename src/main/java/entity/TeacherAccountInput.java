package entity;

import java.util.Map;
import java.util.Set;

public record TeacherAccountInput(String loginId, Set<Long> schoolIds, Set<String> features) {
	public static final Map<String, String> FEATURE_LABELS = Map.of(
			"account-management", "生徒アカウント管理",
			"task-progress", "課題進捗確認",
			"exercise-code-review", "授業演習コード確認",
			"submission-review", "提出課題確認",
			"code-distribution", "コード配信",
			"evaluation-review", "評価確認",
			"survey-results", "アンケート結果確認",
			"task-management", "課題編集",
			"teacher-prompt-design", "プロンプト設計");
	public static final java.util.List<String> FEATURE_ORDER = java.util.List.of(
			"account-management", "task-progress", "exercise-code-review", "submission-review",
			"code-distribution", "evaluation-review", "survey-results", "task-management", "teacher-prompt-design");

	public TeacherAccountInput {
		if (loginId == null || loginId.isBlank() || loginId.length() > 64
				|| loginId.codePoints().anyMatch(character -> Character.isWhitespace(character)
						|| Character.isSpaceChar(character) || Character.isISOControl(character))) {
			throw new IllegalArgumentException("教師IDは空白・制御文字を除く1〜64文字にしてください。");
		}
		if (UserCredential.ADMIN_LOGIN_ID.equalsIgnoreCase(loginId)) {
			throw new IllegalArgumentException("adminは管理者専用のIDです。別の教師IDを指定してください。");
		}
		if (schoolIds == null || features == null || schoolIds.stream().anyMatch(id -> id == null || id < 1)
				|| !FEATURE_LABELS.keySet().containsAll(features)) {
			throw new IllegalArgumentException("学校または機能権限が不正です。");
		}
		schoolIds = Set.copyOf(schoolIds);
		features = Set.copyOf(features);
	}
}
