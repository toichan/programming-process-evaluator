package entity;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

public final class StudentConsentPage {
	private static final DateTimeFormatter RESPONSE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

	private final Optional<ConsentDocument> document;
	private final ConsentStatus status;
	private final LocalDateTime respondedAt;

	public StudentConsentPage(
			Optional<ConsentDocument> document,
			ConsentStatus status,
			LocalDateTime respondedAt) {
		this.document = document;
		this.status = status;
		this.respondedAt = respondedAt;
	}

	public Optional<ConsentDocument> getDocument() {
		return document;
	}

	public ConsentStatus getStatus() {
		return status;
	}

	public LocalDateTime getRespondedAt() {
		return respondedAt;
	}

	public String getRespondedAtDisplay() {
		return respondedAt == null ? "" : respondedAt.format(RESPONSE_FORMAT);
	}
}
