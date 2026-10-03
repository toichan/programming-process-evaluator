package control.student;

import java.sql.SQLException;
import java.util.Map;
import java.util.Optional;

import control.auth.AuthenticatedUser;
import dao.StudentSurveyDao;
import dao.StudentSurveyDao.SaveResult;
import entity.StudentSurveyPage;
import entity.UserCredential.UserType;

public final class StudentSurveyControl {
	private final StudentSurveyDao surveyDao;

	public StudentSurveyControl() {
		this(new StudentSurveyDao());
	}

	StudentSurveyControl(StudentSurveyDao surveyDao) {
		this.surveyDao = surveyDao;
	}

	public Optional<StudentSurveyPage> loadSurvey(
			AuthenticatedUser user,
			long assignmentId,
			long evaluationId,
			Long surveyId) throws SQLException {
		requireStudent(user);
		requirePositiveIds(assignmentId, evaluationId);
		if (surveyId != null && surveyId <= 0) {
			throw new IllegalArgumentException("The requested survey is invalid.");
		}
		return surveyDao.findSurveyPage(user.userId(), assignmentId, evaluationId, surveyId);
	}

	public SaveResult saveResponse(
			AuthenticatedUser user,
			long assignmentId,
			long surveyId,
			long evaluationId,
			Map<String, String[]> parameters,
			boolean submit) throws SQLException {
		requireStudent(user);
		requirePositiveIds(assignmentId, surveyId, evaluationId);
		return surveyDao.saveResponse(
				user.userId(), assignmentId, surveyId, evaluationId, parameters, submit);
	}

	private static void requirePositiveIds(long... ids) {
		for (long id : ids) {
			if (id <= 0) {
				throw new IllegalArgumentException("The requested survey is invalid.");
			}
		}
	}

	private static void requireStudent(AuthenticatedUser user) {
		if (user == null || user.userType() != UserType.STUDENT) {
			throw new SecurityException("A student account is required.");
		}
	}
}
