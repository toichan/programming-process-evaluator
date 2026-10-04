package entity;

import java.util.HashSet;
import java.util.List;
import java.util.Map;

public record ExerciseBatchInput(
		List<Long> entryIds,
		long expectedVersion,
		Long targetParentId,
		Map<Long, String> names,
		List<RestoreTarget> restorations) {

	public ExerciseBatchInput {
		if (entryIds == null || entryIds.isEmpty()) {
			throw new IllegalArgumentException("操作対象を選択してください。");
		}
		entryIds = List.copyOf(entryIds);
		var unique = new HashSet<Long>();
		for (Long id : entryIds) {
			if (id == null || id <= 0 || !unique.add(id)) {
				throw new IllegalArgumentException("操作対象の指定が重複または不正です。");
			}
		}
		StudentExerciseInput.requireVersion(expectedVersion);
		if (targetParentId != null) StudentExerciseInput.requireId(targetParentId);
		names = names == null ? Map.of() : Map.copyOf(names);
		for (var entry : names.entrySet()) {
			if (entry.getKey() == null || !unique.contains(entry.getKey())) {
				throw new IllegalArgumentException("別名は選択した項目にだけ指定できます。");
			}
			StudentExerciseInput.validateName(entry.getValue());
		}
		restorations = restorations == null ? List.of() : List.copyOf(restorations);
		var restoreIds = new HashSet<Long>();
		for (RestoreTarget target : restorations) {
			if (target == null || !unique.contains(target.entryId()) || !restoreIds.add(target.entryId())) {
				throw new IllegalArgumentException("復元先の指定が重複または不正です。");
			}
		}
	}

	public record RestoreTarget(long entryId, boolean destinationSpecified, Long parentEntryId, String name) {
		public RestoreTarget {
			StudentExerciseInput.requireId(entryId);
			if (parentEntryId != null) StudentExerciseInput.requireId(parentEntryId);
			if (name != null) StudentExerciseInput.validateName(name);
			if (!destinationSpecified && parentEntryId != null) {
				throw new IllegalArgumentException("復元先を指定せずに復元先フォルダは指定できません。");
			}
		}
	}
}
