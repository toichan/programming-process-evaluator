CREATE TABLE school_common_prompt_versions (
  common_prompt_version_id BIGINT NOT NULL AUTO_INCREMENT,
  school_id BIGINT NOT NULL,
  version_number INT NOT NULL,
  common_prompt LONGTEXT NOT NULL,
  created_by_user_id BIGINT NOT NULL,
  created_at DATETIME NOT NULL,
  PRIMARY KEY (common_prompt_version_id),
  UNIQUE KEY uq_school_common_prompt_versions_school_version (school_id, version_number),
  CONSTRAINT fk_school_common_prompt_versions_school
    FOREIGN KEY (school_id) REFERENCES schools (school_id),
  CONSTRAINT fk_school_common_prompt_versions_created_by
    FOREIGN KEY (created_by_user_id) REFERENCES users (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

ALTER TABLE prompt_versions
  ADD COLUMN common_prompt_version_id BIGINT NULL AFTER common_prompt,
  ADD CONSTRAINT fk_prompt_versions_common_prompt_version
    FOREIGN KEY (common_prompt_version_id)
    REFERENCES school_common_prompt_versions (common_prompt_version_id);

INSERT INTO school_common_prompt_versions (
  school_id, version_number, common_prompt, created_by_user_id, created_at
)
SELECT latest.school_id, 1, latest.common_prompt, latest.created_by_user_id,
       COALESCE(latest.updated_at, latest.created_at)
FROM (
  SELECT t.school_id, pv.common_prompt, pv.created_by_user_id, pv.created_at, pv.updated_at,
         ROW_NUMBER() OVER (
           PARTITION BY t.school_id
           ORDER BY COALESCE(pv.updated_at, pv.created_at) DESC, pv.prompt_version_id DESC
         ) AS school_order
  FROM prompt_versions pv
  JOIN tasks t ON t.task_id = pv.task_id
) latest
WHERE latest.school_order = 1;

UPDATE prompt_versions pv
JOIN tasks t ON t.task_id = pv.task_id
JOIN school_common_prompt_versions shared
  ON shared.school_id = t.school_id
 AND shared.version_number = 1
 AND shared.common_prompt = pv.common_prompt
SET pv.common_prompt_version_id = shared.common_prompt_version_id;
