package control.teacher;

public final class TaskDraftNotFoundException extends RuntimeException {
	private static final long serialVersionUID = 1L;

	public TaskDraftNotFoundException() {
		super("課題が見つからないか、表示できません。");
	}
}
