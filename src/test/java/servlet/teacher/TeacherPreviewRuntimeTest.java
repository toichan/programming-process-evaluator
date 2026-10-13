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
import control.teacher.TeacherExerciseDatabaseTest;
import lib.mysql.Client;

class TeacherPreviewRuntimeTest {
	private static final String CODE = "print('ready', flush=True)\nname=input()\nprint('hello '+name)\n";
	private Browser teacher;
	private Map<String, String> exercise, progress;
	@BeforeEach void fixture() throws Exception {
		Assumptions.assumeTrue("true".equals(System.getenv("TEACHER_PREVIEW_RUNTIME_TEST")));
		TeacherExerciseDatabaseTest.guard();
		System.setProperty("jdk.httpclient.allowRestrictedHeaders", "host");
		teacher = new Browser(); teacher.login("ex_teacher");
		var rows = teacher.json("/teacher/exercises?view=list").getAsJsonArray();
		var saved = rows.asList().stream().map(JsonElement::getAsJsonObject)
				.filter(row -> row.get("studentLoginId").getAsString().equals("ex_saved")).findFirst().orElseThrow();
		String student = saved.get("studentId").getAsString(), classroom = saved.get("classroomId").getAsString();
		var detail = teacher.json("/teacher/exercises?view=detail&studentId="+student+"&classroomId="+classroom).getAsJsonObject();
		var file = detail.getAsJsonArray("scopes").get(0).getAsJsonObject().getAsJsonArray("entries").asList().stream()
				.map(JsonElement::getAsJsonObject).filter(entry -> "FILE".equals(entry.get("type").getAsString())).findFirst().orElseThrow();
		exercise = Map.of("studentId", student, "classroomId", classroom, "entryId", file.get("entryId").getAsString());
		try (var c = Client.createConnection(); var s = c.createStatement()) {
			long teacherId = TeacherExerciseDatabaseTest.number(c, "SELECT user_id FROM users WHERE login_id='ex_teacher'");
			long school = saved.get("schoolId").getAsLong();
			s.executeUpdate("INSERT IGNORE INTO teacher_feature_permissions(teacher_user_id,feature_code,is_enabled,updated_by_user_id,updated_at)"
					+" VALUES ("+teacherId+",'task-progress',TRUE,"+teacherId+",NOW())");
			if (TeacherExerciseDatabaseTest.number(c, "SELECT COUNT(*) FROM tasks WHERE task_code='REQ158'") == 0) {
				s.executeUpdate("INSERT INTO tasks(school_id,task_code,task_revision_code,revision_number,created_by_user_id,title,difficulty,language,description,save_status,publication_status,created_at,published_at)"
						+" VALUES ("+school+",'REQ158','REQ158-v1',1,"+teacherId+",'合成・対話実行検証','beginner','Python 3.x','Synthetic only','saved','published',NOW(),NOW())");
				long task = TeacherExerciseDatabaseTest.number(c, "SELECT task_id FROM tasks WHERE task_code='REQ158'");
				s.executeUpdate("INSERT INTO task_class_assignments(task_id,classroom_id,assignment_status,publish_at,late_submission_policy,resubmission_policy,created_at)"
						+" VALUES ("+task+","+classroom+",'published',NOW(),'allow','allow',NOW())");
				long assignment = TeacherExerciseDatabaseTest.number(c, "SELECT task_class_assignment_id FROM task_class_assignments WHERE task_id="+task);
				s.executeUpdate("INSERT INTO task_participations(student_user_id,task_class_assignment_id,learning_status,progress_status,save_status,evaluation_status,current_draft_code,draft_updated_at,active_duration_seconds)"
						+" VALUES ("+student+","+assignment+",'in_progress','in_progress','saved','not_started','print(158)',NOW(),0)");
			}
			long assignment = TeacherExerciseDatabaseTest.number(c, "SELECT task_class_assignment_id FROM task_class_assignments a JOIN tasks t ON t.task_id=a.task_id WHERE t.task_code='REQ158'");
			progress = Map.of("assignmentId", Long.toString(assignment), "studentUserId", student);
		}
	}
	@Test void bothSurfacesExecuteInputPollCancelWithoutChangingPersistedData() throws Exception {
		String before = snapshot();
		for (String surface : List.of("progress", "exercises")) {
			String endpoint = "/teacher/"+surface;
			var target = surface.equals("progress") ? progress : exercise;
			var page = teacher.get(endpoint); assertEquals(200, page.statusCode(), page.body());
			assertTrue(page.body().contains("id=\"workCodeEditor\"")); assertTrue(page.body().contains("id=\"terminalInput\""));
			var start = teacher.post(endpoint, values(target, "preview-start", null, CODE)); assertEquals(200, start.statusCode(), start.body());
			var running = JsonParser.parseString(start.body()).getAsJsonObject();
			String id = running.get("sessionId").getAsString();
			try {
				var ready = await(endpoint, target, id, "ready");
				assertEquals("running", ready.get("status").getAsString());
				assertEquals(403, teacher.post(endpoint, withoutCsrf(values(target, "preview-input", id, "bad"))).statusCode());
				assertEquals(400, teacher.post(endpoint, values(target, "preview-input", id, "bad\nline")).statusCode());
				assertEquals(200, teacher.post(endpoint, values(target, "preview-input", id, "synthetic")).statusCode());
				var completed = await(endpoint, target, id, "hello synthetic");
				while ("running".equals(completed.get("status").getAsString())) {
					Thread.sleep(50); completed = teacher.json(poll(endpoint, target, id)).getAsJsonObject();
				}
				assertEquals("succeeded", completed.get("status").getAsString());
				assertEquals("synthetic\n", completed.get("standardInput").getAsString());
				assertEquals(403, teacher.get(poll(endpoint, target, id)).statusCode());
			} finally { teacher.post(endpoint, values(target, "preview-cancel", id, "")); }
			var wait = teacher.post(endpoint, values(target, "preview-start", null, "input()\n"));
			assertEquals(200, wait.statusCode(), wait.body());
			id = JsonParser.parseString(wait.body()).getAsJsonObject().get("sessionId").getAsString();
			var cancel = teacher.post(endpoint, values(target, "preview-cancel", id, ""));
			assertEquals(200, cancel.statusCode(), cancel.body());
			assertEquals("cancelled", JsonParser.parseString(cancel.body()).getAsJsonObject().get("status").getAsString());
			assertEquals(403, teacher.get(poll(endpoint, target, id)).statusCode());
			assertEquals(413, teacher.post(endpoint, values(target, "preview-start", null, "x".repeat(65537))).statusCode());
		}
		assertEquals(before, snapshot(), "Copies must not mutate drafts, versions, submissions, evaluations, logs, activity or audit.");
	}
	@Test void targetOwnerSurfaceAndRevokedFeatureOrSchoolCannotOperateSessions() throws Exception {
		for (String surface : List.of("progress", "exercises")) {
			String endpoint = "/teacher/"+surface;
			var target = surface.equals("progress") ? progress : exercise;
			for (String action : List.of("preview-session", "preview-input", "preview-cancel")) {
				String id = JsonParser.parseString(teacher.post(endpoint, values(target, "preview-start", null, "input()\n")).body())
						.getAsJsonObject().get("sessionId").getAsString();
				String feature = surface.equals("progress") ? "task-progress" : "exercise-code-review";
				try (var c = Client.createConnection(); var s = c.createStatement()) {
					long owner = TeacherExerciseDatabaseTest.number(c, "SELECT user_id FROM users WHERE login_id='ex_teacher'");
					s.executeUpdate("UPDATE teacher_feature_permissions SET is_enabled=FALSE WHERE teacher_user_id="+owner+" AND feature_code='"+feature+"'");
					try {
						assertEquals(403, action.equals("preview-session") ? teacher.get(poll(endpoint, target, id)).statusCode()
								: teacher.post(endpoint, values(target, action, id, "no")).statusCode());
					} finally {
						s.executeUpdate("UPDATE teacher_feature_permissions SET is_enabled=TRUE WHERE teacher_user_id="+owner+" AND feature_code='"+feature+"'");
					}
					assertEquals(403, teacher.get(poll(endpoint, target, id)).statusCode(), "Revocation must release the owner's runner.");
				}
			}
			String id = JsonParser.parseString(teacher.post(endpoint, values(target, "preview-start", null, "input()\n")).body())
					.getAsJsonObject().get("sessionId").getAsString();
			try {
				var wrong = new HashMap<>(target);
				wrong.put(surface.equals("progress") ? "studentUserId" : "entryId", "99999999");
				assertEquals(404, teacher.get(poll(endpoint, wrong, id)).statusCode());
				assertEquals(200, teacher.get(poll(endpoint, target, id)).statusCode(), "Wrong target must not release the real owner's handle.");
				assertEquals(403, teacher.get(poll(surface.equals("progress") ? "/teacher/exercises" : "/teacher/progress",
						surface.equals("progress") ? exercise : progress, id)).statusCode());
				var outsider = new Browser(); outsider.login("ex_outsider");
				assertEquals(surface.equals("progress") ? 403 : 404,
						outsider.post(endpoint, values(target, "preview-cancel", id, "")).statusCode());
			} finally { teacher.post(endpoint, values(target, "preview-cancel", id, "")); }
			id = JsonParser.parseString(teacher.post(endpoint, values(target, "preview-start", null, "input()\n")).body())
					.getAsJsonObject().get("sessionId").getAsString();
			try (var c=Client.createConnection(); var s=c.createStatement()) {
				long owner = TeacherExerciseDatabaseTest.number(c, "SELECT user_id FROM users WHERE login_id='ex_teacher'");
				s.executeUpdate("UPDATE teacher_school_permissions SET access_status='disabled' WHERE teacher_user_id="+owner);
				try { assertEquals(404, teacher.get(poll(endpoint, target, id)).statusCode()); }
				finally { s.executeUpdate("UPDATE teacher_school_permissions SET access_status='enabled' WHERE teacher_user_id="+owner); }
				assertEquals(403, teacher.get(poll(endpoint, target, id)).statusCode());
			}
		}
	}
	private JsonObject await(String endpoint, Map<String,String> target, String id, String text) throws Exception {
		long deadline=System.nanoTime()+Duration.ofSeconds(8).toNanos();
		while (System.nanoTime()<deadline) {
			var value=teacher.json(poll(endpoint,target,id)).getAsJsonObject();
			if(value.get("standardOutput").getAsString().contains(text)
					|| value.getAsJsonArray("events").asList().stream().map(JsonElement::getAsJsonObject)
							.anyMatch(event -> event.get("text").getAsString().contains(text))) return value;
			assertEquals("running",value.get("status").getAsString()); Thread.sleep(50);
		}
		throw new AssertionError("Runner did not emit "+text);
	}
	private static String poll(String endpoint,Map<String,String> target,String id) {
		var values=new HashMap<>(target); values.put("view","preview-session"); values.put("sessionId",id); values.put("after","0");
		return endpoint+"?"+encode(values);
	}
	private static Map<String,String> values(Map<String,String> target,String action,String id,String value) {
		var values=new HashMap<>(target); values.put("action",action); values.put("sessionId",id==null?"":id); values.put("after","0");
		values.put(action.equals("preview-input")?"line":"code",value); return values;
	}
	private static Map<String,String> withoutCsrf(Map<String,String> values) { var copy=new HashMap<>(values);copy.put("csrfToken","invalid");return copy; }
	private static String snapshot() throws Exception {
		try(var c=Client.createConnection();var s=c.createStatement()) {
			var value=new StringBuilder(TeacherExerciseDatabaseTest.snapshot(c));
			for(String table:List.of("task_participations","audit_logs"))try(var r=s.executeQuery("SELECT * FROM "+table+" ORDER BY 1")) {
				while(r.next())for(int i=1;i<=r.getMetaData().getColumnCount();i++)value.append(Objects.toString(r.getObject(i),"NULL")).append('|');
			}
			return value.toString();
		}
	}
	private static String encode(Map<String,String> values) {
		return values.entrySet().stream().map(e->URLEncoder.encode(e.getKey(),StandardCharsets.UTF_8)+"="+URLEncoder.encode(e.getValue(),StandardCharsets.UTF_8)).collect(Collectors.joining("&"));
	}
	private static class Browser {
		final HttpClient client=HttpClient.newBuilder().cookieHandler(new CookieManager(null,CookiePolicy.ACCEPT_ALL)).connectTimeout(Duration.ofSeconds(5)).build();
		String csrf;
		HttpRequest.Builder request(String path) {return HttpRequest.newBuilder(URI.create("http://ppe-exercise-review-runtime:8080"+path)).header("Host","teacher.localhost:18090").timeout(Duration.ofSeconds(20));}
		HttpResponse<String> get(String path)throws Exception{return client.send(request(path).GET().build(),HttpResponse.BodyHandlers.ofString());}
		HttpResponse<String> post(String path,Map<String,String> values)throws Exception{
			var fields=new HashMap<>(values);fields.putIfAbsent("csrfToken",csrf);
			return client.send(request(path).header("Content-Type","application/x-www-form-urlencoded").POST(HttpRequest.BodyPublishers.ofString(encode(fields))).build(),HttpResponse.BodyHandlers.ofString());
		}
		JsonElement json(String path)throws Exception {var result=get(path);assertEquals(200,result.statusCode(),result.body());return JsonParser.parseString(result.body());}
		void login(String login)throws Exception{
			String path="/teacher/account/login";
			var match=Pattern.compile("name=\"csrfToken\"\\s+value=\"([^\"]+)\"").matcher(get(path).body());assertTrue(match.find());csrf=match.group(1);
			assertEquals(302,post(path,Map.of("loginId",login,"password",TeacherExerciseDatabaseTest.PASSWORD)).statusCode());
			match=Pattern.compile("data-csrf-token=\"([^\"]+)\"").matcher(get("/teacher/exercises").body());assertTrue(match.find());csrf=match.group(1);
		}
	}
}
