package entity;

import java.util.Objects;

public record TeacherHintOption(
		long taskId,
		String taskTitle,
		long hintId,
		int order,
		EditorHint hint) {

	public TeacherHintOption {
		Objects.requireNonNull(taskTitle);
		Objects.requireNonNull(hint);
	}

	public long getTaskId() {
		return taskId;
	}

	public String getTaskTitle() {
		return taskTitle;
	}

	public long getHintId() {
		return hintId;
	}

	public int getOrder() {
		return order;
	}

	public EditorHint getHint() {
		return hint;
	}
}
