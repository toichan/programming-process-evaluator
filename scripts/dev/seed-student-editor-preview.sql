-- Local-only preview data for the test-student account. Do not use as production seed data.
START TRANSACTION;

SET @student_user_id = (
  SELECT user_id FROM users
  WHERE login_id = 'test-student'
    AND user_type = 'student'
    AND account_status = 'active'
    AND deleted_at IS NULL
  LIMIT 1
);

INSERT INTO schools (school_code, name, school_status, created_at)
SELECT 'local-editor-preview', '画面確認用', 'active', CURRENT_TIMESTAMP
WHERE @student_user_id IS NOT NULL
  AND NOT EXISTS (
    SELECT 1 FROM schools WHERE school_code = 'local-editor-preview'
  );
SET @school_id = (
  SELECT school_id FROM schools WHERE school_code = 'local-editor-preview' LIMIT 1
);

INSERT INTO classrooms (school_id, name, grade_name, classroom_status, created_at)
SELECT @school_id, 'エディター確認クラス', '動作確認', 'active', CURRENT_TIMESTAMP
WHERE @school_id IS NOT NULL
  AND NOT EXISTS (
    SELECT 1 FROM classrooms
    WHERE school_id = @school_id AND name = 'エディター確認クラス'
  );
SET @classroom_id = (
  SELECT classroom_id FROM classrooms
  WHERE school_id = @school_id AND name = 'エディター確認クラス'
  LIMIT 1
);

INSERT INTO student_class_memberships (student_user_id, classroom_id, membership_status, joined_at)
SELECT @student_user_id, @classroom_id, 'active', CURRENT_TIMESTAMP
WHERE @student_user_id IS NOT NULL
  AND @classroom_id IS NOT NULL
  AND NOT EXISTS (
    SELECT 1 FROM student_class_memberships
    WHERE student_user_id = @student_user_id AND classroom_id = @classroom_id
  );
UPDATE student_class_memberships
SET membership_status = 'active', left_at = NULL
WHERE student_user_id = @student_user_id
  AND classroom_id = @classroom_id
  AND membership_status = 'inactive';

INSERT INTO tasks (
  task_code, task_revision_code, revision_number, created_by_user_id, title, theme, difficulty,
  language, description, input_constraints, creation_rules, initial_code,
  save_status, publication_status, created_at, published_at
)
SELECT
  'local-editor-preview', 'local-editor-preview-v1', 1, @student_user_id,
  'じゃんけん判定プログラム', '条件分岐', 'beginner', 'python',
  '入力された手に応じて、勝敗または入力エラーを表示してください。',
  '入力は「グー」「チョキ」「パー」のいずれかです。',
  '標準入力から値を受け取り、条件分岐を使って結果を表示してください。',
  'player = input()\n\nif player == "グー":\n    print("あいこ")\nelif player == "パー":\n    print("あなたの勝ち")\nelif player == "チョキ":\n    print("あなたの負け")\nelse:\n    print("グー・チョキ・パーを入力してください")',
  'saved', 'published', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE @student_user_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM tasks WHERE task_code = 'local-editor-preview');
SET @task_id = (
  SELECT task_id FROM tasks WHERE task_code = 'local-editor-preview' AND revision_number = 1 LIMIT 1
);

INSERT INTO task_features (task_id, feature_text, sort_order, record_status)
SELECT @task_id, features.feature_text, features.sort_order, 'active'
FROM (
  SELECT '入力値を受け取る' AS feature_text, 1 AS sort_order
  UNION ALL SELECT '条件分岐で勝敗を判定する', 2
  UNION ALL SELECT '不正な入力時に案内を表示する', 3
) AS features
WHERE @task_id IS NOT NULL
  AND NOT EXISTS (
    SELECT 1 FROM task_features
    WHERE task_id = @task_id AND feature_text = features.feature_text
  );

INSERT INTO task_test_cases (
  task_id, title, test_case_input, test_case_output, test_case_order, record_status
)
SELECT @task_id, cases.title, cases.test_case_input, cases.test_case_output, cases.test_case_order, 'active'
FROM (
  SELECT 'パーの場合' AS title, 'パー\n' AS test_case_input, 'あなたの勝ち\n' AS test_case_output, 1 AS test_case_order
  UNION ALL SELECT 'グーの場合', 'グー\n', 'あいこ\n', 2
  UNION ALL SELECT '不正な入力の場合', 'ぐー\n', 'グー・チョキ・パーを入力してください\n', 3
) AS cases
WHERE @task_id IS NOT NULL
  AND NOT EXISTS (
    SELECT 1 FROM task_test_cases
    WHERE task_id = @task_id AND title = cases.title
  );

INSERT INTO task_hints (
  task_id, hint_order, hint_title, hint_content, usage_syntax, hint_code,
  created_at, record_status
)
SELECT
  @task_id, 1, 'input() で入力を受け取る',
  'input() は標準入力から1行の文字列を受け取ります。',
  '変数名 = input()',
  'player = input()',
  CURRENT_TIMESTAMP, 'active'
WHERE @task_id IS NOT NULL
  AND NOT EXISTS (
    SELECT 1 FROM task_hints WHERE task_id = @task_id AND hint_title = 'input() で入力を受け取る'
  );

INSERT INTO task_class_assignments (
  task_id, classroom_id, assignment_status, publish_at, due_at,
  late_submission_policy, resubmission_policy, created_at
)
SELECT
  @task_id, @classroom_id, 'published', CURRENT_TIMESTAMP,
  DATE_ADD(CURRENT_TIMESTAMP, INTERVAL 30 DAY), 'deny', 'allow', CURRENT_TIMESTAMP
WHERE @task_id IS NOT NULL
  AND @classroom_id IS NOT NULL
  AND NOT EXISTS (
    SELECT 1 FROM task_class_assignments
    WHERE task_id = @task_id AND classroom_id = @classroom_id
  );

COMMIT;
