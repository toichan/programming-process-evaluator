package entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class StudentExerciseInputTest {
	@Test
	void preservesNamesAndCountsUnicodeCodePoints() {
		assertEquals(" 練習.py .py", new StudentExerciseInput(
				StudentExerciseEntry.Type.FILE, " 練習.py ").name());
		assertEquals("😀".repeat(255), new StudentExerciseInput(
				StudentExerciseEntry.Type.FOLDER, "😀".repeat(255)).name());
		assertThrows(IllegalArgumentException.class, () -> new StudentExerciseInput(
				StudentExerciseEntry.Type.FILE, "😀".repeat(256)));
	}

	@Test
	void appendsPythonExtensionOnlyToNewFiles() {
		for (String name : new String[] {"hello", "hello.txt", "練習"}) {
			assertEquals(name + ".py", new StudentExerciseInput(StudentExerciseEntry.Type.FILE, name).name());
		}
		for (String name : new String[] {"hello.py", "hello.PY", "hello.Py"}) {
			assertEquals(name, new StudentExerciseInput(StudentExerciseEntry.Type.FILE, name).name());
		}
		assertEquals("hello", new StudentExerciseInput(StudentExerciseEntry.Type.FOLDER, "hello").name());
		assertEquals("😀".repeat(252) + ".py",
				new StudentExerciseInput(StudentExerciseEntry.Type.FILE, "😀".repeat(252)).name());
		assertThrows(IllegalArgumentException.class,
				() -> new StudentExerciseInput(StudentExerciseEntry.Type.FILE, "😀".repeat(253)));
		assertEquals("a".repeat(252) + ".PY",
				new StudentExerciseInput(StudentExerciseEntry.Type.FILE, "a".repeat(252) + ".PY").name());
		var file = new StudentExerciseInput(StudentExerciseEntry.Type.FILE, "a");
		String parent = String.join("/", "a".repeat(255), "b".repeat(255),
				"c".repeat(255), "d".repeat(227));
		assertEquals(1000, file.pathUnder(parent).length());
		assertThrows(IllegalArgumentException.class, () -> file.pathUnder(parent + "d"));
	}

	@Test
	void rejectsUnsafeNamesAndMissingType() {
		for (String name : new String[] {"a.py", "a.PY", "授業1.2", ".hidden"}) {
			assertThrows(IllegalArgumentException.class,
					() -> new StudentExerciseInput(StudentExerciseEntry.Type.FOLDER, name));
		}
		for (String name : new String[] {"", "  ", ".", "..", "a/b", "a\\b", "a\nb",
				"a\0b", "a\u007fb", "\ud800", "\udc00"}) {
			assertThrows(IllegalArgumentException.class,
					() -> new StudentExerciseInput(StudentExerciseEntry.Type.FILE, name));
		}
		assertThrows(IllegalArgumentException.class,
				() -> new StudentExerciseInput(StudentExerciseEntry.Type.FILE, null));
		assertThrows(IllegalArgumentException.class, () -> new StudentExerciseInput(null, "a"));
	}

	@Test
	void buildsAndValidatesServerSidePaths() {
		var file = new StudentExerciseInput(StudentExerciseEntry.Type.FILE, "a.py");
		assertEquals("a.py", file.pathUnder(null));
		assertEquals("練習/a.py", file.pathUnder("練習"));
		String parent = String.join("/", "a".repeat(255), "b".repeat(255),
				"c".repeat(255), "d".repeat(227));
		assertEquals(1000, file.pathUnder(parent).length());
		assertThrows(IllegalArgumentException.class, () -> file.pathUnder(parent + "d"));
		for (String path : new String[] {"/root", "a//b", "a/../b", "a/./b", "a\\b", "a/"}) {
			assertThrows(IllegalArgumentException.class, () -> file.pathUnder(path));
		}
	}

	@Test
	void enforcesUtf8CodeAndInputLimitsWithoutChangingText() {
		assertEquals("", StudentExerciseInput.validateCode(""));
		assertEquals("a".repeat(65536), StudentExerciseInput.validateCode("a".repeat(65536)));
		assertEquals("学".repeat(21845), StudentExerciseInput.validateCode("学".repeat(21845)));
		assertThrows(IllegalArgumentException.class,
				() -> StudentExerciseInput.validateCode("学".repeat(21846)));
		assertThrows(IllegalArgumentException.class,
				() -> StudentExerciseInput.validateCode("a".repeat(65537)));
		assertEquals("a".repeat(8192), StudentExerciseInput.validateStandardInput("a".repeat(8192)));
		assertThrows(IllegalArgumentException.class,
				() -> StudentExerciseInput.validateStandardInput("a".repeat(8193)));
		for (String value : new String[] {"x\0y", "\ud800", "\udc00"}) {
			assertThrows(IllegalArgumentException.class, () -> StudentExerciseInput.validateCode(value));
			assertThrows(IllegalArgumentException.class,
					() -> StudentExerciseInput.validateStandardInput(value));
		}
		assertThrows(IllegalArgumentException.class, () -> StudentExerciseInput.validateCode(null));
		assertThrows(IllegalArgumentException.class,
				() -> StudentExerciseInput.validateStandardInput(null));
	}

	@Test
	void parsesOnlyKnownEntryTypesAndStates() {
		assertEquals(StudentExerciseEntry.Type.FILE, StudentExerciseEntry.Type.fromValue("file"));
		assertEquals(StudentExerciseEntry.Status.TRASHED,
				StudentExerciseEntry.Status.fromValue("trashed"));
		assertThrows(IllegalArgumentException.class, () -> StudentExerciseEntry.Type.fromValue("FILE"));
		assertThrows(IllegalArgumentException.class, () -> StudentExerciseEntry.Type.fromValue(null));
		assertThrows(IllegalArgumentException.class, () -> StudentExerciseEntry.Status.fromValue("unknown"));
	}

	@Test
	void rejectsInvalidIdentifiersAndVersions() {
		assertEquals(1, StudentExerciseInput.requireId(1));
		assertEquals(0, StudentExerciseInput.requireVersion(0));
		assertThrows(IllegalArgumentException.class, () -> StudentExerciseInput.requireId(0));
		assertThrows(IllegalArgumentException.class, () -> StudentExerciseInput.requireVersion(-1));
		assertThrows(IllegalArgumentException.class, () -> StudentExerciseInput.requireVersion(Long.MAX_VALUE));
	}

	@Test
	void preservesReadOnlyStatesUntilTheirPolicyIsDefined() {
		assertEquals(true, StudentExercisePage.State.IN_PROGRESS.isEditable());
		assertEquals(true, StudentExercisePage.State.TEMPORARILY_SAVED.isEditable());
		for (var state : new StudentExercisePage.State[] {
				StudentExercisePage.State.COMPLETED, StudentExercisePage.State.EXPIRED,
				StudentExercisePage.State.NEEDS_REVIEW, StudentExercisePage.State.ARCHIVED}) {
			assertEquals(false, state.isEditable());
			assertEquals(state, StudentExercisePage.State.fromValue(state.getValue()));
		}
		assertThrows(IllegalArgumentException.class, () -> StudentExercisePage.State.fromValue("unknown"));
	}

	@Test
	void validatesPersistedSaveResultIdentifiers() {
		assertEquals(1, new ExerciseSaveResult(1, 2, 1).version());
		assertThrows(IllegalArgumentException.class, () -> new ExerciseSaveResult(0, 2, 1));
		assertThrows(IllegalArgumentException.class, () -> new ExerciseSaveResult(1, 0, 1));
		assertThrows(IllegalArgumentException.class, () -> new ExerciseSaveResult(1, 2, -1));
	}
}
