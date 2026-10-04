package control.student;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import control.auth.AuthenticatedUser;
import entity.ExerciseConflictException;
import entity.ExerciseNotFoundException;
import entity.ExerciseSaveResult;
import entity.StudentExerciseEntry.Type;
import entity.StudentExerciseEntry.Status;
import entity.StudentExerciseInput;
import entity.UserCredential.UserType;
import lib.mysql.Client;

class StudentExerciseDatabaseTest {
	private final StudentExerciseControl control = new StudentExerciseControl();
	private final List<Long> users = new ArrayList<>();
	private final List<Long> schools = new ArrayList<>();
	private AuthenticatedUser student;
	private AuthenticatedUser other;

	@BeforeEach
	void setup() throws SQLException {
		Assumptions.assumeTrue(Boolean.parseBoolean(System.getenv("EXERCISE_DB_TEST")));
		String database = System.getenv("DB_NAME");
		assertTrue(database != null && database.matches("ppe_exercise_test_[a-z0-9_]+"));
		try (Connection connection = Client.createConnection()) {
			assertEquals(database, connection.getCatalog());
			assertEquals(0, scalar(connection, "SELECT COUNT(*) FROM users"));
			long school = insert(connection, """
					INSERT INTO schools (school_code, name, security_level, school_status, created_at)
					VALUES (?, 'Synthetic exercise', 1, 'active', CURRENT_TIMESTAMP)
					""", "exercise-" + UUID.randomUUID());
			schools.add(school);
			student = addStudent(connection, school);
			other = addStudent(connection, school);
		}
	}

	@Test
	void unifiesExistingScopesOnlyAfterConfirmationAndPreservesAllIdentities() throws SQLException {
		var root = create(null, null, 0, Type.FOLDER, "授業");
		var original = create(root.exerciseId(), root.entryId(), 1, Type.FILE, "code");
		var trash = control.trash(student, root.exerciseId(), original.entryId(), 2);
		var replacement = create(root.exerciseId(), root.entryId(), 3, Type.FILE, "code");
		var saved = control.save(student, root.exerciseId(), replacement.entryId(), 4, "print('自作')");
		long source;
		long distributed;
		long earlier;
		long sourceRoot;
		try (Connection c = Client.createConnection()) {
			source = insert(c, """
					INSERT INTO student_exercises(student_user_id,exercise_origin,scope_name,
					  exercise_status,save_status,created_at) VALUES(?,'distribution','配信元','in_progress','saved',NOW())
					""", student.userId());
			sourceRoot = insert(c, """
					INSERT INTO student_exercise_entries(student_exercise_id,entry_type,name,path,entry_status,created_at)
					VALUES(?,'folder','授業','授業','active',NOW())
					""", source);
			distributed = insert(c, """
					INSERT INTO student_exercise_entries(student_exercise_id,parent_entry_id,entry_type,name,path,
					  current_content,entry_status,created_at) VALUES(?,?,'file','code.py','授業/code.py','print(9)','active',NOW())
					""", source, sourceRoot);
			earlier = insert(c, """
					INSERT INTO student_exercise_entries(student_exercise_id,entry_type,name,path,
					  current_content,entry_status,trash_root_entry_id,trashed_at,created_at)
					VALUES(?,'file','old.py','old.py','old','trashed',NULL,NOW(),NOW())
					""", source);
			execute(c, "UPDATE student_exercise_entries SET trash_root_entry_id=? WHERE exercise_entry_id=?", earlier, earlier);
			execute(c, """
					INSERT INTO code_executions(actor_user_id,exercise_entry_id,execution_context,source_code,
					  execution_status,standard_output_truncated,standard_error_truncated,executed_at)
					VALUES(?,?,'student_exercise','print(9)','succeeded',FALSE,FALSE,NOW())
					""", student.userId(), distributed);
		}
		assertEquals(2, control.loadPage(student, null, null).scopes().size());
		var preview = control.previewUnification(student);
		assertTrue(preview.required());
		assertEquals(root.exerciseId(), preview.targetExerciseId());
		assertEquals(6, preview.entryCount());
		assertTrue(preview.items().stream().anyMatch(i -> i.entryId() == sourceRoot
				&& i.newPath().equals("授業 (2)") && i.oldPath().equals("授業")));
		assertThrows(ExerciseConflictException.class, () -> control.unify(student, "0".repeat(64)));
		assertThrows(ExerciseConflictException.class, () -> control.unify(other, preview.token()));
		assertEquals(2, control.loadPage(student, null, null).scopes().size());
		var merged = control.unify(student, preview.token());
		assertEquals(saved.version() + 1, merged.version());
		var page = control.loadPage(student, source, distributed);
		assertEquals(root.exerciseId(), page.selectedExerciseId());
		assertEquals(1, page.scopes().size());
		assertEquals(6, page.entries().size());
		assertEquals("授業 (2)/code.py", page.entries().stream()
				.filter(e -> e.entryId() == distributed).findFirst().orElseThrow().path());
		assertEquals("print(9)", page.entries().stream().filter(e -> e.entryId() == distributed).findFirst().orElseThrow().content());
		assertNotNull(page.latestExecution());
		assertEquals(Status.TRASHED, page.entries().stream().filter(e -> e.entryId() == original.entryId()).findFirst().orElseThrow().status());
		assertEquals(Status.TRASHED, page.entries().stream().filter(e -> e.entryId() == earlier).findFirst().orElseThrow().status());
		assertEquals(4, control.loadDownload(student, root.exerciseId()).entries().size());
		assertEquals(4, control.loadDownload(student, source).entries().size());
		try (Connection c = Client.createConnection()) {
			assertEquals(source, scalar(c, "SELECT source_exercise_id FROM student_exercise_entries WHERE exercise_entry_id=?", distributed));
			assertEquals(root.exerciseId(), scalar(c, "SELECT merged_into_exercise_id FROM student_exercises WHERE student_exercise_id=?", source));
			assertEquals(2, scalar(c, "SELECT COUNT(*) FROM student_exercises WHERE student_user_id=?", student.userId()));
			assertEquals(1, scalar(c, "SELECT COUNT(*) FROM code_executions WHERE exercise_entry_id=?", distributed));
		}
		assertThrows(ExerciseNotFoundException.class,
				() -> control.save(student, source, distributed, merged.version(), "wrong stale link"));
		assertThrows(ExerciseConflictException.class, () -> control.unify(student, preview.token()));
		assertFalse(control.previewUnification(student).required());
		control.save(student, root.exerciseId(), distributed, merged.version(), "print(10)");
		var restored = control.restore(student, root.exerciseId(), earlier, merged.version() + 1);
		assertEquals(merged.version() + 2, restored.version());
	}

	@Test
	void unificationRejectsStalePreviewAndReadonlyScopeWithoutPartialWrites() throws SQLException {
		var first = create(null, null, 0, Type.FILE, "test");
		long source;
		try (Connection c = Client.createConnection()) {
			source = insert(c, """
					INSERT INTO student_exercises(student_user_id,exercise_origin,scope_name,
					  exercise_status,save_status,created_at) VALUES(?,'distribution','配信','in_progress','saved',NOW())
					""", student.userId());
		}
		var preview = control.previewUnification(student);
		control.save(student, first.exerciseId(), first.entryId(), 1, "new");
		assertThrows(ExerciseConflictException.class, () -> control.unify(student, preview.token()));
		var latest = control.previewUnification(student);
		assertThrows(ExerciseConflictException.class,
				() -> control.unify(student, latest.token(), first.exerciseId(), 1));
		try (Connection c = Client.createConnection()) {
			execute(c, "UPDATE student_exercises SET exercise_status='completed' WHERE student_exercise_id=?", source);
		}
		assertThrows(IllegalArgumentException.class, () -> control.previewUnification(student));
		assertEquals(2, control.loadPage(student, null, null).scopes().size());
		assertTaskDataUntouched();
	}

	@Test
	void unificationUsesDatabaseCollationAndRejectsOverlongCandidate() throws SQLException {
		var first = create(null, null, 0, Type.FILE, "Café.PY");
		create(first.exerciseId(), null, 1, Type.FILE, "cafe (2).py");
		long source;
		try (Connection c = Client.createConnection()) {
			source = insert(c, """
					INSERT INTO student_exercises(student_user_id,exercise_origin,scope_name,
					  exercise_status,save_status,created_at) VALUES(?,'distribution','配信','in_progress','saved',NOW())
					""", student.userId());
			insert(c, """
					INSERT INTO student_exercise_entries(student_exercise_id,entry_type,name,path,
					  current_content,entry_status,created_at) VALUES(?,'file','CAFE.py','CAFE.py','','active',NOW())
					""", source);
		}
		var preview = control.previewUnification(student);
		assertTrue(preview.items().stream().anyMatch(i -> i.oldPath().equals("CAFE.py") && i.newPath().equals("CAFE (3).py")));
		create(first.exerciseId(), null, 2, Type.FILE, "x".repeat(252));
		try (Connection c = Client.createConnection()) {
			insert(c, """
					INSERT INTO student_exercise_entries(student_exercise_id,entry_type,name,path,
					  current_content,entry_status,created_at) VALUES(?,'file',?,?,'','active',NOW())
					""", source, "x".repeat(252) + ".py", "x".repeat(252) + ".py");
		}
		assertThrows(IllegalArgumentException.class, () -> control.previewUnification(student));
		assertEquals(2, control.loadPage(student, null, null).scopes().size());
		assertTaskDataUntouched();
	}

