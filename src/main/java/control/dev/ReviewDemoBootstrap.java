package control.dev;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;

import entity.TeacherSurveyResponse;
import lib.mysql.Client;

/** Adds synthetic review results only to the existing local progress demo. */
public final class ReviewDemoBootstrap {
	private static final String TASK_CODE = "PROGRESS-DEMO-20261008";
	private ReviewDemoBootstrap() {}

	public static void requireLocalTarget(Map<String, String> environment) {
		if (!"development".equals(environment.get("PPE_ENV"))
				|| !"db".equals(environment.get("DB_HOST"))
				|| !"programming_process_evaluator".equals(environment.get("DB_NAME"))
				|| !"true".equals(environment.get("PPE_SEED_REVIEW_DEMO"))) {
			throw new IllegalStateException("Review demo requires explicit opt-in on the local development Compose database.");
		}
	}

	public static void main(String[] args) throws SQLException {
		requireLocalTarget(System.getenv());
		try (var c = Client.createConnection()) {
			if (!"programming_process_evaluator".equals(c.getCatalog())) {
				throw new IllegalStateException("Unexpected review demo database catalog.");
			}
			c.setAutoCommit(false);
			try {
				long task = number(c, "SELECT task_id FROM tasks WHERE task_code=? AND title LIKE '【デモ・仮データ】%' AND deleted_at IS NULL", TASK_CODE);
				long rubric = number(c, "SELECT rubric_id FROM tasks WHERE task_id=?", task);
				long survey = optionalNumber(c, "SELECT survey_id FROM surveys WHERE task_id=? AND title='【デモ・仮データ】評価確認アンケート'", task);
				if (survey == 0) {
					survey = insert(c, "INSERT INTO surveys(task_id,title,survey_status,created_at) VALUES (?,'【デモ・仮データ】評価確認アンケート','closed',NOW())", task);
					addSurveyQuestions(c, survey);
				}
				for (int index : new int[] {4, 5}) {
					String login = "demo-progress-20261008-" + index;
					long student = number(c, "SELECT user_id FROM users WHERE login_id=? AND user_type='student' AND account_status='active' AND login_locked_until='9999-12-31 23:59:59'", login);
					number(c, "SELECT consent_id FROM consent_records WHERE user_id=? AND consent_status='agreed' AND consent_id=(SELECT MAX(consent_id) FROM consent_records WHERE user_id=?)", student, student);
					long participation = number(c, """
							SELECT p.participation_id FROM task_participations p JOIN task_class_assignments a
							ON a.task_class_assignment_id=p.task_class_assignment_id WHERE p.student_user_id=? AND a.task_id=?
							""", student, task);
					String code = "LOCAL-REVIEW-DEMO-" + index;
					if (optionalNumber(c, "SELECT evaluation_id FROM evaluations WHERE evaluation_code=?", code) != 0) continue;
					long evaluation = addEvaluation(c, participation, rubric, code, index == 5 ? 4 : 3);
					int score = index == 5 ? 4 : 3;
					long response = insert(c, """
							INSERT INTO survey_responses(response_code,record_code,student_user_id,survey_id,task_id,evaluation_id,
							  consent_status_at_submission,response_status,started_at,submitted_at)
							VALUES (?,?,?,?,?,?,'agreed','submitted',DATE_SUB(NOW(),INTERVAL 1 MINUTE),NOW())
							""", code + "-SURVEY", code + "-RECORD", student, survey, task, evaluation);
					execute(c, """
							INSERT INTO survey_answers(survey_response_id,question_id,answer_value,answer_reason)
							SELECT ?,question_id,?,'【デモ・仮データ】修正内容と評価理由を見比べられたので理解しやすかったです。'
							FROM survey_questions WHERE survey_id=?
							""", response, String.valueOf(score), survey);
				}
				c.commit();
				System.out.println("Local synthetic evaluation and survey demo ready; existing accounts and schema unchanged.");
			} catch (SQLException | RuntimeException failure) {
				c.rollback();
				throw failure;
			}
		}
	}

	static void addSurveyQuestions(Connection c, long survey) throws SQLException {
		String[] prompts = {"思考力等の評価は妥当でしたか", "自分の思考力等の得点", "学習態度の評価は妥当でしたか",
				"自分の学習態度の得点", "取り組み過程の記録への抵抗感", "システムの使いやすさ"};
		for (int i = 0; i < TeacherSurveyResponse.METRICS.size(); i++) {
			long question = insert(c, """
					INSERT INTO survey_questions(survey_id,question_code,question_type,prompt_text,required,sort_order,question_status)
					VALUES (?,?,'rating',?,TRUE,?,'active')
					""", survey, TeacherSurveyResponse.METRICS.get(i), prompts[i], i + 1);
			for (int value = 1; value <= 5; value++) {
				insert(c, """
						INSERT INTO survey_question_options(question_id,option_code,label,value,sort_order,option_status)
						VALUES (?,?,?,?,?,'active')
						""", question, "score-" + value, String.valueOf(value), String.valueOf(value), value);
			}
		}
	}

