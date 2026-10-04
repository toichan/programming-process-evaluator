package entity;

import java.nio.charset.StandardCharsets;

public final class PythonExecutionInput {
	public static final int MAX_SOURCE_BYTES = 64 * 1024;
	public static final int MAX_INPUT_BYTES = 8 * 1024;

	private PythonExecutionInput() {}

	public static String validateSource(String source) {
		return validate(source, MAX_SOURCE_BYTES, "コードは64 KiB以下で入力してください。");
	}

	public static String validateStandardInput(String input) {
		return validate(input, MAX_INPUT_BYTES, "入力は8 KiB以下で指定してください。");
	}

	private static String validate(String text, int limit, String message) {
		if (text == null || text.indexOf('\0') >= 0) {
			throw new IllegalArgumentException(message);
		}
		if (text.getBytes(StandardCharsets.UTF_8).length > limit) {
			throw new TooLargeException(message);
		}
		return text;
	}

	public static final class TooLargeException extends IllegalArgumentException {
		private static final long serialVersionUID = 1L;
		public TooLargeException(String message) { super(message); }
	}
}