	@Test
	void unificationRejectsDescendantOverflowAndKeepsDottedFoldersAndEmptyScopes() throws SQLException {
		var root = create(null, null, 0, Type.FOLDER, "a".repeat(255));
		long source;
		long folder;
		try (Connection c = Client.createConnection()) {
			source = insert(c, """
					INSERT INTO student_exercises(student_user_id,exercise_origin,scope_name,
					  exercise_status,save_status,created_at) VALUES(?,'distribution','配信','in_progress','saved',NOW())
					""", student.userId());
			folder = insert(c, """
					INSERT INTO student_exercise_entries(student_exercise_id,entry_type,name,path,entry_status,created_at)
					VALUES(?,'folder',?,?,'active',NOW())
					""", source, "a".repeat(255), "a".repeat(255));
		}
		assertThrows(IllegalArgumentException.class, () -> control.previewUnification(student));
		try (Connection c = Client.createConnection()) {
			execute(c, "UPDATE student_exercise_entries SET name='a',path='a' WHERE exercise_entry_id=?", root.entryId());
			execute(c, "UPDATE student_exercise_entries SET name='a',path='a' WHERE exercise_entry_id=?", folder);
		}
		Long parent = folder;
		String path = "a";
		try (Connection c = Client.createConnection()) {
			for (int i = 0; i < 3; i++) {
				String name = String.valueOf((char) ('b' + i)).repeat(255);
				path += "/" + name;
				parent = insert(c, """
						INSERT INTO student_exercise_entries(student_exercise_id,parent_entry_id,entry_type,name,path,
						  entry_status,created_at) VALUES(?,?,'folder',?,?,'active',NOW())
						""", source, parent, name, path);
			}
			insert(c, """
					INSERT INTO student_exercise_entries(student_exercise_id,parent_entry_id,entry_type,name,path,
					  current_content,entry_status,created_at) VALUES(?,?,'file',?,?,'','active',NOW())
					""", source, parent, "x".repeat(227) + ".py", path + "/" + "x".repeat(227) + ".py");
		}
		assertThrows(IllegalArgumentException.class, () -> control.previewUnification(student));
		try (Connection c = Client.createConnection()) {
			assertEquals(0, scalar(c, "SELECT COUNT(*) FROM student_exercises WHERE merged_into_exercise_id IS NOT NULL"));
			execute(c, "UPDATE student_exercise_entries SET parent_entry_id=NULL WHERE student_exercise_id=?", source);
			execute(c, "DELETE FROM student_exercise_entries WHERE student_exercise_id=?", source);
			insert(c, """
					INSERT INTO student_exercise_entries(student_exercise_id,entry_type,name,path,entry_status,created_at)
					VALUES(?,'folder','legacy.py','legacy.py','active',NOW())
					""", source);
		}
		var preview = control.previewUnification(student);
		var unified = control.unify(student, preview.token());
		assertTrue(control.loadPage(student, unified.exerciseId(), null).entries().stream()
				.anyMatch(e -> e.name().equals("legacy.py")));
		long empty;
		try (Connection c = Client.createConnection()) {
			empty = insert(c, """
					INSERT INTO student_exercises(student_user_id,exercise_origin,scope_name,
					  exercise_status,save_status,created_at) VALUES(?,'distribution','空','in_progress','saved',NOW())
					""", other.userId());
			insert(c, """
					INSERT INTO student_exercises(student_user_id,exercise_origin,scope_name,
					  exercise_status,save_status,created_at) VALUES(?,'distribution','空2','in_progress','saved',NOW())
					""", other.userId());
		}
		var emptyPreview = control.previewUnification(other);
		assertEquals(0, emptyPreview.entryCount());
		var emptyResult = control.unify(other, emptyPreview.token());
		assertEquals(empty, emptyResult.exerciseId());
		assertEquals(1, control.loadPage(other, null, null).scopes().size());
		assertEquals(1, create(root.exerciseId(), null, unified.version(), Type.FILE, "next").version() - unified.version());
		control.create(other, empty, null, emptyResult.version(), new StudentExerciseInput(Type.FILE, "distributed-root"));
		assertEquals(1, control.loadPage(other, null, null).scopes().size());
		assertTaskDataUntouched();
	}

	@Test
	void readsEmptyWithoutWritesAndPersistsCreationAndSave() throws SQLException {
		assertTrue(control.loadPage(student, null, null).entries().isEmpty());
		try (Connection c = Client.createConnection()) {
			assertEquals(0, scalar(c, "SELECT COUNT(*) FROM student_exercises"));
		}
		var folder = create(null, null, 0, Type.FOLDER, "練習");
		var file = create(folder.exerciseId(), folder.entryId(), folder.version(), Type.FILE, "hello");
		assertEquals(2, file.version());
		var saved = control.save(student, file.exerciseId(), file.entryId(), file.version(), "print('日本語')");
		try (Connection c = Client.createConnection()) {
			execute(c, """
					INSERT INTO user_editor_preferences (user_id, font_size_px, line_wrapping,
					  indent_width, editor_theme, updated_at)
					VALUES (?, 10, TRUE, 2, 'high_contrast', CURRENT_TIMESTAMP)
					""", student.userId());
		}
		var page = control.loadPage(student, file.exerciseId(), file.entryId());
		assertEquals(10, page.preferences().fontSizePx());
		assertTrue(page.preferences().lineWrapping());
		assertEquals("print('日本語')", page.entries().stream()
				.filter(e -> e.entryId() == file.entryId()).findFirst().orElseThrow().content());
		assertEquals("練習/hello.py", page.entries().stream()
				.filter(e -> e.entryId() == file.entryId()).findFirst().orElseThrow().path());
		var tree = entity.ExerciseTree.from(page);
		assertEquals(file.entryId(), tree.selectedEntryId());
		assertEquals("print('日本語')", tree.entries().stream()
				.filter(e -> e.entryId() == file.entryId()).findFirst().orElseThrow().content());
		assertTrue(page.scopes().get(0).saved());
		assertEquals("temporarily_saved", page.scopes().get(0).state().getValue());
		assertEquals(saved.version(), control.loadPage(student, null, null).scopes().get(0).version());
		assertTrue(control.loadPage(other, null, null).scopes().isEmpty());
		assertTaskDataUntouched();
	}

	@Test
	void rejectsOtherOwnerCrossScopeParentsAndFileParents() throws SQLException {
		var file = create(null, null, 0, Type.FILE, "first.py");
		assertThrows(ExerciseNotFoundException.class,
				() -> control.loadPage(other, file.exerciseId(), null));
		assertThrows(ExerciseNotFoundException.class,
				() -> control.save(other, file.exerciseId(), file.entryId(), file.version(), "bad"));
		assertThrows(ExerciseNotFoundException.class,
				() -> control.trash(other, file.exerciseId(), file.entryId(), file.version()));
		assertThrows(IllegalArgumentException.class,
				() -> create(file.exerciseId(), file.entryId(), file.version(), Type.FILE, "child.py"));
		var second = control.create(other, null, null, 0, new StudentExerciseInput(Type.FOLDER, "other"));
		assertThrows(ExerciseNotFoundException.class,
				() -> create(file.exerciseId(), second.entryId(), file.version(), Type.FILE, "child.py"));
		assertEquals(file.version(), control.loadPage(student, null, null).scopes().get(0).version());
	}

	@Test
	void renamesAndMovesHierarchyWithoutChangingCodeOrIndividualTrash() throws SQLException {
		var folder = create(null, null, 0, Type.FOLDER, "元");
		var child = create(folder.exerciseId(), folder.entryId(), 1, Type.FOLDER, "子");
		var file = create(folder.exerciseId(), child.entryId(), 2, Type.FILE, "code");
		var saved = control.save(student, folder.exerciseId(), file.entryId(), 3, "print('保存')");
		var trashed = control.trash(student, folder.exerciseId(), file.entryId(), saved.version());
		var renamed = control.rename(student, folder.exerciseId(), folder.entryId(), trashed.version(), "変更後");
		var page = control.loadPage(student, folder.exerciseId(), null);
		assertEquals("変更後/子/code.py", page.entries().stream()
				.filter(e -> e.entryId() == file.entryId()).findFirst().orElseThrow().path());
		assertEquals(Status.TRASHED, page.entries().stream()
				.filter(e -> e.entryId() == file.entryId()).findFirst().orElseThrow().status());
		var destination = create(folder.exerciseId(), null, renamed.version(), Type.FOLDER, "移動先");
		var moved = control.move(student, folder.exerciseId(), folder.entryId(), destination.version(),
				destination.entryId(), null);
		page = control.loadPage(student, folder.exerciseId(), null);
		var persisted = page.entries().stream().filter(e -> e.entryId() == file.entryId()).findFirst().orElseThrow();
		assertEquals("移動先/変更後/子/code.py", persisted.path());
		assertEquals("print('保存')", persisted.content());
		assertEquals(Status.TRASHED, persisted.status());
		var noop = control.move(student, folder.exerciseId(), folder.entryId(), moved.version(),
				destination.entryId(), null);
		assertEquals(moved.version(), noop.version());
		control.restore(student, folder.exerciseId(), file.entryId(), moved.version());
		assertTaskDataUntouched();
	}

