package dao;

import java.sql.*;
import java.util.*;
import entity.TeacherSurveyResponse;
import entity.TeacherSurveyResponse.Answer;
import entity.TeacherSurveyResponse.Option;

public final class TeacherSurveyDao {
	public List<TeacherSurveyResponse> rows(Connection c, long teacherId) throws SQLException {
		new TeacherPermissionDao().requireSurveyResultsAccess(c, teacherId);
		var result = new ArrayList<TeacherSurveyResponse>();
		try (var statement = c.prepareStatement("""
				SELECT sr.*, u.login_id, sc.school_id, sc.name AS school_name, cl.classroom_id,
				  CONCAT_WS(' ', NULLIF(cl.grade_name,''),cl.name) AS class_name, t.task_code, t.title, t.difficulty,
				  (SELECT dr.score_value FROM evaluation_dimension_results dr
				   JOIN rubric_dimensions d ON d.dimension_id=dr.dimension_id
				   WHERE dr.evaluation_id=e.evaluation_id AND d.dimension_code='thinking' LIMIT 1) AS thinking,
				  (SELECT dr.score_value FROM evaluation_dimension_results dr
				   JOIN rubric_dimensions d ON d.dimension_id=dr.dimension_id
				   WHERE dr.evaluation_id=e.evaluation_id AND d.dimension_code='attitude' LIMIT 1) AS attitude
				FROM survey_responses sr
				JOIN surveys sv ON sv.survey_id=sr.survey_id AND sv.task_id=sr.task_id
				JOIN evaluations e ON e.evaluation_id=sr.evaluation_id
				  AND e.evaluation_status='completed' AND e.evaluation_kind IN ('initial','reevaluation')
				JOIN submissions sub ON sub.submission_id=e.submission_id
				JOIN task_participations p ON p.participation_id=sub.participation_id AND p.student_user_id=sr.student_user_id
				JOIN task_class_assignments a ON a.task_class_assignment_id=p.task_class_assignment_id AND a.task_id=sr.task_id
				JOIN classrooms cl ON cl.classroom_id=a.classroom_id
				JOIN schools sc ON sc.school_id=cl.school_id
				JOIN tasks t ON t.task_id=sr.task_id AND t.school_id=sc.school_id
				JOIN users u ON u.user_id=sr.student_user_id AND u.user_type='student'
				JOIN teacher_school_permissions tsp ON tsp.school_id=sc.school_id AND tsp.teacher_user_id=?
				  AND tsp.access_status='enabled'
				JOIN consent_records cr ON cr.user_id=u.user_id AND cr.consent_id=(
				  SELECT MAX(latest.consent_id) FROM consent_records latest WHERE latest.user_id=u.user_id)
				  AND cr.consent_status='agreed'
				WHERE sr.response_status IN ('in_progress','submitted')
				ORDER BY COALESCE(sr.submitted_at,sr.started_at) DESC,sr.survey_response_id DESC
				""")) {
			statement.setLong(1,teacherId);
			try (var r = statement.executeQuery()) {
				while(r.next()) result.add(new TeacherSurveyResponse(r.getLong("survey_response_id"),
						r.getString("response_code"),r.getString("record_code"),r.getLong("student_user_id"),
						r.getString("login_id"),r.getLong("school_id"),r.getString("school_name"),r.getLong("classroom_id"),
						r.getString("class_name"),r.getLong("task_id"),r.getString("task_code"),r.getString("title"),
						r.getString("difficulty") == null ? "none" : r.getString("difficulty"),r.getLong("survey_id"),
						r.getLong("evaluation_id"),date(r, r.getString("response_status").equals("submitted") ? "submitted_at" : "started_at"),
						r.getString("response_status"),"agreed",number(r,"thinking"),number(r,"attitude"),
						answers(c,r.getLong("survey_id"),r.getLong("survey_response_id"))));
			}
		}
		return List.copyOf(result);
	}
	private static List<Answer> answers(Connection c, long surveyId, long responseId) throws SQLException {
		var result = new ArrayList<Answer>();
		// Read the original definitions, including inactive questions/options, to preserve historical interpretation.
		try (var statement = c.prepareStatement("""
				SELECT q.question_id,q.question_code,q.prompt_text,q.question_type,a.answer_value,a.answer_reason
				FROM survey_questions q LEFT JOIN survey_answers a ON a.question_id=q.question_id AND a.survey_response_id=?
				WHERE q.survey_id=? ORDER BY q.sort_order,q.question_id
				""")) {
			statement.setLong(1,responseId); statement.setLong(2,surveyId);
			try (var r = statement.executeQuery()) {
				while(r.next()) {
					String code=r.getString("question_code"), value=r.getString("answer_value");
					Double score=null;
					if (TeacherSurveyResponse.METRICS.contains(code) && value != null && !value.isBlank()) {
						if (!"rating".equals(r.getString("question_type")) || !value.matches("[1-5](?:\\.0+)?"))
							throw new SQLException("Stored survey metric is not a five-level rating: "+code);
						score=Double.valueOf(value);
					}
					var options = new ArrayList<Option>();
					try(var opts=c.prepareStatement("SELECT value,label FROM survey_question_options WHERE question_id=? ORDER BY sort_order,option_id")){
						opts.setLong(1,r.getLong("question_id"));
						try(var o=opts.executeQuery()){while(o.next())options.add(new Option(o.getString(1),o.getString(2)));}
					}
					result.add(new Answer(code,r.getString("prompt_text"),r.getString("question_type"),
							value == null ? "" : value, r.getString("answer_reason") == null ? "" : r.getString("answer_reason"),score,options));
				}
			}
		}
		return List.copyOf(result);
	}
	public void audit(Connection c,long teacherId,String kind,int count) throws SQLException {
		try(var statement=c.prepareStatement("""
				INSERT INTO audit_logs(actor_user_id,actor_role,feature_code,target_type,action_type,result_status,detail,occurred_at)
				VALUES (?,'teacher','survey-results','survey_response',?,'success',?,CURRENT_TIMESTAMP)
				""")){
			statement.setLong(1,teacherId);statement.setString(2,"export_"+kind);
			statement.setString(3,"Pseudonymous survey export; response_count="+count);statement.executeUpdate();
		}
	}
	private static String date(ResultSet r,String column)throws SQLException{
		var value=r.getTimestamp(column);return value==null?"":value.toLocalDateTime().toString();
	}
	private static Double number(ResultSet r,String column)throws SQLException{
		double value=r.getDouble(column);return r.wasNull()?null:value;
	}
	public static final class NotFoundException extends RuntimeException { private static final long serialVersionUID=1L; }
}
