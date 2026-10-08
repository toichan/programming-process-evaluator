package control.teacher;

import java.sql.*;
import java.util.List;
import com.google.gson.Gson;
import control.auth.AuthenticatedUser;
import dao.TeacherSurveyDao;
import entity.*;
import entity.UserCredential.UserType;
import lib.mysql.Client;
import lib.web.CsvCells;

public final class TeacherSurveyControl {
	private final TeacherSurveyDao dao = new TeacherSurveyDao();
	public List<TeacherSurveyResponse> list(AuthenticatedUser user,TeacherSurveyFilter filter)throws SQLException{
		requireTeacher(user);return transaction(c->filter.apply(dao.rows(c,user.userId())));
	}
	public TeacherSurveyResponse detail(AuthenticatedUser user,long responseId)throws SQLException{
		requireTeacher(user);StudentExerciseInput.requireId(responseId);
		return transaction(c->dao.rows(c,user.userId()).stream().filter(row->row.responseId()==responseId)
				.findFirst().orElseThrow(TeacherSurveyDao.NotFoundException::new));
	}
	public String export(AuthenticatedUser user,TeacherSurveyFilter filter,boolean text)throws SQLException{
		requireTeacher(user);
		return transaction(c->{
			var rows=filter.apply(dao.rows(c,user.userId()));
			String result=text?text(rows):csv(rows);
			if(result.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>32*1024*1024)
				throw new PythonExecutionInput.TooLargeException("出力が32 MiBを超えています。条件を絞ってください。");
			dao.audit(c,user.userId(),text?"text":"csv",rows.size());return result;
		});
	}
	static String csv(List<TeacherSurveyResponse> rows) {
		var out=new StringBuilder("\uFEFF");
		append(out,"recordId","responseId","submittedAt","studentId","taskCode","taskTitle","difficulty",
				"q1ThinkingValidity","q1ThinkingValidityReason","q1ThinkingScore","q1ThinkingReason",
				"q2AttitudeValidity","q2AttitudeValidityReason","q2AttitudeScore","q2AttitudeReason",
				"q3ProcessResistanceScore","q3ProcessResistanceReason","q4UsabilityScore","q4UsabilityComment",
				"回答完了状態","研究同意状態","surveyId","evaluationId","additionalAnswers");
		for(var r:rows)append(out,r.recordCode(),r.responseCode(),r.submittedAt(),r.studentUserId(),r.taskCode(),r.taskTitle(),r.difficultyLabel(),
				r.score("q1ThinkingValidity"),r.reason("q1ThinkingValidity"),r.score("q1ThinkingScore"),r.reason("q1ThinkingScore"),
				r.score("q2AttitudeValidity"),r.reason("q2AttitudeValidity"),r.score("q2AttitudeScore"),r.reason("q2AttitudeScore"),
				r.score("q3ProcessResistanceScore"),r.reason("q3ProcessResistanceScore"),r.score("q4UsabilityScore"),r.reason("q4UsabilityScore"),
				"submitted".equals(r.completionStatus())?"完了":"下書き","同意",r.surveyId(),r.evaluationId(),
				new Gson().toJson(r.answers().stream().filter(a->!TeacherSurveyResponse.METRICS.contains(a.code())).toList()));
		return out.toString();
	}
	static String text(List<TeacherSurveyResponse> rows) {
		var out=new StringBuilder();
		for(var r:rows) {
			out.append("回答ID: ").append(r.responseCode()).append(" / 評価ID: ").append(r.evaluationId()).append("\r\n");
			for(var a:r.answers())out.append(r.studentUserId()).append('_').append(r.taskTitle()).append('_').append(a.code())
					.append("\r\n").append(a.prompt()).append("\r\n").append(a.value()).append("\r\n").append(a.reason()).append("\r\n\r\n");
		}
		return out.toString();
	}
	private static void append(StringBuilder out,Object... cells){
		for(int i=0;i<cells.length;i++){if(i>0)out.append(',');out.append(CsvCells.encode(cells[i]==null?"":cells[i].toString()));}out.append("\r\n");
	}
	private static void requireTeacher(AuthenticatedUser user){
		if(user==null||user.userType()!=UserType.TEACHER)throw new SecurityException("Teacher authentication required.");
	}
	private static <T>T transaction(Read<T> read)throws SQLException{
		try(var c=Client.createConnection()){
			c.setAutoCommit(false);
			try{T result=read.execute(c);c.commit();return result;}
			catch(SQLException|RuntimeException failure){try{c.rollback();}catch(SQLException rollback){failure.addSuppressed(rollback);}throw failure;}
		}
	}
	@FunctionalInterface private interface Read<T>{T execute(Connection c)throws SQLException;}
}