	@Test
	void rejectsInvalidHierarchyChangesAndPreservesVersion() throws SQLException {
		var folder = create(null, null, 0, Type.FOLDER, "親");
		var child = create(folder.exerciseId(), folder.entryId(), 1, Type.FOLDER, "子");
		var file = create(folder.exerciseId(), child.entryId(), 2, Type.FILE, "code");
		var destination = create(folder.exerciseId(), null, 3, Type.FOLDER, "既存");
		assertThrows(IllegalArgumentException.class, () -> control.move(student, folder.exerciseId(),
				folder.entryId(), 4, child.entryId(), null));
		assertThrows(IllegalArgumentException.class, () -> control.move(student, folder.exerciseId(),
				folder.entryId(), 4, folder.entryId(), null));
		assertThrows(IllegalArgumentException.class, () -> control.move(student, folder.exerciseId(),
				folder.entryId(), 4, file.entryId(), null));
		assertThrows(ExerciseConflictException.class, () -> control.rename(student, folder.exerciseId(),
				folder.entryId(), 4, "既存"));
		assertThrows(ExerciseNotFoundException.class, () -> control.rename(other, folder.exerciseId(),
				folder.entryId(), 4, "他人"));
		assertThrows(ExerciseConflictException.class, () -> control.rename(student, folder.exerciseId(),
				folder.entryId(), 3, "古い"));
		assertThrows(IllegalArgumentException.class, () -> control.rename(student, folder.exerciseId(),
				folder.entryId(), 4, "bad.folder"));
		var longParent = create(folder.exerciseId(), null, 4, Type.FOLDER, "a".repeat(255));
		var next = create(folder.exerciseId(), longParent.entryId(), 5, Type.FOLDER, "b".repeat(255));
		var last = create(folder.exerciseId(), next.entryId(), 6, Type.FOLDER, "c".repeat(255));
		var largeName = create(folder.exerciseId(), null, 7, Type.FOLDER, "d".repeat(240));
		assertThrows(IllegalArgumentException.class, () -> control.move(student, folder.exerciseId(),
				largeName.entryId(), 8, last.entryId(), null));
		var shortFolder = create(folder.exerciseId(), null, 8, Type.FOLDER, "短");
		create(folder.exerciseId(), shortFolder.entryId(), 9, Type.FILE, "e".repeat(252));
		assertThrows(IllegalArgumentException.class, () -> control.move(student, folder.exerciseId(),
				shortFolder.entryId(), 10, last.entryId(), null));
		assertEquals(10, control.loadPage(student, folder.exerciseId(), null).scopes().get(0).version());
		assertEquals(destination.entryId(), control.loadPage(student, folder.exerciseId(), null).entries().stream()
				.filter(e -> e.path().equals("既存")).findFirst().orElseThrow().entryId());
	}

	@Test
	void renamesPythonFileAndRetainsExecutionIdentity() throws Exception {
		var file = create(null, null, 0, Type.FILE, "old");
		var result = new StudentExerciseControl((code, input) -> new PythonRunnerClient.TimedResult(
				new entity.PythonExecutionResult("succeeded", 0, "ok\n", "", false, false, null), 1))
				.runCode(student, file.exerciseId(), file.entryId(), "print('ok')", "");
		var renamed = control.rename(student, file.exerciseId(), file.entryId(), 1, "new.PY");
		var page = control.loadPage(student, file.exerciseId(), file.entryId());
		assertEquals("new.PY", page.entries().get(0).name());
		assertEquals(result.executionId(), page.latestExecution().executionId());
		var same = control.rename(student, file.exerciseId(), file.entryId(), renamed.version(), "new.PY");
		assertEquals(renamed.version(), same.version());
	}

	@Test
	void rejectsStaleVersionsAndActiveCollationDuplicatesButReusesTrashNames() throws SQLException {
		var file = create(null, null, 0, Type.FILE, "test.py");
		assertThrows(ExerciseConflictException.class,
				() -> create(null, null, 0, Type.FILE, "another.py"));
		assertThrows(ExerciseConflictException.class,
				() -> create(file.exerciseId(), null, file.version(), Type.FILE, "TEST"));
		var saved = control.save(student, file.exerciseId(), file.entryId(), file.version(), "original");
		assertThrows(ExerciseConflictException.class,
				() -> control.save(student, file.exerciseId(), file.entryId(), file.version(), "lost update"));
		var trashed = control.trash(student, file.exerciseId(), file.entryId(), saved.version());
		var replacement = create(file.exerciseId(), null, trashed.version(), Type.FILE, "TEST.py");
		assertThrows(ExerciseConflictException.class,
				() -> control.restore(student, file.exerciseId(), file.entryId(), replacement.version()));
		var restored = control.restore(student, file.exerciseId(), file.entryId(), replacement.version(),
				false, null, "test (2)");
		assertEquals("original", control.loadPage(student, restored.exerciseId(), restored.entryId()).entries()
				.stream().filter(e -> e.entryId() == file.entryId()).findFirst().orElseThrow().content());
		assertEquals("test (2).py", control.loadPage(student, restored.exerciseId(), restored.entryId()).entries()
				.stream().filter(e -> e.entryId() == file.entryId()).findFirst().orElseThrow().path());
	}

	@Test
	void uploadsAtomicallyAndKeepsSavedStructureWithOneVersion() throws SQLException {
		var uploaded = control.upload(student, null, null, 0, new entity.ExerciseUpload(List.of(
				new entity.ExerciseUpload.File("練習/a.py", "print('学')"),
				new entity.ExerciseUpload.File("練習/sub/b.PY", "print('b')"))));
		assertEquals(1, uploaded.version());
		var page = control.loadPage(student, uploaded.exerciseId(), null);
		assertEquals(4, page.entries().size());
		assertTrue(page.scopes().get(0).saved());
		assertEquals("print('学')", page.entries().stream()
				.filter(e -> e.path().equals("練習/a.py")).findFirst().orElseThrow().content());
		assertThrows(ExerciseConflictException.class, () -> control.upload(student, uploaded.exerciseId(), null,
				uploaded.version(), new entity.ExerciseUpload(List.of(
						new entity.ExerciseUpload.File("new/c.py", ""),
						new entity.ExerciseUpload.File("練習/A.py", "overwrite")))));
		page = control.loadPage(student, uploaded.exerciseId(), null);
		assertEquals(4, page.entries().size());
		assertEquals(1, page.scopes().get(0).version());
		assertThrows(ExerciseNotFoundException.class, () -> control.upload(other, uploaded.exerciseId(), null,
				uploaded.version(), new entity.ExerciseUpload(List.of(new entity.ExerciseUpload.File("x.py", "")))));
		assertThrows(ExerciseConflictException.class, () -> control.upload(student, uploaded.exerciseId(), null,
				0, new entity.ExerciseUpload(List.of(new entity.ExerciseUpload.File("x.py", "")))));
		var folder = page.entries().stream().filter(e -> e.path().equals("練習")).findFirst().orElseThrow();
		var second = control.upload(student, uploaded.exerciseId(), folder.entryId(), uploaded.version(),
				new entity.ExerciseUpload(List.of(new entity.ExerciseUpload.File("c.py", "saved"))));
		assertEquals(2, second.version());
		assertEquals("練習/c.py", control.loadPage(student, second.exerciseId(), second.entryId())
				.entries().stream().filter(e -> e.entryId() == second.entryId()).findFirst().orElseThrow().path());
		assertTaskDataUntouched();
	}

	@Test
	void batchMoveNormalizesSelectedAncestorsAndUpdatesVersionOnce() throws SQLException {
		var source = create(null, null, 0, Type.FOLDER, "source");
		var child = create(source.exerciseId(), source.entryId(), source.version(), Type.FILE, "child");
		var destination = create(source.exerciseId(), null, child.version(), Type.FOLDER, "destination");

		var moved = control.batchMove(student, source.exerciseId(), new entity.ExerciseBatchInput(
				List.of(source.entryId(), child.entryId()), destination.version(), destination.entryId(),
				java.util.Map.of(), List.of()));

		assertEquals(destination.version() + 1, moved.version());
		var entries = control.loadPage(student, moved.exerciseId(), null).entries();
		assertEquals("destination/source", entries.stream().filter(e -> e.entryId() == source.entryId())
				.findFirst().orElseThrow().path());
		assertEquals("destination/source/child.py", entries.stream().filter(e -> e.entryId() == child.entryId())
				.findFirst().orElseThrow().path());
		var noOp = control.batchMove(student, moved.exerciseId(), new entity.ExerciseBatchInput(
				List.of(source.entryId()), moved.version(), destination.entryId(), java.util.Map.of(), List.of()));
		assertEquals(moved.version(), noOp.version());
		assertThrows(ExerciseConflictException.class, () -> control.batchMove(student, moved.exerciseId(),
				new entity.ExerciseBatchInput(List.of(source.entryId()), moved.version() - 1,
						null, java.util.Map.of(), List.of())));
		assertThrows(IllegalArgumentException.class, () -> control.batchMove(student, moved.exerciseId(),
				new entity.ExerciseBatchInput(List.of(source.entryId()), noOp.version(),
						source.entryId(), java.util.Map.of(), List.of())));
		assertThrows(ExerciseNotFoundException.class, () -> control.batchMove(other, moved.exerciseId(),
				new entity.ExerciseBatchInput(List.of(source.entryId()), noOp.version(),
						null, java.util.Map.of(), List.of())));
		assertTaskDataUntouched();
	}

