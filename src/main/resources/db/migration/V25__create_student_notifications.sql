CREATE TABLE student_notifications (
    notification_id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    student_user_id BIGINT NOT NULL,
    notification_type VARCHAR(40) NOT NULL,
    evaluation_id BIGINT NULL,
    submission_id BIGINT NULL,
    task_class_assignment_id BIGINT NULL,
    title VARCHAR(255) NOT NULL,
    message VARCHAR(500) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    read_at DATETIME(6) NULL,
    CONSTRAINT ck_student_notification_type CHECK (notification_type IN ('evaluation_completed')),
    CONSTRAINT uq_student_notification_event UNIQUE (student_user_id, notification_type, evaluation_id),
    INDEX idx_student_notifications_recent (student_user_id, created_at, notification_id),
    INDEX idx_student_notifications_unread (student_user_id, read_at, created_at),
    INDEX idx_student_notifications_created (created_at),
    CONSTRAINT fk_student_notification_user FOREIGN KEY (student_user_id)
        REFERENCES users (user_id) ON DELETE CASCADE,
    CONSTRAINT fk_student_notification_evaluation FOREIGN KEY (evaluation_id)
        REFERENCES evaluations (evaluation_id) ON DELETE CASCADE,
    CONSTRAINT fk_student_notification_submission FOREIGN KEY (submission_id)
        REFERENCES submissions (submission_id) ON DELETE CASCADE,
    CONSTRAINT fk_student_notification_assignment FOREIGN KEY (task_class_assignment_id)
        REFERENCES task_class_assignments (task_class_assignment_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
