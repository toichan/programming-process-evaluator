ALTER TABLE users ADD COLUMN teacher_must_change_password BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE users
SET teacher_must_change_password = TRUE, account_version = account_version + 1
WHERE user_type = 'teacher';
