package lib.mysql;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.annotation.WebListener;

import control.evaluation.EvaluationWorker;
import control.evaluation.ReevaluationJobWorker;
import control.evaluation.ReevaluationPreviewWorker;
import control.teacher.TeacherTaskPublicationWorker;
import control.teacher.TeacherDistributionWorker;

@WebListener
public final class DataSourceLifecycleListener implements ServletContextListener {
	private EvaluationWorker evaluationWorker;
	private ReevaluationPreviewWorker reevaluationPreviewWorker;
	private ReevaluationJobWorker reevaluationJobWorker;
	private TeacherTaskPublicationWorker teacherTaskPublicationWorker;
	private TeacherDistributionWorker teacherDistributionWorker;

	@Override
	public void contextInitialized(ServletContextEvent event) {
		Client.initialize();
		evaluationWorker = new EvaluationWorker();
		reevaluationPreviewWorker = new ReevaluationPreviewWorker();
		reevaluationJobWorker = new ReevaluationJobWorker();
		teacherTaskPublicationWorker = new TeacherTaskPublicationWorker();
		teacherDistributionWorker = new TeacherDistributionWorker();
		evaluationWorker.start();
		reevaluationPreviewWorker.start();
		reevaluationJobWorker.start();
		teacherTaskPublicationWorker.start();
		teacherDistributionWorker.start();
	}

	@Override
	public void contextDestroyed(ServletContextEvent event) {
		if (teacherDistributionWorker != null) {
			teacherDistributionWorker.stop();
		}
		if (teacherTaskPublicationWorker != null) {
			teacherTaskPublicationWorker.stop();
		}
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