	@Test
	void batchMoveCollisionDoesNotLeaveEarlierSelectedItemsChanged() throws SQLException {
		var folder = create(null, null, 0, Type.FOLDER, "folder");
		var file = create(folder.exerciseId(), null, folder.version(), Type.FILE, "occupied");
		var destination = create(folder.exerciseId(), null, file.version(), Type.FOLDER, "destination");
		var collision = create(folder.exerciseId(), destination.entryId(), destination.version(), Type.FILE, "occupied");

		assertThrows(ExerciseConflictException.class, () -> control.batchMove(student, folder.exerciseId(),
				new entity.ExerciseBatchInput(List.of(folder.entryId(), file.entryId()), collision.version(),
						destination.entryId(), java.util.Map.of(), List.of())));
		var page = control.loadPage(student, folder.exerciseId(), null);
		assertEquals(collision.version(), page.scopes().get(0).version());
		assertEquals("folder", page.entries().stream().filter(e -> e.entryId() == folder.entryId())
				.findFirst().orElseThrow().path());
		assertEquals("occupied.py", page.entries().stream().filter(e -> e.entryId() == file.entryId())
				.findFirst().orElseThrow().path());
		assertTaskDataUntouched();
	}

	@Test
	void batchTrashAndRestoreAreAtomicAndRestoreSelectedParentsFirst() throws SQLException {
		var parent = create(null, null, 0, Type.FOLDER, "parent");
		var child = create(parent.exerciseId(), parent.entryId(), parent.version(), Type.FOLDER, "child");
		var file = create(parent.exerciseId(), child.entryId(), child.version(), Type.FILE, "saved");
		var independent = control.trash(student, parent.exerciseId(), child.entryId(), file.version());
		var trashedParent = control.batchTrash(student, parent.exerciseId(), new entity.ExerciseBatchInput(
				List.of(parent.entryId()), independent.version(), null, java.util.Map.of(), List.of()));
		assertEquals(independent.version() + 1, trashedParent.version());

		var restored = control.batchRestore(student, parent.exerciseId(), new entity.ExerciseBatchInput(
				List.of(child.entryId(), parent.entryId()), trashedParent.version(), null,
				java.util.Map.of(), List.of(
						new entity.ExerciseBatchInput.RestoreTarget(child.entryId(), false, null, "child (2)"),
						new entity.ExerciseBatchInput.RestoreTarget(parent.entryId(), false, null, "parent (2)"))));
		assertEquals(trashedParent.version() + 1, restored.version());
		var entries = control.loadPage(student, restored.exerciseId(), null).entries();
		assertEquals("parent (2)", entries.stream().filter(e -> e.entryId() == parent.entryId())
				.findFirst().orElseThrow().path());
		assertEquals("parent (2)/child (2)", entries.stream().filter(e -> e.entryId() == child.entryId())
				.findFirst().orElseThrow().path());
		assertEquals("parent (2)/child (2)/saved.py", entries.stream().filter(e -> e.entryId() == file.entryId())
				.findFirst().orElseThrow().path());
		assertEquals(Status.ACTIVE, entries.stream().filter(e -> e.entryId() == child.entryId())
				.findFirst().orElseThrow().status());
		assertTaskDataUntouched();
	}

	@Test
	void batchRestoreRollsBackEarlierRootsWhenLaterRootCollides() throws SQLException {
		var first = create(null, null, 0, Type.FILE, "first");
		var trashedFirst = control.trash(student, first.exerciseId(), first.entryId(), first.version());
		var second = create(first.exerciseId(), null, trashedFirst.version(), Type.FILE, "second");
		var trashedSecond = control.trash(student, second.exerciseId(), second.entryId(), second.version());
		var replacement = create(second.exerciseId(), null, trashedSecond.version(), Type.FILE, "second");

		assertThrows(ExerciseConflictException.class, () -> control.batchRestore(student, replacement.exerciseId(),
				new entity.ExerciseBatchInput(List.of(first.entryId(), second.entryId()), replacement.version(),
						null, java.util.Map.of(), List.of(
								new entity.ExerciseBatchInput.RestoreTarget(first.entryId(), false, null, null),
								new entity.ExerciseBatchInput.RestoreTarget(second.entryId(), false, null, null)))));
		var page = control.loadPage(student, replacement.exerciseId(), null);
		assertEquals(replacement.version(), page.scopes().get(0).version());
		assertEquals(Status.TRASHED, page.entries().stream().filter(e -> e.entryId() == first.entryId())
				.findFirst().orElseThrow().status());
		assertEquals(Status.TRASHED, page.entries().stream().filter(e -> e.entryId() == second.entryId())
				.findFirst().orElseThrow().status());
		assertTaskDataUntouched();
	}

	@Test
	void selectedDownloadAndDuplicateUseOnlyActiveSavedEntries() throws SQLException {
		var folder = create(null, null, 0, Type.FOLDER, "work");
		var file = create(folder.exerciseId(), folder.entryId(), folder.version(), Type.FILE, "code");
		var empty = create(folder.exerciseId(), folder.entryId(), file.version(), Type.FOLDER, "empty");
		var preview = control.duplicatePreview(student, folder.exerciseId(), List.of(folder.entryId()));
		assertEquals(1, preview.items().size());
		assertEquals("work (2)", preview.items().get(0).suggestedName());

		var duplicated = control.duplicate(student, folder.exerciseId(), new entity.ExerciseBatchInput(
				List.of(folder.entryId()), empty.version(), null,
				java.util.Map.of(folder.entryId(), preview.items().get(0).suggestedName()), List.of()));
		assertEquals(empty.version() + 1, duplicated.version());
		var entries = control.loadPage(student, duplicated.exerciseId(), null).entries();
		assertTrue(entries.stream().anyMatch(e -> e.path().equals("work (2)/code.py")
				&& e.content().isEmpty() && e.entryId() != file.entryId()));
		assertTrue(entries.stream().anyMatch(e -> e.path().equals("work (2)/empty")
				&& e.type() == Type.FOLDER && e.entryId() != empty.entryId()));
		var selection = control.loadDownload(student, duplicated.exerciseId(),
				List.of(folder.entryId(), file.entryId()));
		assertTrue(selection.archive());
		assertEquals("work.zip", selection.fileName());
		assertEquals(3, selection.entries().size());
		assertThrows(ExerciseNotFoundException.class, () -> control.loadDownload(student,
				duplicated.exerciseId(), List.of(folder.entryId(), Long.MAX_VALUE)));
		assertTaskDataUntouched();
	}

	@Test
	void uploadResolutionMergesFoldersAndRenamesFilesWithoutOverwriting() throws SQLException {
		var folder = create(null, null, 0, Type.FOLDER, "same");
		var existing = create(folder.exerciseId(), folder.entryId(), folder.version(), Type.FILE, "code");
		var saved = control.save(student, existing.exerciseId(), existing.entryId(), existing.version(), "original");
		var upload = new entity.ExerciseUpload(List.of(
				new entity.ExerciseUpload.File("same/code.py", "new"),
				new entity.ExerciseUpload.File("same/extra.py", "extra")));
		var preview = control.previewUpload(student, folder.exerciseId(), null, saved.version(), upload);
		assertTrue(preview.conflicts().stream().anyMatch(c -> c.path().equals("same")
				&& c.actions().contains("merge")));
		assertTrue(preview.conflicts().stream().anyMatch(c -> c.path().equals("same/code.py")
				&& c.actions().contains("rename")));
		var uploaded = control.upload(student, folder.exerciseId(), null, saved.version(), upload,
				List.of(new entity.ExerciseUploadResolution("same",
								entity.ExerciseUploadResolution.Action.MERGE, null),
						new entity.ExerciseUploadResolution("same/code.py",
								entity.ExerciseUploadResolution.Action.RENAME, "code (2).py")));
		assertEquals(saved.version() + 1, uploaded.version());
		var entries = control.loadPage(student, uploaded.exerciseId(), null).entries();
		assertEquals("original", entries.stream().filter(e -> e.entryId() == existing.entryId())
				.findFirst().orElseThrow().content());
		assertEquals("new", entries.stream().filter(e -> e.path().equals("same/code (2).py"))
				.findFirst().orElseThrow().content());
		assertTrue(entries.stream().anyMatch(e -> e.path().equals("same/extra.py")));
		assertTaskDataUntouched();
	}

