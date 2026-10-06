CREATE TABLE reevaluation_previews (
  reevaluation_preview_id BIGINT NOT NULL AUTO_INCREMENT,
  preview_code CHAR(36) NOT NULL,
  task_id BIGINT NOT NULL,
  prompt_version_id BIGINT NOT NULL,
  rubric_id BIGINT NOT NULL,
  requested_by_user_id BIGINT NOT NULL,
  task_version BIGINT NOT NULL,
  prompt_row_version BIGINT NOT NULL,
  current_prompt_version_id BIGINT NULL,
  participant_count INT NOT NULL,
  target_count INT NOT NULL,
  scope_fingerprint BINARY(32) NOT NULL,
  configuration_fingerprint BINARY(32) NOT NULL,
  preview_status ENUM('generating','retryable','ready','confirmed','cancelled','stale','expired') NOT NULL,
  created_at DATETIME(6) NOT NULL,
  expires_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  row_version BIGINT NOT NULL DEFAULT 1,
  PRIMARY KEY (reevaluation_preview_id),
  UNIQUE KEY uq_reevaluation_previews_code (preview_code),
  KEY idx_reevaluation_previews_expiry (preview_status, expires_at),
  KEY idx_reevaluation_previews_task (task_id, created_at),
  CONSTRAINT fk_reevaluation_previews_task
    FOREIGN KEY (task_id) REFERENCES tasks (task_id),
  CONSTRAINT fk_reevaluation_previews_prompt
    FOREIGN KEY (prompt_version_id) REFERENCES prompt_versions (prompt_version_id),
  CONSTRAINT fk_reevaluation_previews_rubric
    FOREIGN KEY (rubric_id) REFERENCES rubrics (rubric_id),
  CONSTRAINT fk_reevaluation_previews_requester
    FOREIGN KEY (requested_by_user_id) REFERENCES users (user_id),
  CONSTRAINT fk_reevaluation_previews_current_prompt
    FOREIGN KEY (current_prompt_version_id) REFERENCES prompt_versions (prompt_version_id),
  CONSTRAINT chk_reevaluation_previews_counts
    CHECK (participant_count >= target_count AND target_count >= 0),
  CONSTRAINT chk_reevaluation_previews_row_version
    CHECK (row_version >= 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE reevaluation_preview_targets (
  reevaluation_preview_target_id BIGINT NOT NULL AUTO_INCREMENT,
  reevaluation_preview_id BIGINT NOT NULL,
  participation_id BIGINT NOT NULL,
  submission_id BIGINT NULL,
  revision_number INT NULL,
  target_status ENUM(
    'excluded_no_submission',
    'excluded_submission_processing',
    'pending',
    'in_progress',
    'succeeded',
    'failed'
  ) NOT NULL,
  evaluation_payload JSON NULL,
  input_fingerprint BINARY(32) NULL,
  request_id CHAR(36) NULL,
  provider_response JSON NULL,
  validated_result JSON NULL,
  retry_count INT NOT NULL DEFAULT 0,
  safe_error_code VARCHAR(64) NULL,
  safe_error_message TEXT NULL,
  created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  PRIMARY KEY (reevaluation_preview_target_id),
  UNIQUE KEY uq_reevaluation_preview_targets_participation
    (reevaluation_preview_id, participation_id),
  KEY idx_reevaluation_preview_targets_status
    (reevaluation_preview_id, target_status),
  CONSTRAINT fk_reevaluation_preview_targets_preview
    FOREIGN KEY (reevaluation_preview_id)
    REFERENCES reevaluation_previews (reevaluation_preview_id) ON DELETE CASCADE,
  CONSTRAINT fk_reevaluation_preview_targets_participation
    FOREIGN KEY (participation_id) REFERENCES task_participations (participation_id),
  CONSTRAINT fk_reevaluation_preview_targets_submission
    FOREIGN KEY (submission_id) REFERENCES submissions (submission_id),
  CONSTRAINT chk_reevaluation_preview_targets_retry_count
    CHECK (retry_count >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE reevaluation_job_targets (
  reevaluation_job_target_id BIGINT NOT NULL AUTO_INCREMENT,
  reevaluation_job_id BIGINT NOT NULL,
  participation_id BIGINT NOT NULL,
  submission_id BIGINT NOT NULL,
  target_status ENUM('queued','in_progress','completed','failed') NOT NULL,
  evaluation_id BIGINT NULL,
  evaluation_payload JSON NULL,
  provider_response JSON NULL,
  validated_result JSON NULL,
  materialization_attempts INT NOT NULL DEFAULT 0,
  safe_error_code VARCHAR(64) NULL,
  safe_error_message TEXT NULL,
  created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  completed_at DATETIME(6) NULL,
  PRIMARY KEY (reevaluation_job_target_id),
  UNIQUE KEY uq_reevaluation_job_targets_participation
    (reevaluation_job_id, participation_id),
  UNIQUE KEY uq_reevaluation_job_targets_evaluation (evaluation_id),
  KEY idx_reevaluation_job_targets_claim (target_status, updated_at),
  CONSTRAINT fk_reevaluation_job_targets_job
    FOREIGN KEY (reevaluation_job_id) REFERENCES reevaluation_jobs (reevaluation_job_id),
  CONSTRAINT fk_reevaluation_job_targets_participation
    FOREIGN KEY (participation_id) REFERENCES task_participations (participation_id),
  CONSTRAINT fk_reevaluation_job_targets_submission
    FOREIGN KEY (submission_id) REFERENCES submissions (submission_id),
  CONSTRAINT fk_reevaluation_job_targets_evaluation
    FOREIGN KEY (evaluation_id) REFERENCES evaluations (evaluation_id),
  CONSTRAINT chk_reevaluation_job_targets_attempts
    CHECK (materialization_attempts >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
