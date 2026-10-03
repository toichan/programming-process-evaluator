ALTER TABLE `users`
  ADD COLUMN `consecutive_login_failures` TINYINT UNSIGNED NOT NULL DEFAULT 0,
  ADD COLUMN `login_locked_until` DATETIME NULL,
  ADD CONSTRAINT `chk_users_consecutive_login_failures`
    CHECK (`consecutive_login_failures` BETWEEN 0 AND 5);
