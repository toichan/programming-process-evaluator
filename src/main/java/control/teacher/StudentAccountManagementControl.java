package control.teacher;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import control.auth.AuthenticatedUser;
import control.auth.PasswordGenerator;
import control.auth.PasswordHasher;
import control.auth.PasswordPolicy;
import dao.StudentAccountManagementDao;
import dao.TeacherPermissionDao;
import entity.ManagedStudentAccount;
import entity.StudentAccountCreation;
import entity.UserCredential.UserType;
import lib.mysql.Client;

public final class StudentAccountManagementControl {
	private final StudentAccountManagementDao dao = new StudentAccountManagementDao();
	private final TeacherPermissionDao permissions = new TeacherPermissionDao();
	public record Target(long userId, long version) {
		public Target { if (userId < 1 || version < 1) throw new IllegalArgumentException("対象・更新版が不正です。"); }
	}
	public record ExportRow(ManagedStudentAccount account, String password) {}
	public record CreatedCredential(String loginId, String password) {}

	public List<ManagedStudentAccount> list(AuthenticatedUser teacher) throws SQLException {
		return transaction(teacher, connection -> dao.list(connection, teacher.userId()));
	}
	public List<Map<String, Object>> options(AuthenticatedUser teacher) throws SQLException {
		return transaction(teacher, connection -> dao.options(connection, teacher.userId()));
	}
	public Map<String, Object> detail(AuthenticatedUser teacher, long id) throws SQLException {
		if (id < 1) throw new IllegalArgumentException("生徒IDが不正です。");
		return transaction(teacher, connection -> Map.of("account", dao.lock(connection, teacher.userId(), id),
				"login", dao.history(connection, id, "login"), "credentials", dao.history(connection, id, "credentials"),
				"operations", dao.history(connection, id, "operations")));
	}

	public List<CreatedCredential> create(AuthenticatedUser teacher, StudentAccountCreation input) throws SQLException {
		requireTeacher(teacher);
		if (input == null) throw new IllegalArgumentException("作成するアカウント情報を指定してください。");
		TeacherCredentialCipher cipher = TeacherCredentialCipher.configured();
		return audited(teacher, "create", connection -> {
			permissions.requireAccountAuthorizedSchool(connection, teacher.userId(), input.schoolId());
			if (input.classroomId() > 0) permissions.requireAccountAuthorizedClass(connection, teacher.userId(), input.classroomId(), input.schoolId());
			long classroom = dao.resolveClass(connection, input);
			List<CreatedCredential> credentials = new ArrayList<>();
			for (int i = 0; i < input.count(); i++) {
				char[] password = PasswordGenerator.generate();
				try {
					String login = dao.nextLoginId(connection);
					long id = dao.create(connection, teacher.userId(), login, new PasswordHasher().hash(password), input.schoolId(), classroom);
					dao.saveCredential(connection, id, cipher.encrypt(id, password));
					dao.audit(connection, teacher.userId(), id, "create", true,
							"生徒ID: " + login + " / 学校ID: " + input.schoolId() + " / クラスID: " + classroom);
					credentials.add(new CreatedCredential(login, new String(password)));
				} finally {
					Arrays.fill(password, '\0');
				}
			}
			return List.copyOf(credentials);
		});
	}

	public String reveal(AuthenticatedUser teacher, Target target) throws SQLException {
		return audited(teacher, "reveal", connection -> {
			ManagedStudentAccount account = current(connection, teacher, target);
			if (!account.passwordAvailable()) throw new IllegalArgumentException("本人変更後または未保存のパスワードは確認できません。");
			String password = TeacherCredentialCipher.configured().decrypt(account.userId(), dao.encryptedCredential(connection, account.userId()));
			dao.audit(connection, teacher.userId(), account.userId(), "reveal", true, "確認可能な資格情報を表示");
			return password;
		});
	}

	public List<ExportRow> export(AuthenticatedUser teacher, Predicate<ManagedStudentAccount> filter) throws SQLException {
		return audited(teacher, "csv", connection -> {
			List<ExportRow> result = new ArrayList<>();
			for (var row : dao.list(connection, teacher.userId()).stream().filter(filter).toList()) {
				var account = dao.lock(connection, teacher.userId(), row.userId());
				String password = account.passwordAvailable()
						? TeacherCredentialCipher.configured().decrypt(account.userId(), dao.encryptedCredential(connection, account.userId())) : "";
				result.add(new ExportRow(account, password));
			}
			dao.audit(connection, teacher.userId(), null, "csv", true, "出力件数: " + result.size());
			return List.copyOf(result);
		});
	}

