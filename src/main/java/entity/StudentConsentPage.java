package entity;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

public final class StudentConsentPage {
	private static final DateTimeFormatter RESPONSE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

	private final Optional<ConsentDocument> document;
	private final ConsentStatus status;
	private final LocalDateTime respondedAt;
	private final long responseId;

	public StudentConsentPage(
			Optional<ConsentDocument> document,
			ConsentStatus status,
			LocalDateTime respondedAt,
			long responseId) {
		this.document = document;
		this.status = status;
		this.respondedAt = respondedAt;
		this.responseId = responseId;
	}

	public long getResponseId() {
		return responseId;
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
