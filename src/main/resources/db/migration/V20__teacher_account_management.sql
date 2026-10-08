ALTER TABLE users ADD COLUMN account_version BIGINT NOT NULL DEFAULT 1;

INSERT INTO teacher_feature_permissions
  (teacher_user_id, feature_code, is_enabled, updated_by_user_id, updated_at)
SELECT teacher_user_id, 'teacher-prompt-design', is_enabled, updated_by_user_id, CURRENT_TIMESTAMP
FROM teacher_feature_permissions source
WHERE source.feature_code = 'task-management'
  AND NOT EXISTS (
    SELECT 1 FROM teacher_feature_permissions target
    WHERE target.teacher_user_id = source.teacher_user_id
      AND target.feature_code = 'teacher-prompt-design'
  );
