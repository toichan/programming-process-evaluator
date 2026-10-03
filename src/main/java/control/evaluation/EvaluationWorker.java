package control.evaluation;

import java.sql.SQLException;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import dao.EvaluationWorkRepository;
import dao.EvaluationWorkerDao.EvaluationInput;
import dao.EvaluationWorkerDao.EvaluationJob;

public final class EvaluationWorker implements Runnable {
	private static final Logger LOGGER = Logger.getLogger(EvaluationWorker.class.getName());
	private static final int MAX_ATTEMPTS = 3;
	private static final long IDLE_WAIT_MILLIS = 1_000;

	private final EvaluationWorkRepository repository;
	private final EvaluationProvider provider;
	private volatile boolean running;
	private Thread thread;

	public EvaluationWorker() {
		this(new dao.EvaluationWorkerDao(), new GeminiEvaluationClient());
	}

	EvaluationWorker(EvaluationWorkRepository repository, EvaluationProvider provider) {
		this.repository = repository;
		this.provider = provider;
	}

	public synchronized void start() {
		if (running) {
			return;
		}
		running = true;
		thread = new Thread(this, "student-evaluation-worker");
		thread.setDaemon(true);
		thread.start();
	}

	public synchronized void stop() {
		running = false;
		if (thread != null) {
			thread.interrupt();
			try {
				thread.join(65_000);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
			thread = null;
		}
	}

	@Override
	public void run() {
		while (running && !Thread.currentThread().isInterrupted()) {
			try {
				if (!processNext()) {
					Thread.sleep(IDLE_WAIT_MILLIS);
				}
			} catch (SQLException | RuntimeException e) {
				LOGGER.log(Level.SEVERE, "Evaluation queue processing failed.", e);
				try {
					Thread.sleep(IDLE_WAIT_MILLIS);
				} catch (InterruptedException interrupted) {
					Thread.currentThread().interrupt();
				}
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		}
	}

	boolean processNext() throws SQLException {
		Optional<EvaluationJob> next = repository.claimNext();
		if (next.isEmpty()) {
			return false;
		}
		process(next.get());
		return true;
	}

	private void process(EvaluationJob job) throws SQLException {
		try {
			processClaimedJob(job);
		} catch (SQLException e) {
			try {
				repository.fail(job, "Evaluation storage operation failed.", 0);
			} catch (SQLException failureError) {
				e.addSuppressed(failureError);
			}
			throw e;
		}
	}

	private void processClaimedJob(EvaluationJob job) throws SQLException {
		EvaluationInput input;
		try {
			input = repository.loadAndPersistInput(job);
		} catch (SQLException | RuntimeException e) {
			repository.fail(job, "Evaluation input could not be prepared.", 0);
			LOGGER.log(Level.WARNING, "Evaluation input preparation failed for evaluation " + job.evaluationId(), e);
			return;
		}

		for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
			JsonObject rawResponse = null;
			try {
				JsonObject payload = attempt == MAX_ATTEMPTS - 1
						? simplify(input.payload()) : input.payload();
				rawResponse = provider.generate(job.modelId(), payload);
				String outputText = provider.extractOutputText(rawResponse);
				JsonObject result = EvaluationResponseValidator.parseAndValidate(
						outputText, input.knownLogIds(), input.knownExecutionIds());
				repository.recordResponse(job.requestId(), rawResponse, result);
				repository.complete(job, result);
				return;
			} catch (EvaluationProviderException | IllegalArgumentException e) {
				if (rawResponse != null) {
					repository.recordResponse(job.requestId(), rawResponse, null);
				}
				if (Thread.currentThread().isInterrupted()) {
					repository.fail(job, "Evaluation worker was interrupted.", attempt);
					return;
				}
				if (e instanceof EvaluationProviderException providerException
						&& !providerException.isRetryable()) {
					repository.fail(job, safeFailureDetail(e), attempt);
					return;
				}
				int retryCount = Math.min(attempt + 1, MAX_ATTEMPTS - 1);
				if (attempt < MAX_ATTEMPTS - 1) {
					repository.setRetryCount(job.requestId(), retryCount);
				}
				if (attempt == MAX_ATTEMPTS - 1) {
					repository.fail(job, safeFailureDetail(e), retryCount);
					return;
				}
			}
		}
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

	private static String safeFailureDetail(Exception e) {
		if (e instanceof EvaluationProviderException providerException
				&& !providerException.isRetryable()) {
			return "Evaluation provider configuration is invalid.";
		}
		if (e instanceof IllegalArgumentException) {
			return "AI response did not satisfy the evaluation output schema.";
		}
		return "Evaluation provider request failed after retries.";
	}
}
