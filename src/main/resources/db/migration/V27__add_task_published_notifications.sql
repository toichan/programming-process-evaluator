ALTER TABLE student_notifications
    DROP CHECK ck_student_notification_type,
    ADD CONSTRAINT ck_student_notification_type CHECK (
        notification_type IN ('evaluation_completed', 'evaluation_failed', 'task_published')),
    ADD CONSTRAINT ck_student_notification_task_published_refs CHECK (
        notification_type <> 'task_published'
        OR (task_class_assignment_id IS NOT NULL AND evaluation_id IS NULL AND submission_id IS NULL)),
    ADD COLUMN task_published_dedupe_key BIGINT GENERATED ALWAYS AS (
        CASE WHEN notification_type = 'task_published' THEN task_class_assignment_id ELSE NULL END) STORED,
    ADD CONSTRAINT uq_student_notification_task_published UNIQUE (student_user_id, task_published_dedupe_key);
