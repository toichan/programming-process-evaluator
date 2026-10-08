package servlet.journey;

import static org.junit.jupiter.api.Assertions.*;
import java.sql.Statement;
import java.util.Set;
import org.junit.jupiter.api.*;
import control.admin.TeacherAccountControl;
import control.auth.*;
import control.teacher.TeacherSelfAccountControl;
import entity.TeacherAccountInput;
import entity.UserCredential.UserType;
import lib.mysql.Client;

class LearningFlowFixtureTest {
	@Test
	void installsOnlySyntheticAccountsAndClassWithoutTasksOrEvaluations() throws Exception {
		LearningFlowSupport.guard();
		assertEquals(0, LearningFlowSupport.number("SELECT COUNT(*) FROM users"));
		try (var connection = Client.createConnection(); var statement = connection.createStatement()) {
			statement.execute("ALTER TABLE evaluations AUTO_INCREMENT = 100");
			String hash = new PasswordHasher().hash(LearningFlowSupport.PASSWORD.toCharArray());
			try (var insert = connection.prepareStatement("""
					INSERT INTO users(user_type,login_id,password_hash,display_name,account_status,created_at)
					VALUES(?,?,?,'Synthetic learning participant','active',NOW())
					""")) {
				for (String[] user : new String[][] {{"admin", "admin"}, {"student", "synthetic-learning-student"},
						{"student", "synthetic-learning-declined"}}) {
					insert.setString(1, user[0]); insert.setString(2, user[1]); insert.setString(3, hash);
					insert.executeUpdate();
				}
			}
			statement.executeUpdate("""
					INSERT INTO schools(school_code,name,security_level,school_status,created_at)
					VALUES('SYNTHETIC-LEARNING','Synthetic learning school',2,'active',NOW())
					""");
			statement.executeUpdate("""
					INSERT INTO classrooms(school_id,name,classroom_status,created_at)
					VALUES(1,'Synthetic first-year class','active',NOW())
					""");
			statement.executeUpdate("""
					INSERT INTO student_profiles(user_id,student_code,security_level,first_login_status,must_change_password,school_id)
					SELECT user_id,login_id,2,'completed',FALSE,1 FROM users WHERE user_type='student'
					""");
			statement.executeUpdate("""
					INSERT INTO student_class_memberships(student_user_id,classroom_id,membership_status,joined_at)
					SELECT user_id,1,'active',NOW() FROM users WHERE user_type='student'
					""");
		}
		var admin = new AuthenticatedUser(1, "admin", "Synthetic", UserType.ADMIN, false, "test");
		var control = new TeacherAccountControl();
		String initial = control.create(admin, new TeacherAccountInput("synthetic-learning-teacher", Set.of(1L),
				Set.of("task-management", "teacher-prompt-design", "account-management")));
		var teacher = control.list(admin).getFirst();
		var actor = new AuthenticatedUser(teacher.userId(), teacher.loginId(), "Synthetic", UserType.TEACHER, true, "test");
		assertEquals(TeacherSelfAccountControl.ChangeResult.SUCCESS,
				new TeacherSelfAccountControl().changePassword(actor, teacher.version(), initial.toCharArray(),
						LearningFlowSupport.PASSWORD.toCharArray(), LearningFlowSupport.PASSWORD.toCharArray(),
						RequestMetadata.from("127.0.0.1", "synthetic-learning-runtime")));
		assertEquals(0, LearningFlowSupport.number("SELECT COUNT(*) FROM tasks"));
		assertEquals(0, LearningFlowSupport.number("SELECT COUNT(*) FROM evaluations"));
	}
}
