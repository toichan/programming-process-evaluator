CREATE TEMPORARY TABLE exercise_trash_backfill AS
WITH RECURSIVE hierarchy AS (
  SELECT exercise_entry_id, student_exercise_id, entry_type,
    CASE WHEN entry_status = 'active' THEN CAST(NULL AS SIGNED)
      ELSE exercise_entry_id END AS trash_root_entry_id
  FROM student_exercise_entries
  WHERE parent_entry_id IS NULL
  UNION ALL
  SELECT e.exercise_entry_id, e.student_exercise_id, e.entry_type,
    CASE WHEN e.entry_status = 'active' THEN h.trash_root_entry_id
      ELSE e.exercise_entry_id END
  FROM student_exercise_entries e
  JOIN hierarchy h ON h.exercise_entry_id = e.parent_entry_id
    AND h.student_exercise_id = e.student_exercise_id AND h.entry_type = 'folder'
)
SELECT exercise_entry_id, trash_root_entry_id FROM hierarchy;

CREATE TEMPORARY TABLE exercise_trash_migration_guard (
  hierarchy_complete INT NOT NULL CHECK (hierarchy_complete = 1)
);
INSERT INTO exercise_trash_migration_guard
SELECT (SELECT COUNT(*) FROM exercise_trash_backfill) =
  (SELECT COUNT(*) FROM student_exercise_entries);

ALTER TABLE student_exercise_entries
  ADD COLUMN trash_root_entry_id BIGINT NULL,
  ADD KEY ix_exercise_trash_root (student_exercise_id, trash_root_entry_id);

UPDATE student_exercise_entries e
JOIN exercise_trash_backfill b ON b.exercise_entry_id = e.exercise_entry_id
SET e.trash_root_entry_id = b.trash_root_entry_id;

ALTER TABLE student_exercise_entries
  DROP INDEX uq_student_exercise_entries_path,
  ADD COLUMN active_path_hash BINARY(32) GENERATED ALWAYS AS (
    CASE WHEN entry_status = 'active' AND trash_root_entry_id IS NULL
      THEN UNHEX(SHA2(WEIGHT_STRING(path), 256)) ELSE NULL END
  ) STORED,
  ADD UNIQUE KEY uq_student_exercise_entries_active_path (student_exercise_id, active_path_hash);

DROP TEMPORARY TABLE exercise_trash_migration_guard;
DROP TEMPORARY TABLE exercise_trash_backfill;
