package lib.mysql;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.annotation.WebListener;

import control.evaluation.EvaluationWorker;
import control.evaluation.ReevaluationJobWorker;
import control.evaluation.ReevaluationPreviewWorker;

@WebListener
public final class DataSourceLifecycleListener implements ServletContextListener {
	private EvaluationWorker evaluationWorker;
	private ReevaluationPreviewWorker reevaluationPreviewWorker;
	private ReevaluationJobWorker reevaluationJobWorker;

	@Override
	public void contextInitialized(ServletContextEvent event) {
		Client.initialize();
		evaluationWorker = new EvaluationWorker();
		reevaluationPreviewWorker = new ReevaluationPreviewWorker();
		reevaluationJobWorker = new ReevaluationJobWorker();
		evaluationWorker.start();
		reevaluationPreviewWorker.start();
		reevaluationJobWorker.start();
	}

	@Override
	public void contextDestroyed(ServletContextEvent event) {
		if (reevaluationJobWorker != null) {
			reevaluationJobWorker.stop();
		}
		if (reevaluationPreviewWorker != null) {
			reevaluationPreviewWorker.stop();
		}
		if (evaluationWorker != null) {
			evaluationWorker.stop();
		}
		Client.closeDataSource();
	}
}
