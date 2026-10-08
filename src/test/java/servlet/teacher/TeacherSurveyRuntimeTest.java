package servlet.teacher;

import static org.junit.jupiter.api.Assertions.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.*;
import com.google.gson.*;
import control.teacher.TeacherSurveyDatabaseTest;
import lib.mysql.Client;

class TeacherSurveyRuntimeTest {
	@Test void authenticatedPageJsonExportAndFailureBoundaries()throws Exception{
		Assumptions.assumeTrue("true".equals(System.getenv("TEACHER_SURVEY_RUNTIME_TEST")));TeacherSurveyDatabaseTest.guard();
		System.setProperty("jdk.httpclient.allowRestrictedHeaders","host");
		var teacher=new Browser("teacher");teacher.login("review_teacher");
		var page=teacher.get("/teacher/surveys");assertEquals(200,page.statusCode(),page.body());
		assertTrue(page.body().contains("id=\"teacherSurveyReview\""));assertTrue(page.body().contains("href=\"/teacher/surveys\""));
		assertEquals("no-store",page.headers().firstValue("Cache-Control").orElseThrow());
		var rows=JsonParser.parseString(teacher.get("/teacher/surveys?view=list").body()).getAsJsonArray();assertEquals(2,rows.size());
		long id=rows.get(0).getAsJsonObject().get("responseId").getAsLong();
		assertEquals(200,teacher.get("/teacher/surveys?view=detail&responseId="+id).statusCode());
		String before;try(var c=Client.createConnection()){before=TeacherSurveyDatabaseTest.snapshot(c);}
		for(String view:List.of("csv","text")){
			var export=teacher.get("/teacher/surveys?view="+view);assertEquals(200,export.statusCode());
			assertFalse(export.body().contains("review_student"));assertFalse(export.body().contains("review_withdrawn"));
			assertTrue(export.headers().firstValue("Content-Disposition").orElseThrow().contains("attachment"));
		}
		assertEquals(1,JsonParser.parseString(teacher.get("/teacher/surveys?view=list&q1ThinkingValidity=%3E3").body()).getAsJsonArray().size());
		for(String metric:entity.TeacherSurveyResponse.METRICS){
			String target=metric.equals("q1ThinkingValidity")?"4":"3";
			assertEquals(1,JsonParser.parseString(teacher.get("/teacher/surveys?view=list&"+metric+"=%3D"+target).body()).getAsJsonArray().size(),metric);
		}
		for(String sort:entity.TeacherSurveyFilter.SORTS){
			String query="&sort="+sort+"&direction=asc";
			var sorted=JsonParser.parseString(teacher.get("/teacher/surveys?view=list"+query).body()).getAsJsonArray();
			String csv=teacher.get("/teacher/surveys?view=csv"+query).body();
			assertTrue(csv.indexOf(sorted.get(0).getAsJsonObject().get("responseCode").getAsString())
					<csv.indexOf(sorted.get(1).getAsJsonObject().get("responseCode").getAsString()),sort);
		}
		for(String query:List.of("sort=bad","schoolId=0","schoolId=9223372036854775808","q1ThinkingValidity=4","search=a&search=b","view=list&view=csv")){
			assertEquals(400,teacher.get("/teacher/surveys?"+query).statusCode(),query);
		}
		assertEquals(404,teacher.get("/teacher/surveys?view=detail&responseId=999999").statusCode());
		assertEquals(405,teacher.post("/teacher/surveys",Map.of()).statusCode());
		var outsider=new Browser("teacher");outsider.login("review_outsider");
		assertEquals(404,outsider.get("/teacher/surveys?view=detail&responseId="+id).statusCode());
		var denied=new Browser("teacher");denied.login("review_denied");assertEquals(403,denied.get("/teacher/surveys?view=list").statusCode());
		var student=new Browser("student");student.login("review_student");student.host="teacher";assertEquals(403,student.get("/teacher/surveys?view=list").statusCode());
		assertEquals(302,new Browser("teacher").get("/teacher/surveys").statusCode());
		try(var c=Client.createConnection();var s=c.createStatement()){
			assertEquals(before,TeacherSurveyDatabaseTest.snapshot(c));
			s.execute("CREATE TRIGGER survey_http_audit_reject BEFORE INSERT ON audit_logs FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Synthetic failure'");
			try{var result=teacher.get("/teacher/surveys?view=csv");assertEquals(503,result.statusCode());assertFalse(result.body().contains("REC-standard"));}
			finally{s.execute("DROP TRIGGER survey_http_audit_reject");}
			long studentId=rows.get(0).getAsJsonObject().get("studentUserId").getAsLong();
			s.executeUpdate("INSERT INTO consent_records(user_id,consent_status,consent_document_version_id,withdrawn_at,updated_at) SELECT "+studentId+",'withdrawn',MAX(consent_document_version_id),NOW(),NOW() FROM consent_document_versions");
			try{
				assertEquals(0,JsonParser.parseString(teacher.get("/teacher/surveys?view=list").body()).getAsJsonArray().size());
				assertEquals(404,teacher.get("/teacher/surveys?view=detail&responseId="+id).statusCode());
				assertFalse(teacher.get("/teacher/surveys?view=csv").body().contains("REC-standard"));
				assertFalse(teacher.get("/teacher/surveys?view=text").body().contains("SR-standard"));
			}finally{s.executeUpdate("INSERT INTO consent_records(user_id,consent_status,consent_document_version_id,consented_at,updated_at) SELECT "+studentId+",'agreed',MAX(consent_document_version_id),NOW(),NOW() FROM consent_document_versions");}
			assertEquals(2,JsonParser.parseString(teacher.get("/teacher/surveys?view=list").body()).getAsJsonArray().size());
		}
	}
	private static final class Browser{
		String host;final HttpClient client=HttpClient.newBuilder().cookieHandler(new CookieManager(null,CookiePolicy.ACCEPT_ALL)).connectTimeout(Duration.ofSeconds(5)).build();
		Browser(String host){this.host=host;}
		HttpRequest.Builder request(String path){return HttpRequest.newBuilder(URI.create("http://ppe-survey-review-runtime:8080"+path)).header("Host",host+".localhost:18091").timeout(Duration.ofSeconds(20));}
		HttpResponse<String> get(String path)throws Exception{return client.send(request(path).GET().build(),HttpResponse.BodyHandlers.ofString());}
		HttpResponse<String> post(String path,Map<String,String> values)throws Exception{
			String body=values.entrySet().stream().map(e->URLEncoder.encode(e.getKey(),StandardCharsets.UTF_8)+"="+URLEncoder.encode(e.getValue(),StandardCharsets.UTF_8)).collect(Collectors.joining("&"));
			return client.send(request(path).header("Content-Type","application/x-www-form-urlencoded").POST(HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString());
		}
		void login(String id)throws Exception{
			String path="/"+host+"/account/login";var match=Pattern.compile("name=\"csrfToken\"\\s+value=\"([^\"]+)\"").matcher(get(path).body());assertTrue(match.find());
			assertEquals(302,post(path,Map.of("loginId",id,"password","ReviewDummy42!","csrfToken",match.group(1))).statusCode());
		}
	}
}
