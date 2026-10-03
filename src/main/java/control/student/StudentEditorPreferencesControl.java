package control.student;

import java.sql.SQLException;
import java.util.Objects;

import control.auth.AuthenticatedUser;
import dao.StudentEditorPreferencesDao;
import entity.EditorPreferences;
import entity.UserCredential.UserType;

public final class StudentEditorPreferencesControl {
	private final StudentEditorPreferencesDao preferencesDao;

	public StudentEditorPreferencesControl() {
		this(new StudentEditorPreferencesDao());
	}

	StudentEditorPreferencesControl(StudentEditorPreferencesDao preferencesDao) {
		this.preferencesDao = Objects.requireNonNull(preferencesDao, "preferencesDao");
	}

	public EditorPreferences load(AuthenticatedUser user) throws SQLException {
		requireStudent(user);
		return preferencesDao.findByUserId(user.userId());
	}

	public void save(AuthenticatedUser user, EditorPreferences preferences) throws SQLException {
		requireStudent(user);
		preferencesDao.save(user.userId(), Objects.requireNonNull(preferences, "preferences"));
	}

	private static void requireStudent(AuthenticatedUser user) {
		if (user == null || user.userType() != UserType.STUDENT || user.passwordChangeRequired()) {
			throw new SecurityException("A student account is required.");
		}
	}
}
