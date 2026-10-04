package control.student;

import java.io.IOException;

public final class PythonRunnerRequestException extends IOException {
	private static final long serialVersionUID = 1L;
	private final int statusCode;
	private final String errorCode;

	PythonRunnerRequestException(int statusCode, String errorCode) {
		super("The isolated Python execution service returned HTTP " + statusCode + ": " + errorCode);
		this.statusCode = statusCode;
		this.errorCode = errorCode;
	}

	public int statusCode() { return statusCode; }
	public String errorCode() { return errorCode; }
}
