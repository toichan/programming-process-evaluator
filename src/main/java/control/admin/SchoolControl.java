package control.admin;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

import control.auth.AuthenticatedUser;
import dao.SchoolDao;
import entity.SchoolDetails;
import entity.SchoolInput;
import entity.UserCredential.UserType;
import lib.mysql.Client;

public final class SchoolControl {
	private final SchoolDao dao = new SchoolDao();

	public List<SchoolDetails> loadSchools(AuthenticatedUser user) throws SQLException {
		requireAdmin(user);
		try (Connection connection = Client.createConnection()) {
			connection.setAutoCommit(false);
			try {
				dao.requireActiveAdmin(connection, user.userId());
				List<SchoolDetails> schools = dao.findAll(connection);
				connection.commit();
				return schools;
			} catch (SQLException | RuntimeException e) {
				connection.rollback();
				throw e;
			}
		}
	}

	public long save(AuthenticatedUser user, long id, long expectedVersion, SchoolInput input)
			throws SQLException {
		requireAdmin(user);
		if (input == null || id < 0 || expectedVersion < 0 || (id > 0 && expectedVersion == 0)) {
			throw new IllegalArgumentException("学校または更新情報が不正です。画面を読み込み直してください。");
		}
		try (Connection connection = Client.createConnection()) {
			connection.setAutoCommit(false);
			try {
				dao.requireActiveAdmin(connection, user.userId());
				SchoolDetails before = null;
				long savedId;
				if (id == 0) {
					savedId = dao.insert(connection, input);
				} else {
					before = dao.findForUpdate(connection, id)
							.orElseThrow(() -> new IllegalArgumentException("学校が見つかりません。"));
					if (before.version() != expectedVersion) {
						throw new IllegalArgumentException("学校情報が更新されています。画面を読み込み直してください。");
					}
					if (before.securityLevelLocked()
							&& !Integer.valueOf(input.securityLevel()).equals(before.securityLevel())) {
						throw new IllegalArgumentException("生徒登録後はセキュリティレベルを変更できません。");
					}
					dao.update(connection, id, input);
					savedId = id;
				}
				dao.audit(connection, user.userId(), savedId, id == 0 ? "create" : "update", before, input);
				connection.commit();
				return savedId;
			} catch (SQLException | RuntimeException e) {
				connection.rollback();
				throw e;
			}
		}
	}

	private static void requireAdmin(AuthenticatedUser user) {
		if (user == null || user.userType() != UserType.ADMIN) {
			throw new SecurityException("Admin authentication is required.");
		}
	}
}
