ALTER TABLE `tasks`
  ADD COLUMN `version` BIGINT NOT NULL DEFAULT 1,
  ADD CONSTRAINT `chk_tasks_version` CHECK (`version` >= 1);
