package control.teacher;

import static org.junit.jupiter.api.Assertions.*;
import java.sql.*;
import java.util.*;
import org.junit.jupiter.api.*;
import control.auth.AuthenticatedUser;
import dao.TeacherSurveyDao;
import entity.*;
import entity.UserCredential.UserType;
import lib.mysql.Client;

public class TeacherSurveyDatabaseTest {
	public static void guard() { TeacherReviewFixture.guard(System.getenv()); }
	public static void seed(Connection c) throws SQLException {
		guard();
		if(count(c,"users")!=0)return;
		var d=TeacherReviewFixture.install(c);
		for(long teacher:new long[]{d.teacher(),d.outsider(),d.deniedTeacher()})
			insert(c,"INSERT INTO teacher_feature_permissions(teacher_user_id,feature_code,is_enabled,updated_by_user_id,updated_at) VALUES (?,'survey-results',?,?,NOW())",teacher,teacher!=d.deniedTeacher(),d.teacher());
		long task=number(c,"SELECT task_id FROM task_class_assignments WHERE task_class_assignment_id="+d.assignment());
		long rubric=number(c,"SELECT rubric_id FROM evaluations WHERE evaluation_id="+d.completedEvaluation());
		long survey=insert(c,"INSERT INTO surveys(task_id,title,survey_status,created_at) VALUES (?,'Synthetic survey','active',NOW())",task);
		long submitted=response(c,d.student(),survey,task,d.completedEvaluation(),"submitted","standard");
		long secondEvaluation=insert(c,"INSERT INTO evaluations(evaluation_code,submission_id,rubric_id,evaluation_status,evaluation_kind,auto_save_count,execution_count,created_at,completed_at) VALUES ('survey-reevaluation',?,?,'completed','reevaluation',0,0,NOW(),NOW())",d.submission(),rubric);
		long draft=response(c,d.student(),survey,task,secondEvaluation,"in_progress","draft");
		long withdrawnEvaluation=number(c,"SELECT e.evaluation_id FROM evaluations e JOIN submissions s ON s.submission_id=e.submission_id JOIN task_participations p ON p.participation_id=s.participation_id WHERE p.student_user_id="+d.withdrawnStudent()+" AND e.evaluation_status='completed' LIMIT 1");
		response(c,d.withdrawnStudent(),survey,task,withdrawnEvaluation,"submitted","withdrawn");
		int i=0;
		for(String metric:TeacherSurveyResponse.METRICS){
			long question=insert(c,"INSERT INTO survey_questions(survey_id,question_code,question_type,prompt_text,required,sort_order,question_status) VALUES (?,?,'rating',?,TRUE,?,'active')",survey,metric,"Synthetic "+metric,++i);
			for(int value=1;value<=5;value++)insert(c,"INSERT INTO survey_question_options(question_id,option_code,label,value,sort_order,option_status) VALUES (?,?,?,?,?,'active')",question,"score-"+value,String.valueOf(value),String.valueOf(value),value);
			insert(c,"INSERT INTO survey_answers(survey_response_id,question_id,answer_value,answer_reason) VALUES (?,?,?,'=Synthetic reason <script>not executable</script>')",submitted,question,String.valueOf(metric.equals("q1ThinkingValidity")?4:3));
			if(metric.equals("q1ThinkingValidity"))insert(c,"INSERT INTO survey_answers(survey_response_id,question_id,answer_value,answer_reason) VALUES (?,?, '2','Draft reason')",draft,question);
		}
		long extra=insert(c,"INSERT INTO survey_questions(survey_id,question_code,question_type,prompt_text,required,sort_order,question_status) VALUES (?,'generic-text','text','追加の感想',FALSE,7,'active')",survey);
		insert(c,"INSERT INTO survey_answers(survey_response_id,question_id,answer_value,answer_reason) VALUES (?,?,'<img src=x onerror=alert(1)>長い回答','理由の改行\n次の行')",submitted,extra);
		long choices=insert(c,"INSERT INTO survey_questions(survey_id,question_code,question_type,prompt_text,required,sort_order,question_status) VALUES (?,'generic-choice','multiple_choice','使用した機能',FALSE,8,'active')",survey);
		insert(c,"INSERT INTO survey_question_options(question_id,option_code,label,value,sort_order,option_status) VALUES (?,'editor','エディター','editor',1,'active')",choices);
		insert(c,"INSERT INTO survey_question_options(question_id,option_code,label,value,sort_order,option_status) VALUES (?,'logs','ログ','logs',2,'inactive')",choices);
		insert(c,"INSERT INTO survey_answers(survey_response_id,question_id,answer_value) VALUES (?,?,'[\"editor\",\"logs\"]')",submitted,choices);
		c.commit();
	}
	private static long response(Connection c,long user,long survey,long task,long evaluation,String status,String code)throws SQLException{
		return insert(c,"INSERT INTO survey_responses(response_code,record_code,student_user_id,survey_id,task_id,evaluation_id,consent_status_at_submission,response_status,started_at,submitted_at) VALUES (?,?, ?,?,?,?,'agreed',?, '2026-10-08 09:00:00',?)","SR-"+code,"REC-"+code,user,survey,task,evaluation,status,status.equals("submitted")?"2026-10-08 09:10:00":null);
	}
	@Test void consentSchoolHistoricalAnswersExportsAndReadImmutability()throws Exception{
		Assumptions.assumeTrue("true".equals(System.getenv("TEACHER_SURVEY_DB_TEST")));guard();
		try(var c=Client.createConnection()){seed(c);}
		var control=new TeacherSurveyControl();var teacher=teacher("review_teacher");
		var rows=control.list(teacher,TeacherSurveyFilter.empty());assertEquals(2,rows.size());
		var submitted=rows.stream().filter(r->r.completionStatus().equals("submitted")).findFirst().orElseThrow();
		var draft=rows.stream().filter(r->r.completionStatus().equals("in_progress")).findFirst().orElseThrow();
		assertEquals("2026-10-08T09:00",draft.submittedAt());assertNull(draft.score("q4UsabilityScore"));
		assertEquals(4.0,submitted.systemThinkingScore());assertEquals(8,submitted.answers().size());
		assertEquals(2,submitted.answers().getLast().options().size());
		String before;try(var c=Client.createConnection()){before=snapshot(c);}
		assertEquals(submitted,control.detail(teacher,submitted.responseId()));
		assertTrue(control.list(teacher("review_outsider"),TeacherSurveyFilter.empty()).isEmpty());
		assertThrows(TeacherSurveyDao.NotFoundException.class,()->control.detail(teacher("review_outsider"),submitted.responseId()));
		assertThrows(SecurityException.class,()->control.list(teacher("review_denied"),TeacherSurveyFilter.empty()));
		assertThrows(SecurityException.class,()->control.list(new AuthenticatedUser(submitted.studentUserId(),"review_student","Student",UserType.STUDENT,false,"synthetic"),TeacherSurveyFilter.empty()));
		assertFalse(control.export(teacher,TeacherSurveyFilter.empty(),false).contains("review_student"));
		assertTrue(control.export(teacher,TeacherSurveyFilter.empty(),true).contains("generic-choice"));
		try(var c=Client.createConnection()){
			assertEquals(before,snapshot(c));
			c.setAutoCommit(false);
			try{
				update(c,"UPDATE student_class_memberships SET membership_status='inactive' WHERE student_user_id=?",submitted.studentUserId());
				update(c,"UPDATE task_class_assignments SET assignment_status='archived' WHERE task_id=?",submitted.taskId());
				update(c,"UPDATE surveys SET survey_status='closed' WHERE survey_id=?",submitted.surveyId());
				assertEquals(2,new TeacherSurveyDao().rows(c,teacher.userId()).size());
			}finally{c.rollback();c.setAutoCommit(true);}
			for(String mutation:List.of(
					"UPDATE survey_responses SET evaluation_id=(SELECT evaluation_id FROM evaluations WHERE evaluation_code='review-failed') WHERE response_code='SR-standard'",
					"UPDATE survey_responses SET student_user_id=(SELECT user_id FROM users WHERE login_id='review_withdrawn') WHERE response_code='SR-standard'")) {
				c.setAutoCommit(false);
				try{update(c,mutation);assertEquals(1,new TeacherSurveyDao().rows(c,teacher.userId()).size());}
				finally{c.rollback();c.setAutoCommit(true);}
			}
			c.setAutoCommit(false);
			try{update(c,"UPDATE surveys SET task_id=NULL WHERE survey_id=?",submitted.surveyId());assertTrue(new TeacherSurveyDao().rows(c,teacher.userId()).isEmpty());}
			finally{c.rollback();c.setAutoCommit(true);}
			for(String mutation:List.of(
					"UPDATE teacher_feature_permissions SET is_enabled=FALSE WHERE feature_code='survey-results' AND teacher_user_id="+teacher.userId(),
					"UPDATE users SET account_status='suspended' WHERE user_id="+teacher.userId())){
				c.setAutoCommit(false);
				try{update(c,mutation);assertThrows(SecurityException.class,()->new TeacherSurveyDao().rows(c,teacher.userId()));}
				finally{c.rollback();c.setAutoCommit(true);}
			}
			c.setAutoCommit(false);
			try{update(c,"UPDATE teacher_school_permissions SET access_status='disabled' WHERE teacher_user_id=?",teacher.userId());assertTrue(new TeacherSurveyDao().rows(c,teacher.userId()).isEmpty());}
			finally{c.rollback();c.setAutoCommit(true);}
			insert(c,"INSERT INTO consent_records(user_id,consent_status,consent_document_version_id,withdrawn_at,updated_at) VALUES (?,'withdrawn',(SELECT MAX(consent_document_version_id) FROM consent_document_versions),NOW(),NOW())",submitted.studentUserId());
			assertTrue(control.list(teacher,TeacherSurveyFilter.empty()).isEmpty());
			assertThrows(TeacherSurveyDao.NotFoundException.class,()->control.detail(teacher,submitted.responseId()));
			insert(c,"INSERT INTO consent_records(user_id,consent_status,consent_document_version_id,consented_at,updated_at) VALUES (?,'agreed',(SELECT MAX(consent_document_version_id) FROM consent_document_versions),NOW(),NOW())",submitted.studentUserId());
			assertEquals(2,control.list(teacher,TeacherSurveyFilter.empty()).size());
			try(var s=c.createStatement()){
				s.execute("CREATE TRIGGER survey_audit_reject BEFORE INSERT ON audit_logs FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Synthetic audit rejection'");
				try{assertThrows(SQLException.class,()->control.export(teacher,TeacherSurveyFilter.empty(),false));}
				finally{s.execute("DROP TRIGGER survey_audit_reject");}
			}
		}
	}
	public static AuthenticatedUser teacher(String login)throws SQLException{
		try(var c=Client.createConnection();var s=c.prepareStatement("SELECT user_id FROM users WHERE login_id=?")){
			s.setString(1,login);try(var r=s.executeQuery()){r.next();return new AuthenticatedUser(r.getLong(1),login,login,UserType.TEACHER,false,"synthetic");}
		}
	}
	public static String snapshot(Connection c)throws SQLException{
		var out=new StringBuilder();
		for(String table:List.of("survey_responses","survey_answers","evaluations","submissions","code_logs")){
			try(var s=c.createStatement();var r=s.executeQuery("SELECT * FROM "+table+" ORDER BY 1")){
				while(r.next())for(int i=1;i<=r.getMetaData().getColumnCount();i++)out.append(r.getString(i)).append('|');
			}
		}
		return out.toString();
	}
	static long count(Connection c,String table)throws SQLException{return number(c,"SELECT COUNT(*) FROM "+table);}
	static long number(Connection c,String sql)throws SQLException{try(var s=c.createStatement();var r=s.executeQuery(sql)){r.next();return r.getLong(1);}}
	static void update(Connection c,String sql,Object...values)throws SQLException{try(var s=c.prepareStatement(sql)){for(int i=0;i<values.length;i++)s.setObject(i+1,values[i]);s.executeUpdate();}}
	static long insert(Connection c,String sql,Object...values)throws SQLException{try(var s=c.prepareStatement(sql,Statement.RETURN_GENERATED_KEYS)){for(int i=0;i<values.length;i++)s.setObject(i+1,values[i]);s.executeUpdate();try(var r=s.getGeneratedKeys()){r.next();return r.getLong(1);}}}
}
