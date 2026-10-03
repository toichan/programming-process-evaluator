package entity;

import java.time.LocalDateTime;

public record EditorSaveResult(Status status, LocalDateTime updatedAt) {
	public enum Status {
		SAVED,
		CONFLICT,
		NOT_FOUND,
		READ_ONLY
	}
}
