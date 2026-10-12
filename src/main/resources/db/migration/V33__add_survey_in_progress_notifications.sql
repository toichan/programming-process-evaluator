ALTER TABLE student_notifications
    DROP CHECK ck_student_notification_type,
    DROP CHECK ck_student_notification_survey_available_refs,
    ADD CONSTRAINT ck_student_notification_type CHECK (
        notification_type IN ('evaluation_completed', 'evaluation_failed', 'task_published', 'due_soon',
                              'deadline_extended', 'exercise_distributed', 'assignment_expired',
                              'survey_available', 'survey_in_progress')),
    ADD CONSTRAINT ck_student_notification_survey_refs CHECK (
        (notification_type IN ('survey_available', 'survey_in_progress')
         AND evaluation_id IS NOT NULL AND submission_id IS NULL AND task_class_assignment_id IS NOT NULL
         AND survey_id IS NOT NULL AND distribution_target_id IS NULL AND student_exercise_id IS NULL)
        OR (notification_type NOT IN ('survey_available', 'survey_in_progress') AND survey_id IS NULL));
