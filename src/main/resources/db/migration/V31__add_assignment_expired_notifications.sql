ALTER TABLE student_notifications
    DROP CHECK ck_student_notification_type,
    ADD CONSTRAINT ck_student_notification_type CHECK (
        notification_type IN ('evaluation_completed', 'evaluation_failed', 'task_published', 'due_soon',
                              'deadline_extended', 'exercise_distributed', 'assignment_expired')),
    ADD CONSTRAINT ck_student_notification_assignment_expired_refs CHECK (
        notification_type <> 'assignment_expired'
        OR (task_class_assignment_id IS NOT NULL AND evaluation_id IS NULL AND submission_id IS NULL
            AND distribution_target_id IS NULL AND student_exercise_id IS NULL));
