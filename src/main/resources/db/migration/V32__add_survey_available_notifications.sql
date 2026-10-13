ALTER TABLE student_notifications
    ADD COLUMN survey_id BIGINT NULL AFTER task_class_assignment_id,
    ADD CONSTRAINT fk_student_notification_survey FOREIGN KEY (survey_id)
        REFERENCES surveys (survey_id);

ALTER TABLE student_notifications
    DROP CHECK ck_student_notification_type,
    ADD CONSTRAINT ck_student_notification_type CHECK (
        notification_type IN ('evaluation_completed', 'evaluation_failed', 'task_published', 'due_soon',
                              'deadline_extended', 'exercise_distributed', 'assignment_expired',
                              'survey_available')),
    ADD CONSTRAINT ck_student_notification_survey_available_refs CHECK (
        (notification_type = 'survey_available'
         AND evaluation_id IS NOT NULL AND submission_id IS NULL AND task_class_assignment_id IS NOT NULL
         AND survey_id IS NOT NULL AND distribution_target_id IS NULL AND student_exercise_id IS NULL)
        OR (notification_type <> 'survey_available' AND survey_id IS NULL));
