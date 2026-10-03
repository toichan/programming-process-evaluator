package entity;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public final class StudentCredentialHistoryEntry {
	private static final DateTimeFormatter DISPLAY_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

	private final String actionType;
	private final String resultStatus;
	private final String actorLabel;
	private final LocalDateTime occurredAt;

	public StudentCredentialHistoryEntry(
			String actionType,
			String resultStatus,
			String actorLabel,
			LocalDateTime occurredAt) {
		this.actionType = actionType;
		this.resultStatus = resultStatus;
		this.actorLabel = actorLabel;
		this.occurredAt = occurredAt;
	}

	public String getActionLabel() {
		return switch (actionType) {
			case "initial_credential_issued" -> "初期発行";
			case "first_password_changed" -> "初回変更";
			case "password_changed" -> "パスワード変更";
			case "password_reset" -> "パスワード再設定";
			default -> "その他の認証情報操作";
		};
	}

	public String getResultStatus() {
		return resultStatus;
	}

	public String getActorLabel() {
		return actorLabel;
	}

	public String getOccurredAtDisplay() {
		return occurredAt.format(DISPLAY_FORMAT);
	}
}
