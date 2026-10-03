package control.evaluation;

public final class EvaluationProviderException extends Exception {
	private static final long serialVersionUID = 1L;

	private final boolean retryable;
	private final Integer httpStatusCode;

	public EvaluationProviderException(String message, boolean retryable) {
		super(message);
		this.retryable = retryable;
		this.httpStatusCode = null;
	}

	public EvaluationProviderException(String message, boolean retryable, Throwable cause) {
		super(message, cause);
		this.retryable = retryable;
		this.httpStatusCode = null;
	}

	public EvaluationProviderException(String message, boolean retryable, int httpStatusCode) {
		super(message);
		this.retryable = retryable;
		this.httpStatusCode = httpStatusCode;
	}

	public Integer getHttpStatusCode() {
		return httpStatusCode;
	}

	public boolean isRetryable() {
		return retryable;
	}
}
