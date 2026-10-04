package entity;

import java.util.List;

public record ExerciseUnification(boolean required, Long targetExerciseId, long expectedVersion,
		String token, int entryCount, List<Item> items) {
	public ExerciseUnification {
		items = List.copyOf(items);
	}

	public record Item(long entryId, long sourceExerciseId, String sourceName, String type,
			String status, String oldPath, String newPath) {}
}
