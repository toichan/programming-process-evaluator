package control.teacher;

import static org.junit.jupiter.api.Assertions.*;
import java.sql.*;
import java.util.*;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import control.auth.AuthenticatedUser;
import control.auth.PasswordHasher;
import dao.TeacherExerciseDao;
import entity.*;
import entity.UserCredential.UserType;
import lib.mysql.Client;

public class TeacherExerciseDatabaseTest {
	public static final String PASSWORD = "ExerciseDummy42!";
	public static void guard() {
		if (!"true".equals(System.getenv("TEACHER_EXERCISE_DB_TEST"))
				|| !"ppe-exercise-review-db".equals(System.getenv("DB_HOST"))
				|| !"ppe_exercise_review_test".equals(System.getenv("DB_NAME")))
			throw new IllegalStateException("Only the explicitly isolated exercise-review fixture is allowed.");
	}
	@Test void currentSavedTreesScopeDownloadsCsvAndRevocation() throws Exception {
		Assumptions.assumeTrue("true".equals(System.getenv("TEACHER_EXERCISE_DB_TEST")));
		guard();
		try (var c = Client.createConnection()) {
			assertEquals("ppe_exercise_review_test", c.getCatalog());
			if (number(c,"SELECT COUNT(*) FROM users") == 0) install(c);
		}
		var teacher = teacher("ex_teacher");
		var control = new TeacherExerciseControl();
		var rows = control.list(teacher, TeacherExerciseFilter.empty());
		assertEquals(4, rows.size());
		var saved = rows.stream().filter(row -> row.studentLoginId().equals("ex_saved")).findFirst().orElseThrow();
		assertEquals(3, saved.fileCount());
		assertEquals(5, saved.entryCount());
		assertFalse(saved.updatedAt().isEmpty());
		var detail = control.detail(teacher, saved.studentId(), saved.classroomId());
		assertEquals(2, detail.scopes().size());
		assertEquals(5, detail.scopes().stream().mapToInt(scope -> scope.entries().size()).sum());
		assertTrue(detail.scopes().stream().flatMap(scope -> scope.entries().stream())
				.noneMatch(entry -> entry.path().contains("hidden") || entry.content() != null && entry.content().contains("secret")));
		var empty = rows.stream().filter(row -> row.studentLoginId().equals("ex_empty")).findFirst().orElseThrow();
		assertEquals(0,empty.fileCount());
		assertEquals(0,empty.entryCount());
		assertTrue(control.detail(teacher, empty.studentId(), empty.classroomId()).scopes().isEmpty());
		var withdrawn = rows.stream().filter(row -> row.studentLoginId().equals("ex_withdrawn")).findFirst().orElseThrow();
		var withdrawnFile = control.detail(teacher, withdrawn.studentId(), withdrawn.classroomId()).scopes().get(0).entries().get(0);
		assertEquals("withdrawn", withdrawn.consent());
		assertFalse(control.download(teacher,withdrawn.studentId(),withdrawn.classroomId(),withdrawnFile.entryId()).archive());
		assertThrows(ExerciseNotFoundException.class, () -> control.detail(teacher, saved.studentId(), 999999));
		assertThrows(ExerciseNotFoundException.class, () -> control.detail(teacher("ex_outsider"), saved.studentId(), saved.classroomId()));
		assertThrows(SecurityException.class, () -> control.list(teacher("ex_denied"), TeacherExerciseFilter.empty()));
		String before;
		try (var c = Client.createConnection()) { before = snapshot(c); }
		var single = detail.scopes().get(0).entries().stream().filter(e -> e.type() == StudentExerciseEntry.Type.FILE).findFirst().orElseThrow();
		assertFalse(control.download(teacher,saved.studentId(),saved.classroomId(),single.entryId()).archive());
		assertThrows(ExerciseNotFoundException.class, () -> control.download(teacher,saved.studentId(),saved.classroomId(),999999L));
		var zip = control.download(teacher,saved.studentId(),saved.classroomId(),null);
		assertEquals(5,zip.entries().size());
		var bulk = control.bulk(teacher,TeacherExerciseFilter.empty());
		assertEquals(6, bulk.entries().size(), "Two class memberships must not duplicate student files.");
		assertEquals(6, bulk.entries().stream().map(StudentExerciseEntry::path).distinct().count());
		assertThrows(IllegalArgumentException.class, () -> control.bulk(teacher,
				new TeacherExerciseFilter(null,null,"","no-match","","")));
		String csv = control.csv(teacher,TeacherExerciseFilter.empty());
		assertTrue(csv.startsWith("\uFEFF")); assertTrue(csv.contains("ex_saved"));
		assertFalse(csv.contains("ex_withdrawn")); assertFalse(csv.contains("ex_empty"));
		try (var c = Client.createConnection()) {
			assertEquals(before, snapshot(c), "Read/export must not mutate exercise/log/evaluation data.");
			assertTrue(number(c,"SELECT COUNT(*) FROM audit_logs WHERE feature_code='exercise-code-review' AND action_type='csv_export'") >= 1);
			c.setAutoCommit(false);
			try {
				c.createStatement().executeUpdate("UPDATE teacher_school_permissions SET access_status='disabled' WHERE teacher_user_id="+teacher.userId());
				assertTrue(new TeacherExerciseDao().rows(c,teacher.userId()).isEmpty());
				assertThrows(ExerciseNotFoundException.class, () -> new TeacherExerciseDao().detail(c,teacher.userId(),saved.studentId(),saved.classroomId()));
			} finally { c.rollback(); }
			try {
				c.createStatement().executeUpdate("UPDATE teacher_feature_permissions SET is_enabled=FALSE WHERE teacher_user_id="+teacher.userId());
				assertThrows(SecurityException.class, () -> new TeacherExerciseDao().rows(c,teacher.userId()));
			} finally { c.rollback(); }
			try {
				c.createStatement().executeUpdate("UPDATE student_class_memberships SET membership_status='inactive' WHERE student_user_id="+saved.studentId());
				assertThrows(ExerciseNotFoundException.class, () -> new TeacherExerciseDao().detail(c,teacher.userId(),saved.studentId(),saved.classroomId()));
			} finally { c.rollback(); }
			try {
				assertThrows(SQLException.class, () -> c.createStatement().executeUpdate(
						"UPDATE student_profiles SET school_id=(SELECT school_id FROM schools WHERE school_code='ex-other') WHERE user_id="+saved.studentId()));
			} finally { c.rollback(); }
			for (String sql : List.of(
					"UPDATE classrooms SET classroom_status='inactive' WHERE classroom_id="+saved.classroomId(),
					"UPDATE schools SET school_status='inactive' WHERE school_id="+saved.schoolId(),
					"UPDATE users SET account_status='suspended' WHERE user_id="+saved.studentId())) {
				try {
					c.createStatement().executeUpdate(sql);
					assertThrows(ExerciseNotFoundException.class, () -> new TeacherExerciseDao().detail(c,teacher.userId(),saved.studentId(),saved.classroomId()));
				} finally { c.rollback(); }
			}
		}
	}
	public static AuthenticatedUser teacher(String login) throws SQLException {
		guard();
		try (var c = Client.createConnection(); var s = c.prepareStatement("SELECT user_id FROM users WHERE login_id=?")) {
			s.setString(1,login); try(var r = s.executeQuery()) { assertTrue(r.next()); return new AuthenticatedUser(r.getLong(1),login,login,UserType.TEACHER,false,"dummy"); }
		}
	}
	public static long number(Connection c,String sql) throws SQLException {
		try(var s=c.createStatement();var r=s.executeQuery(sql)){assertTrue(r.next());return r.getLong(1);}
	}
	public static String snapshot(Connection c) throws SQLException {
		var result = new StringBuilder();
		for (String table : List.of("student_exercises","student_exercise_entries","code_logs","code_executions","submissions","evaluations")) {
			try(var s=c.createStatement();var r=s.executeQuery("SELECT * FROM "+table+" ORDER BY 1")){
				while(r.next()) for(int i=1;i<=r.getMetaData().getColumnCount();i++) {
					Object value = r.getObject(i);
					result.append(value instanceof byte[] bytes ? HexFormat.of().formatHex(bytes) : Objects.toString(value,"NULL")).append('|');
				}
			}
		}
		return result.toString();
	}
	private static void install(Connection c) throws SQLException {
		c.setAutoCommit(false);
		try {
			String hash = new PasswordHasher().hash(PASSWORD.toCharArray());
			long school=insert(c,"INSERT INTO schools(school_code,name,school_status,security_level,created_at) VALUES ('ex-school','演習検証校','active',1,NOW())");
			long other=insert(c,"INSERT INTO schools(school_code,name,school_status,created_at) VALUES ('ex-other','担当外検証校','active',NOW())");
			long classroom=insert(c,"INSERT INTO classrooms(school_id,name,grade_name,classroom_status,created_at) VALUES (?,'A組','1年','active',NOW())",school);
			long second=insert(c,"INSERT INTO classrooms(school_id,name,grade_name,classroom_status,created_at) VALUES (?,'B組','1年','active',NOW())",school);
			long teacher=user(c,"ex_teacher","teacher",hash), outsider=user(c,"ex_outsider","teacher",hash), denied=user(c,"ex_denied","teacher",hash);
			user(c,"admin","admin",hash);
			for(long id:new long[]{teacher,outsider,denied}){
				insert(c,"INSERT INTO teacher_school_permissions(teacher_user_id,school_id,access_status,updated_by_user_id,updated_at) VALUES (?,?,'enabled',?,NOW())",id,id==outsider?other:school,teacher);
				insert(c,"INSERT INTO teacher_feature_permissions(teacher_user_id,feature_code,is_enabled,updated_by_user_id,updated_at) VALUES (?,'exercise-code-review',?,?,NOW())",id,id!=denied,teacher);
			}
			long student=user(c,"ex_saved","student",hash), withdrawn=user(c,"ex_withdrawn","student",hash), empty=user(c,"ex_empty","student",hash);
			for(long id:new long[]{student,withdrawn,empty}){
				insert(c,"INSERT INTO student_profiles(user_id,school_id,student_code,security_level,first_login_status,must_change_password) VALUES (?,?,?,1,'completed',FALSE)",id,school,"ex-"+id);
				insert(c,"INSERT INTO student_class_memberships(student_user_id,classroom_id,membership_status,joined_at) VALUES (?,?,'active',NOW())",id,classroom);
			}
			insert(c,"INSERT INTO student_class_memberships(student_user_id,classroom_id,membership_status,joined_at) VALUES (?,?,'active',NOW())",student,second);
			long document=insert(c,"INSERT INTO consent_document_versions(version_code,title,body,document_status,created_at) VALUES ('ex-v1','Dummy only','Synthetic','active',NOW())");
			for(long id:new long[]{student,withdrawn}) insert(c,"INSERT INTO consent_records(user_id,consent_document_version_id,consent_status,consented_at,updated_at) VALUES (?,?,'agreed',NOW(),NOW())",id,document);
			insert(c,"INSERT INTO consent_records(user_id,consent_document_version_id,consent_status,withdrawn_at,updated_at) VALUES (?,?,'withdrawn',NOW(),NOW())",withdrawn,document);
			long scope=scope(c,student,"通常演習"), extra=scope(c,student,"未統合演習"), archived=scope(c,student,"アーカイブ"), merged=scope(c,student,"統合済み");
			c.createStatement().executeUpdate("UPDATE student_exercises SET exercise_status='archived' WHERE student_exercise_id="+archived);
			c.createStatement().executeUpdate("UPDATE student_exercises SET merged_into_exercise_id="+scope+" WHERE student_exercise_id="+merged);
			long folder=entry(c,scope,null,"folder","基礎","基礎",null,"active");
			entry(c,scope,folder,"file","hello.py","基礎/hello.py","print('保存済み')\n","active");
			entry(c,scope,null,"folder","空フォルダ","空フォルダ",null,"active");
			entry(c,scope,null,"file","main.py","main.py","# <script>dummy</script>\nprint(2)\n","active");
			entry(c,extra,null,"file","main.py","main.py","print('別領域')\n","active");
			long trash=entry(c,scope,null,"folder","hidden","hidden",null,"trashed");
			entry(c,scope,trash,"file","secret.py","hidden/secret.py","secret","active");
			entry(c,scope,null,"file","deleted.py","deleted.py","secret","deleted");
			entry(c,archived,null,"file","secret.py","secret.py","secret","active");
			entry(c,merged,null,"file","secret.py","secret.py","secret","active");
			long deletedFolder=entry(c,scope,null,"folder","deleted","deleted",null,"deleted");
			entry(c,scope,deletedFolder,"file","secret.py","deleted/secret.py","secret","active");
			long withdrawnScope=scope(c,withdrawn,"演習");
			entry(c,withdrawnScope,null,"file","main.py","main.py","print('withdrawn')\n","active");
			c.commit();
		} catch(SQLException|RuntimeException failure){c.rollback();throw failure;}
	}
	private static long scope(Connection c,long student,String name)throws SQLException{
		return insert(c,"INSERT INTO student_exercises(student_user_id,exercise_origin,scope_name,exercise_status,save_status,created_at) VALUES (?,'student_created',?,'temporarily_saved','saved',NOW())",student,name);
	}
	private static long entry(Connection c,long scope,Long parent,String type,String name,String path,String content,String status)throws SQLException{
		return insert(c,"INSERT INTO student_exercise_entries(student_exercise_id,parent_entry_id,entry_type,name,path,current_content,entry_status,updated_at,created_at) VALUES (?,?,?,?,?,?,?,NOW(),NOW())",scope,parent,type,name,path,content,status);
	}
	private static long user(Connection c,String login,String role,String hash)throws SQLException{
		return insert(c,"INSERT INTO users(login_id,password_hash,display_name,user_type,account_status,created_at) VALUES (?,?,?,?,'active',NOW())",login,hash,login,role);
	}
	private static long insert(Connection c,String sql,Object...values)throws SQLException{
		try(var s=c.prepareStatement(sql,Statement.RETURN_GENERATED_KEYS)){for(int i=0;i<values.length;i++)s.setObject(i+1,values[i]);s.executeUpdate();try(var r=s.getGeneratedKeys()){return r.next()?r.getLong(1):0;}}
	}
}
