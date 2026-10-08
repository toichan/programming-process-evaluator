package control.teacher;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import control.auth.AuthenticatedUser;
import control.student.PythonRunnerClient;
import dao.TeacherReviewDao;
import entity.*;
import entity.UserCredential.UserType;
import lib.mysql.Client;
import lib.web.CsvCells;

public final class TeacherReviewControl {
	private final TeacherReviewDao dao = new TeacherReviewDao();
	private final PreviewRunner runner;

	public TeacherReviewControl() {
		this((code, input) -> new PythonRunnerClient().executePreview(code, input));
	}
	TeacherReviewControl(PreviewRunner runner) { this.runner = java.util.Objects.requireNonNull(runner); }

	public List<TeacherReviewRow> list(AuthenticatedUser user, boolean evaluations, TeacherReviewFilter filter) throws SQLException {
		requireTeacher(user);
		return transaction(connection -> filter.apply(dao.rows(connection, user.userId(), evaluations)));
	}
	public TeacherReviewDetail detail(AuthenticatedUser user, boolean evaluations, long submissionId,
			Long evaluationId) throws SQLException {
		requireTeacher(user);
		StudentExerciseInput.requireId(submissionId);
		if (evaluationId != null) StudentExerciseInput.requireId(evaluationId);
		return transaction(connection -> dao.detail(connection, user.userId(), evaluations, submissionId, evaluationId));
	}
	public PythonExecutionResult preview(AuthenticatedUser user, long submissionId, String code, String standardInput)
			throws SQLException, IOException, InterruptedException {
		requireTeacher(user);
		StudentExerciseInput.requireId(submissionId);
		StudentExerciseInput.validateCode(code);
		StudentExerciseInput.validateStandardInput(standardInput);
		detail(user, false, submissionId, null);
		// Never hold a database transaction while running untrusted code; do not persist the copy.
		PythonExecutionResult result = runner.execute(code, standardInput);
		detail(user, false, submissionId, null);
		return result;
	}
	public String exportCsv(AuthenticatedUser user, TeacherReviewFilter filter) throws SQLException {
		requireTeacher(user);
		return transaction(connection -> {
			var rows = filter.apply(dao.rows(connection, user.userId(), true)).stream()
					.filter(row -> "agreed".equals(row.consent())).toList();
			StringBuilder csv = new StringBuilder("\uFEFF");
			append(csv, "生徒ID", "学校", "クラス", "課題", "難易度", "提出版", "提出日時", "評価履歴ID",
					"評価状態", "思考力・判断力・表現力", "主体的に学習に取り組む態度", "総合評価",
					"評価実施日時", "研究同意", "履歴区分");
			for (var row : rows) {
				append(csv, row.studentLoginId(), row.schoolName(), row.className(), row.taskName(),
						row.difficultyLabel(), row.revision(), row.submittedAt(), row.evaluationId(),
						row.evaluationStatus(), row.thinkingScore(), row.attitudeScore(), row.overallScore(),
						row.evaluatedAt(), row.consentLabel(), row.historyLabel());
			}
			dao.auditCsv(connection, user.userId(), rows.size());
			return csv.toString();
		});
	}
	private static void append(StringBuilder csv, Object... values) {
		for (int i = 0; i < values.length; i++) {
			if (i > 0) csv.append(',');
			csv.append(CsvCells.encode(values[i] == null ? "" : values[i].toString()));
		}
		csv.append("\r\n");
	}
	private static void requireTeacher(AuthenticatedUser user) {
		if (user == null || user.userType() != UserType.TEACHER) throw new SecurityException("Teacher authentication required.");
	}
	private static <T> T transaction(Read<T> read) throws SQLException {
		try (Connection connection = Client.createConnection()) {
			connection.setAutoCommit(false);
			try {
				T result = read.execute(connection);
				connection.commit();
				return result;
			} catch (SQLException | RuntimeException failure) {
				try { connection.rollback(); } catch (SQLException rollback) { failure.addSuppressed(rollback); }
				throw failure;
			}
		}
	}
	@FunctionalInterface private interface Read<T> { T execute(Connection connection) throws SQLException; }
	@FunctionalInterface interface PreviewRunner {
		PythonExecutionResult execute(String code, String input) throws IOException, InterruptedException;
	}
}
