package control.evaluation;

import java.sql.SQLException;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

import dao.EvaluationWorkerDao;
import dao.ReevaluationJobDao;
import dao.ReevaluationJobDao.JobWorkItem;

public final class ReevaluationJobWorker implements Runnable {
	private static final Logger LOGGER = Logger.getLogger(ReevaluationJobWorker.class.getName());
	private static final long IDLE_WAIT_MILLIS = 1_000;

	private final ReevaluationJobDao repository;
	private final EvaluationWorkerDao evaluationDao;
	private volatile boolean running;
	private Thread thread;

	public ReevaluationJobWorker() {
		this(new ReevaluationJobDao(), new EvaluationWorkerDao());
	}

	ReevaluationJobWorker(ReevaluationJobDao repository, EvaluationWorkerDao evaluationDao) {
		this.repository = repository;
		this.evaluationDao = evaluationDao;
	}

	public synchronized void start() {
		if (running) {
			return;
		}
		running = true;
		thread = new Thread(this, "reevaluation-job-worker");
		thread.setDaemon(true);
		thread.start();
	}

	public synchronized void stop() {
		running = false;
		if (thread != null) {
			thread.interrupt();
			try {
				thread.join(65_000);
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
				if (!processNext()) {
					Thread.sleep(IDLE_WAIT_MILLIS);
				}
			} catch (SQLException | RuntimeException failure) {
				LOGGER.log(Level.SEVERE, "Reevaluation job materialization failed.", failure);
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
		Optional<JobWorkItem> next = repository.claimNextTarget();
		if (next.isEmpty()) {
			return false;
		}
		materialize(next.get());
		return true;
	}

	private void materialize(JobWorkItem item) throws SQLException {
		try {
			evaluationDao.materializeReevaluationTarget(item);
		} catch (SQLException failure) {
			try {
				repository.releaseForRetry(
						item.targetId(), "materialization_retry", "評価履歴の保存に失敗しました。処理を再試行します。");
			} catch (SQLException releaseFailure) {
				failure.addSuppressed(releaseFailure);
			}
			throw failure;
		} catch (IllegalArgumentException | IllegalStateException invalidStoredResult) {
			repository.markFailed(
					item.targetId(), item.jobId(), "invalid_stored_result",
					"保存済みの評価結果を履歴へ反映できませんでした。");
			LOGGER.log(Level.WARNING, "Stored reevaluation result failed validation: target=" + item.targetId(),
					invalidStoredResult);
		}
	}
}
