ALTER TABLE `student_exercises`
  ADD COLUMN `version` BIGINT NOT NULL DEFAULT 0,
  ADD CONSTRAINT `chk_student_exercise_version` CHECK (`version` >= 0),
  ADD KEY `idx_student_exercise_owner_origin`
    (`student_user_id`, `exercise_origin`, `student_exercise_id`);

ALTER TABLE `code_executions`
  MODIFY COLUMN `execution_context`
    ENUM('student_task','submission_check','teacher_review_copy','distribution_template','student_exercise') NOT NULL,
  ADD COLUMN `exercise_entry_id` BIGINT NULL,
  ADD KEY `idx_code_execution_exercise` (`exercise_entry_id`, `execution_id`),
  ADD CONSTRAINT `fk_code_execution_exercise_entry`
    FOREIGN KEY (`exercise_entry_id`) REFERENCES `student_exercise_entries` (`exercise_entry_id`),
  ADD CONSTRAINT `chk_code_execution_exercise_context` CHECK (
    (`execution_context` = 'student_exercise'
      AND `exercise_entry_id` IS NOT NULL AND `actor_user_id` IS NOT NULL
      AND `participation_id` IS NULL AND `submission_id` IS NULL)
    OR (`execution_context` <> 'student_exercise' AND `exercise_entry_id` IS NULL)
  );
