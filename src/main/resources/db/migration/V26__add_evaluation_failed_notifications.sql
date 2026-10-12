ALTER TABLE student_notifications
    DROP CHECK ck_student_notification_type,
    DROP CHECK ck_student_notification_evaluation_refs,
    ADD CONSTRAINT ck_student_notification_type CHECK (
        notification_type IN ('evaluation_completed', 'evaluation_failed')),
    ADD CONSTRAINT ck_student_notification_evaluation_refs CHECK (
        notification_type NOT IN ('evaluation_completed', 'evaluation_failed')
        OR (evaluation_id IS NOT NULL AND submission_id IS NOT NULL AND task_class_assignment_id IS NOT NULL));
