package dao;

import java.sql.SQLException;
import java.util.Optional;

import com.google.gson.JsonObject;

import dao.EvaluationWorkerDao.EvaluationInput;
import dao.EvaluationWorkerDao.EvaluationJob;

public interface EvaluationWorkRepository {
	Optional<EvaluationJob> claimNext() throws SQLException;

	EvaluationInput loadAndPersistInput(EvaluationJob job) throws SQLException;

	void recordResponse(long requestId, JsonObject rawResponse, JsonObject validatedOutput) throws SQLException;

	void complete(EvaluationJob job, JsonObject result) throws SQLException;

	void fail(EvaluationJob job, String safeErrorDetail, int retryCount) throws SQLException;

	void setRetryCount(long requestId, int retryCount) throws SQLException;
}
