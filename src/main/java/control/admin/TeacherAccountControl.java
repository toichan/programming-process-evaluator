package control.admin;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;

import com.google.gson.JsonObject;
import control.auth.AuthenticatedUser;
import control.auth.PasswordHasher;
import control.auth.PasswordGenerator;
import dao.SchoolDao;
import dao.TeacherAccountDao;
import entity.TeacherAccountDetails;
import entity.TeacherAccountHistory;
import entity.TeacherAccountInput;
import entity.UserCredential.UserType;
import lib.mysql.Client;

public final class TeacherAccountControl {
	private final TeacherAccountDao dao = new TeacherAccountDao();
	private final SchoolDao schools = new SchoolDao();

	public List<TeacherAccountDetails> list(AuthenticatedUser admin) throws SQLException {
		return transaction(admin, connection -> dao.findAll(connection));
	}

	public List<TeacherAccountHistory> history(AuthenticatedUser admin, Long id, boolean login) throws SQLException {
		if (id != null && id < 1) throw new IllegalArgumentException("教師IDが不正です。");
		return transaction(admin, connection -> {
			if (id != null) dao.lock(connection, id);
			return dao.history(connection, id, login);
		});
	}

	public String create(AuthenticatedUser admin, TeacherAccountInput input) throws SQLException {
		if (input == null) throw new IllegalArgumentException("教師情報を指定してください。");
		return issuePassword(admin, null, "create", input.loginId(), (connection, hash) -> {
			try {
				long id = dao.create(connection, admin.userId(), input, hash);
				dao.permissions(connection, admin.userId(), id, input);
				dao.credentialHistory(connection, admin.userId(), id, false);
				dao.audit(connection, admin.userId(), id, "create", null, snapshot(dao.lock(connection, id)));
			} catch (SQLException failure) {
				if (failure.getErrorCode() == 1062) throw new IllegalArgumentException("この教師IDは既に使用されています。");
				throw failure;
			}
		});
	}

	public String change(AuthenticatedUser admin, long id, long version, String action, TeacherAccountInput input)
			throws SQLException {
		if (id < 1 || version < 1 || !List.of("permissions", "suspend", "activate", "delete", "reset").contains(action)) {
			throw new IllegalArgumentException("操作または更新情報が不正です。");
		}
		if ("reset".equals(action)) {
			return issuePassword(admin, id, action, null, (connection, hash) -> update(connection, admin, id, version, action, input, hash));
		}
		auditedTransaction(admin, id, action, null,
				connection -> { update(connection, admin, id, version, action, input, null); return null; });
		return null;
	}

	private void update(Connection connection, AuthenticatedUser admin, long id, long version,
			String action, TeacherAccountInput input, String hash) throws SQLException {
		TeacherAccountDetails before = dao.lock(connection, id);
		if (before.version() != version) throw new IllegalArgumentException("更新されています。画面を再読み込みしてください。");
		if ("deleted".equals(before.status())) throw new IllegalArgumentException("削除済みの教師は変更できません。");
		String status = switch (action) {
			case "suspend" -> {
				if (!"active".equals(before.status())) throw new IllegalArgumentException("利用中の教師だけ停止できます。");
				yield "suspended";
			}
			case "activate" -> {
				if (!"suspended".equals(before.status())) throw new IllegalArgumentException("停止中の教師だけ解除できます。");
				yield "active";
			}
			case "delete" -> "deleted";
			default -> before.status();
		};
		if ("permissions".equals(action)) {
			if (input == null || !before.loginId().equals(input.loginId()))
				throw new IllegalArgumentException("教師IDは変更できません。");
			dao.permissions(connection, admin.userId(), id, input);
		}
		dao.update(connection, admin.userId(), id, status, hash);
		if ("reset".equals(action)) dao.credentialHistory(connection, admin.userId(), id, true);
		dao.audit(connection, admin.userId(), id, action, snapshot(before), snapshot(dao.lock(connection, id)));
	}

	private String issuePassword(AuthenticatedUser admin, Long id, String action, String loginId, PasswordOperation operation) throws SQLException {
		char[] password = PasswordGenerator.generate();
		try {
			String hash = new PasswordHasher().hash(password);
			auditedTransaction(admin, id, action, loginId, connection -> { operation.run(connection, hash); return null; });
			return new String(password);
		} finally {
			Arrays.fill(password, '\0');
		}
	}

	private <T> T auditedTransaction(AuthenticatedUser admin, Long id, String action, String loginId, Operation<T> operation)
			throws SQLException {
		try {
			return transaction(admin, operation);
		} catch (SQLException | RuntimeException failure) {
			if (admin != null && admin.userType() == UserType.ADMIN) {
				try {
					transaction(admin, connection -> { dao.failureAudit(connection, admin.userId(), id, action, loginId); return null; });
				} catch (SQLException | RuntimeException auditFailure) {
					failure.addSuppressed(auditFailure);
					java.util.logging.Logger.getLogger(TeacherAccountControl.class.getName())
							.log(java.util.logging.Level.SEVERE, "Teacher operation failure audit could not be saved.", auditFailure);
				}
			}
			throw failure;
		}
	}

	private <T> T transaction(AuthenticatedUser admin, Operation<T> operation) throws SQLException {
		if (admin == null || admin.userType() != UserType.ADMIN) throw new SecurityException("Admin authentication required.");
		try (Connection connection = Client.createConnection()) {
			connection.setAutoCommit(false);
			try {
				schools.requireActiveAdmin(connection, admin.userId());
				T result = operation.run(connection);
				connection.commit();
				return result;
			} catch (SQLException | RuntimeException failure) {
				connection.rollback(); throw failure;
			}
		}
	}

	private static String snapshot(TeacherAccountDetails account) {
		JsonObject data = new JsonObject();
		data.addProperty("teacherId", account.loginId()); data.addProperty("status", account.status());
		data.addProperty("version", account.version());
		var schoolIds = new com.google.gson.JsonArray();
		account.schools().forEach(school -> schoolIds.add(school.schoolId()));
		data.add("schoolIds", schoolIds);
		var features = new com.google.gson.JsonArray();
		account.features().stream().sorted().forEach(features::add); data.add("features", features);
		return data.toString();
	}
	@FunctionalInterface private interface Operation<T> { T run(Connection connection) throws SQLException; }
	@FunctionalInterface private interface PasswordOperation { void run(Connection connection, String hash) throws SQLException; }
}
