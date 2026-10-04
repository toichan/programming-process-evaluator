package entity;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;

public record ExerciseUpload(List<File> files) {
	public static final int MAX_FILES = 100;
	public static final int MAX_TOTAL_BYTES = 1024 * 1024;

	public ExerciseUpload {
		if (files == null || files.isEmpty() || files.size() > MAX_FILES) {
			throw new IllegalArgumentException("アップロードは1〜100ファイルで指定してください。");
		}
		if (files.stream().anyMatch(java.util.Objects::isNull)) {
			throw new IllegalArgumentException("アップロードファイルを正しく指定してください。");
		}
		files = List.copyOf(files);
		var paths = new HashSet<String>();
		long bytes = 0;
		for (File file : files) {
			if (file.path() == null || !file.path().matches("(?is).*\\.py$")) {
				throw new IllegalArgumentException("アップロードできるのは.pyファイルだけです。");
			}
			String[] parts = file.path().split("/", -1);
			for (int i = 0; i < parts.length - 1; i++) {
				new StudentExerciseInput(StudentExerciseEntry.Type.FOLDER, parts[i]);
			}
			var name = new StudentExerciseInput(StudentExerciseEntry.Type.FILE, parts[parts.length - 1]);
			name.pathUnder(parts.length == 1 ? null
					: file.path().substring(0, file.path().lastIndexOf('/')));
			if (!paths.add(file.path())) {
				throw new IllegalArgumentException("同じアップロードパスを複数指定しないでください。");
			}
			StudentExerciseInput.validateCode(file.code());
			bytes += file.code().getBytes(StandardCharsets.UTF_8).length;
			if (bytes > MAX_TOTAL_BYTES) {
				throw new IllegalArgumentException("アップロードするコードの合計は1 MiB以内にしてください。");
			}
		}
	}

	public record File(String path, String code) {}
}
