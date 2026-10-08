package entity;

import java.util.List;

public record TeacherExerciseDetail(TeacherExerciseRow row, List<Scope> scopes) {
	public TeacherExerciseDetail { scopes = List.copyOf(scopes); }
	public record Scope(long exerciseId, String name, List<StudentExerciseEntry> entries) {
		public Scope { entries = List.copyOf(entries); }
	}
}
