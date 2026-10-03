-- Initial schema derived from docs/database-design/table-definitions.md.
-- Foreign keys intentionally omit ON DELETE CASCADE to preserve history.
-- Long utf8mb4 paths use collation-weight hashes to keep full-path uniqueness within InnoDB key limits.
SET NAMES utf8mb4;

CREATE TABLE `users` (
  `user_id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_type` ENUM('student','teacher','admin') NOT NULL,
  `login_id` VARCHAR(64) NOT NULL,
  `password_hash` VARCHAR(255) NOT NULL,
  `display_name` VARCHAR(100) NOT NULL,
  `account_status` ENUM('active','suspended','deleted') NOT NULL,
  `created_by_user_id` BIGINT NULL,
  `updated_by_user_id` BIGINT NULL,
  `created_at` DATETIME NOT NULL,
  `updated_at` DATETIME NULL,
  `deleted_at` DATETIME NULL,
  PRIMARY KEY (`user_id`),
  UNIQUE KEY `uq_users_1` (`login_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `student_profiles` (
  `user_id` BIGINT NOT NULL,
  `student_code` VARCHAR(32) NOT NULL,
  `security_level` TINYINT NOT NULL,
  `first_login_status` ENUM('not_logged_in','password_change_required','completed') NOT NULL,
  `must_change_password` BOOLEAN NOT NULL,
  PRIMARY KEY (`user_id`),
  UNIQUE KEY `uq_student_profiles_1` (`student_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `research_subject_identifiers` (
  `research_subject_identifier_id` BIGINT NOT NULL AUTO_INCREMENT,
  `student_user_id` BIGINT NOT NULL,
  `research_subject_code` VARCHAR(64) NOT NULL,
  `generated_at` DATETIME NOT NULL,
  `generated_by_user_id` BIGINT NULL,
  PRIMARY KEY (`research_subject_identifier_id`),
  UNIQUE KEY `uq_research_subject_identifiers_1` (`student_user_id`),
  UNIQUE KEY `uq_research_subject_identifiers_2` (`research_subject_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `schools` (
  `school_id` BIGINT NOT NULL AUTO_INCREMENT,
  `school_code` VARCHAR(64) NOT NULL,
  `name` VARCHAR(200) NOT NULL,
  `school_status` ENUM('active','inactive') NOT NULL,
  `created_at` DATETIME NOT NULL,
  `updated_at` DATETIME NULL,
  PRIMARY KEY (`school_id`),
  UNIQUE KEY `uq_schools_1` (`school_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `classrooms` (
  `classroom_id` BIGINT NOT NULL AUTO_INCREMENT,
  `school_id` BIGINT NOT NULL,
  `name` VARCHAR(100) NOT NULL,
  `grade_name` VARCHAR(50) NULL,
  `classroom_status` ENUM('active','inactive') NOT NULL,
  `created_at` DATETIME NOT NULL,
  `updated_at` DATETIME NULL,
  PRIMARY KEY (`classroom_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `student_class_memberships` (
  `membership_id` BIGINT NOT NULL AUTO_INCREMENT,
  `student_user_id` BIGINT NOT NULL,
  `classroom_id` BIGINT NOT NULL,
  `membership_status` ENUM('active','inactive') NOT NULL,
  `joined_at` DATETIME NOT NULL,
  `left_at` DATETIME NULL,
  PRIMARY KEY (`membership_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `teacher_school_permissions` (
  `teacher_school_permission_id` BIGINT NOT NULL AUTO_INCREMENT,
  `teacher_user_id` BIGINT NOT NULL,
  `school_id` BIGINT NOT NULL,
  `access_status` ENUM('enabled','disabled') NOT NULL,
  `updated_by_user_id` BIGINT NOT NULL,
  `updated_at` DATETIME NOT NULL,
  PRIMARY KEY (`teacher_school_permission_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `teacher_feature_permissions` (
  `teacher_feature_permission_id` BIGINT NOT NULL AUTO_INCREMENT,
  `teacher_user_id` BIGINT NOT NULL,
  `feature_code` VARCHAR(64) NOT NULL,
  `is_enabled` BOOLEAN NOT NULL,
  `updated_by_user_id` BIGINT NOT NULL,
  `updated_at` DATETIME NOT NULL,
  PRIMARY KEY (`teacher_feature_permission_id`),
  UNIQUE KEY `uq_teacher_feature_permissions_1` (`teacher_user_id`, `feature_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `password_reset_records` (
  `password_reset_record_id` BIGINT NOT NULL AUTO_INCREMENT,
  `target_user_id` BIGINT NOT NULL,
  `requested_by_user_id` BIGINT NOT NULL,
  `reset_status` ENUM('requested','completed','expired','cancelled') NOT NULL,
  `must_change_at_next_login` BOOLEAN NOT NULL,
  `expires_at` DATETIME NULL,
  `requested_at` DATETIME NOT NULL,
  `completed_at` DATETIME NULL,
  PRIMARY KEY (`password_reset_record_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `credential_history` (
  `credential_history_id` BIGINT NOT NULL AUTO_INCREMENT,
  `target_user_id` BIGINT NOT NULL,
  `actor_user_id` BIGINT NULL,
  `password_reset_record_id` BIGINT NULL,
  `action_type` ENUM('initial_credential_issued','first_password_changed','password_changed','password_reset') NOT NULL,
  `result_status` ENUM('success','failure') NOT NULL,
  `error_code` VARCHAR(64) NULL,
  `error_message` TEXT NULL,
  `occurred_at` DATETIME NOT NULL,
  PRIMARY KEY (`credential_history_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `tasks` (
  `task_id` BIGINT NOT NULL AUTO_INCREMENT,
  `task_code` VARCHAR(64) NOT NULL,
  `task_revision_code` VARCHAR(80) NOT NULL,
  `revision_number` INT NOT NULL,
  `supersedes_task_id` BIGINT NULL,
  `created_by_user_id` BIGINT NOT NULL,
  `updated_by_user_id` BIGINT NULL,
  `rubric_id` BIGINT NULL,
  `rubric_version` VARCHAR(50) NULL,
  `active_prompt_version_id` BIGINT NULL,
  `title` VARCHAR(255) NOT NULL,
  `theme` VARCHAR(255) NULL,
  `difficulty` ENUM('beginner','intermediate','advanced','none') NULL,
  `language` VARCHAR(32) NOT NULL,
  `description` TEXT NOT NULL,
  `input_constraints` TEXT NULL,
  `creation_rules` TEXT NULL,
  `initial_code` LONGTEXT NULL,
  `save_status` ENUM('draft','saved') NOT NULL,
  `publication_status` ENUM('draft','published','requires_update','archived') NOT NULL,
  `created_at` DATETIME NOT NULL,
  `updated_at` DATETIME NULL,
  `published_at` DATETIME NULL,
  `deleted_at` DATETIME NULL,
  `deleted_by_user_id` BIGINT NULL,
  PRIMARY KEY (`task_id`),
  UNIQUE KEY `uq_tasks_1` (`task_revision_code`),
  UNIQUE KEY `uq_tasks_2` (`task_code`, `revision_number`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `task_features` (
  `feature_id` BIGINT NOT NULL AUTO_INCREMENT,
  `task_id` BIGINT NOT NULL,
  `feature_text` TEXT NOT NULL,
  `sort_order` INT NOT NULL,
  `record_status` ENUM('active','inactive') NOT NULL,
  PRIMARY KEY (`feature_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `task_class_assignments` (
  `task_class_assignment_id` BIGINT NOT NULL AUTO_INCREMENT,
  `task_id` BIGINT NOT NULL,
  `classroom_id` BIGINT NOT NULL,
  `assignment_status` ENUM('not_published','scheduled','published','requires_update','expired','archived') NOT NULL,
  `publish_at` DATETIME NULL,
  `due_at` DATETIME NULL,
  `late_submission_policy` ENUM('allow','deny') NOT NULL,
  `resubmission_policy` ENUM('allow','deny') NOT NULL,
  `created_at` DATETIME NOT NULL,
  `updated_at` DATETIME NULL,
  PRIMARY KEY (`task_class_assignment_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `task_participations` (
  `participation_id` BIGINT NOT NULL AUTO_INCREMENT,
  `student_user_id` BIGINT NOT NULL,
  `task_class_assignment_id` BIGINT NOT NULL,
  `learning_status` ENUM('not_started','in_progress','completed','needs_revision') NOT NULL,
  `progress_status` ENUM('not_started','in_progress','submitted','awaiting_evaluation','completed','needs_action') NOT NULL,
  `save_status` ENUM('unsaved','draft','saved') NOT NULL,
  `current_draft_code` LONGTEXT NULL,
  `draft_base_submission_id` BIGINT NULL,
  `draft_updated_at` DATETIME NULL,
  `evaluation_status` ENUM('not_started','in_progress','completed','failed','needs_revision') NOT NULL,
  `active_duration_seconds` BIGINT NOT NULL,
  `last_activity_at` DATETIME NULL,
  `completed_at` DATETIME NULL,
  PRIMARY KEY (`participation_id`),
  UNIQUE KEY `uq_task_participations_1` (`student_user_id`, `task_class_assignment_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `task_activity_sessions` (
  `activity_session_id` BIGINT NOT NULL AUTO_INCREMENT,
  `participation_id` BIGINT NOT NULL,
  `session_status` ENUM('active','ended','timed_out') NOT NULL,
  `started_at` DATETIME NOT NULL,
  `last_active_at` DATETIME NOT NULL,
  `ended_at` DATETIME NULL,
  `active_duration_seconds` BIGINT NOT NULL,
  PRIMARY KEY (`activity_session_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `task_test_cases` (
  `test_case_id` BIGINT NOT NULL AUTO_INCREMENT,
  `task_id` BIGINT NOT NULL,
  `title` VARCHAR(255) NULL,
  `test_case_input` TEXT NOT NULL,
  `test_case_output` TEXT NOT NULL,
  `test_case_order` INT NOT NULL,
  `record_status` ENUM('active','inactive') NOT NULL,
  PRIMARY KEY (`test_case_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `task_hints` (
  `hint_id` BIGINT NOT NULL AUTO_INCREMENT,
  `task_id` BIGINT NOT NULL,
  `hint_order` INT NOT NULL,
  `hint_title` VARCHAR(255) NOT NULL,
  `hint_content` TEXT NOT NULL,
  `usage_syntax` TEXT NULL,
  `hint_code` LONGTEXT NULL,
  `created_at` DATETIME NOT NULL,
  `updated_at` DATETIME NULL,
  `record_status` ENUM('active','inactive') NOT NULL,
  PRIMARY KEY (`hint_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `submissions` (
  `submission_id` BIGINT NOT NULL AUTO_INCREMENT,
  `participation_id` BIGINT NOT NULL,
  `revision_number` INT NOT NULL,
  `submitted_code` LONGTEXT NOT NULL,
  `submission_status` ENUM('submitted','accepted','locked') NOT NULL,
  `submitted_at` DATETIME NOT NULL,
  `review_locked_at` DATETIME NULL,
  `created_at` DATETIME NOT NULL,
  PRIMARY KEY (`submission_id`),
  UNIQUE KEY `uq_submissions_1` (`participation_id`, `revision_number`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `code_logs` (
  `code_log_id` BIGINT NOT NULL AUTO_INCREMENT,
  `participation_id` BIGINT NOT NULL,
  `submission_id` BIGINT NULL,
  `code_execution_id` BIGINT NULL,
  `event_type` ENUM('periodic_snapshot','manual_save','submission','edit') NOT NULL,
  `snapshot_text` LONGTEXT NULL,
  `execution_status_at_capture` ENUM('not_run','succeeded','failed','timed_out') NULL,
  `observed_at` DATETIME NOT NULL,
  `created_at` DATETIME NOT NULL,
  PRIMARY KEY (`code_log_id`),
  KEY `idx_code_logs_1` (`participation_id`, `observed_at`),
  KEY `idx_code_logs_2` (`event_type`, `observed_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `code_executions` (
  `execution_id` BIGINT NOT NULL AUTO_INCREMENT,
  `participation_id` BIGINT NULL,
  `submission_id` BIGINT NULL,
  `actor_user_id` BIGINT NULL,
  `execution_context` ENUM('student_task','submission_check','teacher_review_copy','distribution_template') NOT NULL,
  `source_code` LONGTEXT NOT NULL,
  `standard_input` LONGTEXT NULL,
  `execution_status` ENUM('queued','running','succeeded','failed','timed_out') NOT NULL,
  `exit_code` INT NULL,
  `standard_output` LONGTEXT NULL,
  `standard_output_truncated` BOOLEAN NOT NULL,
  `standard_error` LONGTEXT NULL,
  `standard_error_truncated` BOOLEAN NOT NULL,
  `error_code` VARCHAR(64) NULL,
  `error_message` TEXT NULL,
  `duration_milliseconds` INT NULL,
  `executed_at` DATETIME NOT NULL,
  PRIMARY KEY (`execution_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `code_execution_test_results` (
  `execution_test_result_id` BIGINT NOT NULL AUTO_INCREMENT,
  `execution_id` BIGINT NOT NULL,
  `test_case_id` BIGINT NULL,
  `case_order` INT NOT NULL,
  `input_snapshot` LONGTEXT NULL,
  `expected_output_snapshot` LONGTEXT NULL,
  `actual_output` LONGTEXT NULL,
  `actual_output_truncated` BOOLEAN NOT NULL,
  `result_status` ENUM('matched','mismatched','error','not_run') NOT NULL,
  `error_code` VARCHAR(64) NULL,
  `error_message` TEXT NULL,
  `checked_at` DATETIME NOT NULL,
  PRIMARY KEY (`execution_test_result_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `rubrics` (
  `rubric_id` BIGINT NOT NULL AUTO_INCREMENT,
  `created_by_user_id` BIGINT NOT NULL,
  `title` VARCHAR(255) NOT NULL,
  `version` VARCHAR(50) NOT NULL,
  `rubric_status` ENUM('draft','active','archived') NOT NULL,
  `created_at` DATETIME NOT NULL,
  `updated_at` DATETIME NULL,
  PRIMARY KEY (`rubric_id`),
  UNIQUE KEY `uq_rubrics_1` (`title`, `version`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `rubric_dimensions` (
  `dimension_id` BIGINT NOT NULL AUTO_INCREMENT,
  `rubric_id` BIGINT NOT NULL,
  `dimension_code` VARCHAR(64) NOT NULL,
  `label` VARCHAR(255) NOT NULL,
  `scale_minimum` INT NOT NULL,
  `scale_maximum` INT NOT NULL,
  `scale_label` VARCHAR(64) NOT NULL,
  `sort_order` INT NOT NULL,
  PRIMARY KEY (`dimension_id`),
  UNIQUE KEY `uq_rubric_dimensions_1` (`rubric_id`, `dimension_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `rubric_criteria` (
  `criterion_id` BIGINT NOT NULL AUTO_INCREMENT,
  `rubric_id` BIGINT NOT NULL,
  `dimension_id` BIGINT NOT NULL,
  `criterion_code` VARCHAR(64) NOT NULL,
  `name` VARCHAR(255) NOT NULL,
  `description` TEXT NULL,
  `weight` DECIMAL(6,3) NOT NULL,
  `sort_order` INT NOT NULL,
  PRIMARY KEY (`criterion_id`),
  UNIQUE KEY `uq_rubric_criteria_1` (`rubric_id`, `criterion_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `criterion_levels` (
  `criterion_level_id` BIGINT NOT NULL AUTO_INCREMENT,
  `criterion_id` BIGINT NOT NULL,
  `level_value` INT NOT NULL,
  `level_label` VARCHAR(100) NOT NULL,
  `level_description` TEXT NOT NULL,
  PRIMARY KEY (`criterion_level_id`),
  UNIQUE KEY `uq_criterion_levels_1` (`criterion_id`, `level_value`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `prompt_versions` (
  `prompt_version_id` BIGINT NOT NULL AUTO_INCREMENT,
  `task_id` BIGINT NOT NULL,
  `version` VARCHAR(50) NOT NULL,
  `ai_model` VARCHAR(100) NOT NULL,
  `common_prompt` LONGTEXT NOT NULL,
  `additional_evaluation_instruction` TEXT NULL,
  `prompt_status` ENUM('draft','configured','requires_review','versioned') NOT NULL,
  `fluctuation_generation_status` ENUM('not_generated','in_progress','completed','failed') NOT NULL,
  `evaluation_examples_status` ENUM('not_generated','in_progress','completed','failed') NOT NULL,
  `created_by_user_id` BIGINT NOT NULL,
  `created_at` DATETIME NOT NULL,
  PRIMARY KEY (`prompt_version_id`),
  UNIQUE KEY `uq_prompt_versions_1` (`task_id`, `version`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `prompt_fluctuation_items` (
  `fluctuation_item_id` BIGINT NOT NULL AUTO_INCREMENT,
  `prompt_version_id` BIGINT NOT NULL,
  `item_text` TEXT NOT NULL,
  `teacher_resolution` TEXT NULL,
  `resolution_status` ENUM('pending','resolved','not_applicable') NOT NULL,
  `sort_order` INT NOT NULL,
  PRIMARY KEY (`fluctuation_item_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `evaluation_examples` (
  `evaluation_example_id` BIGINT NOT NULL AUTO_INCREMENT,
  `prompt_version_id` BIGINT NOT NULL,
  `example_input` JSON NOT NULL,
  `example_output` JSON NOT NULL,
  `example_status` ENUM('active','inactive') NOT NULL,
  `created_at` DATETIME NOT NULL,
  PRIMARY KEY (`evaluation_example_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `evaluations` (
  `evaluation_id` BIGINT NOT NULL AUTO_INCREMENT,
  `evaluation_code` VARCHAR(64) NOT NULL,
  `submission_id` BIGINT NOT NULL,
  `reevaluation_job_id` BIGINT NULL,
  `rubric_id` BIGINT NOT NULL,
  `prompt_version_id` BIGINT NULL,
  `evaluation_status` ENUM('not_started','in_progress','completed','failed','needs_revision') NOT NULL,
  `evaluation_kind` ENUM('initial','reevaluation','manual_review') NOT NULL,
  `format_version` VARCHAR(32) NULL,
  `locale` VARCHAR(16) NULL,
  `generated_at` DATETIME NULL,
  `final_evaluated_at` DATETIME NULL,
  `auto_save_count` INT NOT NULL,
  `execution_count` INT NOT NULL,
  `overall_score` DECIMAL(6,3) NULL,
  `process_analysis` LONGTEXT NULL,
  `feedback_summary` TEXT NULL,
  `created_at` DATETIME NOT NULL,
  `completed_at` DATETIME NULL,
  PRIMARY KEY (`evaluation_id`),
  UNIQUE KEY `uq_evaluations_1` (`evaluation_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `evaluation_scores` (
  `evaluation_score_id` BIGINT NOT NULL AUTO_INCREMENT,
  `evaluation_id` BIGINT NOT NULL,
  `criterion_id` BIGINT NOT NULL,
  `dimension_id` BIGINT NOT NULL,
  `criterion_level_id` BIGINT NULL,
  `metric_key` VARCHAR(64) NOT NULL,
  `metric_title` VARCHAR(255) NOT NULL,
  `score_value` INT NOT NULL,
  `metric_description` TEXT NULL,
  `rationale` TEXT NULL,
  PRIMARY KEY (`evaluation_score_id`),
  UNIQUE KEY `uq_evaluation_scores_1` (`evaluation_id`, `metric_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `evaluation_dimension_results` (
  `evaluation_dimension_result_id` BIGINT NOT NULL AUTO_INCREMENT,
  `evaluation_id` BIGINT NOT NULL,
  `dimension_id` BIGINT NOT NULL,
  `score_value` DECIMAL(6,3) NOT NULL,
  `score_text` VARCHAR(32) NULL,
  `summary_title` VARCHAR(255) NULL,
  `summary_description` TEXT NULL,
  PRIMARY KEY (`evaluation_dimension_result_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `evaluation_reasons` (
  `evaluation_reason_id` BIGINT NOT NULL AUTO_INCREMENT,
  `evaluation_id` BIGINT NOT NULL,
  `reason_code` VARCHAR(64) NOT NULL,
  `dimension_id` BIGINT NULL,
  `category_label` VARCHAR(100) NULL,
  `title` VARCHAR(255) NOT NULL,
  `score_value` DECIMAL(6,3) NULL,
  `body` LONGTEXT NOT NULL,
  `sort_order` INT NOT NULL,
  PRIMARY KEY (`evaluation_reason_id`),
  UNIQUE KEY `uq_evaluation_reasons_1` (`evaluation_id`, `reason_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `evaluation_reason_details` (
  `reason_detail_id` BIGINT NOT NULL AUTO_INCREMENT,
  `evaluation_reason_id` BIGINT NOT NULL,
  `detail_code` VARCHAR(64) NULL,
  `detail_text` TEXT NOT NULL,
  `sort_order` INT NOT NULL,
  PRIMARY KEY (`reason_detail_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `evaluation_evidence` (
  `evidence_id` BIGINT NOT NULL AUTO_INCREMENT,
  `evaluation_score_id` BIGINT NULL,
  `evaluation_reason_id` BIGINT NULL,
  `reason_detail_id` BIGINT NULL,
  `code_log_id` BIGINT NULL,
  `code_execution_id` BIGINT NULL,
  `evidence_type` VARCHAR(64) NOT NULL,
  `evidence_label` VARCHAR(255) NULL,
  `source_record_type` VARCHAR(64) NULL,
  `source_record_id` VARCHAR(128) NULL,
  `evidence_value` TEXT NULL,
  `evidence_unit` VARCHAR(64) NULL,
  `code_range` VARCHAR(255) NULL,
  `evidence_summary` TEXT NOT NULL,
  `sort_order` INT NOT NULL,
  PRIMARY KEY (`evidence_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `evaluation_feedback` (
  `feedback_id` BIGINT NOT NULL AUTO_INCREMENT,
  `evaluation_id` BIGINT NOT NULL,
  `feedback_type` ENUM('positive','warning','improvement','critical') NOT NULL,
  `priority` ENUM('high','medium','low') NOT NULL,
  `message` TEXT NOT NULL,
  `created_at` DATETIME NOT NULL,
  PRIMARY KEY (`feedback_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `reevaluation_jobs` (
  `reevaluation_job_id` BIGINT NOT NULL AUTO_INCREMENT,
  `task_id` BIGINT NOT NULL,
  `prompt_version_id` BIGINT NOT NULL,
  `requested_by_user_id` BIGINT NOT NULL,
  `reevaluation_status` ENUM('queued','in_progress','completed','failed','cancelled') NOT NULL,
  `target_count` INT NOT NULL,
  `completed_count` INT NOT NULL,
  `progress_percent` DECIMAL(5,2) NOT NULL,
  `result_summary` JSON NULL,
  `started_at` DATETIME NULL,
  `completed_at` DATETIME NULL,
  PRIMARY KEY (`reevaluation_job_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `distribution_templates` (
  `distribution_template_id` BIGINT NOT NULL AUTO_INCREMENT,
  `created_by_user_id` BIGINT NOT NULL,
  `updated_by_user_id` BIGINT NULL,
  `template_name` VARCHAR(255) NOT NULL,
  `root_path` VARCHAR(500) NULL,
  `save_status` ENUM('draft','saved') NOT NULL,
  `template_status` ENUM('active','archived') NOT NULL,
  `overwrite_policy` ENUM('overwrite','append') NOT NULL,
  `created_at` DATETIME NOT NULL,
  `updated_at` DATETIME NULL,
  PRIMARY KEY (`distribution_template_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `distribution_template_files` (
  `template_file_id` BIGINT NOT NULL AUTO_INCREMENT,
  `distribution_template_id` BIGINT NOT NULL,
  `path` VARCHAR(1000) NOT NULL,
  `path_hash` BINARY(32) GENERATED ALWAYS AS (UNHEX(SHA2(WEIGHT_STRING(`path`), 256))) STORED,
  `entry_type` ENUM('folder','file') NOT NULL,
  `initial_content` LONGTEXT NULL,
  `entry_status` ENUM('active','inactive') NOT NULL,
  PRIMARY KEY (`template_file_id`),
  UNIQUE KEY `uq_distribution_template_files_path` (`distribution_template_id`, `path_hash`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `distributions` (
  `distribution_id` BIGINT NOT NULL AUTO_INCREMENT,
  `distribution_template_id` BIGINT NOT NULL,
  `executed_by_user_id` BIGINT NOT NULL,
  `distribution_status` ENUM('draft','scheduled','in_progress','completed','stopped') NOT NULL,
  `created_at` DATETIME NOT NULL,
  `completed_at` DATETIME NULL,
  PRIMARY KEY (`distribution_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `distribution_targets` (
  `distribution_target_id` BIGINT NOT NULL AUTO_INCREMENT,
  `distribution_id` BIGINT NOT NULL,
  `classroom_id` BIGINT NOT NULL,
  `target_status` ENUM('not_distributed','scheduled','distributed','stopped') NOT NULL,
  `scheduled_at` DATETIME NULL,
  `distributed_at` DATETIME NULL,
  `execution_result` TEXT NULL,
  PRIMARY KEY (`distribution_target_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `distribution_histories` (
  `distribution_history_id` BIGINT NOT NULL AUTO_INCREMENT,
  `distribution_id` BIGINT NOT NULL,
  `actor_user_id` BIGINT NOT NULL,
  `action_type` VARCHAR(64) NOT NULL,
  `change_detail` JSON NULL,
  `execution_status` ENUM('success','failure') NOT NULL,
  `occurred_at` DATETIME NOT NULL,
  PRIMARY KEY (`distribution_history_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `student_exercises` (
  `student_exercise_id` BIGINT NOT NULL AUTO_INCREMENT,
  `student_user_id` BIGINT NOT NULL,
  `distribution_target_id` BIGINT NULL,
  `exercise_origin` ENUM('distribution','student_created') NOT NULL,
  `scope_name` VARCHAR(255) NOT NULL,
  `exercise_status` ENUM('not_started','in_progress','temporarily_saved','completed','expired','needs_review','archived') NOT NULL,
  `save_status` ENUM('unsaved','saved') NOT NULL,
  `started_at` DATETIME NULL,
  `completed_at` DATETIME NULL,
  `expires_at` DATETIME NULL,
  `created_at` DATETIME NOT NULL,
  `updated_at` DATETIME NULL,
  `deleted_at` DATETIME NULL,
  `deleted_by_user_id` BIGINT NULL,
  PRIMARY KEY (`student_exercise_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `student_exercise_entries` (
  `exercise_entry_id` BIGINT NOT NULL AUTO_INCREMENT,
  `student_exercise_id` BIGINT NOT NULL,
  `parent_entry_id` BIGINT NULL,
  `entry_type` ENUM('folder','file') NOT NULL,
  `name` VARCHAR(255) NOT NULL,
  `path` VARCHAR(1000) NOT NULL,
  `path_hash` BINARY(32) GENERATED ALWAYS AS (UNHEX(SHA2(WEIGHT_STRING(`path`), 256))) STORED,
  `language` VARCHAR(32) NULL,
  `description` TEXT NULL,
  `current_content` LONGTEXT NULL,
  `entry_status` ENUM('active','trashed','deleted') NOT NULL,
  `created_at` DATETIME NOT NULL,
  `updated_at` DATETIME NULL,
  `trashed_at` DATETIME NULL,
  `deleted_at` DATETIME NULL,
  PRIMARY KEY (`exercise_entry_id`),
  UNIQUE KEY `uq_student_exercise_entries_path` (`student_exercise_id`, `path_hash`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `consent_document_versions` (
  `consent_document_version_id` BIGINT NOT NULL AUTO_INCREMENT,
  `version_code` VARCHAR(50) NOT NULL,
  `title` VARCHAR(255) NOT NULL,
  `body` LONGTEXT NOT NULL,
  `document_status` ENUM('draft','active','retired') NOT NULL,
  `effective_at` DATETIME NULL,
  `created_by_user_id` BIGINT NULL,
  `created_at` DATETIME NOT NULL,
  PRIMARY KEY (`consent_document_version_id`),
  UNIQUE KEY `uq_consent_document_versions_1` (`version_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `consent_records` (
  `consent_id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL,
  `consent_status` ENUM('unconfirmed','agreed','declined','withdrawn') NOT NULL,
  `consent_document_version_id` BIGINT NOT NULL,
  `consented_at` DATETIME NULL,
  `withdrawn_at` DATETIME NULL,
  `updated_at` DATETIME NOT NULL,
  PRIMARY KEY (`consent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `surveys` (
  `survey_id` BIGINT NOT NULL AUTO_INCREMENT,
  `task_id` BIGINT NULL,
  `title` VARCHAR(255) NOT NULL,
  `survey_status` ENUM('draft','active','closed','archived') NOT NULL,
  `created_at` DATETIME NOT NULL,
  `updated_at` DATETIME NULL,
  PRIMARY KEY (`survey_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `survey_questions` (
  `question_id` BIGINT NOT NULL AUTO_INCREMENT,
  `survey_id` BIGINT NOT NULL,
  `question_code` VARCHAR(64) NOT NULL,
  `question_type` ENUM('rating','text','single_choice','multiple_choice') NOT NULL,
  `prompt_text` TEXT NOT NULL,
  `required` BOOLEAN NOT NULL,
  `sort_order` INT NOT NULL,
  `question_status` ENUM('active','inactive') NOT NULL,
  PRIMARY KEY (`question_id`),
  UNIQUE KEY `uq_survey_questions_1` (`survey_id`, `question_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `survey_question_options` (
  `option_id` BIGINT NOT NULL AUTO_INCREMENT,
  `question_id` BIGINT NOT NULL,
  `option_code` VARCHAR(64) NOT NULL,
  `label` VARCHAR(255) NOT NULL,
  `value` VARCHAR(255) NOT NULL,
  `sort_order` INT NOT NULL,
  `option_status` ENUM('active','inactive') NOT NULL,
  PRIMARY KEY (`option_id`),
  UNIQUE KEY `uq_survey_question_options_1` (`question_id`, `option_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `survey_responses` (
  `survey_response_id` BIGINT NOT NULL AUTO_INCREMENT,
  `response_code` VARCHAR(64) NOT NULL,
  `record_code` VARCHAR(64) NOT NULL,
  `student_user_id` BIGINT NOT NULL,
  `survey_id` BIGINT NOT NULL,
  `task_id` BIGINT NULL,
  `evaluation_id` BIGINT NULL,
  `consent_status_at_submission` ENUM('unconfirmed','agreed','declined','withdrawn') NOT NULL,
  `response_status` ENUM('not_answered','in_progress','submitted','not_applicable') NOT NULL,
  `started_at` DATETIME NULL,
  `submitted_at` DATETIME NULL,
  PRIMARY KEY (`survey_response_id`),
  UNIQUE KEY `uq_survey_responses_1` (`response_code`),
  UNIQUE KEY `uq_survey_responses_2` (`record_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `survey_answers` (
  `survey_answer_id` BIGINT NOT NULL AUTO_INCREMENT,
  `survey_response_id` BIGINT NOT NULL,
  `question_id` BIGINT NOT NULL,
  `answer_value` TEXT NULL,
  `answer_reason` TEXT NULL,
  PRIMARY KEY (`survey_answer_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `evaluation_requests` (
  `evaluation_request_id` BIGINT NOT NULL AUTO_INCREMENT,
  `evaluation_id` BIGINT NOT NULL,
  `model_id` VARCHAR(100) NOT NULL,
  `prompt_version` VARCHAR(50) NOT NULL,
  `rubric_version` VARCHAR(50) NOT NULL,
  `actor_role` ENUM('student','teacher','admin','system') NOT NULL,
  `consent_status_at_request` ENUM('unconfirmed','agreed','declined','withdrawn') NOT NULL,
  `locale` VARCHAR(16) NULL,
  `request_payload` JSON NOT NULL,
  `request_status` ENUM('queued','in_progress','succeeded','failed') NOT NULL,
  `retry_count` INT NOT NULL,
  `requested_at` DATETIME NOT NULL,
  `completed_at` DATETIME NULL,
  `error_detail` TEXT NULL,
  PRIMARY KEY (`evaluation_request_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `evaluation_responses` (
  `evaluation_response_id` BIGINT NOT NULL AUTO_INCREMENT,
  `evaluation_request_id` BIGINT NOT NULL,
  `raw_response` JSON NOT NULL,
  `response_status` ENUM('received','validated','invalid') NOT NULL,
  `confidence` DECIMAL(5,4) NULL,
  `warnings` JSON NULL,
  `received_at` DATETIME NOT NULL,
  PRIMARY KEY (`evaluation_response_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `evaluation_input_snapshots` (
  `input_snapshot_id` BIGINT NOT NULL AUTO_INCREMENT,
  `evaluation_request_id` BIGINT NOT NULL,
  `anonymized_subject_id` VARCHAR(128) NOT NULL,
  `task_snapshot` JSON NOT NULL,
  `submission_snapshot` JSON NOT NULL,
  `log_range_start` DATETIME NULL,
  `log_range_end` DATETIME NULL,
  `created_at` DATETIME NOT NULL,
  PRIMARY KEY (`input_snapshot_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `login_history` (
  `login_history_id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NULL,
  `attempted_login_id` VARCHAR(64) NULL,
  `operation_type` ENUM('login','logout','password_change','password_reset') NOT NULL,
  `result_status` ENUM('success','failure') NOT NULL,
  `error_code` VARCHAR(64) NULL,
  `error_message` TEXT NULL,
  `ip_address` VARCHAR(45) NULL,
  `user_agent` VARCHAR(512) NULL,
  `session_id` VARCHAR(128) NULL,
  `error_detail` TEXT NULL,
  `occurred_at` DATETIME NOT NULL,
  PRIMARY KEY (`login_history_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `audit_logs` (
  `audit_log_id` BIGINT NOT NULL AUTO_INCREMENT,
  `actor_user_id` BIGINT NULL,
  `actor_role` ENUM('student','teacher','admin','system') NOT NULL,
  `feature_code` VARCHAR(64) NOT NULL,
  `target_type` VARCHAR(100) NOT NULL,
  `target_id` BIGINT NULL,
  `action_type` VARCHAR(64) NOT NULL,
  `result_status` ENUM('success','failure') NOT NULL,
  `error_code` VARCHAR(64) NULL,
  `error_message` TEXT NULL,
  `detail` TEXT NULL,
  `before_data` JSON NULL,
  `after_data` JSON NULL,
  `request_id` VARCHAR(128) NULL,
  `occurred_at` DATETIME NOT NULL,
  PRIMARY KEY (`audit_log_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `application_error_logs` (
  `application_error_log_id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NULL,
  `request_id` VARCHAR(128) NULL,
  `feature_code` VARCHAR(64) NULL,
  `operation_name` VARCHAR(128) NULL,
  `error_category` ENUM('validation','authentication','authorization','database','execution','external_api','unexpected') NOT NULL,
  `error_code` VARCHAR(64) NULL,
  `user_message` TEXT NULL,
  `diagnostic_detail` LONGTEXT NULL,
  `occurred_at` DATETIME NOT NULL,
  PRIMARY KEY (`application_error_log_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

ALTER TABLE `users` ADD FOREIGN KEY (`created_by_user_id`) REFERENCES `users` (`user_id`);
ALTER TABLE `users` ADD FOREIGN KEY (`updated_by_user_id`) REFERENCES `users` (`user_id`);
ALTER TABLE `student_profiles` ADD FOREIGN KEY (`user_id`) REFERENCES `users` (`user_id`);
ALTER TABLE `research_subject_identifiers` ADD FOREIGN KEY (`student_user_id`) REFERENCES `student_profiles` (`user_id`);
ALTER TABLE `research_subject_identifiers` ADD FOREIGN KEY (`generated_by_user_id`) REFERENCES `users` (`user_id`);
ALTER TABLE `classrooms` ADD FOREIGN KEY (`school_id`) REFERENCES `schools` (`school_id`);
ALTER TABLE `student_class_memberships` ADD FOREIGN KEY (`student_user_id`) REFERENCES `student_profiles` (`user_id`);
ALTER TABLE `student_class_memberships` ADD FOREIGN KEY (`classroom_id`) REFERENCES `classrooms` (`classroom_id`);
ALTER TABLE `teacher_school_permissions` ADD FOREIGN KEY (`teacher_user_id`) REFERENCES `users` (`user_id`);
ALTER TABLE `teacher_school_permissions` ADD FOREIGN KEY (`school_id`) REFERENCES `schools` (`school_id`);
ALTER TABLE `teacher_school_permissions` ADD FOREIGN KEY (`updated_by_user_id`) REFERENCES `users` (`user_id`);
ALTER TABLE `teacher_feature_permissions` ADD FOREIGN KEY (`teacher_user_id`) REFERENCES `users` (`user_id`);
ALTER TABLE `teacher_feature_permissions` ADD FOREIGN KEY (`updated_by_user_id`) REFERENCES `users` (`user_id`);
ALTER TABLE `password_reset_records` ADD FOREIGN KEY (`target_user_id`) REFERENCES `users` (`user_id`);
ALTER TABLE `password_reset_records` ADD FOREIGN KEY (`requested_by_user_id`) REFERENCES `users` (`user_id`);
ALTER TABLE `credential_history` ADD FOREIGN KEY (`target_user_id`) REFERENCES `users` (`user_id`);
ALTER TABLE `credential_history` ADD FOREIGN KEY (`actor_user_id`) REFERENCES `users` (`user_id`);
ALTER TABLE `credential_history` ADD FOREIGN KEY (`password_reset_record_id`) REFERENCES `password_reset_records` (`password_reset_record_id`);
ALTER TABLE `tasks` ADD FOREIGN KEY (`supersedes_task_id`) REFERENCES `tasks` (`task_id`);
ALTER TABLE `tasks` ADD FOREIGN KEY (`created_by_user_id`) REFERENCES `users` (`user_id`);
ALTER TABLE `tasks` ADD FOREIGN KEY (`updated_by_user_id`) REFERENCES `users` (`user_id`);
ALTER TABLE `tasks` ADD FOREIGN KEY (`rubric_id`) REFERENCES `rubrics` (`rubric_id`);
ALTER TABLE `tasks` ADD FOREIGN KEY (`active_prompt_version_id`) REFERENCES `prompt_versions` (`prompt_version_id`);
ALTER TABLE `tasks` ADD FOREIGN KEY (`deleted_by_user_id`) REFERENCES `users` (`user_id`);
ALTER TABLE `task_features` ADD FOREIGN KEY (`task_id`) REFERENCES `tasks` (`task_id`);
ALTER TABLE `task_class_assignments` ADD FOREIGN KEY (`task_id`) REFERENCES `tasks` (`task_id`);
ALTER TABLE `task_class_assignments` ADD FOREIGN KEY (`classroom_id`) REFERENCES `classrooms` (`classroom_id`);
ALTER TABLE `task_participations` ADD FOREIGN KEY (`student_user_id`) REFERENCES `student_profiles` (`user_id`);
ALTER TABLE `task_participations` ADD FOREIGN KEY (`task_class_assignment_id`) REFERENCES `task_class_assignments` (`task_class_assignment_id`);
ALTER TABLE `task_participations` ADD FOREIGN KEY (`draft_base_submission_id`) REFERENCES `submissions` (`submission_id`);
ALTER TABLE `task_activity_sessions` ADD FOREIGN KEY (`participation_id`) REFERENCES `task_participations` (`participation_id`);
ALTER TABLE `task_test_cases` ADD FOREIGN KEY (`task_id`) REFERENCES `tasks` (`task_id`);
ALTER TABLE `task_hints` ADD FOREIGN KEY (`task_id`) REFERENCES `tasks` (`task_id`);
ALTER TABLE `submissions` ADD FOREIGN KEY (`participation_id`) REFERENCES `task_participations` (`participation_id`);
ALTER TABLE `code_logs` ADD FOREIGN KEY (`participation_id`) REFERENCES `task_participations` (`participation_id`);
ALTER TABLE `code_logs` ADD FOREIGN KEY (`submission_id`) REFERENCES `submissions` (`submission_id`);
ALTER TABLE `code_logs` ADD FOREIGN KEY (`code_execution_id`) REFERENCES `code_executions` (`execution_id`);
ALTER TABLE `code_executions` ADD FOREIGN KEY (`participation_id`) REFERENCES `task_participations` (`participation_id`);
ALTER TABLE `code_executions` ADD FOREIGN KEY (`submission_id`) REFERENCES `submissions` (`submission_id`);
ALTER TABLE `code_executions` ADD FOREIGN KEY (`actor_user_id`) REFERENCES `users` (`user_id`);
ALTER TABLE `code_execution_test_results` ADD FOREIGN KEY (`execution_id`) REFERENCES `code_executions` (`execution_id`);
ALTER TABLE `code_execution_test_results` ADD FOREIGN KEY (`test_case_id`) REFERENCES `task_test_cases` (`test_case_id`);
ALTER TABLE `rubrics` ADD FOREIGN KEY (`created_by_user_id`) REFERENCES `users` (`user_id`);
ALTER TABLE `rubric_dimensions` ADD FOREIGN KEY (`rubric_id`) REFERENCES `rubrics` (`rubric_id`);
ALTER TABLE `rubric_criteria` ADD FOREIGN KEY (`rubric_id`) REFERENCES `rubrics` (`rubric_id`);
ALTER TABLE `rubric_criteria` ADD FOREIGN KEY (`dimension_id`) REFERENCES `rubric_dimensions` (`dimension_id`);
ALTER TABLE `criterion_levels` ADD FOREIGN KEY (`criterion_id`) REFERENCES `rubric_criteria` (`criterion_id`);
ALTER TABLE `prompt_versions` ADD FOREIGN KEY (`task_id`) REFERENCES `tasks` (`task_id`);
ALTER TABLE `prompt_versions` ADD FOREIGN KEY (`created_by_user_id`) REFERENCES `users` (`user_id`);
ALTER TABLE `prompt_fluctuation_items` ADD FOREIGN KEY (`prompt_version_id`) REFERENCES `prompt_versions` (`prompt_version_id`);
ALTER TABLE `evaluation_examples` ADD FOREIGN KEY (`prompt_version_id`) REFERENCES `prompt_versions` (`prompt_version_id`);
ALTER TABLE `evaluations` ADD FOREIGN KEY (`submission_id`) REFERENCES `submissions` (`submission_id`);
ALTER TABLE `evaluations` ADD FOREIGN KEY (`reevaluation_job_id`) REFERENCES `reevaluation_jobs` (`reevaluation_job_id`);
ALTER TABLE `evaluations` ADD FOREIGN KEY (`rubric_id`) REFERENCES `rubrics` (`rubric_id`);
ALTER TABLE `evaluations` ADD FOREIGN KEY (`prompt_version_id`) REFERENCES `prompt_versions` (`prompt_version_id`);
ALTER TABLE `evaluation_scores` ADD FOREIGN KEY (`evaluation_id`) REFERENCES `evaluations` (`evaluation_id`);
ALTER TABLE `evaluation_scores` ADD FOREIGN KEY (`criterion_id`) REFERENCES `rubric_criteria` (`criterion_id`);
ALTER TABLE `evaluation_scores` ADD FOREIGN KEY (`dimension_id`) REFERENCES `rubric_dimensions` (`dimension_id`);
ALTER TABLE `evaluation_scores` ADD FOREIGN KEY (`criterion_level_id`) REFERENCES `criterion_levels` (`criterion_level_id`);
ALTER TABLE `evaluation_dimension_results` ADD FOREIGN KEY (`evaluation_id`) REFERENCES `evaluations` (`evaluation_id`);
ALTER TABLE `evaluation_dimension_results` ADD FOREIGN KEY (`dimension_id`) REFERENCES `rubric_dimensions` (`dimension_id`);
ALTER TABLE `evaluation_reasons` ADD FOREIGN KEY (`evaluation_id`) REFERENCES `evaluations` (`evaluation_id`);
ALTER TABLE `evaluation_reasons` ADD FOREIGN KEY (`dimension_id`) REFERENCES `rubric_dimensions` (`dimension_id`);
ALTER TABLE `evaluation_reason_details` ADD FOREIGN KEY (`evaluation_reason_id`) REFERENCES `evaluation_reasons` (`evaluation_reason_id`);
ALTER TABLE `evaluation_evidence` ADD FOREIGN KEY (`evaluation_score_id`) REFERENCES `evaluation_scores` (`evaluation_score_id`);
ALTER TABLE `evaluation_evidence` ADD FOREIGN KEY (`evaluation_reason_id`) REFERENCES `evaluation_reasons` (`evaluation_reason_id`);
ALTER TABLE `evaluation_evidence` ADD FOREIGN KEY (`reason_detail_id`) REFERENCES `evaluation_reason_details` (`reason_detail_id`);
ALTER TABLE `evaluation_evidence` ADD FOREIGN KEY (`code_log_id`) REFERENCES `code_logs` (`code_log_id`);
ALTER TABLE `evaluation_evidence` ADD FOREIGN KEY (`code_execution_id`) REFERENCES `code_executions` (`execution_id`);
ALTER TABLE `evaluation_feedback` ADD FOREIGN KEY (`evaluation_id`) REFERENCES `evaluations` (`evaluation_id`);
ALTER TABLE `reevaluation_jobs` ADD FOREIGN KEY (`task_id`) REFERENCES `tasks` (`task_id`);
ALTER TABLE `reevaluation_jobs` ADD FOREIGN KEY (`prompt_version_id`) REFERENCES `prompt_versions` (`prompt_version_id`);
ALTER TABLE `reevaluation_jobs` ADD FOREIGN KEY (`requested_by_user_id`) REFERENCES `users` (`user_id`);
ALTER TABLE `distribution_templates` ADD FOREIGN KEY (`created_by_user_id`) REFERENCES `users` (`user_id`);
ALTER TABLE `distribution_templates` ADD FOREIGN KEY (`updated_by_user_id`) REFERENCES `users` (`user_id`);
ALTER TABLE `distribution_template_files` ADD FOREIGN KEY (`distribution_template_id`) REFERENCES `distribution_templates` (`distribution_template_id`);
ALTER TABLE `distributions` ADD FOREIGN KEY (`distribution_template_id`) REFERENCES `distribution_templates` (`distribution_template_id`);
ALTER TABLE `distributions` ADD FOREIGN KEY (`executed_by_user_id`) REFERENCES `users` (`user_id`);
ALTER TABLE `distribution_targets` ADD FOREIGN KEY (`distribution_id`) REFERENCES `distributions` (`distribution_id`);
ALTER TABLE `distribution_targets` ADD FOREIGN KEY (`classroom_id`) REFERENCES `classrooms` (`classroom_id`);
ALTER TABLE `distribution_histories` ADD FOREIGN KEY (`distribution_id`) REFERENCES `distributions` (`distribution_id`);
ALTER TABLE `distribution_histories` ADD FOREIGN KEY (`actor_user_id`) REFERENCES `users` (`user_id`);
ALTER TABLE `student_exercises` ADD FOREIGN KEY (`student_user_id`) REFERENCES `student_profiles` (`user_id`);
ALTER TABLE `student_exercises` ADD FOREIGN KEY (`distribution_target_id`) REFERENCES `distribution_targets` (`distribution_target_id`);
ALTER TABLE `student_exercises` ADD FOREIGN KEY (`deleted_by_user_id`) REFERENCES `users` (`user_id`);
ALTER TABLE `student_exercise_entries` ADD FOREIGN KEY (`student_exercise_id`) REFERENCES `student_exercises` (`student_exercise_id`);
ALTER TABLE `student_exercise_entries` ADD FOREIGN KEY (`parent_entry_id`) REFERENCES `student_exercise_entries` (`exercise_entry_id`);
ALTER TABLE `consent_document_versions` ADD FOREIGN KEY (`created_by_user_id`) REFERENCES `users` (`user_id`);
ALTER TABLE `consent_records` ADD FOREIGN KEY (`user_id`) REFERENCES `users` (`user_id`);
ALTER TABLE `consent_records` ADD FOREIGN KEY (`consent_document_version_id`) REFERENCES `consent_document_versions` (`consent_document_version_id`);
ALTER TABLE `surveys` ADD FOREIGN KEY (`task_id`) REFERENCES `tasks` (`task_id`);
ALTER TABLE `survey_questions` ADD FOREIGN KEY (`survey_id`) REFERENCES `surveys` (`survey_id`);
ALTER TABLE `survey_question_options` ADD FOREIGN KEY (`question_id`) REFERENCES `survey_questions` (`question_id`);
ALTER TABLE `survey_responses` ADD FOREIGN KEY (`student_user_id`) REFERENCES `student_profiles` (`user_id`);
ALTER TABLE `survey_responses` ADD FOREIGN KEY (`survey_id`) REFERENCES `surveys` (`survey_id`);
ALTER TABLE `survey_responses` ADD FOREIGN KEY (`task_id`) REFERENCES `tasks` (`task_id`);
ALTER TABLE `survey_responses` ADD FOREIGN KEY (`evaluation_id`) REFERENCES `evaluations` (`evaluation_id`);
ALTER TABLE `survey_answers` ADD FOREIGN KEY (`survey_response_id`) REFERENCES `survey_responses` (`survey_response_id`);
ALTER TABLE `survey_answers` ADD FOREIGN KEY (`question_id`) REFERENCES `survey_questions` (`question_id`);
ALTER TABLE `evaluation_requests` ADD FOREIGN KEY (`evaluation_id`) REFERENCES `evaluations` (`evaluation_id`);
ALTER TABLE `evaluation_responses` ADD FOREIGN KEY (`evaluation_request_id`) REFERENCES `evaluation_requests` (`evaluation_request_id`);
ALTER TABLE `evaluation_input_snapshots` ADD FOREIGN KEY (`evaluation_request_id`) REFERENCES `evaluation_requests` (`evaluation_request_id`);
ALTER TABLE `login_history` ADD FOREIGN KEY (`user_id`) REFERENCES `users` (`user_id`);
ALTER TABLE `audit_logs` ADD FOREIGN KEY (`actor_user_id`) REFERENCES `users` (`user_id`);
ALTER TABLE `application_error_logs` ADD FOREIGN KEY (`user_id`) REFERENCES `users` (`user_id`);
