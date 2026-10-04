package entity;

public record ExerciseUploadResolution(String path, Action action, String name) {
	public ExerciseUploadResolution {
		if (path == null || path.isBlank() || action == null) {
			throw new IllegalArgumentException("アップロードの同名解決が正しくありません。");
		}
		String[] parts = path.split("/", -1);
		for (String part : parts) StudentExerciseInput.validateName(part);
		if (action == Action.RENAME) StudentExerciseInput.validateName(name);
		else if (name != null) throw new IllegalArgumentException("別名以外の解決方法に名前は指定できません。");
	}

	public enum Action { MERGE, RENAME, SKIP }
}
