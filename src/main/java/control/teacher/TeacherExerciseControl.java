package control.teacher;

import java.sql.Connection;
import java.sql.SQLException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import control.auth.AuthenticatedUser;
import dao.TeacherExerciseDao;
import entity.*;
import entity.UserCredential.UserType;
import lib.mysql.Client;
import lib.web.CsvCells;

public final class TeacherExerciseControl {
	private final TeacherExerciseDao dao = new TeacherExerciseDao();
	public List<TeacherExerciseRow> list(AuthenticatedUser user, TeacherExerciseFilter filter) throws SQLException {
		requireTeacher(user);
		return transaction(c -> filter.apply(dao.rows(c, user.userId())));
	}
	public TeacherExerciseDetail detail(AuthenticatedUser user, long studentId, long classroomId) throws SQLException {
		requireTeacher(user);
		StudentExerciseInput.requireId(studentId);
		StudentExerciseInput.requireId(classroomId);
		return transaction(c -> dao.detail(c, user.userId(), studentId, classroomId));
	}
	public ExerciseDownload download(AuthenticatedUser user, long studentId, long classroomId, Long entryId)
			throws SQLException {
		requireTeacher(user);
		StudentExerciseInput.requireId(studentId);
		StudentExerciseInput.requireId(classroomId);
		if (entryId != null) StudentExerciseInput.requireId(entryId);
		return transaction(c -> {
			var detail = dao.detail(c, user.userId(), studentId, classroomId);
			if (entryId != null) {
				var file = detail.scopes().stream().flatMap(scope -> scope.entries().stream())
						.filter(entry -> entry.entryId() == entryId && entry.type() == StudentExerciseEntry.Type.FILE)
						.findFirst().orElseThrow(ExerciseNotFoundException::new);
				String name = detail.row().studentLoginId() + "_" + file.name();
				if (name.codePointCount(0, name.length()) > StudentExerciseInput.MAX_NAME_CHARACTERS) name = file.name();
				return new ExerciseDownload(List.of(file), name, "text/x-python; charset=UTF-8", false);
			}
			return new ExerciseDownload(archiveEntries(detail), detail.row().studentLoginId() + "_exercise.zip",
					"application/zip", true, archivePrefixAllowance(detail));
		});
	}
	public ExerciseDownload bulk(AuthenticatedUser user, TeacherExerciseFilter filter) throws SQLException {
		requireTeacher(user);
		return transaction(c -> {
			var entries = new ArrayList<StudentExerciseEntry>();
			int prefixAllowance = 0;
			var seen = new HashSet<Long>();
			for (var row : filter.apply(dao.rows(c, user.userId()))) {
				if (seen.add(row.studentId())) {
					var detail = dao.detail(c, user.userId(), row.studentId(), row.classroomId());
					if (detail.scopes().stream().allMatch(scope -> scope.entries().isEmpty())) continue;
					prefixAllowance = Math.max(prefixAllowance, archivePrefixAllowance(detail));
					entries.addAll(archiveEntries(detail));
					checkArchiveLimit(entries);
				}
			}
			if (entries.isEmpty()) throw new IllegalArgumentException("ダウンロード対象の項目がありません。");
			return new ExerciseDownload(entries, "exercise_files.zip", "application/zip", true, prefixAllowance);
		});
	}
	private static int archivePrefixAllowance(TeacherExerciseDetail detail) {
		return detail.row().studentLoginId().codePointCount(0, detail.row().studentLoginId().length()) + 1
				+ (detail.scopes().size() > 1 ? detail.scopes().stream()
						.mapToInt(scope -> ("領域-" + scope.exerciseId() + "/").length()).max().orElseThrow() : 0);
	}
	static List<StudentExerciseEntry> archiveEntries(TeacherExerciseDetail detail) {
		var entries = new ArrayList<StudentExerciseEntry>();
		for (var scope : detail.scopes()) {
			new ExerciseDownload(scope.entries());
			String prefix = detail.row().studentLoginId() + "/"
					+ (detail.scopes().size() > 1 ? "領域-" + scope.exerciseId() + "/" : "");
			for (var entry : scope.entries()) {
				entries.add(new StudentExerciseEntry(entry.entryId(), entry.exerciseId(), entry.parentEntryId(),
						entry.type(), entry.name(), prefix + entry.path(), entry.description(),
						entry.content(), entry.status(), entry.updatedAt()));
			}
		}
		checkArchiveLimit(entries);
		if (entries.isEmpty()) throw new IllegalArgumentException("ダウンロード対象の項目がありません。");
		return List.copyOf(entries);
	}
	static void checkArchiveLimit(List<StudentExerciseEntry> entries) {
		long bytes = 0;
		for (var entry : entries) {
			bytes += entry.path().getBytes(StandardCharsets.UTF_8).length;
			if (entry.content() != null) bytes += entry.content().getBytes(StandardCharsets.UTF_8).length;
		}
		if (entries.size() > 10_000 || bytes > 32L * 1024 * 1024)
			throw new PythonExecutionInput.TooLargeException("一括取得は32 MiB・10,000項目までです。条件を絞り込んでください。");
	}
	public String csv(AuthenticatedUser user, TeacherExerciseFilter filter) throws SQLException {
		requireTeacher(user);
		return transaction(c -> {
			var rows = filter.apply(dao.rows(c, user.userId())).stream()
					.filter(row -> "agreed".equals(row.consent())).toList();
			var csv = new StringBuilder("\uFEFF");
			append(csv, "生徒ID", "学校", "クラス", "ファイル数", "最終更新", "研究同意");
			for (var row : rows) append(csv, row.studentLoginId(), row.schoolName(), row.className(),
					Integer.toString(row.fileCount()), row.updatedAt().replace('T', ' '), row.consentLabel());
			dao.auditCsv(c, user.userId(), rows.size());
			return csv.toString();
		});
	}
	private static void append(StringBuilder csv, String... cells) {
		csv.append(java.util.Arrays.stream(cells).map(CsvCells::encode)
				.collect(java.util.stream.Collectors.joining(","))).append("\r\n");
	}
	private static void requireTeacher(AuthenticatedUser user) {
		if (user == null || user.userType() != UserType.TEACHER)
			throw new SecurityException("Teacher authentication required.");
	}
	private static <T> T transaction(Read<T> action) throws SQLException {
		try (Connection c = Client.createConnection()) {
			c.setAutoCommit(false);
			try {
				T result = action.execute(c);
				c.commit();
				return result;
			} catch (SQLException | RuntimeException failure) {
				try { c.rollback(); } catch (SQLException rollback) { failure.addSuppressed(rollback); }
				throw failure;
			}
		}
	}
	@FunctionalInterface private interface Read<T> { T execute(Connection c) throws SQLException; }
}
