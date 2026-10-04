package control.student;

import java.sql.SQLException;

import control.auth.AuthenticatedUser;
import dao.StandardRubricDao;
import entity.StandardRubric;
import entity.UserCredential.UserType;

public final class StudentRubricControl {
	public StandardRubric load(AuthenticatedUser user) throws SQLException {
		if (user == null || user.userType() != UserType.STUDENT || user.passwordChangeRequired()) {
			throw new SecurityException("A student account is required.");
		}
		return new StandardRubricDao().find()
				.orElseThrow(() -> new IllegalStateException("標準ルーブリックがまだ登録されていません。"));
	}
}
