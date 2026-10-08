CREATE TABLE student_login_sequence (
  singleton_id TINYINT NOT NULL PRIMARY KEY,
  next_number BIGINT NOT NULL,
  CONSTRAINT chk_student_sequence_singleton CHECK (singleton_id = 1),
  CONSTRAINT chk_student_sequence_positive CHECK (next_number >= 1)
) ENGINE=InnoDB;

INSERT INTO student_login_sequence (singleton_id, next_number)
SELECT 1, COALESCE(MAX(CAST(SUBSTRING(login_id, 2) AS UNSIGNED)), 0) + 1
FROM users WHERE login_id REGEXP '^s[0-9]{1,15}$';

CREATE TABLE student_teacher_credentials (
  student_user_id BIGINT NOT NULL PRIMARY KEY,
  encrypted_password VARCHAR(256) NOT NULL,
  updated_at DATETIME NOT NULL,
  CONSTRAINT fk_student_teacher_credential_user FOREIGN KEY (student_user_id) REFERENCES users (user_id)
) ENGINE=InnoDB;

DELIMITER //
CREATE TRIGGER version_student_password BEFORE UPDATE ON users FOR EACH ROW
BEGIN
  IF NEW.user_type = 'student' AND NOT (OLD.password_hash <=> NEW.password_hash)
     AND NEW.account_version = OLD.account_version THEN
    SET NEW.account_version = OLD.account_version + 1;
  END IF;
END//
CREATE TRIGGER clear_changed_student_credential AFTER UPDATE ON users FOR EACH ROW
BEGIN
  IF NEW.user_type = 'student' AND
    (NOT (OLD.password_hash <=> NEW.password_hash) OR NEW.account_status = 'deleted') THEN
    DELETE FROM student_teacher_credentials WHERE student_user_id = NEW.user_id;
  END IF;
END//
CREATE TRIGGER clear_completed_student_credential AFTER UPDATE ON student_profiles FOR EACH ROW
BEGIN
  IF NEW.security_level = 2 AND NEW.first_login_status = 'completed' AND NOT NEW.must_change_password THEN
    DELETE FROM student_teacher_credentials WHERE student_user_id = NEW.user_id;
  END IF;
END//
DELIMITER ;
