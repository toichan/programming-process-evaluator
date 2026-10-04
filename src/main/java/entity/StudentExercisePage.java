package entity;

import java.util.List;

public record StudentExercisePage(
		List<Scope> scopes,
		Long selectedExerciseId,
		Long selectedEntryId,
		List<StudentExerciseEntry> entries,
		EditorPreferences preferences,
		ExerciseExecutionResult latestExecution) {

	public StudentExercisePage {
		scopes = List.copyOf(scopes);
		entries = List.copyOf(entries);
	}

	public record Scope(long exerciseId, String name, String origin, State state,
			boolean saved, long version) {
		public long getExerciseId() { return exerciseId; }
		public String getName() { return name; }
		public String getOrigin() { return origin; }
		public State getState() { return state; }
		public boolean isSaved() { return saved; }
		public long getVersion() { return version; }
	}

	public enum State {
		NOT_STARTED("not_started"), IN_PROGRESS("in_progress"),
		TEMPORARILY_SAVED("temporarily_saved"), COMPLETED("completed"),
		EXPIRED("expired"), NEEDS_REVIEW("needs_review"), ARCHIVED("archived");

		private final String value;

		State(String value) { this.value = value; }
		public String getValue() { return value; }
		public boolean isEditable() {
			return this == NOT_STARTED || this == IN_PROGRESS || this == TEMPORARILY_SAVED;
		}

		public static State fromValue(String value) {
			for (State state : values()) {
				if (state.value.equals(value)) return state;
			}
			throw new IllegalArgumentException("演習の状態が正しくありません。");
		}
	}

	public List<Scope> getScopes() { return scopes; }
	public Long getSelectedExerciseId() { return selectedExerciseId; }
	public Long getSelectedEntryId() { return selectedEntryId; }
	public List<StudentExerciseEntry> getEntries() { return entries; }
	public EditorPreferences getPreferences() { return preferences; }
	public ExerciseExecutionResult getLatestExecution() { return latestExecution; }
}
