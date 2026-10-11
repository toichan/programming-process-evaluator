package control.dev;

import static control.dev.ReviewDemoBootstrap.*;

import java.sql.SQLException;
import lib.mysql.Client;

public final class StudentReviewDemoBootstrap {
	private static final String TASK_CODE = "LOCAL-S008-REVIEW-20261011";
	private static final String EVALUATION_CODE = "LOCAL-S008-REVIEW-EVALUATION";

	private StudentReviewDemoBootstrap() {}

	public static void main(String[] args) throws SQLException {
		requireLocalTarget(System.getenv());
		try (var c = Client.createConnection()) {
			if (!"programming_process_evaluator".equals(c.getCatalog()))
				throw new IllegalStateException("Unexpected student review demo catalog.");
			c.setAutoCommit(false);
			try {
				long student = number(c, """
						SELECT u.user_id FROM users u JOIN student_profiles p ON p.user_id=u.user_id
						WHERE u.login_id='s008' AND u.user_type='student' AND u.account_status='active'
						FOR UPDATE
						""");
				long classroom = number(c, """
						SELECT cl.classroom_id FROM student_class_memberships m
						JOIN classrooms cl ON cl.classroom_id=m.classroom_id
						JOIN schools sc ON sc.school_id=cl.school_id
						JOIN student_profiles p ON p.user_id=m.student_user_id AND p.school_id=cl.school_id
						WHERE m.student_user_id=? AND m.membership_status='active'
						  AND cl.classroom_status='active' AND sc.school_status='active'
						ORDER BY cl.classroom_id LIMIT 1
						""", student);
				long school = number(c, "SELECT school_id FROM classrooms WHERE classroom_id=?", classroom);
				long template = number(c, """
						SELECT task_id FROM tasks WHERE task_code='PROGRESS-DEMO-20261008'
						  AND school_id=? AND title LIKE '【デモ・仮データ】%' AND deleted_at IS NULL
						""", school);
				long task = optionalNumber(c, "SELECT task_id FROM tasks WHERE task_code=? AND deleted_at IS NULL", TASK_CODE);
				if (task == 0) {
					task = insert(c, """
							INSERT INTO tasks(school_id,task_code,task_revision_code,revision_number,created_by_user_id,
							  rubric_id,rubric_version,title,theme,difficulty,language,description,input_constraints,
							  creation_rules,initial_code,save_status,publication_status,created_at,published_at)
							SELECT school_id,?,?,1,created_by_user_id,rubric_id,rubric_version,
							  '【デモ・仮データ】s008 評価・編集ログ確認','合成データによる画面確認','beginner',
							  'python','整数2つの合計を表示してください。確認用合成課題です。',
							  '空白区切りの整数2つ','標準入力と標準出力を使用してください。','',
							  'saved','published',NOW(),NOW() FROM tasks WHERE task_id=?
							""", TASK_CODE, TASK_CODE + "-R1", template);
				}
				number(c, """
						SELECT task_id FROM tasks WHERE task_id=? AND school_id=?
						  AND title='【デモ・仮データ】s008 評価・編集ログ確認' AND publication_status='published'
						""", task, school);
				long assignment = optionalNumber(c, "SELECT task_class_assignment_id FROM task_class_assignments WHERE task_id=? AND classroom_id=?", task, classroom);
				if (assignment == 0) {
					assignment = insert(c, """
							INSERT INTO task_class_assignments(task_id,classroom_id,assignment_status,publish_at,due_at,
							  late_submission_policy,resubmission_policy,created_at)
							VALUES (?,?,'published',DATE_SUB(NOW(),INTERVAL 1 DAY),DATE_ADD(NOW(),INTERVAL 30 DAY),'allow','deny',NOW())
							""", task, classroom);
				}
				long participation = optionalNumber(c, "SELECT participation_id FROM task_participations WHERE student_user_id=? AND task_class_assignment_id=?", student, assignment);
				if (participation == 0) {
					participation = insert(c, """
							INSERT INTO task_participations(student_user_id,task_class_assignment_id,learning_status,
							  progress_status,save_status,evaluation_status,active_duration_seconds,last_activity_at,completed_at)
							VALUES (?,?,'completed','completed','saved','completed',180,NOW(),NOW())
							""", student, assignment);
				}
				if (optionalNumber(c, "SELECT evaluation_id FROM evaluations WHERE evaluation_code=?", EVALUATION_CODE) == 0) {
					long rubric = number(c, "SELECT rubric_id FROM tasks WHERE task_id=?", task);
					addEvaluation(c, participation, rubric, EVALUATION_CODE, 4);
				} else {
					number(c, """
							SELECT e.evaluation_id FROM evaluations e JOIN submissions s ON s.submission_id=e.submission_id
							WHERE e.evaluation_code=? AND s.participation_id=? AND e.evaluation_status='completed'
							""", EVALUATION_CODE, participation);
				}
				long survey = optionalNumber(c, """
						SELECT survey_id FROM surveys WHERE task_id=?
						  AND title='【デモ・仮データ】s008 評価確認アンケート'
						""", task);
				if (survey == 0) {
					survey = insert(c, """
							INSERT INTO surveys(task_id,title,survey_status,created_at)
							VALUES (?,'【デモ・仮データ】s008 評価確認アンケート','active',NOW())
							""", task);
					addSurveyQuestions(c, survey);
				}
				c.commit();
				System.out.println("Local s008 synthetic task, evaluation, edit logs and survey ready; account, consent and answers unchanged.");
			} catch (SQLException | RuntimeException failure) {
				c.rollback();
				throw failure;
			}
		}
	}
}
