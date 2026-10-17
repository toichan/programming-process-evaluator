-- Apply the approved local survey definition to a new, task-specific draft
-- survey with no questions or responses.
-- Set @student_survey_id to the target surveys.survey_id before running.
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
    (p_survey_id, 'q1ThinkingValidity', 'rating',
     '思考力等の評価は妥当でしたか', '理由・補足', TRUE, FALSE, 1, 'active'),
    (p_survey_id, 'q1ThinkingScore', 'rating',
     '自分の思考力等の得点', '理由・補足', TRUE, FALSE, 2, 'active'),
    (p_survey_id, 'q2AttitudeValidity', 'rating',
     '学習態度の評価は妥当でしたか', '理由・補足', TRUE, FALSE, 3, 'active'),
    (p_survey_id, 'q2AttitudeScore', 'rating',
     '自分の学習態度の得点', '理由・補足', TRUE, FALSE, 4, 'active'),
    (p_survey_id, 'q3ProcessResistanceScore', 'rating',
     '取り組み過程の記録への抵抗感', '理由・補足', TRUE, FALSE, 5, 'active'),
    (p_survey_id, 'q4UsabilityScore', 'rating',
     'システムの使いやすさ', '理由・補足', TRUE, FALSE, 6, 'active');

  INSERT INTO survey_question_options (
    question_id, option_code, label, value, sort_order, option_status
  )
  SELECT q.question_id, o.option_code, o.label, o.option_value, o.sort_order, 'active'
  FROM survey_questions q
  JOIN (
    SELECT 'q1ThinkingValidity' AS question_code, 'score-1' AS option_code, '1' AS label, '1' AS option_value, 1 AS sort_order
    UNION ALL SELECT 'q1ThinkingValidity', 'score-2', '2', '2', 2
    UNION ALL SELECT 'q1ThinkingValidity', 'score-3', '3', '3', 3
    UNION ALL SELECT 'q1ThinkingValidity', 'score-4', '4', '4', 4
    UNION ALL SELECT 'q1ThinkingValidity', 'score-5', '5', '5', 5
    UNION ALL SELECT 'q1ThinkingScore', 'score-1', '1', '1', 1
    UNION ALL SELECT 'q1ThinkingScore', 'score-2', '2', '2', 2
    UNION ALL SELECT 'q1ThinkingScore', 'score-3', '3', '3', 3
    UNION ALL SELECT 'q1ThinkingScore', 'score-4', '4', '4', 4
    UNION ALL SELECT 'q1ThinkingScore', 'score-5', '5', '5', 5
    UNION ALL SELECT 'q2AttitudeValidity', 'score-1', '1', '1', 1
    UNION ALL SELECT 'q2AttitudeValidity', 'score-2', '2', '2', 2
    UNION ALL SELECT 'q2AttitudeValidity', 'score-3', '3', '3', 3
    UNION ALL SELECT 'q2AttitudeValidity', 'score-4', '4', '4', 4
    UNION ALL SELECT 'q2AttitudeValidity', 'score-5', '5', '5', 5
    UNION ALL SELECT 'q2AttitudeScore', 'score-1', '1', '1', 1
    UNION ALL SELECT 'q2AttitudeScore', 'score-2', '2', '2', 2
    UNION ALL SELECT 'q2AttitudeScore', 'score-3', '3', '3', 3
    UNION ALL SELECT 'q2AttitudeScore', 'score-4', '4', '4', 4
    UNION ALL SELECT 'q2AttitudeScore', 'score-5', '5', '5', 5
    UNION ALL SELECT 'q3ProcessResistanceScore', 'score-1', '1', '1', 1
    UNION ALL SELECT 'q3ProcessResistanceScore', 'score-2', '2', '2', 2
    UNION ALL SELECT 'q3ProcessResistanceScore', 'score-3', '3', '3', 3
    UNION ALL SELECT 'q3ProcessResistanceScore', 'score-4', '4', '4', 4
    UNION ALL SELECT 'q3ProcessResistanceScore', 'score-5', '5', '5', 5
    UNION ALL SELECT 'q4UsabilityScore', 'score-1', '1', '1', 1
    UNION ALL SELECT 'q4UsabilityScore', 'score-2', '2', '2', 2
    UNION ALL SELECT 'q4UsabilityScore', 'score-3', '3', '3', 3
    UNION ALL SELECT 'q4UsabilityScore', 'score-4', '4', '4', 4
    UNION ALL SELECT 'q4UsabilityScore', 'score-5', '5', '5', 5
  ) o ON o.question_code = q.question_code
  WHERE q.survey_id = p_survey_id;

  COMMIT;
END//
DELIMITER ;

CALL configure_student_survey_template(@student_survey_id);
DROP PROCEDURE configure_student_survey_template;
