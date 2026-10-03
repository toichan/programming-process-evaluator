ALTER TABLE `survey_questions`
  ADD COLUMN `reason_prompt_text` VARCHAR(255) NOT NULL DEFAULT '理由・補足'
    AFTER `prompt_text`,
  ADD COLUMN `reason_required` BOOLEAN NOT NULL DEFAULT FALSE
    AFTER `required`;
