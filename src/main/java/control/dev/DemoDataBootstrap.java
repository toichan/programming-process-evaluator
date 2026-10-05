package control.dev;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import control.auth.PasswordHasher;
import control.auth.PasswordPolicy;
import control.student.StandardRubricSource;
import control.teacher.TeacherTaskInputValidator;
import dao.StandardRubricDao;
import dao.TeacherPermissionDao;
import dao.TeacherTaskDao;
import entity.EditorTestCase;
import entity.TeacherTaskInput;
import entity.TeacherTaskInput.ClassAssignmentInput;
import entity.TeacherTaskInput.Difficulty;
import entity.TeacherTaskInput.LateSubmissionPolicy;
import lib.mysql.Client;

public final class DemoDataBootstrap {
	private static final String EXPECTED_DATABASE = "programming_process_evaluator";
	private static final char[] PASSWORD_ALPHABET =
			"ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789!@#$%".toCharArray();
	private static final SecureRandom RANDOM = new SecureRandom();

	private DemoDataBootstrap() {
	}

	public static void main(String[] args) throws Exception {
		requireLocalDemoDatabase();
		var rubric = StandardRubricSource.parse(
				Files.readString(Path.of("docs/rubric/思考力・判断力・表現力_ルーブリック_0805.md")),
				Files.readString(Path.of("docs/rubric/主体的に学習に取り組む態度_ルーブリック_0805.md")));
		new StandardRubricDao().register(rubric);

		char[] password = generatedPassword();
		try {
			SeededDemo demo = createDemo(password);
			System.out.println("Synthetic demo data created in " + EXPECTED_DATABASE + ".");
			System.out.println("Teacher login ID: " + demo.loginId());
			System.out.println("Teacher password (shown once): " + new String(password));
			System.out.println("School: " + demo.schoolName() + " / class: " + demo.className());
		} finally {
			Arrays.fill(password, '\0');
		}
	}

	private static SeededDemo createDemo(char[] password) throws SQLException {
		try (Connection connection = Client.createConnection()) {
			if (!EXPECTED_DATABASE.equals(connection.getCatalog())) {
				throw new IllegalStateException("Refusing to seed an unexpected database.");
			}
			connection.setAutoCommit(false);
			try {
				requireNoDemoUsersOrSchools(connection);
				long teacherId = insertTeacher(connection, password);
				String schoolCode = "DEMO-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12)
						.toUpperCase(java.util.Locale.ROOT);
				String schoolName = "デモ学校";
				long schoolId = insertSchool(connection, schoolCode, schoolName);
				String className = "A組";
				long classroomId = insertClassroom(connection, schoolId, className);
				insertPermissions(connection, teacherId, schoolId);

				TeacherTaskInput input = new TeacherTaskInput(
						"数値の合計を計算する",
						"基本的な計算",
						Difficulty.BEGINNER,
						"入力された2つの整数を読み取り、その合計を出力してください。",
						"入力は整数2つです。",
						"標準入力から値を読み取り、合計を1行で表示します。",
						"",
						List.of("整数の入力", "加算結果の表示"),
						List.of(new EditorTestCase(0, "正の整数", "2 3", "5", 1)),
						List.of(),
						List.of(new ClassAssignmentInput(
								0, classroomId, LocalDateTime.now().plusDays(1), null,
								LateSubmissionPolicy.ALLOW)),
						schoolId);
				input = new TeacherTaskInputValidator().validateAndNormalize(input);
				TeacherPermissionDao permissions = new TeacherPermissionDao();
				permissions.requireTaskManagementAccess(connection, teacherId);
				permissions.requireAuthorizedSchool(connection, teacherId, schoolId);
				permissions.requireAuthorizedClass(connection, teacherId, classroomId, schoolId);
				long taskId = new TeacherTaskDao().insertDraft(connection, teacherId, input);
				new TeacherTaskDao().recordAudit(
						connection, teacherId, taskId, "create_draft", UUID.randomUUID().toString(),
						"合成デモ課題を作成", null, "{\"version\":1}");
				connection.commit();
				return new SeededDemo("teacher-demo", schoolName, className);
			} catch (SQLException | RuntimeException | Error failure) {
				connection.rollback();
				throw failure;
			}
		}
	}

