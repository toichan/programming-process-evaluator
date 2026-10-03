ALTER TABLE `task_participations`
  MODIFY COLUMN `draft_updated_at` DATETIME(6) NULL;

ALTER TABLE `submissions`
  ADD COLUMN `submission_request_key` CHAR(36) NULL AFTER `submission_status`,
  ADD UNIQUE KEY `uq_submissions_request_key` (`participation_id`, `submission_request_key`);
