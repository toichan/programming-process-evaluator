UPDATE distribution_templates
SET overwrite_policy = 'append'
WHERE overwrite_policy = 'overwrite';

ALTER TABLE distribution_templates
  MODIFY COLUMN overwrite_policy ENUM('append') NOT NULL,
  ADD COLUMN template_version INT NOT NULL DEFAULT 0;

ALTER TABLE distributions
  ADD COLUMN template_name_snapshot VARCHAR(255) NOT NULL DEFAULT '',
  ADD COLUMN root_path_snapshot VARCHAR(500) NOT NULL DEFAULT '',
  ADD COLUMN request_token CHAR(36) NULL;

ALTER TABLE distributions
  ADD UNIQUE KEY uq_distributions_actor_request (executed_by_user_id, request_token);

UPDATE distributions d
JOIN distribution_templates t ON t.distribution_template_id = d.distribution_template_id
SET d.template_name_snapshot = t.template_name,
    d.root_path_snapshot = COALESCE(t.root_path, t.template_name)
WHERE d.template_name_snapshot = '';

ALTER TABLE distribution_targets
  ADD UNIQUE KEY uq_distribution_targets_distribution_class (distribution_id, classroom_id);

ALTER TABLE student_exercises
  ADD UNIQUE KEY uq_student_exercises_distribution_student (distribution_target_id, student_user_id);

CREATE TABLE distribution_snapshot_files (
  distribution_snapshot_file_id BIGINT NOT NULL AUTO_INCREMENT,
  distribution_id BIGINT NOT NULL,
  path VARCHAR(1000) NOT NULL,
  path_hash BINARY(32) GENERATED ALWAYS AS (UNHEX(SHA2(WEIGHT_STRING(path), 256))) STORED,
  entry_type ENUM('folder','file') NOT NULL,
  initial_content LONGTEXT NULL,
  PRIMARY KEY (distribution_snapshot_file_id),
  UNIQUE KEY uq_distribution_snapshot_files_path (distribution_id, path_hash),
  CONSTRAINT fk_distribution_snapshot_files_distribution
    FOREIGN KEY (distribution_id) REFERENCES distributions (distribution_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