	@Test
	void rollsBackCollidingFirstUploadAndReusesNamesWithoutUsingTrashedFolders() throws SQLException {
		assertThrows(ExerciseConflictException.class, () -> control.upload(student, null, null, 0,
				new entity.ExerciseUpload(List.of(new entity.ExerciseUpload.File("a.py", ""),
						new entity.ExerciseUpload.File("A.PY", "")))));
		assertTrue(control.loadPage(student, null, null).scopes().isEmpty());
		var folder = create(null, null, 0, Type.FOLDER, "folder");
		var trash = control.trash(student, folder.exerciseId(), folder.entryId(), folder.version());
		var uploaded = control.upload(student, folder.exerciseId(), null, trash.version(),
				new entity.ExerciseUpload(List.of(new entity.ExerciseUpload.File("folder/x.py", ""))));
		var entries = control.loadPage(student, folder.exerciseId(), null).entries();
		assertNotEquals(folder.entryId(), entries.stream()
				.filter(e -> e.path().equals("folder") && e.status() == Status.ACTIVE).findFirst().orElseThrow().entryId());
		assertThrows(ExerciseNotFoundException.class, () -> control.upload(student, folder.exerciseId(),
				folder.entryId(), uploaded.version(),
				new entity.ExerciseUpload(List.of(new entity.ExerciseUpload.File("y.py", "")))));
		try (Connection c = Client.createConnection()) {
			execute(c, "UPDATE student_exercise_entries SET name='old.py',path='old.py' WHERE exercise_entry_id=?",
					folder.entryId());
		}
		var restored = control.restore(student, folder.exerciseId(), folder.entryId(), uploaded.version());
		var child = create(folder.exerciseId(), folder.entryId(), restored.version(), Type.FILE, "child");
		assertEquals("old.py/child.py", control.loadPage(student, child.exerciseId(), child.entryId()).entries()
				.stream().filter(e -> e.entryId() == child.entryId()).findFirst().orElseThrow().path());
	}

	@Test
	void restoresDeletionBatchUnderAnotherNameAndKeepsIndependentTrash() throws SQLException {
		var parent = create(null, null, 0, Type.FOLDER, "授業");
		var old = create(parent.exerciseId(), parent.entryId(), parent.version(), Type.FILE, "old");
		var firstTrash = control.trash(student, parent.exerciseId(), old.entryId(), old.version());
		var replacement = create(parent.exerciseId(), parent.entryId(), firstTrash.version(), Type.FILE, "old");
		var saved = control.save(student, parent.exerciseId(), replacement.entryId(), replacement.version(), "print(12)");
		var deleted = control.trash(student, parent.exerciseId(), parent.entryId(), saved.version());
		var tree = entity.ExerciseTree.from(control.loadPage(student, parent.exerciseId(), null));
		assertEquals(2, tree.entries().stream().filter(e -> e.status().equals("trashed")).count());
		assertEquals(parent.entryId(), tree.entries().stream()
				.filter(e -> e.entryId() == replacement.entryId()).findFirst().orElseThrow().trashRootEntryId());
		var newParent = create(parent.exerciseId(), null, deleted.version(), Type.FOLDER, "授業");
		var incoming = create(parent.exerciseId(), newParent.entryId(), newParent.version(), Type.FILE, "old");
		assertThrows(ExerciseConflictException.class,
				() -> control.restore(student, parent.exerciseId(), parent.entryId(), incoming.version()));
		assertThrows(IllegalArgumentException.class, () -> control.restore(student, parent.exerciseId(),
				old.entryId(), incoming.version(), true, null, "another"));
		var restored = control.restore(student, parent.exerciseId(), parent.entryId(), incoming.version(),
				false, null, "授業 (2)");
		var page = control.loadPage(student, parent.exerciseId(), replacement.entryId());
		assertEquals("print(12)", page.entries().stream()
				.filter(e -> e.entryId() == replacement.entryId()).findFirst().orElseThrow().content());
		assertEquals("授業 (2)/old.py", page.entries().stream()
				.filter(e -> e.entryId() == old.entryId()).findFirst().orElseThrow().path());
		assertEquals(Status.TRASHED, page.entries().stream()
				.filter(e -> e.entryId() == old.entryId()).findFirst().orElseThrow().status());
		assertThrows(ExerciseConflictException.class,
				() -> control.restore(student, parent.exerciseId(), old.entryId(), restored.version()));
		var last = control.restore(student, parent.exerciseId(), old.entryId(), restored.version(), false, null, "old (2)");
		assertEquals(restored.version() + 1, last.version());
		assertEquals(5, control.loadDownload(student, parent.exerciseId()).entries().size());
		assertTaskDataUntouched();
	}

	@Test
	void restoresWithExplicitDestinationOnlyWhenOriginalParentIsUnrecoverable() throws SQLException {
		var parent = create(null, null, 0, Type.FOLDER, "元");
		var file = create(parent.exerciseId(), parent.entryId(), parent.version(), Type.FILE, "code");
		var deleted = control.trash(student, parent.exerciseId(), file.entryId(), file.version());
		try (Connection c = Client.createConnection()) {
			execute(c, "UPDATE student_exercise_entries SET entry_status='deleted' WHERE exercise_entry_id=?", parent.entryId());
		}
		assertTrue(control.loadPage(student, parent.exerciseId(), null).entries().stream()
				.anyMatch(e -> e.entryId() == file.entryId()));
		assertThrows(IllegalArgumentException.class,
				() -> control.restore(student, parent.exerciseId(), file.entryId(), deleted.version()));
		assertThrows(ExerciseNotFoundException.class, () -> control.restore(other, parent.exerciseId(),
				file.entryId(), deleted.version(), true, null, null));
		var restored = control.restore(student, parent.exerciseId(), file.entryId(), deleted.version(), true, null, null);
		assertEquals("code.py", control.loadPage(student, parent.exerciseId(), file.entryId()).entries()
				.stream().filter(e -> e.entryId() == file.entryId()).findFirst().orElseThrow().path());
		assertEquals(deleted.version() + 1, restored.version());
		var outer = create(parent.exerciseId(), null, restored.version(), Type.FOLDER, "outer");
		var nested = create(parent.exerciseId(), outer.entryId(), outer.version(), Type.FOLDER, "nested");
		var child = create(parent.exerciseId(), nested.entryId(), nested.version(), Type.FILE, "child");
		var batch = control.trash(student, parent.exerciseId(), nested.entryId(), child.version());
		try (Connection c = Client.createConnection()) {
			execute(c, "SET FOREIGN_KEY_CHECKS=0");
			try {
				execute(c, "DELETE FROM student_exercise_entries WHERE exercise_entry_id=?", outer.entryId());
			} finally {
				execute(c, "SET FOREIGN_KEY_CHECKS=1");
			}
		}
		assertTrue(control.loadPage(student, parent.exerciseId(), null).entries().stream()
				.anyMatch(e -> e.entryId() == nested.entryId()));
		assertThrows(IllegalArgumentException.class,
				() -> control.restore(student, parent.exerciseId(), nested.entryId(), batch.version()));
		var recovered = control.restore(student, parent.exerciseId(), nested.entryId(), batch.version(), true, null, null);
		assertEquals(batch.version() + 1, recovered.version());
		assertEquals("nested/child.py", control.loadPage(student, parent.exerciseId(), child.entryId()).entries()
				.stream().filter(e -> e.entryId() == child.entryId()).findFirst().orElseThrow().path());
		assertTaskDataUntouched();
	}

	@Test
	void restorationRejectsInvalidNamesTargetsStaleVersionsAndDescendantPathOverflowAtomically() throws SQLException {
		var parent = create(null, null, 0, Type.FOLDER, "source");
		var child = create(parent.exerciseId(), parent.entryId(), 1, Type.FILE, "x".repeat(252));
		var trashed = control.trash(student, parent.exerciseId(), parent.entryId(), 2);
		var a = create(parent.exerciseId(), null, 3, Type.FOLDER, "a".repeat(255));
		var b = create(parent.exerciseId(), a.entryId(), 4, Type.FOLDER, "b".repeat(255));
		var c = create(parent.exerciseId(), b.entryId(), 5, Type.FOLDER, "c".repeat(255));
		assertThrows(IllegalArgumentException.class, () -> control.restore(student, parent.exerciseId(),
				parent.entryId(), 6, true, c.entryId(), null));
		assertThrows(IllegalArgumentException.class, () -> control.restore(student, parent.exerciseId(),
				parent.entryId(), 6, false, null, "bad.py"));
		assertThrows(ExerciseConflictException.class, () -> control.restore(student, parent.exerciseId(),
				parent.entryId(), trashed.version(), false, null, "ok"));
		assertThrows(IllegalArgumentException.class, () -> control.restore(student, parent.exerciseId(),
				parent.entryId(), 6, true, child.entryId(), null));
		var page = control.loadPage(student, parent.exerciseId(), null);
		assertEquals(6, page.scopes().get(0).version());
		assertEquals("source", page.entries().stream()
				.filter(e -> e.entryId() == parent.entryId()).findFirst().orElseThrow().path());
		assertThrows(ExerciseNotFoundException.class, () -> control.loadPage(student, parent.exerciseId(), child.entryId()));
	}

