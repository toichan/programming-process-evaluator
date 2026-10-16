package lib.mysql;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.annotation.WebListener;

import control.evaluation.EvaluationWorker;
import control.evaluation.ReevaluationJobWorker;
import control.evaluation.ReevaluationPreviewWorker;
import control.student.StudentDueSoonReminderWorker;
import control.student.StudentSurveyProgressReminderWorker;
import control.teacher.TeacherTaskPublicationWorker;
import control.teacher.TeacherDistributionWorker;
import control.teacher.TeacherPromptGenerationQueue;

@WebListener
public final class DataSourceLifecycleListener implements ServletContextListener {
	private EvaluationWorker evaluationWorker;
	private ReevaluationPreviewWorker reevaluationPreviewWorker;
	private ReevaluationJobWorker reevaluationJobWorker;
	private TeacherTaskPublicationWorker teacherTaskPublicationWorker;
	private TeacherDistributionWorker teacherDistributionWorker;
	private StudentDueSoonReminderWorker studentDueSoonReminderWorker;
	private StudentSurveyProgressReminderWorker studentSurveyProgressReminderWorker;
	private TeacherPromptGenerationQueue teacherPromptGenerationQueue;

	@Override
	public void contextInitialized(ServletContextEvent event) {
		Client.initialize();
		evaluationWorker = new EvaluationWorker();
		reevaluationPreviewWorker = new ReevaluationPreviewWorker();
		reevaluationJobWorker = new ReevaluationJobWorker();
		teacherTaskPublicationWorker = new TeacherTaskPublicationWorker();
		teacherDistributionWorker = new TeacherDistributionWorker();
		teacherPromptGenerationQueue = new TeacherPromptGenerationQueue();
		event.getServletContext().setAttribute(
				TeacherPromptGenerationQueue.SERVLET_CONTEXT_ATTRIBUTE, teacherPromptGenerationQueue);
		studentDueSoonReminderWorker = new StudentDueSoonReminderWorker();
		studentSurveyProgressReminderWorker = new StudentSurveyProgressReminderWorker();
		evaluationWorker.start();
		reevaluationPreviewWorker.start();
		reevaluationJobWorker.start();
		teacherTaskPublicationWorker.start();
		teacherDistributionWorker.start();
		studentDueSoonReminderWorker.start();
		studentSurveyProgressReminderWorker.start();
	}

	@Override
	public void contextDestroyed(ServletContextEvent event) {
		if (studentDueSoonReminderWorker != null) {
			studentDueSoonReminderWorker.stop();
		}
		if (studentSurveyProgressReminderWorker != null) {
			studentSurveyProgressReminderWorker.stop();
		}
		if (teacherDistributionWorker != null) {
			teacherDistributionWorker.stop();
		}
		if (teacherPromptGenerationQueue != null) {
			teacherPromptGenerationQueue.stop();
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