	static long addEvaluation(Connection c, long participation, long rubric, String code, int score) throws SQLException {
		String initial = "a, b = map(int, input().split())\nprint(a - b)\n";
		String source = "a, b = map(int, input().split())\nprint(a + b)\n";
		int revision = (int) number(c, "SELECT COALESCE(MAX(revision_number),0)+1 FROM submissions WHERE participation_id=?", participation);
		long submission = insert(c, "INSERT INTO submissions(participation_id,revision_number,submitted_code,submission_status,submitted_at,created_at) VALUES (?,?,?,'locked',NOW(),NOW())",
				participation, revision, source);
		insert(c, "INSERT INTO code_logs(participation_id,event_type,snapshot_text,observed_at,created_at) VALUES (?,'manual_save',?,DATE_SUB(NOW(),INTERVAL 3 MINUTE),NOW())", participation, initial);
		insert(c, "INSERT INTO code_logs(participation_id,event_type,snapshot_text,observed_at,created_at) VALUES (?,'periodic_snapshot',?,DATE_SUB(NOW(),INTERVAL 2 MINUTE),NOW())", participation, source);
		long log = insert(c, "INSERT INTO code_logs(participation_id,submission_id,event_type,snapshot_text,observed_at,created_at) VALUES (?,?,'submission',?,NOW(),NOW())", participation, submission, source);
		long evaluation = insert(c, """
				INSERT INTO evaluations(evaluation_code,submission_id,rubric_id,evaluation_status,evaluation_kind,
				  overall_score,auto_save_count,execution_count,feedback_summary,process_analysis,created_at,completed_at)
				VALUES (?,?,?,'completed','initial',?,1,0,?,?,NOW(),NOW())
				""", code, submission, rubric, score, "【デモ・仮データ】入力処理と加算を完成させました。AIによる実評価ではありません。",
				"【合成データ】減算の誤りを見直して加算へ修正しました。ソースコード編集ログで変更過程を確認できます。");
		try (var s = c.prepareStatement("SELECT dimension_id,label FROM rubric_dimensions WHERE rubric_id=? ORDER BY sort_order")) {
			s.setLong(1, rubric);
			try (var r = s.executeQuery()) {
				while (r.next()) {
					long dimension = r.getLong(1);
					insert(c, "INSERT INTO evaluation_dimension_results(evaluation_id,dimension_id,score_value,score_text,summary_title,summary_description) VALUES (?,?,?,?,?,?)",
							evaluation, dimension, score, String.valueOf(score), r.getString(2), "【合成データ】修正と振り返りが確認できました。");
					long reason = insert(c, "INSERT INTO evaluation_reasons(evaluation_id,reason_code,dimension_id,category_label,title,body,score_value,sort_order) VALUES (?,?,?,?,?,?,?,?)",
							evaluation, "demo-" + dimension, dimension, r.getString(2), "計算処理の見直し", "【合成データ】期待する結果に合わせて計算式を修正しました。", score, dimension);
					insert(c, "INSERT INTO evaluation_evidence(evaluation_reason_id,code_log_id,evidence_type,evidence_label,code_range,evidence_summary,sort_order) VALUES (?,?,'log','ソースコード編集ログ','1-2','【合成データ】加算に修正しました。',1)", reason, log);
				}
			}
		}
		execute(c, """
				INSERT INTO evaluation_scores(evaluation_id,criterion_id,dimension_id,metric_key,metric_title,score_value,metric_description,rationale)
				SELECT ?,criterion_id,dimension_id,criterion_code,name,?,'【合成データ】確認用指標','変更過程を振り返ることができました。'
				FROM rubric_criteria WHERE rubric_id=?
				""", evaluation, score, rubric);
		execute(c, "UPDATE task_participations SET progress_status='completed',learning_status='completed',evaluation_status='completed' WHERE participation_id=?", participation);
		return evaluation;
	}

	static long number(Connection c, String sql, Object... values) throws SQLException {
		long value = optionalNumber(c, sql, values);
		if (value == 0) throw new IllegalStateException("Required synthetic demo relation is missing.");
		return value;
	}
	static long optionalNumber(Connection c, String sql, Object... values) throws SQLException {
		try (var s = c.prepareStatement(sql)) {
			for (int i = 0; i < values.length; i++) s.setObject(i + 1, values[i]);
			try (var r = s.executeQuery()) { return r.next() ? r.getLong(1) : 0; }
		}
	}
	static void execute(Connection c, String sql, Object... values) throws SQLException {
		try (var s = c.prepareStatement(sql)) {
			for (int i = 0; i < values.length; i++) s.setObject(i + 1, values[i]);
			s.executeUpdate();
		}
	}
	static long insert(Connection c, String sql, Object... values) throws SQLException {
		try (var s = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
			for (int i = 0; i < values.length; i++) s.setObject(i + 1, values[i]);
			s.executeUpdate();
			try (var r = s.getGeneratedKeys()) {
				if (!r.next()) throw new SQLException("Demo insert did not return an identifier.");
				return r.getLong(1);
			}
		}
	}
}