	public void change(AuthenticatedUser teacher, List<Target> targets, String action, char[] password) throws SQLException {
		if (targets == null || targets.isEmpty() || targets.size() > 200
				|| targets.stream().map(Target::userId).distinct().count() != targets.size()
				|| action == null
				|| !List.of("reset", "unlock", "suspend", "activate", "delete").contains(action)
				|| (!"delete".equals(action) && targets.size() != 1))
			throw new IllegalArgumentException("操作・選択対象が不正です。");
		if ("reset".equals(action) && !PasswordPolicy.isValid(password))
			throw new IllegalArgumentException("再設定パスワードは8〜32文字、半角4種類中3種類以上で指定してください。");
		audited(teacher, action, connection -> {
			List<ManagedStudentAccount> accounts = new ArrayList<>();
			for (Target target : targets.stream().sorted(Comparator.comparingLong(Target::userId)).toList())
				accounts.add(current(connection, teacher, target));
			for (var account : accounts) {
				if ("deleted".equals(account.status())) throw new IllegalArgumentException("削除済みの生徒は変更できません。");
				if ("reset".equals(action) && account.securityLevel() != 2) throw new IllegalArgumentException("再設定はレベル2の生徒だけに利用できます。");
				if ("suspend".equals(action) && !"active".equals(account.status())) throw new IllegalArgumentException("利用中の生徒だけ停止できます。");
				if ("activate".equals(action) && !"suspended".equals(account.status())) throw new IllegalArgumentException("停止中の生徒だけ解除できます。");
			}
			TeacherCredentialCipher cipher = "reset".equals(action) ? TeacherCredentialCipher.configured() : null;
			for (var account : accounts) {
				String status = switch (action) { case "suspend" -> "suspended"; case "activate" -> "active"; case "delete" -> "deleted"; default -> account.status(); };
				dao.change(connection, teacher.userId(), account.userId(), status,
						"reset".equals(action) ? new PasswordHasher().hash(password) : null,
						"reset".equals(action) || "unlock".equals(action));
				if (cipher != null) dao.saveCredential(connection, account.userId(), cipher.encrypt(account.userId(), password));
				dao.audit(connection, teacher.userId(), account.userId(), action, true,
						"生徒ID: " + account.loginId() + " / 状態: " + account.status() + " → " + status + " / 更新版: " + account.version() + " → " + (account.version() + 1));
			}
			return null;
		});
	}

	private ManagedStudentAccount current(Connection connection, AuthenticatedUser teacher, Target target) throws SQLException {
		var account = dao.lock(connection, teacher.userId(), target.userId());
		if (account.version() != target.version()) throw new IllegalArgumentException("更新されています。画面を再読み込みしてください。");
		return account;
	}
	private <T> T audited(AuthenticatedUser teacher, String action, Operation<T> operation) throws SQLException {
		try { return transaction(teacher, operation); }
		catch (SQLException | RuntimeException failure) {
			if (teacher != null && teacher.userType() == UserType.TEACHER) {
				try { transaction(teacher, connection -> { dao.audit(connection, teacher.userId(), null, action, false, "操作失敗・変更はロールバック済み"); return null; }); }
				catch (SQLException | RuntimeException auditFailure) {
					failure.addSuppressed(auditFailure);
					java.util.logging.Logger.getLogger(getClass().getName()).log(java.util.logging.Level.SEVERE, "Student management failure audit could not be saved.", auditFailure);
				}
			}
			throw failure;
		}
	}
	private <T> T transaction(AuthenticatedUser teacher, Operation<T> operation) throws SQLException {
		requireTeacher(teacher);
		try (var connection = Client.createConnection()) {
			connection.setAutoCommit(false);
			try {
				permissions.requireAccountManagementAccess(connection, teacher.userId());
				T result = operation.run(connection); connection.commit(); return result;
			} catch (SQLException | RuntimeException failure) { connection.rollback(); throw failure; }
		}
	}
	private static void requireTeacher(AuthenticatedUser teacher) {
		if (teacher == null || teacher.userType() != UserType.TEACHER) throw new SecurityException("Teacher authentication required.");
	}
	@FunctionalInterface private interface Operation<T> { T run(Connection connection) throws SQLException; }
}
