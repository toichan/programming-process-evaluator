DROP PROCEDURE IF EXISTS `assert_tasks_empty_before_v19`;

DELIMITER $$
CREATE PROCEDURE `assert_tasks_empty_before_v19`()
BEGIN
  IF EXISTS (SELECT 1 FROM `tasks` LIMIT 1) THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT = 'V19 requires existing demo tasks to be reset or explicitly mapped to one school first.';
  END IF;
END$$
DELIMITER ;

CALL `assert_tasks_empty_before_v19`();
DROP PROCEDURE `assert_tasks_empty_before_v19`;

ALTER TABLE `tasks`
  ADD COLUMN `school_id` BIGINT NOT NULL AFTER `task_id`,
  ADD CONSTRAINT `fk_tasks_school`
    FOREIGN KEY (`school_id`) REFERENCES `schools` (`school_id`);
