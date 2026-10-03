package lib.mysql;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.annotation.WebListener;

import control.evaluation.EvaluationWorker;

@WebListener
public final class DataSourceLifecycleListener implements ServletContextListener {
	private EvaluationWorker evaluationWorker;

	@Override
	public void contextInitialized(ServletContextEvent event) {
		Client.initialize();
		evaluationWorker = new EvaluationWorker();
		evaluationWorker.start();
	}

	@Override
	public void contextDestroyed(ServletContextEvent event) {
		if (evaluationWorker != null) {
			evaluationWorker.stop();
		}
		Client.closeDataSource();
	}
}
