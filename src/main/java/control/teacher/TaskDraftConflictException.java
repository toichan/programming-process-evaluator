package control.teacher;

public final class TaskDraftConflictException extends RuntimeException {
	private static final long serialVersionUID = 1L;

	public TaskDraftConflictException() {
		super("課題が更新されています。画面を読み込み直してください。");
	}
}
