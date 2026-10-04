ALTER TABLE student_exercises
  ADD COLUMN merged_into_exercise_id BIGINT NULL,
  ADD KEY ix_student_exercises_unified_root (student_user_id, merged_into_exercise_id),
  ADD CONSTRAINT fk_exercise_unified_root FOREIGN KEY (merged_into_exercise_id)
    REFERENCES student_exercises (student_exercise_id);

ALTER TABLE student_exercise_entries
  ADD COLUMN source_exercise_id BIGINT NULL,
  ADD KEY ix_exercise_entry_source (source_exercise_id),
  ADD CONSTRAINT fk_exercise_entry_source FOREIGN KEY (source_exercise_id)
    REFERENCES student_exercises (student_exercise_id);

UPDATE student_exercise_entries SET source_exercise_id = student_exercise_id;
