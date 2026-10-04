package entity;

import java.util.List;

public record ExerciseTree(Long exerciseId, List<Scope> scopes, List<Entry> entries,
		Long selectedEntryId, Execution latestExecution) {
	public static ExerciseTree from(StudentExercisePage page) {
		return new ExerciseTree(page.selectedExerciseId(),
				page.scopes().stream().map(s -> new Scope(s.exerciseId(), s.name(), s.version(),
						s.state().isEditable())).toList(),
				page.entries().stream().map(e -> new Entry(e.entryId(), e.parentEntryId(),
						e.type().getValue(), e.status().getValue(), e.name(), e.path(), e.content(),
						e.trashRootEntryId(), e.trashedAt() == null ? null : e.trashedAt().toString(),
						e.updatedAt() == null ? null : e.updatedAt().toString())).toList(),
				page.selectedEntryId(), page.latestExecution() == null ? null
						: new Execution(page.latestExecution().executionId(), page.latestExecution().result(),
								page.latestExecution().standardInput(),
								page.latestExecution().executedAt() == null ? null
										: page.latestExecution().executedAt().toString()));
	}

	public record Scope(long exerciseId, String name, long version, boolean editable) {}
	public record Entry(long entryId, Long parentId, String type, String status,
			String name, String path, String content, Long trashRootEntryId, String trashedAt, String updatedAt) {}

	public record Execution(long executionId, PythonExecutionResult result, String standardInput,
			String executedAt) {}
}
