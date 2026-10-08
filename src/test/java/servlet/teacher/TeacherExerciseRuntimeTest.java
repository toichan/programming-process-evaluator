package servlet.teacher;

import static org.junit.jupiter.api.Assertions.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.zip.ZipInputStream;
import java.io.ByteArrayInputStream;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import com.google.gson.*;
import control.teacher.TeacherExerciseDatabaseTest;
import lib.mysql.Client;

class TeacherExerciseRuntimeTest {
	@Test void authenticatedHttpReadExportsErrorsAndAuditFailure() throws Exception {
		Assumptions.assumeTrue("true".equals(System.getenv("TEACHER_EXERCISE_RUNTIME_TEST")));
		TeacherExerciseDatabaseTest.guard();
		assertEquals("http://ppe-exercise-review-runtime:8080",System.getenv("TEACHER_EXERCISE_HTTP_BASE"));
		System.setProperty("jdk.httpclient.allowRestrictedHeaders","host");
		var teacher=new Browser("teacher"); teacher.login("ex_teacher","teacher");
		var page=teacher.get("/teacher/exercises");
		assertEquals(200,page.statusCode(),page.body());
		assertTrue(page.body().contains("id=\"teacherExerciseReview\""));
		assertTrue(page.body().contains("href=\"/teacher/exercises\""));
		assertEquals("no-store",page.headers().firstValue("Cache-Control").orElseThrow());
		var rows=teacher.json("/teacher/exercises?view=list").getAsJsonArray(); assertEquals(4,rows.size());
		var saved=rows.asList().stream().map(JsonElement::getAsJsonObject)
				.filter(row->row.get("studentLoginId").getAsString().equals("ex_saved")).findFirst().orElseThrow();
		String target="&studentId="+saved.get("studentId").getAsLong()+"&classroomId="+saved.get("classroomId").getAsLong();
		var detail=teacher.json("/teacher/exercises?view=detail"+target).getAsJsonObject();
		assertEquals(2,detail.getAsJsonArray("scopes").size());
		var entries=detail.getAsJsonArray("scopes").get(0).getAsJsonObject().getAsJsonArray("entries");
		assertTrue(entries.get(0).getAsJsonObject().get("updatedAt").getAsString().contains("T"));
		var file=entries.asList().stream().map(JsonElement::getAsJsonObject).filter(e->e.get("type").getAsString().equals("FILE")).findFirst().orElseThrow();
		var download=teacher.get("/teacher/exercises?view=download"+target+"&entryId="+file.get("entryId").getAsLong());
		assertEquals(200,download.statusCode());
		assertEquals(file.get("content").getAsString(),download.body());
		assertTrue(download.headers().firstValue("Content-Disposition").orElseThrow().contains("attachment"));
		String before; long audits;
		try(var c=Client.createConnection()){
			before=TeacherExerciseDatabaseTest.snapshot(c);
			audits=TeacherExerciseDatabaseTest.number(c,"SELECT COUNT(*) FROM audit_logs WHERE feature_code='exercise-code-review'");
		}
		for(String path:new String[]{"/teacher/exercises?view=zip"+target,"/teacher/exercises?view=bulk"}){
			var result=teacher.bytes(path); assertEquals(200,result.statusCode());
			var names=new java.util.HashSet<String>();
			try(var zip=new ZipInputStream(new ByteArrayInputStream(result.body()))){
				java.util.zip.ZipEntry entry;
				while((entry=zip.getNextEntry())!=null){assertTrue(names.add(entry.getName()));zip.readAllBytes();}
			}
			assertEquals(path.contains("view=bulk") ? 6 : 5,names.size());
			assertTrue(names.stream().anyMatch(name->name.endsWith("空フォルダ/")));
		}
		var csv=teacher.get("/teacher/exercises?view=csv");assertEquals(200,csv.statusCode());
		assertTrue(csv.body().startsWith("\uFEFF")); assertTrue(csv.body().contains("ex_saved")); assertFalse(csv.body().contains("ex_withdrawn"));
		assertEquals(400,teacher.get("/teacher/exercises?view=list&sort=bad").statusCode());
		assertEquals(400,teacher.get("/teacher/exercises?view=detail"+target+"&studentId=1").statusCode());
		assertEquals(400,teacher.get("/teacher/exercises?view=detail&studentId=9223372036854775808&classroomId=1").statusCode());
		assertEquals(404,teacher.get("/teacher/exercises?view=detail&studentId=999999&classroomId=1").statusCode());
		assertEquals(404,teacher.get("/teacher/exercises?view=download"+target+"&entryId=999999").statusCode());
		assertEquals(400,teacher.get("/teacher/exercises?view=bulk&search=unmatched").statusCode());
		assertEquals(405,teacher.post("/teacher/exercises",Map.of()).statusCode());
		var outsider=new Browser("teacher");outsider.login("ex_outsider","teacher");
		assertEquals(0,outsider.json("/teacher/exercises?view=list").getAsJsonArray().size());
		assertEquals(404,outsider.get("/teacher/exercises?view=detail"+target).statusCode());
		var denied=new Browser("teacher");denied.login("ex_denied","teacher");
		assertEquals(403,denied.get("/teacher/exercises?view=list").statusCode());
		var student=new Browser("student"); student.login("ex_saved","student");student.host="teacher";
		assertEquals(403,student.get("/teacher/exercises?view=list").statusCode());
		try(var c=Client.createConnection()){
			assertEquals(before,TeacherExerciseDatabaseTest.snapshot(c));
			assertEquals(audits+1,TeacherExerciseDatabaseTest.number(c,"SELECT COUNT(*) FROM audit_logs WHERE feature_code='exercise-code-review'"));
			try(var s=c.createStatement()){
				s.execute("CREATE TRIGGER ex_audit_reject BEFORE INSERT ON audit_logs FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Dummy audit failure'");
				try{
					var failure=teacher.get("/teacher/exercises?view=csv");
					assertEquals(503,failure.statusCode());assertFalse(failure.body().contains("ex_saved"));
				}finally{s.execute("DROP TRIGGER ex_audit_reject");}
			}
		}
	}
	private static final class Browser {
		String host;
		final HttpClient client=HttpClient.newBuilder().cookieHandler(new CookieManager(null,CookiePolicy.ACCEPT_ALL))
				.connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build();
		Browser(String host){this.host=host;}
		HttpRequest.Builder request(String path){
			return HttpRequest.newBuilder(URI.create(System.getenv("TEACHER_EXERCISE_HTTP_BASE")+path))
					.header("Host",host+".localhost:18090").timeout(Duration.ofSeconds(20));
		}
		HttpResponse<String> get(String path)throws Exception{return client.send(request(path).GET().build(),HttpResponse.BodyHandlers.ofString());}
		HttpResponse<byte[]> bytes(String path)throws Exception{return client.send(request(path).GET().build(),HttpResponse.BodyHandlers.ofByteArray());}
		HttpResponse<String> post(String path,Map<String,String> values)throws Exception{
			String body=values.entrySet().stream().map(e->URLEncoder.encode(e.getKey(),StandardCharsets.UTF_8)+"="+URLEncoder.encode(e.getValue(),StandardCharsets.UTF_8)).collect(Collectors.joining("&"));
			return client.send(request(path).header("Content-Type","application/x-www-form-urlencoded").POST(HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString());
		}
		JsonElement json(String path)throws Exception{var result=get(path);assertEquals(200,result.statusCode(),result.body());return JsonParser.parseString(result.body());}
		void login(String id,String portal)throws Exception{
			String path="/"+portal+"/account/login";
			var match=Pattern.compile("name=\"csrfToken\"\\s+value=\"([^\"]+)\"").matcher(get(path).body());assertTrue(match.find());
			assertEquals(302,post(path,Map.of("loginId",id,"password",TeacherExerciseDatabaseTest.PASSWORD,"csrfToken",match.group(1))).statusCode());
		}
	}
}