	@Test
	void preservesIndividuallyTrashedChildrenAndBlocksInactiveAncestors() throws SQLException {
		var folder = create(null, null, 0, Type.FOLDER, "folder");
		var first = create(folder.exerciseId(), folder.entryId(), folder.version(), Type.FILE, "first.py");
		var second = create(folder.exerciseId(), folder.entryId(), first.version(), Type.FILE, "second.py");
		var childTrash = control.trash(student, first.exerciseId(), first.entryId(), second.version());
		var parentTrash = control.trash(student, folder.exerciseId(), folder.entryId(), childTrash.version());
		assertThrows(ExerciseNotFoundException.class,
				() -> control.save(student, second.exerciseId(), second.entryId(), parentTrash.version(), "blocked"));
		assertThrows(ExerciseNotFoundException.class,
				() -> control.loadPage(student, second.exerciseId(), second.entryId()));
		assertThrows(IllegalArgumentException.class,
				() -> control.restore(student, first.exerciseId(), first.entryId(), parentTrash.version()));
		var parentRestore = control.restore(student, folder.exerciseId(), folder.entryId(), parentTrash.version());
		var page = control.loadPage(student, folder.exerciseId(), second.entryId());
		assertEquals(Status.TRASHED, page.entries().stream()
				.filter(e -> e.entryId() == first.entryId()).findFirst().orElseThrow().status());
		assertEquals(Status.ACTIVE, page.entries().stream()
				.filter(e -> e.entryId() == second.entryId()).findFirst().orElseThrow().status());
		control.restore(student, first.exerciseId(), first.entryId(), parentRestore.version());
		assertTaskDataUntouched();
	}

	@Test
	void rejectsReadOnlyAndDisabledAccountsAndInvalidInputs() throws SQLException {
		var file = create(null, null, 0, Type.FILE, "test.py");
		for (String state : new String[] {"completed", "expired", "needs_review", "archived"}) {
			try (Connection c = Client.createConnection()) {
				execute(c, "UPDATE student_exercises SET exercise_status = ? WHERE student_exercise_id = ?", state, file.exerciseId());
			}

			assertThrows(RuntimeException.class,
					() -> control.save(student, file.exerciseId(), file.entryId(), file.version(), "no"));
		}
		try (Connection c = Client.createConnection()) {
			execute(c, "UPDATE student_exercises SET exercise_status = 'in_progress' WHERE student_exercise_id = ?", file.exerciseId());
			execute(c, "UPDATE users SET account_status = 'suspended' WHERE user_id = ?", student.userId());
		}
		assertThrows(SecurityException.class, () -> control.loadPage(student, null, null));
		assertThrows(SecurityException.class, () -> control.save(student, file.exerciseId(), file.entryId(), file.version(), "no"));
		assertThrows(SecurityException.class, () -> control.loadPage(null, null, null));
		var teacher = new AuthenticatedUser(other.userId(), "synthetic", "synthetic", UserType.TEACHER, false, "synthetic");
		assertThrows(SecurityException.class, () -> control.loadPage(teacher, null, null));
		assertThrows(IllegalArgumentException.class, () -> control.loadPage(other, 0L, null));
		assertThrows(IllegalArgumentException.class,
				() -> control.create(other, null, null, 0, null));
	}

	@Test
	void exportsOnlyOwnedSavedActiveEntriesWithoutMutation() throws Exception {
		var folder = create(null, null, 0, Type.FOLDER, "日本語");
		var file = create(folder.exerciseId(), folder.entryId(), folder.version(), Type.FILE, "hello.py");
		var saved = control.save(student, file.exerciseId(), file.entryId(), file.version(), "print('保存済み')");
		var empty = create(file.exerciseId(), null, saved.version(), Type.FOLDER, "空");
		var trash = create(file.exerciseId(), null, empty.version(), Type.FOLDER, "ごみ箱対象");
		var child = create(file.exerciseId(), trash.entryId(), trash.version(), Type.FILE, "excluded.py");
		var trashed = control.trash(student, file.exerciseId(), trash.entryId(), child.version());
		var downloaded = control.loadDownload(student, file.exerciseId());
		assertEquals(List.of("日本語", "日本語/hello.py", "空"), downloaded.entries().stream()
				.map(entity.StudentExerciseEntry::path).sorted().toList());
		var stream = new java.io.ByteArrayOutputStream();
		downloaded.writeZip(stream);
		var content = new java.util.HashMap<String, String>();
		try (var zip = new java.util.zip.ZipInputStream(new java.io.ByteArrayInputStream(stream.toByteArray()),
				java.nio.charset.StandardCharsets.UTF_8)) {
			for (var entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
				content.put(entry.getName(), new String(zip.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
			}
		}
		assertEquals(java.util.Set.of("日本語/", "日本語/hello.py", "空/"), content.keySet());
		assertEquals("print('保存済み')", content.get("日本語/hello.py"));
		assertEquals(trashed.version(), control.loadPage(student, file.exerciseId(), null).scopes().get(0).version());
		assertThrows(ExerciseNotFoundException.class, () -> control.loadDownload(other, file.exerciseId()));
		try (Connection c = Client.createConnection()) {
			execute(c, "UPDATE student_exercises SET exercise_status='expired' WHERE student_exercise_id=?", file.exerciseId());
			assertEquals(3, control.loadDownload(student, file.exerciseId()).entries().size());
			execute(c, "UPDATE users SET account_status='suspended' WHERE user_id=?", student.userId());
			assertThrows(SecurityException.class, () -> control.loadDownload(student, file.exerciseId()));
		}
		assertTaskDataUntouched();
	}

	@Test
	void serializesConcurrentInitialCreation() throws Exception {
		List<Boolean> outcomes = race(() -> {
			try {
				control.create(student, null, null, 0, new StudentExerciseInput(Type.FILE, "initial.py"));
				return true;
			} catch (ExerciseConflictException e) {
				return false;
			}
		});
		assertEquals(1, outcomes.stream().filter(Boolean::booleanValue).count());
		try (Connection c = Client.createConnection()) {
			assertEquals(1, scalar(c, "SELECT COUNT(*) FROM student_exercises"));
			assertEquals(1, scalar(c, "SELECT COUNT(*) FROM student_exercise_entries"));
		}
	}

	@Test
	void serializesConcurrentSavesWithOneWinner() throws Exception {
		var file = create(null, null, 0, Type.FILE, "test.py");
		List<Boolean> outcomes = race(() -> {
			try {
				control.save(student, file.exerciseId(), file.entryId(), file.version(), "winner");
				return true;
			} catch (ExerciseConflictException e) {
				return false;
			}
		});
		assertEquals(1, outcomes.stream().filter(Boolean::booleanValue).count());
		assertEquals(2, control.loadPage(student, null, null).scopes().get(0).version());
	}

	@Test
	void rollsBackInvalidParentsAndRejectsLongPathsWithoutChangingVersion() throws SQLException {
		assertThrows(IllegalArgumentException.class,
				() -> create(null, 1L, 0, Type.FILE, "invalid.py"));
		assertTrue(control.loadPage(student, null, null).scopes().isEmpty());
		var folder = create(null, null, 0, Type.FOLDER, "a".repeat(255));
		var second = create(folder.exerciseId(), folder.entryId(), folder.version(), Type.FOLDER, "b".repeat(255));
		var third = create(folder.exerciseId(), second.entryId(), second.version(), Type.FOLDER, "c".repeat(255));
		var fourth = create(folder.exerciseId(), third.entryId(), third.version(), Type.FOLDER, "d".repeat(227));
		var file = create(folder.exerciseId(), fourth.entryId(), fourth.version(), Type.FILE, "a.py");
		assertEquals(1000, control.loadPage(student, file.exerciseId(), file.entryId()).entries().stream()
				.filter(e -> e.entryId() == file.entryId()).findFirst().orElseThrow().path().length());
		assertThrows(IllegalArgumentException.class,
				() -> create(folder.exerciseId(), fourth.entryId(), file.version(), Type.FILE, "ab.py"));
		assertEquals(file.version(), control.loadPage(student, null, null).scopes().get(0).version());
	}

	@Test
	void preservesExecutionHistoryAndDoesNotRestorePermanentlyDeletedChildren() throws SQLException {
		var folder = create(null, null, 0, Type.FOLDER, "folder");
		var file = create(folder.exerciseId(), folder.entryId(), folder.version(), Type.FILE, "test.py");
		long execution;
		try (Connection c = Client.createConnection()) {
			execution = insert(c, """
					INSERT INTO code_executions (actor_user_id, exercise_entry_id, execution_context,
					  source_code, execution_status, standard_output_truncated, standard_error_truncated, executed_at)
					VALUES (?, ?, 'student_exercise', 'print(1)', 'succeeded', FALSE, FALSE, CURRENT_TIMESTAMP)
					""", student.userId(), file.entryId());
		}
		var trash = control.trash(student, folder.exerciseId(), folder.entryId(), file.version());
		var restore = control.restore(student, folder.exerciseId(), folder.entryId(), trash.version());
		try (Connection c = Client.createConnection()) {
			assertEquals(1, scalar(c, "SELECT COUNT(*) FROM code_executions WHERE execution_id = ?", execution));
			execute(c, "UPDATE student_exercise_entries SET entry_status = 'deleted' WHERE exercise_entry_id = ?", file.entryId());
		}
		assertThrows(ExerciseNotFoundException.class,
				() -> control.restore(student, file.exerciseId(), file.entryId(), restore.version()));
		assertFalse(control.loadPage(student, file.exerciseId(), null).entries().stream()
				.anyMatch(e -> e.entryId() == file.entryId()));
	}

	@Test
	void rejectsCrossScopeHierarchyAndPendingPasswordChange() throws SQLException {
		var file = create(null, null, 0, Type.FILE, "test.py");
		long scope;
		long foreignParent;
		try (Connection c = Client.createConnection()) {
			scope = insert(c, """
					INSERT INTO student_exercises (student_user_id, exercise_origin, scope_name,
					  exercise_status, save_status, created_at)
					VALUES (?, 'student_created', 'Second scope', 'in_progress', 'unsaved', CURRENT_TIMESTAMP)
					""", student.userId());
			foreignParent = insert(c, """
					INSERT INTO student_exercise_entries (student_exercise_id, entry_type, name,
					  path, entry_status, created_at)
					VALUES (?, 'folder', 'parent', 'parent', 'active', CURRENT_TIMESTAMP)
					""", scope);
		}
		assertThrows(ExerciseNotFoundException.class,
				() -> create(file.exerciseId(), foreignParent, file.version(), Type.FILE, "child.py"));
		assertThrows(ExerciseNotFoundException.class,
				() -> control.loadPage(student, scope, file.entryId()));
		assertThrows(SecurityException.class,
				() -> control.loadPage(student.withPasswordChangeRequired(true), null, null));
		try (Connection c = Client.createConnection()) {
			execute(c, "UPDATE student_exercise_entries SET parent_entry_id = ? WHERE exercise_entry_id = ?",
					foreignParent, file.entryId());
		}
		assertThrows(SQLException.class, () -> control.loadPage(student, file.exerciseId(), null));
		assertThrows(SQLException.class,
				() -> control.save(student, file.exerciseId(), file.entryId(), file.version(), "invalid hierarchy"));
		assertEquals(file.version(), control.loadPage(student, scope, null).scopes().get(0).version());
	}

	private List<Boolean> race(Callable<Boolean> action) throws Exception {
		var barrier = new CyclicBarrier(2);
		try (var executor = Executors.newFixedThreadPool(2)) {
			Callable<Boolean> synchronizedAction = () -> {
				barrier.await(10, TimeUnit.SECONDS);
				return action.call();
			};
			var first = executor.submit(synchronizedAction);
			var second = executor.submit(synchronizedAction);
			return List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS));
		}
	}

