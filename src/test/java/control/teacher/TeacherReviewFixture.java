package control.teacher;

import java.sql.*;
import java.util.Map;
import control.auth.PasswordHasher;

/** Synthetic-only fixture; never usable against the development or production database. */
public final class TeacherReviewFixture {
	public static final String PASSWORD = "ReviewDummy42!";
	public record Data(long teacher, long outsider, long deniedTeacher, long student, long withdrawnStudent,
			long admin, long school, long classroom, long assignment, long participation, long submission,
			long secondSubmission, long completedEvaluation, long pendingEvaluation) {}

	public static void guard(Map<String, String> environment) {
		if (!"true".equals(environment.get("TEACHER_REVIEW_DB_TEST"))
				|| !"ppe-preparation-20261008-db-1".equals(environment.get("DB_HOST"))
				|| !environment.getOrDefault("DB_NAME", "").matches("ppe_teacher_review_test_[a-z0-9_]+")) {
			throw new IllegalStateException("Teacher review fixture requires its explicitly isolated test database and host.");
		}
	}
	public static Data install(Connection c) throws SQLException {
		guard(System.getenv());
		if (!System.getenv("DB_NAME").equals(c.getCatalog())) throw new IllegalStateException("Unexpected database catalog.");
		try (var s = c.createStatement(); var r = s.executeQuery("SELECT COUNT(*) FROM users")) {
			r.next(); if (r.getLong(1) != 0) throw new IllegalStateException("Fixture requires an empty synthetic-only database.");
		}
		c.setAutoCommit(false);
		try {
			String hash = new PasswordHasher().hash(PASSWORD.toCharArray());
			long teacher = user(c, "review_teacher", "teacher", hash);
			long outsider = user(c, "review_outsider", "teacher", hash);
			long denied = user(c, "review_denied", "teacher", hash);
			long student = user(c, "review_student", "student", hash);
			long withdrawnStudent = user(c, "review_withdrawn", "student", hash);
			long admin = user(c, "admin", "admin", hash);
			long school = insert(c, "INSERT INTO schools (school_code,name,school_status,security_level,created_at) VALUES ('review-dummy','Synthetic Review School','active',1,NOW())");
			long otherSchool = insert(c, "INSERT INTO schools (school_code,name,school_status,created_at) VALUES ('review-other','Synthetic Other School','active',NOW())");
			long classroom = insert(c, "INSERT INTO classrooms (school_id,name,grade_name,classroom_status,created_at) VALUES (?,'Synthetic Class','1','active',NOW())", school);
			for (long id : new long[] { teacher, outsider, denied }) {
				insert(c, "INSERT INTO teacher_school_permissions (teacher_user_id,school_id,access_status,updated_by_user_id,updated_at) VALUES (?,?,'enabled',?,NOW())", id, id == outsider ? otherSchool : school, teacher);
				for (String feature : new String[] { "submission-review", "evaluation-review" }) {
					insert(c, "INSERT INTO teacher_feature_permissions (teacher_user_id,feature_code,is_enabled,updated_by_user_id,updated_at) VALUES (?,?,?,?,NOW())", id, feature, id != denied, teacher);
				}
			}
			for (long id : new long[] { student, withdrawnStudent }) {
				insert(c, "INSERT INTO student_profiles (user_id,school_id,student_code,security_level,first_login_status,must_change_password) VALUES (?,?,?,1,'completed',FALSE)", id, school, "dummy-" + id);
				insert(c, "INSERT INTO student_class_memberships (student_user_id,classroom_id,membership_status,joined_at) VALUES (?,?,'active',NOW())", id, classroom);
			}
			long rubric = insert(c, "INSERT INTO rubrics (title,version,rubric_status,created_at) VALUES ('Synthetic review rubric','review-fixed-v1','active',NOW())");
			long thinking = insert(c, "INSERT INTO rubric_dimensions (rubric_id,dimension_code,label,scale_minimum,scale_maximum,scale_label,sort_order) VALUES (?,'thinking','思考力・判断力・表現力',1,5,'5段階',1)", rubric);
			long attitude = insert(c, "INSERT INTO rubric_dimensions (rubric_id,dimension_code,label,scale_minimum,scale_maximum,scale_label,sort_order) VALUES (?,'attitude','主体的に学習に取り組む態度',1,5,'5段階',2)", rubric);
			long task = insert(c, """
					INSERT INTO tasks (school_id,task_code,task_revision_code,revision_number,created_by_user_id,rubric_id,
					  title,difficulty,language,description,save_status,publication_status,created_at,published_at)
					VALUES (?,'review-task','review-task-v1',1,?,?,'Synthetic Review Task','beginner','Python 3','Dummy only','saved','published',NOW(),NOW())
					""", school, teacher, rubric);
			long assignment = insert(c, """
					INSERT INTO task_class_assignments (task_id,classroom_id,assignment_status,publish_at,due_at,
					  late_submission_policy,resubmission_policy,created_at)
					VALUES (?,?,'published',NOW(),DATE_ADD(NOW(),INTERVAL 1 DAY),'deny','allow',NOW())
					""", task, classroom);
			long prompt = insert(c, """
					INSERT INTO prompt_versions (task_id,version,ai_model,common_prompt,prompt_status,
					  fluctuation_generation_status,evaluation_examples_status,created_by_user_id,created_at)
					VALUES (?,'review-fixed-prompt-v1','synthetic-no-api','Dummy only','configured','completed','completed',?,NOW())
					""", task, teacher);
			long currentPrompt = insert(c, """
					INSERT INTO prompt_versions (task_id,version,ai_model,common_prompt,prompt_status,
					  fluctuation_generation_status,evaluation_examples_status,created_by_user_id,created_at)
					VALUES (?,'review-current-prompt-v2','synthetic-no-api','Dummy only','configured','completed','completed',?,NOW())
					""", task, teacher);
			try (var s = c.prepareStatement("UPDATE tasks SET active_prompt_version_id=? WHERE task_id=?")) {
				s.setLong(1, currentPrompt); s.setLong(2, task); s.executeUpdate();
			}
			long participation = insert(c, """
					INSERT INTO task_participations (student_user_id,task_class_assignment_id,progress_status,learning_status,
					  evaluation_status,save_status,last_activity_at,active_duration_seconds)
					VALUES (?,?,'submitted','completed','in_progress','saved',NOW(),60)
					""", student, assignment);
			long withdrawnParticipation = insert(c, """
					INSERT INTO task_participations (student_user_id,task_class_assignment_id,progress_status,learning_status,
					  evaluation_status,save_status,last_activity_at,active_duration_seconds)
					VALUES (?,?,'submitted','completed','completed','saved',NOW(),60)
					""", withdrawnStudent, assignment);
			long submission = insert(c, """
					INSERT INTO submissions (participation_id,revision_number,submitted_code,submission_status,submitted_at,created_at)
					VALUES (?,1,'print(input())','locked',DATE_SUB(NOW(),INTERVAL 2 MINUTE),NOW())
					""", participation);
			long second = insert(c, """
					INSERT INTO submissions (participation_id,revision_number,submitted_code,submission_status,submitted_at,created_at)
					VALUES (?,2,'print(input().upper())','locked',DATE_SUB(NOW(),INTERVAL 1 MINUTE),NOW())
					""", participation);
			long withdrawnSubmission = insert(c, """
					INSERT INTO submissions (participation_id,revision_number,submitted_code,submission_status,submitted_at,created_at)
					VALUES (?,1,'print(2)','locked',NOW(),NOW())
					""", withdrawnParticipation);
			long evaluation = insert(c, """
					INSERT INTO evaluations (evaluation_code,submission_id,rubric_id,prompt_version_id,evaluation_status,evaluation_kind,overall_score,auto_save_count,execution_count,
					  feedback_summary,process_analysis,created_at,completed_at)
					VALUES ('review-complete',?,?,?,'completed','initial',4,0,1,'Synthetic summary','Synthetic process',DATE_SUB(NOW(),INTERVAL 2 MINUTE),NOW())
					""", submission, rubric, prompt);
			insert(c, """
					INSERT INTO evaluations (evaluation_code,submission_id,rubric_id,evaluation_status,evaluation_kind,auto_save_count,execution_count,created_at)
					VALUES ('review-failed',?,?,'failed','initial',0,0,DATE_SUB(NOW(),INTERVAL 1 MINUTE))
					""", second, rubric);
			long pending = insert(c, """
					INSERT INTO evaluations (evaluation_code,submission_id,rubric_id,evaluation_status,evaluation_kind,auto_save_count,execution_count,created_at)
					VALUES ('review-pending',?,?,'in_progress','initial',0,0,NOW())
					""", second, rubric);
			insert(c, """
					INSERT INTO evaluations (evaluation_code,submission_id,rubric_id,evaluation_status,evaluation_kind,overall_score,auto_save_count,execution_count,created_at,completed_at)
					VALUES ('review-withdrawn',?,?,'completed','initial',3,0,0,NOW(),NOW())
					""", withdrawnSubmission, rubric);
			for (long dimension : new long[] { thinking, attitude }) {
				insert(c, "INSERT INTO evaluation_dimension_results (evaluation_id,dimension_id,score_value,score_text,summary_title,summary_description) VALUES (?,?,4,'4','Synthetic dimension','Synthetic evidence')", evaluation, dimension);
				long criterion = insert(c, "INSERT INTO rubric_criteria (rubric_id,dimension_id,criterion_code,name,weight,sort_order) VALUES (?,?,?,'Synthetic metric',1,?)", rubric, dimension, "synthetic-" + dimension, dimension);
				insert(c, """
						INSERT INTO evaluation_scores (evaluation_id,criterion_id,dimension_id,metric_key,metric_title,
						  score_value,metric_description,rationale)
						VALUES (?,?,?,?,'Synthetic metric',4,'Synthetic metric description','Synthetic metric rationale')
						""", evaluation, criterion, dimension, "synthetic-" + dimension);
			}
			long reason = insert(c, "INSERT INTO evaluation_reasons (evaluation_id,reason_code,dimension_id,title,body,sort_order) VALUES (?,'synthetic-reason',?,'Synthetic reason','<script>dummy only</script>',1)", evaluation, thinking);
			insert(c, "INSERT INTO evaluation_reason_details (evaluation_reason_id,detail_text,sort_order) VALUES (?,'Synthetic detail',1)", reason);
			long execution = insert(c, """
					INSERT INTO code_executions (participation_id,submission_id,actor_user_id,execution_context,source_code,
					  standard_input,execution_status,exit_code,standard_output,standard_output_truncated,standard_error_truncated,executed_at)
					VALUES (?,?,?,'submission_check','print(input())','dummy','succeeded',0,'dummy',FALSE,FALSE,NOW())
					""", participation, submission, student);
			insert(c, """
					INSERT INTO code_execution_test_results (execution_id,case_order,input_snapshot,expected_output_snapshot,actual_output,actual_output_truncated,result_status,checked_at)
					VALUES (?,1,'dummy','dummy','dummy',FALSE,'matched',NOW())
					""", execution);
			insert(c, """
					INSERT INTO code_execution_test_results (execution_id,case_order,input_snapshot,expected_output_snapshot,actual_output,actual_output_truncated,result_status,checked_at)
					VALUES (?,2,'<script>input</script>','expected','different',FALSE,'mismatched',NOW())
					""", execution);
			insert(c, "INSERT INTO code_logs (participation_id,submission_id,event_type,snapshot_text,observed_at,created_at) VALUES (?,?,'submission','print(input())',DATE_SUB(NOW(),INTERVAL 2 MINUTE),NOW())", participation, submission);
			insert(c, "INSERT INTO code_logs (participation_id,event_type,snapshot_text,observed_at,created_at) VALUES (?,'manual_save','print(0)',DATE_SUB(NOW(),INTERVAL 3 MINUTE),NOW())", participation);
			insert(c, "INSERT INTO code_logs (participation_id,event_type,snapshot_text,observed_at,created_at) VALUES (?,'manual_save','print(input().upper())',DATE_SUB(NOW(),INTERVAL 90 SECOND),NOW())", participation);
			long document = insert(c, "INSERT INTO consent_document_versions (version_code,title,body,document_status,effective_at,created_at) VALUES ('review-v1','Synthetic consent','Dummy only','active',NOW(),NOW())");
			insert(c, "INSERT INTO consent_records (user_id,consent_document_version_id,consent_status,consented_at,updated_at) VALUES (?,?,'agreed',NOW(),NOW())", student, document);
			insert(c, "INSERT INTO consent_records (user_id,consent_document_version_id,consent_status,consented_at,updated_at) VALUES (?,?,'agreed',NOW(),NOW())", withdrawnStudent, document);
			insert(c, "INSERT INTO consent_records (user_id,consent_document_version_id,consent_status,withdrawn_at,updated_at) VALUES (?,?,'withdrawn',NOW(),NOW())", withdrawnStudent, document);
			c.commit();
			return new Data(teacher, outsider, denied, student, withdrawnStudent, admin, school, classroom, assignment, participation, submission, second, evaluation, pending);
		} catch (SQLException | RuntimeException failure) { c.rollback(); throw failure; }
	}
	static long insert(Connection c, String sql, Object... parameters) throws SQLException {
		try (var s = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
			for (int i = 0; i < parameters.length; i++) s.setObject(i + 1, parameters[i]);
			s.executeUpdate();
			try (var r = s.getGeneratedKeys()) { return r.next() ? r.getLong(1) : 0; }
		}
	}
	private static long user(Connection c, String login, String role, String hash) throws SQLException {
		return insert(c, "INSERT INTO users (login_id,password_hash,display_name,user_type,account_status,created_at) VALUES (?,?,?,?,'active',NOW())", login, hash, login, role);
	}
}
