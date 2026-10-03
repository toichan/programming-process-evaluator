DELIMITER //
CREATE PROCEDURE validate_school_policy_migration()
BEGIN
  IF EXISTS (
    SELECT c.school_id FROM student_class_memberships m
    JOIN classrooms c ON c.classroom_id = m.classroom_id
    JOIN student_profiles p ON p.user_id = m.student_user_id
    GROUP BY c.school_id HAVING COUNT(DISTINCT p.security_level) > 1
  ) OR EXISTS (
    SELECT m.student_user_id FROM student_class_memberships m
    JOIN classrooms c ON c.classroom_id = m.classroom_id
    GROUP BY m.student_user_id HAVING COUNT(DISTINCT c.school_id) > 1
  ) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'School policy migration requires consistent school and security level histories';
  END IF;
END//
DELIMITER ;
CALL validate_school_policy_migration();
DROP PROCEDURE validate_school_policy_migration;

ALTER TABLE schools
  ADD COLUMN security_level TINYINT NULL,
  ADD COLUMN security_level_locked BOOLEAN NOT NULL DEFAULT FALSE,
  ADD COLUMN version BIGINT NOT NULL DEFAULT 1,
  ADD CONSTRAINT chk_school_security_level CHECK (security_level IN (1, 2));
ALTER TABLE student_profiles
  ADD COLUMN school_id BIGINT NULL,
  ADD CONSTRAINT fk_student_profile_school FOREIGN KEY (school_id) REFERENCES schools (school_id);

UPDATE schools s JOIN (
  SELECT c.school_id, MIN(p.security_level) AS security_level
  FROM student_class_memberships m
  JOIN classrooms c ON c.classroom_id = m.classroom_id
  JOIN student_profiles p ON p.user_id = m.student_user_id
  GROUP BY c.school_id
) existing ON existing.school_id = s.school_id
SET s.security_level = existing.security_level, s.security_level_locked = TRUE;
UPDATE student_profiles p JOIN (
  SELECT m.student_user_id, MIN(c.school_id) AS school_id
  FROM student_class_memberships m JOIN classrooms c ON c.classroom_id = m.classroom_id
  GROUP BY m.student_user_id
) existing ON existing.student_user_id = p.user_id
SET p.school_id = existing.school_id;

DELIMITER //
CREATE TRIGGER protect_school_security_policy BEFORE UPDATE ON schools FOR EACH ROW
BEGIN
  IF OLD.security_level_locked AND
    (NOT (NEW.security_level <=> OLD.security_level) OR NOT NEW.security_level_locked) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'School security level is locked after student registration';
  END IF;
END//

CREATE TRIGGER apply_student_school_policy BEFORE INSERT ON student_profiles FOR EACH ROW
BEGIN
  DECLARE school_level TINYINT;
  IF NEW.school_id IS NULL THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Student registration requires a school';
  ELSE
    SELECT security_level INTO school_level FROM schools
      WHERE school_id = NEW.school_id AND school_status = 'active' FOR UPDATE;
    IF school_level IS NULL THEN
      SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'School security level must be configured before student registration';
    END IF;
    SET NEW.security_level = school_level;
    SET NEW.must_change_password = (school_level = 2);
    SET NEW.first_login_status = IF(school_level = 2, 'password_change_required', 'not_logged_in');
    UPDATE schools SET security_level_locked = TRUE WHERE school_id = NEW.school_id;
  END IF;
END//

CREATE TRIGGER protect_student_school_policy BEFORE UPDATE ON student_profiles FOR EACH ROW
BEGIN
  DECLARE school_level TINYINT;
  IF OLD.school_id IS NOT NULL AND NOT (OLD.school_id <=> NEW.school_id) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Student school cannot be changed by a profile update';
  END IF;
  IF NEW.school_id IS NOT NULL THEN
    SELECT security_level INTO school_level FROM schools WHERE school_id = NEW.school_id FOR UPDATE;
    IF school_level IS NULL OR NEW.security_level <> school_level THEN
      SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Student security level must match school policy';
    END IF;
    UPDATE schools SET security_level_locked = TRUE WHERE school_id = NEW.school_id;
  END IF;
END//

CREATE TRIGGER bind_membership_school BEFORE INSERT ON student_class_memberships FOR EACH ROW
BEGIN
  DECLARE target_school BIGINT;
  DECLARE bound_school BIGINT;
  DECLARE profile_exists BIGINT;
  SELECT school_id INTO target_school FROM classrooms WHERE classroom_id = NEW.classroom_id;
  SELECT COUNT(*) INTO profile_exists FROM student_profiles WHERE user_id = NEW.student_user_id;
  IF profile_exists <> 1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Class membership requires a student profile';
  END IF;
  SELECT school_id INTO bound_school FROM student_profiles WHERE user_id = NEW.student_user_id FOR UPDATE;
  IF bound_school IS NOT NULL AND bound_school <> target_school THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Class membership must belong to student school';
  END IF;
  UPDATE student_profiles SET school_id = target_school WHERE user_id = NEW.student_user_id;
END//

CREATE TRIGGER protect_membership_school BEFORE UPDATE ON student_class_memberships FOR EACH ROW
BEGIN
  DECLARE target_school BIGINT;
  DECLARE bound_school BIGINT;
  SELECT school_id INTO target_school FROM classrooms WHERE classroom_id = NEW.classroom_id;
  SELECT school_id INTO bound_school FROM student_profiles WHERE user_id = NEW.student_user_id FOR UPDATE;
  IF bound_school IS NULL OR bound_school <> target_school THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Class membership must belong to student school';
  END IF;
END//
DELIMITER ;