	@Test
	void runsUnsavedCodeThroughRealRunnerAndPersistsOnlyExerciseExecution() throws Exception {
		var file = create(null, null, 0, Type.FILE, "test.py");
		String code = "name = input()\nprint('こんにちは ' + name)";
		var executed = control.runCode(student, file.exerciseId(), file.entryId(), code, "合成\n");
		assertEquals("succeeded", executed.result().getStatus());
		assertEquals("こんにちは 合成\n", executed.result().getStandardOutput());
		try (Connection c = Client.createConnection();
				PreparedStatement s = c.prepareStatement("SELECT * FROM code_executions WHERE execution_id = ?")) {
			s.setLong(1, executed.executionId());
			try (ResultSet rows = s.executeQuery()) {
				assertTrue(rows.next());
				assertEquals(file.entryId(), rows.getLong("exercise_entry_id"));
				assertEquals(student.userId(), rows.getLong("actor_user_id"));
				assertEquals("student_exercise", rows.getString("execution_context"));
				assertNull(rows.getObject("participation_id"));
				assertNull(rows.getObject("submission_id"));
				assertEquals(code, rows.getString("source_code"));
				assertEquals("合成\n", rows.getString("standard_input"));
				assertEquals(executed.result().getStandardOutput(), rows.getString("standard_output"));
				assertTrue(rows.getInt("duration_milliseconds") >= 0);
			}
			for (String table : new String[] {"code_logs", "submissions", "task_participations", "evaluations"}) {
				assertEquals(0, scalar(c, "SELECT COUNT(*) FROM " + table));
			}
		}
		var page = control.loadPage(student, file.exerciseId(), file.entryId());
		assertEquals("", page.entries().get(0).content());
		assertEquals(file.version(), page.scopes().get(0).version());
		assertFalse(page.scopes().get(0).saved());
		assertEquals(executed.executionId(), page.latestExecution().executionId());
		assertEquals("こんにちは 合成\n", page.latestExecution().result().getStandardOutput());
		var failed = control.runCode(student, file.exerciseId(), file.entryId(), "raise ValueError('synthetic')", "");
		assertEquals("failed", failed.result().getStatus());
		assertTrue(failed.result().getStandardError().contains("ValueError"));
		assertTrue(failed.executionId() > executed.executionId());
		assertEquals(failed.executionId(), control.loadPage(student, file.exerciseId(), file.entryId())
				.latestExecution().executionId());
		assertThrows(ExerciseNotFoundException.class,
				() -> control.loadPage(other, file.exerciseId(), file.entryId()));
	}

	@Test
	void validatesExecutionTargetBeforeCallingRunner() throws Exception {
		var folder = create(null, null, 0, Type.FOLDER, "folder");
		var neverRun = new StudentExerciseControl((code, input) -> {
			fail("Runner must not be called for an unavailable target.");
			return null;
		});
		assertThrows(IllegalArgumentException.class,
				() -> neverRun.runCode(student, folder.exerciseId(), folder.entryId(), "print(1)", ""));
		assertThrows(ExerciseNotFoundException.class,
				() -> neverRun.runCode(other, folder.exerciseId(), folder.entryId(), "print(1)", ""));
		var file = create(folder.exerciseId(), folder.entryId(), folder.version(), Type.FILE, "test.py");
		var trash = control.trash(student, folder.exerciseId(), folder.entryId(), file.version());
		assertThrows(ExerciseNotFoundException.class,
				() -> neverRun.runCode(student, file.exerciseId(), file.entryId(), "print(1)", ""));
		assertThrows(IllegalArgumentException.class,
				() -> neverRun.runCode(student, file.exerciseId(), file.entryId(), "a".repeat(65537), ""));
		assertEquals(trash.version(), control.loadPage(student, null, null).scopes().get(0).version());
	}

	@Test
	void runsInteractiveExerciseWithOwnerBoundariesAndSinglePersistence() throws Exception {
		var file = create(null, null, 0, Type.FILE, "interactive.py");
		var started = control.startInteractiveExecution(student, file.exerciseId(), file.entryId(),
				"print('名前: ', end='', flush=True)\nname=input()\nprint('こんにちは '+name)");
		String session = started.update().sessionId();
		assertThrows(SecurityException.class, () -> control.pollInteractiveExecution(other,
				file.exerciseId(), file.entryId(), session, 0));
		assertThrows(SecurityException.class, () -> control.sendInteractiveInput(other,
				file.exerciseId(), file.entryId(), session, "bad"));
		assertThrows(SecurityException.class, () -> control.cancelInteractiveExecution(other,
				file.exerciseId(), file.entryId(), session));
		assertThrows(IllegalArgumentException.class, () -> control.sendInteractiveInput(student,
				file.exerciseId(), file.entryId(), session, "a\nb"));
		control.sendInteractiveInput(student, file.exerciseId(), file.entryId(), session, "合成");
		var completed = waitForInteractive(file, session);
		assertEquals("succeeded", completed.update().status());
		assertEquals("合成\n", completed.update().standardInput());
		assertTrue(completed.update().standardOutput().contains("こんにちは 合成"));
		assertNotNull(completed.executionId());
		assertEquals(completed.executionId(), control.pollInteractiveExecution(student,
				file.exerciseId(), file.entryId(), session, 0).executionId());
		try (Connection c = Client.createConnection()) {
			assertEquals(1, scalar(c, "SELECT COUNT(*) FROM code_executions WHERE actor_user_id = ?", student.userId()));
			for (String table : new String[] {"code_logs", "submissions", "task_participations", "evaluations"}) {
				assertEquals(0, scalar(c, "SELECT COUNT(*) FROM " + table));
			}
		}
		var page = control.loadPage(student, file.exerciseId(), file.entryId());
		assertEquals("", page.entries().get(0).content());
		assertEquals(file.version(), page.scopes().get(0).version());
	}

	@Test
	void monitorsDetachedExecutionAndPersistsCancellation() throws Exception {
		var file = create(null, null, 0, Type.FILE, "monitor.py");
		control.startInteractiveExecution(student, file.exerciseId(), file.entryId(), "print('detached')");
		long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
		while (control.loadPage(student, file.exerciseId(), file.entryId()).latestExecution() == null
				&& System.nanoTime() < deadline) Thread.sleep(100);
		assertNotNull(control.loadPage(student, file.exerciseId(), file.entryId()).latestExecution());
		var running = control.startInteractiveExecution(student, file.exerciseId(), file.entryId(), "input()");
		control.cancelInteractiveExecution(student, file.exerciseId(), file.entryId(), running.update().sessionId());
		var cancelled = waitForInteractive(file, running.update().sessionId());
		assertEquals("cancelled", cancelled.update().status());
		assertEquals("execution_cancelled", cancelled.update().errorCode());
		assertEquals("failed", control.loadPage(student, file.exerciseId(), file.entryId())
				.latestExecution().result().getStatus());
	}

