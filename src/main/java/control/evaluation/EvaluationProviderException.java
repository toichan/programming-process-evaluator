package control.evaluation;

public final class EvaluationProviderException extends Exception {
	private static final long serialVersionUID = 1L;

	private final boolean retryable;

	public EvaluationProviderException(String message, boolean retryable) {
		super(message);
		this.retryable = retryable;
	}

	public EvaluationProviderException(String message, boolean retryable, Throwable cause) {
		super(message, cause);
		this.retryable = retryable;
	}

	public boolean isRetryable() {
		return retryable;
	}
}
