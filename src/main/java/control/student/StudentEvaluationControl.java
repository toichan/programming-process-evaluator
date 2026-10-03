package control.student;

import java.sql.SQLException;
import java.util.Optional;

import control.auth.AuthenticatedUser;
import dao.StudentEvaluationDao;
import dao.EvaluationQueueDao;
import entity.StudentCodeLogPage;
import entity.StudentEvaluationPage;
import entity.UserCredential.UserType;

public final class StudentEvaluationControl {
	private final StudentEvaluationDao evaluationDao;

	public StudentEvaluationControl() {
		this(new StudentEvaluationDao());
	}

	StudentEvaluationControl(StudentEvaluationDao evaluationDao) {
		this.evaluationDao = evaluationDao;
	}

	public Optional<StudentEvaluationPage> loadEvaluation(
			AuthenticatedUser user,
			long assignmentId,
			Long submissionId) throws SQLException {
		requireStudent(user);
		if (assignmentId <= 0 || (submissionId != null && submissionId <= 0)) {
			throw new IllegalArgumentException("The requested evaluation is invalid.");
		}
		return evaluationDao.findEvaluationPage(user.userId(), assignmentId, submissionId);
	}

	public Optional<StudentCodeLogPage> loadCodeLogs(
			AuthenticatedUser user,
			long submissionId) throws SQLException {
		requireStudent(user);
		if (submissionId <= 0) {
			throw new IllegalArgumentException("The requested submission is invalid.");
		}
		return evaluationDao.findCodeLogPage(user.userId(), submissionId);
	}

	public boolean retryFailedEvaluation(
			AuthenticatedUser user,
			long assignmentId,
			long submissionId) throws SQLException {
		requireStudent(user);
		if (assignmentId <= 0 || submissionId <= 0) {
			throw new IllegalArgumentException("The requested evaluation is invalid.");
		}
		return EvaluationQueueDao.retryFailedEvaluation(user.userId(), assignmentId, submissionId);
	}

	private static void requireStudent(AuthenticatedUser user) {
		if (user == null || user.userType() != UserType.STUDENT) {
			throw new SecurityException("A student account is required.");
		}
	}
}
