INSERT INTO users (user_id, user_type, login_id, password_hash, display_name, account_status, created_at)
VALUES (1, 'student', 'debug-student', '@PASSWORD_HASH@', 'Synthetic Student', 'active', NOW()),
       (2, 'student', 'debug-other', '@PASSWORD_HASH@', 'Synthetic Other', 'active', NOW()),
       (3, 'admin', 'debug-admin', '@PASSWORD_HASH@', 'Synthetic Admin', 'active', NOW());
INSERT INTO schools (school_id, school_code, name, school_status, security_level, created_at)
VALUES (1, 'DEBUG-SCHOOL', 'Synthetic School', 'active', 1, NOW());
INSERT INTO student_profiles (user_id, student_code, security_level, first_login_status, must_change_password, school_id)
VALUES (1, 'DBG001', 1, 'completed', FALSE, 1), (2, 'DBG002', 1, 'completed', FALSE, 1);
INSERT INTO classrooms (classroom_id, school_id, name, classroom_status, created_at)
VALUES (1, 1, 'Synthetic Class', 'active', NOW());
INSERT INTO student_class_memberships (student_user_id, classroom_id, membership_status, joined_at)
VALUES (1, 1, 'active', NOW()), (2, 1, 'active', NOW());
INSERT INTO consent_document_versions (consent_document_version_id, version_code, title, body, document_status, created_at)
VALUES (9001, 'debug-v1', 'Synthetic consent', 'Synthetic consent body', 'active', NOW());
INSERT INTO consent_records (user_id, consent_status, consent_document_version_id, consented_at, updated_at)
VALUES (1, 'agreed', 9001, NOW(), NOW()), (2, 'agreed', 9001, NOW(), NOW());
INSERT INTO rubrics (rubric_id, created_by_user_id, title, version, rubric_status, created_at)
VALUES (1, 3, 'Synthetic Rubric', 'debug-v1', 'active', NOW());
INSERT INTO tasks (task_id, task_code, task_revision_code, revision_number, created_by_user_id, rubric_id,
 title, difficulty, language, description, save_status, publication_status, created_at)
VALUES (1, 'DBG-TASK', 'DBG-TASK-v1', 1, 3, 1, 'Synthetic Debug Task', 'beginner', 'python',
 'Synthetic task description', 'saved', 'published', NOW());
INSERT INTO task_class_assignments (task_class_assignment_id, task_id, classroom_id, assignment_status,
 late_submission_policy, resubmission_policy, created_at)
VALUES (1, 1, 1, 'published', 'deny', 'allow', NOW());
INSERT INTO task_participations (participation_id, student_user_id, task_class_assignment_id,
 learning_status, progress_status, save_status, evaluation_status, active_duration_seconds)
VALUES (1, 1, 1, 'completed', 'submitted', 'saved', 'completed', 0),
       (2, 2, 1, 'completed', 'submitted', 'saved', 'completed', 0);
INSERT INTO submissions (submission_id, participation_id, revision_number, submitted_code, submission_status, submitted_at, created_at)
VALUES (1, 1, 1, 'print(1)', 'submitted', NOW(), NOW()),
       (2, 1, 2, 'print(2)', 'submitted', NOW(), NOW()),
       (3, 1, 3, 'print(3)', 'submitted', NOW(), NOW()),
       (4, 2, 1, 'print(4)', 'submitted', NOW(), NOW());
INSERT INTO evaluations (evaluation_id, evaluation_code, submission_id, rubric_id, evaluation_status,
 evaluation_kind, auto_save_count, execution_count, feedback_summary, created_at, completed_at)
VALUES (1, 'DBG-EVAL-1', 1, 1, 'completed', 'initial', 0, 0, 'Synthetic feedback summary', NOW(), NOW()),
       (2, 'DBG-EVAL-2', 2, 1, 'completed', 'initial', 0, 0, 'Synthetic feedback two', NOW(), NOW()),
       (3, 'DBG-EVAL-3', 3, 1, 'completed', 'initial', 0, 0, 'Synthetic feedback three', NOW(), NOW()),
       (4, 'DBG-EVAL-4', 4, 1, 'completed', 'initial', 0, 0, 'Other student feedback', NOW(), NOW());
INSERT INTO surveys (survey_id, task_id, title, survey_status, created_at)
VALUES (1, 1, 'Synthetic Survey', 'active', NOW());
INSERT INTO survey_questions (question_id, survey_id, question_code, question_type, prompt_text,
 required, sort_order, question_status)
VALUES (1, 1, 'DBG-Q1', 'text', 'Synthetic response?', TRUE, 1, 'active');
INSERT INTO code_logs (participation_id, submission_id, event_type, snapshot_text, observed_at, created_at)
VALUES (1, 2, 'manual_save', 'print(2)', NOW(), NOW()),
       (1, 3, 'manual_save', 'print(1)', NOW(), NOW()),
       (1, 3, 'manual_save', 'print(2)', NOW(), NOW()),
       (1, 3, 'submission', 'print(3)', NOW(), NOW());
INSERT INTO code_executions (execution_id, participation_id, submission_id, actor_user_id, execution_context,
 source_code, standard_input, execution_status, exit_code, standard_output, standard_output_truncated,
 standard_error, standard_error_truncated, duration_milliseconds, executed_at)
VALUES (9001, 1, 2, 1, 'student_task', 'print(2)', 'Synthetic stdin', 'succeeded', 0,
 'Synthetic stdout', FALSE, 'Synthetic stderr', FALSE, 123, NOW());
UPDATE code_logs SET code_execution_id = 9001 WHERE submission_id = 2;
