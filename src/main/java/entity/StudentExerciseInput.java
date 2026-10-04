package entity;

public record StudentExerciseInput(StudentExerciseEntry.Type type, String name) {
	public static final int MAX_NAME_CHARACTERS = 255;
	public static final int MAX_PATH_CHARACTERS = 1000;
	public static final int MAX_CODE_BYTES = PythonExecutionInput.MAX_SOURCE_BYTES;
	public static final int MAX_INPUT_BYTES = PythonExecutionInput.MAX_INPUT_BYTES;

	public StudentExerciseInput {
		if (type == null) {
			throw new IllegalArgumentException("フォルダまたはファイルを指定してください。");
		}
		validateName(name);
		if (type == StudentExerciseEntry.Type.FOLDER && name.indexOf('.') >= 0) {
			throw new IllegalArgumentException("フォルダ名に『.』は使えません。例：『授業1』『練習用』");
		}
		if (type == StudentExerciseEntry.Type.FILE
				&& !name.regionMatches(true, name.length() - 3, ".py", 0, 3)) {
			name += ".py";
			validateName(name);
		}
	}

	public String pathUnder(String parentPath) {
		if (parentPath != null && !parentPath.isEmpty()) {
			for (String part : parentPath.split("/", -1)) {
				validateName(part);
			}
		}
		String path = parentPath == null || parentPath.isEmpty() ? name : parentPath + "/" + name;
		if (path.codePointCount(0, path.length()) > MAX_PATH_CHARACTERS) {
			throw new IllegalArgumentException("パス全体は1000文字以内にしてください。");
		}
		return path;
	}

	public static long requireId(long id) {
		if (id <= 0) {
			throw new IllegalArgumentException("対象を正しく指定してください。");
		}
		return id;
	}

	public static long requireVersion(long version) {
		if (version < 0 || version == Long.MAX_VALUE) {
			throw new IllegalArgumentException("画面を再読み込みしてから操作してください。");
		}
		return version;
	}

	public static String validateCode(String code) {
		PythonExecutionInput.validateSource(code);
		requireValidUnicode(code);
		return code;
	}

	public static String validateStandardInput(String input) {
		PythonExecutionInput.validateStandardInput(input);
		requireValidUnicode(input);
		return input;
	}

	public static void validateName(String name) {
		if (name == null || name.isBlank()
				|| name.codePointCount(0, name.length()) > MAX_NAME_CHARACTERS) {
			throw new IllegalArgumentException("名前は空白だけにせず、1〜255文字で入力してください。");
		}
		requireValidUnicode(name);
		if (".".equals(name) || "..".equals(name)
				|| name.indexOf('/') >= 0 || name.indexOf('\\') >= 0
				|| name.codePoints().anyMatch(Character::isISOControl)) {
			throw new IllegalArgumentException("名前に /（スラッシュ）、\\（バックスラッシュ）、改行、タブなどは使えません。「.」だけ・「..」だけの名前も使えません。");
		}
	}

	private static void requireValidUnicode(String text) {
		for (int index = 0; index < text.length(); index++) {
			char current = text.charAt(index);
			if (Character.isHighSurrogate(current)) {
				if (index + 1 >= text.length() || !Character.isLowSurrogate(text.charAt(index + 1))) {
					throw new IllegalArgumentException("入力に不正な文字が含まれています。");
				}
				index++;
			} else if (Character.isLowSurrogate(current)) {
				throw new IllegalArgumentException("入力に不正な文字が含まれています。");
			}
		}
	}
}
