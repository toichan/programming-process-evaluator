package dao;

import java.sql.SQLException;
import java.util.Optional;

import com.google.gson.JsonObject;

public interface ReevaluationPreviewWorkRepository {
	Optional<ReevaluationPreviewDao.PreviewWorkItem> claimNextTarget() throws SQLException;

	void markSucceeded(long targetId, long previewId, int attempts, JsonObject rawResponse, JsonObject result)
			throws SQLException;

	void markFailed(long targetId, long previewId, int attempts, String safeCode, String safeMessage)
			throws SQLException;

	int cleanupExpired() throws SQLException;
}
