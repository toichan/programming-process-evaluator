ALTER TABLE `survey_responses`
  ADD UNIQUE KEY `uq_survey_responses_student_survey_evaluation`
    (`student_user_id`, `survey_id`, `evaluation_id`);

ALTER TABLE `survey_answers`
  ADD UNIQUE KEY `uq_survey_answers_response_question`
    (`survey_response_id`, `question_id`);
