package servlet.student;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

final class ExerciseForm {
	static final int MAX_BYTES = 256 * 1024;

	private ExerciseForm() {}

	static Map<String, String> read(InputStream input) throws IOException {
		return read(input, MAX_BYTES);
	}

	static Map<String, String> read(InputStream input, int maximumBytes) throws IOException {
		byte[] body = input.readNBytes(maximumBytes + 1);
		if (body.length > maximumBytes) throw new TooLargeException();
		Map<String, String> values = new LinkedHashMap<>();
		for (String pair : utf8(body).split("&")) {
			if (pair.isEmpty()) continue;
			int separator = pair.indexOf('=');
			String name = decode(separator < 0 ? pair : pair.substring(0, separator));
			String value = decode(separator < 0 ? "" : pair.substring(separator + 1));
			if (values.putIfAbsent(name, value) != null) {
				throw new IllegalArgumentException("同じ入力項目を複数指定しないでください。");
			}
		}
		return Map.copyOf(values);
	}

	static long number(String value, boolean positive) {
		if (value == null || !value.matches("[0-9]+")) {
			throw new IllegalArgumentException("対象IDと版番号を正しく指定してください。");
		}
		try {
			long number = Long.parseLong(value);
			if (positive && number == 0) throw new NumberFormatException();
			return number;
		} catch (NumberFormatException e) {
			throw new IllegalArgumentException("対象IDと版番号を正しく指定してください。", e);
		}
	}

	private static String decode(String value) {
		byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
		ByteArrayOutputStream decoded = new ByteArrayOutputStream();
		for (int i = 0; i < bytes.length; i++) {
			int current = bytes[i] & 0xff;
			if (current == '%') {
				if (i + 2 >= bytes.length) throw new IllegalArgumentException("送信内容の文字コードが正しくありません。");
				int high = Character.digit((char) bytes[++i], 16);
				int low = Character.digit((char) bytes[++i], 16);
				if (high < 0 || low < 0) throw new IllegalArgumentException("送信内容の文字コードが正しくありません。");
				decoded.write(high * 16 + low);
			} else {
				decoded.write(current == '+' ? ' ' : current);
			}
		}
		return utf8(decoded.toByteArray());
	}

	static String utf8(byte[] bytes) {
		try {
			return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
					.onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
		} catch (CharacterCodingException e) {
			throw new IllegalArgumentException("送信内容の文字コードが正しくありません。", e);
		}
	}

	static final class TooLargeException extends IllegalArgumentException {
		private static final long serialVersionUID = 1L;
		TooLargeException() { super("送信内容が上限を超えています。"); }
	}
}
