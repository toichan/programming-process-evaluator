package entity;

public final class ExerciseNotFoundException extends RuntimeException {
	private static final long serialVersionUID = 1L;

	public ExerciseNotFoundException() {
		super("利用できる演習またはファイルが見つかりません。");
	}
}
