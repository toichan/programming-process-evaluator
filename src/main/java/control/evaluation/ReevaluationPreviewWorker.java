package control.evaluation;

import java.sql.SQLException;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import dao.ReevaluationPreviewDao;
import dao.ReevaluationPreviewWorkRepository;
import dao.ReevaluationPreviewDao.PreviewWorkItem;

public final class ReevaluationPreviewWorker implements Runnable {
	private static final Logger LOGGER = Logger.getLogger(ReevaluationPreviewWorker.class.getName());
	private static final int MAX_ATTEMPTS = EvaluationRetryPolicy.MAX_ATTEMPTS;
	private static final long IDLE_WAIT_MILLIS = 1_000;
	private static final long CLEANUP_INTERVAL_MILLIS = 60_000;

	private final ReevaluationPreviewWorkRepository repository;
	private final EvaluationProvider provider;
	private volatile boolean running;
	private Thread thread;
	private long lastCleanupAt;

	public ReevaluationPreviewWorker() {
		this(new ReevaluationPreviewDao(), new GeminiEvaluationClient());
	}

	public ReevaluationPreviewWorker(ReevaluationPreviewWorkRepository repository, EvaluationProvider provider) {
		this.repository = java.util.Objects.requireNonNull(repository);
		this.provider = java.util.Objects.requireNonNull(provider);
	}

	public synchronized void start() {
		if (running) {
			return;
		}
		running = true;
		thread = new Thread(this, "reevaluation-preview-worker");
		thread.setDaemon(true);
		thread.start();
	}

	public synchronized void stop() {
		running = false;
		if (thread != null) {
			thread.interrupt();
			try {
				thread.join(EvaluationRetryPolicy.REQUEST_TIMEOUT.toMillis() + 5_000);
			} catch (InterruptedException interrupted) {
				Thread.currentThread().interrupt();
			}
			thread = null;
		}
	}

	@Override
	public void run() {
		while (running && !Thread.currentThread().isInterrupted()) {
			try {
				boolean processed = processNext();
				cleanupExpiredIfDue();
				if (!processed) {
					Thread.sleep(IDLE_WAIT_MILLIS);
				}
			} catch (SQLException | RuntimeException failure) {
				LOGGER.log(Level.SEVERE, "Reevaluation preview queue processing failed.", failure);
				try {
					Thread.sleep(IDLE_WAIT_MILLIS);
				} catch (InterruptedException interrupted) {
					Thread.currentThread().interrupt();
				}
			} catch (InterruptedException interrupted) {
				Thread.currentThread().interrupt();
			}
		}
	}

	boolean processNext() throws SQLException {
		Optional<PreviewWorkItem> next = repository.claimNextTarget();
		if (next.isEmpty()) {
			return false;
		}
		process(next.get());
		return true;
	}

	private void process(PreviewWorkItem item) throws SQLException {
		Set<Long> knownLogIds;
		Set<Long> knownExecutionIds;
		try {
			knownLogIds = idsFrom(item.payload(), "log_id");
			knownExecutionIds = idsFrom(item.payload(), "execution_id");
		} catch (IllegalStateException invalidInput) {
			repository.markFailed(item.targetId(), item.previewId(), 0,
					"invalid_input", "評価入力の形式を確認できませんでした。");
			LOGGER.log(Level.WARNING, "Stored reevaluation preview input is invalid: target=" + item.targetId(),
					invalidInput);
			return;
		}
		EvaluationRetryPolicy retries = new EvaluationRetryPolicy();
		for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
			JsonObject rawResponse = null;
			try {
				JsonObject payload = item.payload().deepCopy();
				payload.getAsJsonObject("metadata").addProperty("request_id", item.requestId());
				if (attempt == MAX_ATTEMPTS - 1) {
					payload = simplify(payload);
				}
				rawResponse = provider.generate(item.modelId(), payload);
				String output = provider.extractOutputText(rawResponse);
				JsonObject validated = EvaluationResponseValidator.parseAndValidate(
						output, knownLogIds, knownExecutionIds);
				repository.markSucceeded(item.targetId(), item.previewId(), attempt + 1, rawResponse, validated);
				return;
			} catch (EvaluationProviderException | IllegalArgumentException failure) {
				String category = failure instanceof EvaluationProviderException providerFailure
						? "provider, HTTP " + providerFailure.getHttpStatusCode()
						: "invalid evaluation output";
				LOGGER.warning("Reevaluation preview attempt failed: preview=" + item.previewCode()
						+ ", target=" + item.targetId() + ", model=" + item.modelId()
						+ ", attempt=" + (attempt + 1) + ", category=" + category);
				if (Thread.currentThread().isInterrupted()) {
					repository.markFailed(item.targetId(), item.previewId(), attempt + 1,
							"worker_interrupted", "予測処理が中断されました。");
					return;
				}
				if (failure instanceof EvaluationProviderException providerFailure
						&& !providerFailure.isRetryable()) {
					repository.markFailed(item.targetId(), item.previewId(), attempt + 1,
							"provider_configuration", "予測サービスの設定を確認してください。");
					return;
				}
				if (attempt == MAX_ATTEMPTS - 1) {
					repository.markFailed(item.targetId(), item.previewId(), MAX_ATTEMPTS,
							failure instanceof IllegalArgumentException ? "invalid_response" : "provider_unavailable",
							failure instanceof IllegalArgumentException
									? "AI応答が評価形式に適合しませんでした。"
									: "予測サービスへの接続に失敗しました。");
				} else {
					try {
						retries.awaitNext(attempt, failure);
					} catch (EvaluationProviderException stopped) {
						repository.markFailed(item.targetId(), item.previewId(), attempt + 1,
								"retry_stopped", "予測の待機が中断されたか処理時間の上限を超えました。");
						return;
					}
				}
			}
		}
	}

	private void cleanupExpiredIfDue() throws SQLException {
		long now = System.currentTimeMillis();
		if (now - lastCleanupAt >= CLEANUP_INTERVAL_MILLIS) {
			repository.cleanupExpired();
			lastCleanupAt = now;
		}
	}

	private static Set<Long> idsFrom(JsonObject payload, String field) {
		Set<Long> ids = new HashSet<>();
		JsonArray logs = payload.getAsJsonArray("timeline_logs");
		if (logs == null) {
			throw new IllegalStateException("The reevaluation preview input is missing timeline logs.");
		}
		for (JsonElement log : logs) {
			if (!log.isJsonObject()) {
				throw new IllegalStateException("The reevaluation preview input contains an invalid log entry.");
			}
			JsonElement value = log.getAsJsonObject().get(field);
			if (value != null) {
				if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
					throw new IllegalStateException("The reevaluation preview input contains an invalid evidence ID.");
				}
				ids.add(value.getAsLong());
			}
		}
		return Set.copyOf(ids);
	}

	private static JsonObject simplify(JsonObject original) {
		JsonObject simplified = original.deepCopy();
		JsonObject prompt = simplified.getAsJsonObject("prompt_evaluation_settings");
		if (prompt != null) {
			prompt.remove("evaluation_examples");
		}
		JsonArray logs = simplified.getAsJsonArray("timeline_logs");
		if (logs != null) {
			for (JsonElement element : logs) {
				if (element.isJsonObject()) {
					JsonObject log = element.getAsJsonObject();
					log.remove("snapshot");
					log.remove("standard_input");
					log.remove("standard_output");
					log.remove("standard_error");
				}
			}
		}
		return simplified;
	}
}
