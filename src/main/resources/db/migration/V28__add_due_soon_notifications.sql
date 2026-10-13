ALTER TABLE student_notifications
    DROP CHECK ck_student_notification_type,
    ADD CONSTRAINT ck_student_notification_type CHECK (
        notification_type IN ('evaluation_completed', 'evaluation_failed', 'task_published', 'due_soon')),
    ADD CONSTRAINT ck_student_notification_due_soon_refs CHECK (
        notification_type <> 'due_soon'
        OR (task_class_assignment_id IS NOT NULL AND evaluation_id IS NULL AND submission_id IS NULL)),
    ADD COLUMN due_soon_dedupe_key BIGINT GENERATED ALWAYS AS (
        CASE WHEN notification_type = 'due_soon' THEN task_class_assignment_id ELSE NULL END) STORED,
    ADD CONSTRAINT uq_student_notification_due_soon UNIQUE (student_user_id, due_soon_dedupe_key);
