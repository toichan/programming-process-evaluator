package control.evaluation;

import java.sql.Connection;
import java.sql.SQLException;

import dao.EvaluationWorkerDao;
import dao.EvaluationWorkerDao.EvaluationJob;

public final class EvaluationInputBuilder {
	private EvaluationInputBuilder() {
	}

	public static EvaluationWorkerDao.EvaluationPayload build(
			Connection connection,
			EvaluationJob job,
			long rubricId,
			long promptVersionId,
			String actorRole) throws SQLException {
		return EvaluationWorkerDao.buildPayload(connection, job, rubricId, promptVersionId, actorRole);
	}

	public static EvaluationWorkerDao.EvaluationPayload build(
			Connection connection,
			long taskId,
			long submissionId,
			long studentUserId,
			long participationId,
			long rubricId,
			long promptVersionId,
			String modelId,
			String promptVersion,
			String rubricVersion,
			String consentStatus,
			String requestId,
			String actorRole) throws SQLException {
		EvaluationJob context = new EvaluationJob(
				0, 0, submissionId, studentUserId, participationId, taskId, modelId,
				promptVersion, rubricVersion, consentStatus, requestId);
		return EvaluationWorkerDao.buildPayload(
				connection, context, rubricId, promptVersionId, actorRole);
	}
}
