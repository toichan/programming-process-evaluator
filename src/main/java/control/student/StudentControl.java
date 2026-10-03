package control.student;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

import control.auth.AuthenticatedUser;
import dao.StudentDao;
import entity.ConsentSaveResult;
import entity.ConsentStatus;
import entity.StudentAccountDetails;
import entity.StudentConsentPage;
import entity.StudentHomePage;
import entity.StudentTaskSummary;
import entity.UserCredential.UserType;

public final class StudentControl {
	private final StudentDao studentDao;

	public StudentControl() {
		this(new StudentDao());
	}

	StudentControl(StudentDao studentDao) {
		this.studentDao = studentDao;
	}

	public Optional<StudentHomePage> loadHome(AuthenticatedUser user) throws SQLException {
		requireStudent(user);
		Optional<StudentAccountDetails> account = studentDao.findAccountDetails(user.userId());
		if (account.isEmpty()) {
			return Optional.empty();
		}
		ConsentStatus consentStatus = studentDao.findConsentStatus(user.userId());
		List<StudentTaskSummary> tasks = studentDao.findPublishedTasks(user.userId(), consentStatus);
		return Optional.of(new StudentHomePage(account.get(), consentStatus, tasks));
	}

	public Optional<StudentAccountDetails> loadAccount(AuthenticatedUser user) throws SQLException {
		requireStudent(user);
		return studentDao.findAccountDetails(user.userId());
	}

	public Optional<StudentConsentPage> loadConsentPage(AuthenticatedUser user) throws SQLException {
		requireStudent(user);
		if (studentDao.findAccountDetails(user.userId()).isEmpty()) {
			return Optional.empty();
		}
		return Optional.of(studentDao.findConsentPage(user.userId()));
	}

	public ConsentSaveResult saveInitialConsent(
			AuthenticatedUser user,
			long documentId,
			String decision) throws SQLException {
		requireStudent(user);
		if (!"agree".equals(decision) && !"decline".equals(decision)) {
			return ConsentSaveResult.INVALID_DECISION;
		}
		ConsentStatus status = "agree".equals(decision) ? ConsentStatus.AGREED : ConsentStatus.DECLINED;
		return studentDao.saveInitialConsent(user.userId(), documentId, status);
	}

	public ConsentSaveResult saveConsent(
			AuthenticatedUser user,
			long documentId,
			String decision,
			long expectedResponseId,
			boolean changeConfirmed) throws SQLException {
		requireStudent(user);
		if (!"agree".equals(decision) && !"decline".equals(decision)) {
			return ConsentSaveResult.INVALID_DECISION;
		}
		ConsentStatus status = "agree".equals(decision) ? ConsentStatus.AGREED : ConsentStatus.DECLINED;
		return studentDao.saveConsent(user.userId(), documentId, status, expectedResponseId, changeConfirmed);
	}

	private static void requireStudent(AuthenticatedUser user) {
		if (user == null || user.userType() != UserType.STUDENT) {
			throw new SecurityException("Student authentication is required.");
		}
	}
}
