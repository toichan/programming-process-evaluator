package entity;

import java.util.List;

public record ExerciseDuplicatePreview(List<Item> items) {
	public ExerciseDuplicatePreview {
		items = List.copyOf(items);
	}

	public record Item(long entryId, String path, StudentExerciseEntry.Type type, String suggestedName) {
		public Item {
			StudentExerciseInput.requireId(entryId);
			if (path == null || type == null) throw new IllegalArgumentException("複製内容が正しくありません。");
			StudentExerciseInput.validateName(suggestedName);
		}
	}
}
