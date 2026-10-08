package control.teacher;

import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
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
			requireExportSize(csv.toString().getBytes(StandardCharsets.UTF_8).length);
			dao.auditCsv(connection, user.userId(), rows.size());
			return csv.toString();
		});
	}
	public String exportSubmissionCsv(AuthenticatedUser user, TeacherReviewFilter filter) throws SQLException {
		requireTeacher(user);
		return transaction(connection -> {
			var rows = filter.apply(dao.rows(connection, user.userId(), false)).stream()
					.filter(row -> "agreed".equals(row.consent())).toList();
			StringBuilder csv = new StringBuilder("\uFEFF");
			append(csv, "生徒ID", "学校", "クラス", "課題名", "難易度", "一致件数", "提出日時", "研究同意", "提出ID", "提出版", "履歴区分");
			for (var row : rows) {
				append(csv, row.studentLoginId(), row.schoolName(), row.className(), row.taskName(), row.difficultyLabel(),
						row.matchedCases() + "/" + row.totalCases(), row.submittedAt(), row.consentLabel(),
						row.submissionId(), row.revision(), row.historyLabel());
				requireExportSize(csv.length());
			}
			requireExportSize(csv.toString().getBytes(StandardCharsets.UTF_8).length);
			dao.auditCsv(connection, user.userId(), rows.size(), false);
			return csv.toString();
		});
	}
	private static final int MAX_EXPORT_BYTES = 32 * 1024 * 1024;
	public record Download(String filename, byte[] content) {}
	public Download evaluationFile(AuthenticatedUser user, long submissionId, Long evaluationId, boolean logs) throws SQLException {
		return evaluationDownload(detail(user, true, submissionId, evaluationId), logs);
	}
	private static Download evaluationDownload(TeacherReviewDetail detail, boolean logs) {
		var json = new com.google.gson.Gson();
		Object payload = logs ? java.util.Map.of("row", detail.row(), "logs", detail.logs(), "submittedCode", detail.code()) : detail;
		byte[] content = json.toJson(payload).getBytes(StandardCharsets.UTF_8);
		requireExportSize(content.length);
		String base = filename(detail.row()).replaceFirst("\\.py$", "") + "_e"
				+ (detail.row().evaluationId() == null ? "none" : detail.row().evaluationId());
		return new Download(base + (logs ? "_logs.json" : "_evaluation.json"), content);
	}
	public Download evaluationZip(AuthenticatedUser user, TeacherReviewFilter filter, boolean logs) throws SQLException {
		requireTeacher(user);
		return transaction(connection -> {
			var rows = filter.apply(dao.rows(connection, user.userId(), true));
			if (rows.isEmpty()) throw new IllegalArgumentException("取得対象の評価履歴がありません。");
			var bytes = new ByteArrayOutputStream();
			long size = 0;
			try (var zip = new ZipOutputStream(bytes, StandardCharsets.UTF_8)) {
				for (var row : rows) {
					var download = evaluationDownload(dao.detail(connection, user.userId(), true, row.submissionId(), row.evaluationId()), logs);
					size += download.content().length;
					requireExportSize(size);
					zip.putNextEntry(new ZipEntry(download.filename()));
					zip.write(download.content());
					zip.closeEntry();
					requireExportSize(bytes.size());
				}
			} catch (IOException failure) { throw new SQLException("Evaluation archive generation failed.", failure); }
			requireExportSize(bytes.size());
			return new Download(logs ? "evaluation_logs.zip" : "evaluation_results.zip", bytes.toByteArray());
		});
	}
	public Download submissionFile(AuthenticatedUser user, long submissionId) throws SQLException {
		var detail = detail(user, false, submissionId, null);
		return new Download(filename(detail.row()), detail.code().getBytes(StandardCharsets.UTF_8));
	}
	public Download submissionZip(AuthenticatedUser user, TeacherReviewFilter filter) throws SQLException {
		requireTeacher(user);
		return transaction(connection -> {
			var rows = filter.apply(dao.rows(connection, user.userId(), false));
			if (rows.isEmpty()) throw new IllegalArgumentException("取得対象の提出がありません。");
			var bytes = new ByteArrayOutputStream();
			long size = 0;
			try (var zip = new ZipOutputStream(bytes, StandardCharsets.UTF_8)) {
				for (var row : rows) {
					byte[] code = dao.submittedCode(connection, user.userId(), row.submissionId()).getBytes(StandardCharsets.UTF_8);
					size += code.length;
					requireExportSize(size);
					zip.putNextEntry(new ZipEntry("submissions/" + filename(row)));
					zip.write(code);
					zip.closeEntry();
					requireExportSize(bytes.size());
				}
			} catch (IOException failure) { throw new SQLException("Submission archive generation failed.", failure); }
			requireExportSize(bytes.size());
			return new Download("submission_files.zip", bytes.toByteArray());
		});
	}
	static String filename(TeacherReviewRow row) {
		String student = row.studentLoginId().replaceAll("[^A-Za-z0-9_-]", "_");
		String task = row.taskName().replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}\\s]", "_");
		if (task.length() > 40) task = task.substring(0, 40);
		return student + "_" + task + "_r" + row.revision() + "_s" + row.submissionId() + ".py";
	}
	static void requireExportSize(long bytes) {
		if (bytes > MAX_EXPORT_BYTES)
			throw new entity.PythonExecutionInput.TooLargeException("取得対象は32 MiB以下に絞り込んでください。");
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