	private static void requireLocalDemoDatabase() throws SQLException {
		String databaseHost = System.getenv("DB_HOST");
		if (!EXPECTED_DATABASE.equals(System.getenv("DB_NAME")) || !"db".equals(databaseHost)) {
			throw new IllegalStateException(
					"Demo seeding is restricted to the project Compose database and will not run elsewhere.");
		}
		try (Connection connection = Client.createConnection()) {
			if (!EXPECTED_DATABASE.equals(connection.getCatalog())) {
				throw new IllegalStateException("Refusing to seed an unexpected database.");
			}
			try (PreparedStatement statement = connection.prepareStatement("""
					SELECT COUNT(*) FROM information_schema.columns
					WHERE table_schema = ? AND table_name = 'tasks' AND column_name = 'school_id'
					""")) {
				statement.setString(1, EXPECTED_DATABASE);
				try (ResultSet rows = statement.executeQuery()) {
					if (!rows.next() || rows.getInt(1) != 1) {
						throw new IllegalStateException("V19 must be applied before seeding demo data.");
					}
				}
			}
		}
	}

	private static void requireNoDemoUsersOrSchools(Connection connection) throws SQLException {
		for (String table : List.of("users", "schools", "tasks")) {
			try (Statement statement = connection.createStatement();
					ResultSet rows = statement.executeQuery("SELECT COUNT(*) FROM `" + table + "`")) {
				if (!rows.next() || rows.getLong(1) != 0) {
					throw new IllegalStateException(
							"Demo database is not empty; refusing to add duplicate synthetic accounts or tasks.");
				}
			}
		}
	}

	private static long insertTeacher(Connection connection, char[] password) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement("""
				INSERT INTO users (user_type, login_id, password_hash, display_name, account_status, created_at)
				VALUES ('teacher', 'teacher-demo', ?, 'デモ教師', 'active', CURRENT_TIMESTAMP)
				""", Statement.RETURN_GENERATED_KEYS)) {
			statement.setString(1, new PasswordHasher().hash(password));
			statement.executeUpdate();
			try (ResultSet keys = statement.getGeneratedKeys()) {
				if (!keys.next()) {
					throw new SQLException("Synthetic teacher ID was not generated.");
				}
				return keys.getLong(1);
			}
		}
	}

	private static long insertSchool(Connection connection, String code, String name) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement("""
				INSERT INTO schools (school_code, name, school_status, security_level, created_at)
				VALUES (?, ?, 'active', 1, CURRENT_TIMESTAMP)
				""", Statement.RETURN_GENERATED_KEYS)) {
			statement.setString(1, code);
			statement.setString(2, name);
			statement.executeUpdate();
			try (ResultSet keys = statement.getGeneratedKeys()) {
				if (!keys.next()) {
					throw new SQLException("Synthetic school ID was not generated.");
				}
				return keys.getLong(1);
			}
		}
	}

	private static long insertClassroom(Connection connection, long schoolId, String name) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement("""
				INSERT INTO classrooms (school_id, name, grade_name, classroom_status, created_at)
				VALUES (?, ?, '1年', 'active', CURRENT_TIMESTAMP)
				""", Statement.RETURN_GENERATED_KEYS)) {
			statement.setLong(1, schoolId);
			statement.setString(2, name);
			statement.executeUpdate();
			try (ResultSet keys = statement.getGeneratedKeys()) {
				if (!keys.next()) {
					throw new SQLException("Synthetic class ID was not generated.");
				}
				return keys.getLong(1);
			}
		}
	}

	private static void insertPermissions(Connection connection, long teacherId, long schoolId)
			throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement("""
				INSERT INTO teacher_school_permissions
				  (teacher_user_id, school_id, access_status, updated_by_user_id, updated_at)
				VALUES (?, ?, 'enabled', ?, CURRENT_TIMESTAMP)
				""")) {
			statement.setLong(1, teacherId);
			statement.setLong(2, schoolId);
			statement.setLong(3, teacherId);
			statement.executeUpdate();
		}
		try (PreparedStatement statement = connection.prepareStatement("""
				INSERT INTO teacher_feature_permissions
				  (teacher_user_id, feature_code, is_enabled, updated_by_user_id, updated_at)
				VALUES (?, 'task-management', TRUE, ?, CURRENT_TIMESTAMP)
				""")) {
			statement.setLong(1, teacherId);
			statement.setLong(2, teacherId);
			statement.executeUpdate();
		}
	}

	private static char[] generatedPassword() {
		char[] password;
		do {
			password = new char[24];
			for (int index = 0; index < password.length; index++) {
				password[index] = PASSWORD_ALPHABET[RANDOM.nextInt(PASSWORD_ALPHABET.length)];
			}
			if (!PasswordPolicy.isValid(password)) {
				Arrays.fill(password, '\0');
			}
		} while (!PasswordPolicy.isValid(password));
		return password;
	}

	private record SeededDemo(String loginId, String schoolName, String className) {
	}
}
