ALTER TABLE student_notifications
    ADD COLUMN distribution_target_id BIGINT NULL,
    ADD COLUMN student_exercise_id BIGINT NULL,
    ADD CONSTRAINT fk_student_notification_distribution_target FOREIGN KEY (distribution_target_id)
        REFERENCES distribution_targets (distribution_target_id),
    ADD CONSTRAINT fk_student_notification_student_exercise FOREIGN KEY (student_exercise_id)
        REFERENCES student_exercises (student_exercise_id),
    DROP CHECK ck_student_notification_type,
    ADD CONSTRAINT ck_student_notification_type CHECK (
        notification_type IN ('evaluation_completed', 'evaluation_failed', 'task_published', 'due_soon',
                               'deadline_extended', 'exercise_distributed')),
    ADD CONSTRAINT ck_student_notification_exercise_distributed_refs CHECK (
        notification_type <> 'exercise_distributed'
        OR (distribution_target_id IS NOT NULL AND student_exercise_id IS NOT NULL
            AND evaluation_id IS NULL AND submission_id IS NULL AND task_class_assignment_id IS NULL)),
    ADD CONSTRAINT ck_student_notification_non_exercise_delivery_refs CHECK (
        notification_type = 'exercise_distributed'
        OR (distribution_target_id IS NULL AND student_exercise_id IS NULL)),
    ADD COLUMN exercise_distributed_dedupe_key BIGINT GENERATED ALWAYS AS (
        CASE WHEN notification_type = 'exercise_distributed' THEN distribution_target_id ELSE NULL END) STORED,
    ADD CONSTRAINT uq_student_notification_exercise_distributed
        UNIQUE (student_user_id, exercise_distributed_dedupe_key);
