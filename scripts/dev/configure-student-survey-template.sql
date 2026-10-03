-- Apply the student survey items from the student screen-flow prototype to a
-- new, task-specific draft survey with no questions or responses.
-- Set @student_survey_id to the target surveys.survey_id before running.
SET @student_survey_id = NULL;

DROP PROCEDURE IF EXISTS configure_student_survey_template;
DELIMITER //
CREATE PROCEDURE configure_student_survey_template(IN p_survey_id BIGINT)
BEGIN
  DECLARE v_survey_count INT DEFAULT 0;
  DECLARE v_survey_status VARCHAR(16);
  DECLARE v_task_id BIGINT;

  DECLARE EXIT HANDLER FOR SQLEXCEPTION
  BEGIN
    ROLLBACK;
    RESIGNAL;
  END;

  START TRANSACTION;

  SELECT COUNT(*), MAX(survey_status), MAX(task_id)
    INTO v_survey_count, v_survey_status, v_task_id
  FROM surveys
  WHERE survey_id = p_survey_id;

  IF p_survey_id IS NULL OR v_survey_count <> 1 OR v_task_id IS NULL THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT = 'Target must be an existing task-specific survey.';
  END IF;

  IF v_survey_status <> 'draft' THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT = 'Target survey must be in draft status.';
  END IF;

  IF EXISTS (SELECT 1 FROM survey_questions WHERE survey_id = p_survey_id)
     OR EXISTS (SELECT 1 FROM survey_responses WHERE survey_id = p_survey_id) THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT = 'Target survey must not already have questions or responses.';
  END IF;

  INSERT INTO survey_questions (
    survey_id, question_code, question_type, prompt_text, reason_prompt_text,
    required, reason_required, sort_order, question_status
  ) VALUES
    (p_survey_id, 'thinking_validity', 'rating',
     '思考力・判断力・表現力について、システムの評価は妥当でしたか？',
     'その理由', TRUE, TRUE, 1, 'active'),
    (p_survey_id, 'thinking_self_rating', 'rating',
     '思考力・判断力・表現力について、自分の取り組みを5段階で自己評価してください。',
     'その理由', TRUE, TRUE, 2, 'active'),
    (p_survey_id, 'attitude_validity', 'rating',
     '主体的に取り組む態度について、システムの評価は妥当でしたか？',
     'その理由', TRUE, TRUE, 3, 'active'),
    (p_survey_id, 'attitude_self_rating', 'rating',
     '主体的に取り組む態度について、自分の取り組みを5段階で自己評価してください。',
     'その理由', TRUE, TRUE, 4, 'active'),
    (p_survey_id, 'process_resistance', 'rating',
     'AIによって試行錯誤の過程が評価されることに抵抗を感じますか？',
     'その理由', TRUE, TRUE, 5, 'active'),
    (p_survey_id, 'system_usability', 'rating',
     'エディター、評価画面、画面遷移などを含めて、システム全体の操作性を評価してください。',
     '操作性に関するコメント', TRUE, FALSE, 6, 'active');

  INSERT INTO survey_question_options (
    question_id, option_code, label, value, sort_order, option_status
  )
  SELECT q.question_id, o.option_code, o.label, o.option_value, o.sort_order, 'active'
  FROM survey_questions q
  JOIN (
    SELECT 'thinking_validity' AS question_code, '1' AS option_code,
           '妥当でない' AS label, '1' AS option_value, 1 AS sort_order
    UNION ALL SELECT 'thinking_validity', '2', 'あまり妥当でない', '2', 2
    UNION ALL SELECT 'thinking_validity', '3', 'どちらともいえない', '3', 3
    UNION ALL SELECT 'thinking_validity', '4', 'おおむね妥当', '4', 4
    UNION ALL SELECT 'thinking_validity', '5', '妥当', '5', 5
    UNION ALL SELECT 'thinking_self_rating', '1', '1', '1', 1
    UNION ALL SELECT 'thinking_self_rating', '2', '2', '2', 2
    UNION ALL SELECT 'thinking_self_rating', '3', '3', '3', 3
    UNION ALL SELECT 'thinking_self_rating', '4', '4', '4', 4
    UNION ALL SELECT 'thinking_self_rating', '5', '5', '5', 5
    UNION ALL SELECT 'attitude_validity', '1', '妥当でない', '1', 1
    UNION ALL SELECT 'attitude_validity', '2', 'あまり妥当でない', '2', 2
    UNION ALL SELECT 'attitude_validity', '3', 'どちらともいえない', '3', 3
    UNION ALL SELECT 'attitude_validity', '4', 'おおむね妥当', '4', 4
    UNION ALL SELECT 'attitude_validity', '5', '妥当', '5', 5
    UNION ALL SELECT 'attitude_self_rating', '1', '1', '1', 1
    UNION ALL SELECT 'attitude_self_rating', '2', '2', '2', 2
    UNION ALL SELECT 'attitude_self_rating', '3', '3', '3', 3
    UNION ALL SELECT 'attitude_self_rating', '4', '4', '4', 4
    UNION ALL SELECT 'attitude_self_rating', '5', '5', '5', 5
    UNION ALL SELECT 'process_resistance', '1', 'とても感じる', '1', 1
    UNION ALL SELECT 'process_resistance', '2', 'やや感じる', '2', 2
    UNION ALL SELECT 'process_resistance', '3', 'どちらともいえない', '3', 3
    UNION ALL SELECT 'process_resistance', '4', 'あまり感じない', '4', 4
    UNION ALL SELECT 'process_resistance', '5', '全く感じない', '5', 5
    UNION ALL SELECT 'system_usability', '1', 'とても使いにくい', '1', 1
    UNION ALL SELECT 'system_usability', '2', 'やや使いにくい', '2', 2
    UNION ALL SELECT 'system_usability', '3', 'どちらともいえない', '3', 3
    UNION ALL SELECT 'system_usability', '4', 'やや使いやすい', '4', 4
    UNION ALL SELECT 'system_usability', '5', 'とても使いやすい', '5', 5
  ) o ON o.question_code = q.question_code
  WHERE q.survey_id = p_survey_id;

  COMMIT;
END//
DELIMITER ;

CALL configure_student_survey_template(@student_survey_id);
DROP PROCEDURE configure_student_survey_template;
