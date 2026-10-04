ALTER TABLE prompt_versions
  ADD COLUMN updated_by_user_id BIGINT NULL AFTER created_by_user_id,
  ADD COLUMN updated_at DATETIME NULL AFTER created_at,
  ADD COLUMN row_version BIGINT NOT NULL DEFAULT 1 AFTER updated_at,
  ADD CONSTRAINT fk_prompt_versions_updated_by_user
    FOREIGN KEY (updated_by_user_id) REFERENCES users (user_id);

UPDATE tasks t
JOIN rubrics r
  ON r.title = '生徒共通標準ルーブリック'
 AND r.version = '0805-2026-v1'
 AND r.rubric_status = 'active'
SET t.rubric_id = r.rubric_id
WHERE t.rubric_id IS NULL
  AND t.publication_status = 'draft'
  AND t.deleted_at IS NULL;
