package control.teacher;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.io.ByteArrayOutputStream;
import java.io.ByteArrayInputStream;
import java.time.LocalDateTime;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.Test;
import control.auth.AuthenticatedUser;
import entity.*;
import entity.UserCredential.UserType;

class TeacherExerciseControlTest {
	@Test void rejectsRolesAndInvalidIdsBeforeDatabaseAccess() {
		var control = new TeacherExerciseControl();
		for (var role : List.of(UserType.STUDENT, UserType.ADMIN)) {
			var user = new AuthenticatedUser(1, "dummy", "dummy", role, false, "test");
			assertThrows(SecurityException.class, () -> control.list(user, TeacherExerciseFilter.empty()));
			assertThrows(SecurityException.class, () -> control.detail(user, 1, 1));
			assertThrows(SecurityException.class, () -> control.csv(user, TeacherExerciseFilter.empty()));
			assertThrows(SecurityException.class, () -> control.bulk(user, TeacherExerciseFilter.empty()));
			assertThrows(SecurityException.class, () -> control.download(user, 1, 1, 1L));
		}
		assertThrows(SecurityException.class, () -> control.detail(null, 1, 1));
		var teacher = new AuthenticatedUser(1, "dummy", "dummy", UserType.TEACHER, false, "test");
		assertThrows(IllegalArgumentException.class, () -> control.detail(teacher, 0, 1));
		assertThrows(IllegalArgumentException.class, () -> control.download(teacher, 1, 1, -1L));
	}
	@Test void filtersValidateAndMatchCurrentConsentAndSort() {
		assertThrows(IllegalArgumentException.class, () -> new TeacherExerciseFilter(-1L, null, "", "", "", ""));
		assertThrows(IllegalArgumentException.class, () -> new TeacherExerciseFilter(null, null, "withdrawn", "", "", ""));
		assertThrows(IllegalArgumentException.class, () -> new TeacherExerciseFilter(null, null, "", "", "bad", ""));
		assertThrows(IllegalArgumentException.class, () -> new TeacherExerciseFilter(null, null, "", "x".repeat(101), "", ""));
		var rows = List.of(row(1, "agreed", 3), row(2, "withdrawn", 1), row(3, "unconfirmed", 0));
		assertEquals(2, new TeacherExerciseFilter(null, null, "not_agreed", "", "", "").apply(rows).get(0).studentId());
		assertEquals(1, new TeacherExerciseFilter(1L, 1L, "", "S", "fileCount", "desc").apply(rows).get(0).studentId());
		assertEquals(0, new TeacherExerciseFilter(2L, null, "", "", "", "").apply(rows).size());
	}
	@Test void archivePreservesScopesPathsEmptyFoldersAndSavedContents() throws Exception {
		var entry = new StudentExerciseEntry(1, 1, null, StudentExerciseEntry.Type.FILE, "a.py", "a.py", "",
				"print('保存済み')\n", StudentExerciseEntry.Status.ACTIVE, LocalDateTime.of(2026,10,8,12,0));
		var folder = new StudentExerciseEntry(2, 1, null, StudentExerciseEntry.Type.FOLDER, "空", "空", "", null,
				StudentExerciseEntry.Status.ACTIVE, null);
		var detail = new TeacherExerciseDetail(row(1,"agreed",2), List.of(
				new TeacherExerciseDetail.Scope(1,"one",List.of(entry,folder)),
				new TeacherExerciseDetail.Scope(2,"two",List.of(entry))));
		var download = new ExerciseDownload(TeacherExerciseControl.archiveEntries(detail));
		var bytes = new ByteArrayOutputStream(); download.write(bytes);
		var paths = new java.util.HashSet<String>();
		try (var zip = new ZipInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
			java.util.zip.ZipEntry item;
			while ((item = zip.getNextEntry()) != null) {
				assertTrue(paths.add(item.getName()));
				if (!item.isDirectory()) assertEquals(entry.content(), new String(zip.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
			}
		}
		assertEquals(java.util.Set.of("s1/領域-1/a.py", "s1/領域-1/空/", "s1/領域-2/a.py"), paths);
		assertThrows(IllegalArgumentException.class, () -> TeacherExerciseControl.archiveEntries(
				new TeacherExerciseDetail(row(1,"agreed",0),List.of())));
		assertThrows(PythonExecutionInput.TooLargeException.class,
				() -> TeacherExerciseControl.checkArchiveLimit(java.util.Collections.nCopies(10_001, entry)));
		var full = new StudentExerciseEntry(3, 1, null, StudentExerciseEntry.Type.FILE, "a.py",
				"a".repeat(250)+"/"+"b".repeat(250)+"/"+"c".repeat(250)+"/"+"d".repeat(244)+".py",
				"", "print(1)", StudentExerciseEntry.Status.ACTIVE, null);
		assertEquals(1000,full.path().length());
		assertDoesNotThrow(() -> new ExerciseDownload(List.of(full)));
		var prefixed = TeacherExerciseControl.archiveEntries(new TeacherExerciseDetail(row(1,"agreed",1),
				List.of(new TeacherExerciseDetail.Scope(1,"one",List.of(full)))));
		assertThrows(IllegalArgumentException.class, () -> new ExerciseDownload(prefixed));
		assertDoesNotThrow(() -> new ExerciseDownload(prefixed, "exercise.zip", "application/zip", true, 3));
		assertThrows(IllegalArgumentException.class, () -> new ExerciseDownload(prefixed, "exercise.zip", "application/zip", true, 513));
		var large = new StudentExerciseEntry(4,1,null,StudentExerciseEntry.Type.FILE,"x.py","x.py","",
				"x".repeat(65536),StudentExerciseEntry.Status.ACTIVE,null);
		assertDoesNotThrow(() -> TeacherExerciseControl.checkArchiveLimit(java.util.Collections.nCopies(511,large)));
		assertThrows(PythonExecutionInput.TooLargeException.class, () -> TeacherExerciseControl.checkArchiveLimit(
				java.util.Collections.nCopies(512,large)));
	}
	private static TeacherExerciseRow row(long id, String consent, int files) {
		return new TeacherExerciseRow(id, "s"+id, 1, "school", 1, "class", files, files, "", consent);
	}
}