	@Test
	void revalidatesInteractiveTargetsAndRejectsCrossFileAccess() throws Exception {
		var folder = create(null, null, 0, Type.FOLDER, "sessions");
		var file = create(folder.exerciseId(), folder.entryId(), folder.version(), Type.FILE, "first.py");
		var second = create(file.exerciseId(), null, file.version(), Type.FILE, "second.py");
		var started = control.startInteractiveExecution(student, file.exerciseId(), file.entryId(), "input()");
		String session = started.update().sessionId();
		assertThrows(SecurityException.class, () -> control.pollInteractiveExecution(student,
				file.exerciseId(), second.entryId(), session, 0));
		assertThrows(SecurityException.class, () -> control.cancelInteractiveExecution(student,
				file.exerciseId() + 1, file.entryId(), session));
		try (Connection c = Client.createConnection()) {
			execute(c, "UPDATE users SET account_status = 'suspended' WHERE user_id = ?", student.userId());
			assertThrows(SecurityException.class, () -> control.pollInteractiveExecution(student,
					file.exerciseId(), file.entryId(), session, 0));
			execute(c, "UPDATE users SET account_status = 'active' WHERE user_id = ?", student.userId());
			execute(c, "UPDATE student_profiles SET must_change_password = TRUE WHERE user_id = ?", student.userId());
			assertThrows(SecurityException.class, () -> control.sendInteractiveInput(student,
					file.exerciseId(), file.entryId(), session, "no"));
			execute(c, "UPDATE student_profiles SET must_change_password = FALSE WHERE user_id = ?", student.userId());
			execute(c, "UPDATE student_exercises SET exercise_status = 'expired' WHERE student_exercise_id = ?", file.exerciseId());
			assertThrows(IllegalArgumentException.class, () -> control.cancelInteractiveExecution(student,
					file.exerciseId(), file.entryId(), session));
			execute(c, "UPDATE student_exercises SET exercise_status = 'in_progress' WHERE student_exercise_id = ?", file.exerciseId());
		}
		control.trash(student, file.exerciseId(), folder.entryId(), second.version());
		assertThrows(ExerciseNotFoundException.class, () -> control.pollInteractiveExecution(student,
				file.exerciseId(), file.entryId(), session, 0));
		assertThrows(ExerciseNotFoundException.class, () -> control.sendInteractiveInput(student,
				file.exerciseId(), file.entryId(), session, "no"));
		assertThrows(ExerciseNotFoundException.class, () -> control.cancelInteractiveExecution(student,
				file.exerciseId(), file.entryId(), session));
		new PythonRunnerClient().cancelSession(session);
		Thread.sleep(1000);
		try (Connection c = Client.createConnection()) {
			assertEquals(0, scalar(c, "SELECT COUNT(*) FROM code_executions"));
		}
	}

	private entity.ExerciseInteractiveResult waitForInteractive(ExerciseSaveResult file, String session) throws Exception {
		long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
		entity.ExerciseInteractiveResult result;
		do {
			result = control.pollInteractiveExecution(student, file.exerciseId(), file.entryId(), session, 0);
			if (!"running".equals(result.update().status())) return result;
			Thread.sleep(100);
		} while (System.nanoTime() < deadline);
		fail("Interactive exercise did not finish.");
		return result;
	}

	@Test
	void propagatesRunnerFailuresAndRevalidatesTargetAfterExecution() throws Exception {
		var file = create(null, null, 0, Type.FILE, "test.py");
		var unavailable = new StudentExerciseControl((code, input) -> {
			throw new java.io.IOException("Synthetic runner failure");
		});
		assertThrows(java.io.IOException.class,
				() -> unavailable.runCode(student, file.exerciseId(), file.entryId(), "print(1)", ""));
		var changedWhileRunning = new StudentExerciseControl((code, input) -> {
			try {
				control.trash(student, file.exerciseId(), file.entryId(), file.version());
			} catch (SQLException e) {
				throw new java.io.IOException(e);
			}
			return new PythonRunnerClient.TimedResult(
					new entity.PythonExecutionResult("succeeded", 0, "1\n", "", false, false, null), 1);
		});
		assertThrows(ExerciseNotFoundException.class,
				() -> changedWhileRunning.runCode(student, file.exerciseId(), file.entryId(), "print(1)", ""));
		try (Connection c = Client.createConnection()) {
			assertEquals(0, scalar(c, "SELECT COUNT(*) FROM code_executions"));
		}
	}

	@Test
	void persistsTimeoutFlagsAndPropagatesInvalidResultStorageFailure() throws Exception {
		var file = create(null, null, 0, Type.FILE, "test.py");
		var timedOut = new StudentExerciseControl((code, input) -> new PythonRunnerClient.TimedResult(
				new entity.PythonExecutionResult("timed_out", null, "partial", "timeout", true, true, "timeout"), 100));
		var executed = timedOut.runCode(student, file.exerciseId(), file.entryId(), "while True: pass", "");
		try (Connection c = Client.createConnection()) {
			assertEquals(1, scalar(c, """
					SELECT COUNT(*) FROM code_executions WHERE execution_id = ?
					  AND execution_status = 'timed_out' AND exit_code IS NULL
					  AND standard_output_truncated = TRUE AND standard_error_truncated = TRUE
					  AND error_code = 'timeout' AND duration_milliseconds = 100
					""", executed.executionId()));
		}
		var invalidResult = new StudentExerciseControl((code, input) -> new PythonRunnerClient.TimedResult(
				new entity.PythonExecutionResult("unknown", null, "", "", false, false, null), 1));
		assertThrows(SQLException.class,
				() -> invalidResult.runCode(student, file.exerciseId(), file.entryId(), "print(1)", ""));
		try (Connection c = Client.createConnection()) {
			assertEquals(1, scalar(c, "SELECT COUNT(*) FROM code_executions"));
		}
	}

	private ExerciseSaveResult create(Long scope, Long parent, long version, Type type, String name) throws SQLException {
		return control.create(student, scope, parent, version, new StudentExerciseInput(type, name));
	}

	private AuthenticatedUser addStudent(Connection c, long school) throws SQLException {
		long user = insert(c, """
				INSERT INTO users (user_type, login_id, password_hash, display_name, account_status, created_at)
				VALUES ('student', ?, 'synthetic-unusable', 'Synthetic', 'active', CURRENT_TIMESTAMP)
				""", "exercise-" + UUID.randomUUID());
		users.add(user);
		execute(c, """
				INSERT INTO student_profiles (user_id, student_code, school_id, security_level,
				  first_login_status, must_change_password) VALUES (?, ?, ?, 1, 'completed', FALSE)
				""", user, UUID.randomUUID().toString().substring(0, 32), school);
		return new AuthenticatedUser(user, "synthetic", "Synthetic", UserType.STUDENT, false, "synthetic");
	}

	private void assertTaskDataUntouched() throws SQLException {
		try (Connection c = Client.createConnection()) {
			for (String table : new String[] {"code_logs", "submissions", "task_participations", "evaluations", "code_executions"}) {
				assertEquals(0, scalar(c, "SELECT COUNT(*) FROM " + table));
			}
		}
	}

	@AfterEach
	void cleanup() throws SQLException {
		if (users.isEmpty()) return;
		try (Connection c = Client.createConnection()) {
			for (long user : users) {
				execute(c, "DELETE FROM user_editor_preferences WHERE user_id = ?", user);
				execute(c, "DELETE FROM code_executions WHERE actor_user_id = ?", user);
				execute(c, """
						UPDATE student_exercise_entries e JOIN student_exercises s
						  ON e.student_exercise_id = s.student_exercise_id
						SET e.parent_entry_id = NULL WHERE s.student_user_id = ?
						""", user);
				execute(c, """
						DELETE e FROM student_exercise_entries e JOIN student_exercises s
						  ON e.student_exercise_id = s.student_exercise_id WHERE s.student_user_id = ?
						""", user);
				execute(c, "UPDATE student_exercises SET merged_into_exercise_id=NULL WHERE student_user_id=?", user);
				execute(c, "DELETE FROM student_exercises WHERE student_user_id = ?", user);
				execute(c, "DELETE FROM student_profiles WHERE user_id = ?", user);
				execute(c, "DELETE FROM users WHERE user_id = ?", user);
			}
			for (long school : schools) execute(c, "DELETE FROM schools WHERE school_id = ?", school);
		}
	}

	private static void execute(Connection c, String sql, Object... values) throws SQLException {
		try (PreparedStatement s = c.prepareStatement(sql)) {
			for (int i = 0; i < values.length; i++) s.setObject(i + 1, values[i]);
			s.executeUpdate();
		}
	}

	private static long insert(Connection c, String sql, Object... values) throws SQLException {
		try (PreparedStatement s = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
			for (int i = 0; i < values.length; i++) s.setObject(i + 1, values[i]);
			s.executeUpdate();
			try (ResultSet keys = s.getGeneratedKeys()) {
				assertTrue(keys.next());
				return keys.getLong(1);
			}
		}
	}

	private static long scalar(Connection c, String sql, Object... values) throws SQLException {
		try (PreparedStatement s = c.prepareStatement(sql)) {
			for (int i = 0; i < values.length; i++) s.setObject(i + 1, values[i]);
			try (ResultSet rows = s.executeQuery()) {
				assertTrue(rows.next());
				return rows.getLong(1);
			}
		}
	}
}
