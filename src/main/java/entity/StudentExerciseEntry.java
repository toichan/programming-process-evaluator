package entity;

import java.time.LocalDateTime;

public record StudentExerciseEntry(
		long entryId,
		long exerciseId,
		Long parentEntryId,
		Type type,
		String name,
		String path,
		String description,
		String content,
		Status status,
		LocalDateTime updatedAt,
		Long trashRootEntryId,
		LocalDateTime trashedAt) {

	public StudentExerciseEntry(long entryId, long exerciseId, Long parentEntryId, Type type,
			String name, String path, String description, String content, Status status, LocalDateTime updatedAt) {
		this(entryId, exerciseId, parentEntryId, type, name, path, description, content, status, updatedAt, null, null);
	}

	public enum Type {
		FOLDER("folder"), FILE("file");

		private final String value;

		Type(String value) { this.value = value; }
		public String getValue() { return value; }

		public static Type fromValue(String value) {
			for (Type type : values()) {
				if (type.value.equals(value)) return type;
			}
			throw new IllegalArgumentException("フォルダまたはファイルを指定してください。");
		}
	}

	public enum Status {
		ACTIVE("active"), TRASHED("trashed"), DELETED("deleted");

		private final String value;

		Status(String value) { this.value = value; }
		public String getValue() { return value; }

		public static Status fromValue(String value) {
			for (Status status : values()) {
				if (status.value.equals(value)) return status;
			}
			throw new IllegalArgumentException("演習項目の状態が正しくありません。");
		}
	}

	public long getEntryId() { return entryId; }
	public long getExerciseId() { return exerciseId; }
	public Long getParentEntryId() { return parentEntryId; }
	public Type getType() { return type; }
	public String getName() { return name; }
	public String getPath() { return path; }
	public String getDescription() { return description; }
	public String getContent() { return content; }
	public Status getStatus() { return status; }
	public LocalDateTime getUpdatedAt() { return updatedAt; }
	public Long getTrashRootEntryId() { return trashRootEntryId; }
	public LocalDateTime getTrashedAt() { return trashedAt; }
}
