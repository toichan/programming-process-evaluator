ALTER TABLE `users`
  ADD COLUMN `admin_account_guard` TINYINT
    GENERATED ALWAYS AS (CASE WHEN `user_type` = 'admin' THEN 1 ELSE NULL END) STORED,
  ADD UNIQUE KEY `uq_users_single_admin` (`admin_account_guard`);

ALTER TABLE `student_profiles`
  ADD CONSTRAINT `chk_student_security_level` CHECK (`security_level` IN (1, 2));
