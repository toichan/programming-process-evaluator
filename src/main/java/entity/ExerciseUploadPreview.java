package entity;

import java.util.List;

public record ExerciseUploadPreview(List<Conflict> conflicts) {
	public ExerciseUploadPreview {
		conflicts = List.copyOf(conflicts);
	}

	public record Conflict(String path, StudentExerciseEntry.Type uploadType,
			StudentExerciseEntry.Type existingType, String suggestedName, List<String> actions) {
		public Conflict {
			if (path == null || uploadType == null) throw new IllegalArgumentException("競合情報が正しくありません。");
			StudentExerciseInput.validateName(suggestedName);
			actions = List.copyOf(actions);
		}
	}
}
