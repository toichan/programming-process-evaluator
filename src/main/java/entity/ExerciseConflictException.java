package entity;

public final class ExerciseConflictException extends IllegalArgumentException {
	private static final long serialVersionUID = 1L;
	public enum Reason { UPDATE, NAME }
	private final Reason reason;

	public ExerciseConflictException(String message) {
		this(message, Reason.UPDATE);
	}

	public ExerciseConflictException(String message, Reason reason) {
		super(message);
		this.reason = java.util.Objects.requireNonNull(reason);
	}

	public Reason reason() { return reason; }
}
