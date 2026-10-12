-- Allows a "deadline_extended" notification each time a teacher extends (or reopens via extension)
-- a published class assignment's deadline. Unlike task_published/due_soon, no dedupe generated
-- column is added: repeated extensions of the same assignment are each a distinct, newsworthy event,
-- and a NULL evaluation_id/submission_id never collides with another NULL under InnoDB's unique index
-- semantics, so the existing per-type uniqueness protections for other notification types are unaffected.
ALTER TABLE student_notifications
    DROP CHECK ck_student_notification_type,
    ADD CONSTRAINT ck_student_notification_type CHECK (
        notification_type IN ('evaluation_completed', 'evaluation_failed', 'task_published', 'due_soon',
                               'deadline_extended')),
    ADD CONSTRAINT ck_student_notification_deadline_extended_refs CHECK (
        notification_type <> 'deadline_extended'
        OR (task_class_assignment_id IS NOT NULL AND evaluation_id IS NULL AND submission_id IS NULL));
