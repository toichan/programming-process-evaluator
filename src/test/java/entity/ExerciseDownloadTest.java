package entity;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.Test;

class ExerciseDownloadTest {
	@Test
	void archiveNameMatchesRootAndHeaderEscapesSpecialCharacters() {
		assertEquals("s001.zip", ExerciseDownload.archiveName("s001"));
		assertTrue(ExerciseDownload.contentDisposition("s001").contains("filename=\"s001.zip\""));
		assertTrue(ExerciseDownload.contentDisposition("生徒 1").contains(
				"filename*=UTF-8''%E7%94%9F%E5%BE%92%201.zip"));
		assertTrue(ExerciseDownload.contentDisposition("a\"b").contains("filename=\"a\\\"b.zip\""));
		for (String invalid : List.of("../bad", "a\\b", "a\r\nb", "")) {
			assertThrows(IllegalArgumentException.class, () -> ExerciseDownload.archiveName(invalid));
		}
	}

	@Test
	void retainsUtf8NamesContentAndEmptyFolders() throws Exception {
		var download = new ExerciseDownload(List.of(entry("空", StudentExerciseEntry.Type.FOLDER, null),
				entry("日本語.py", StudentExerciseEntry.Type.FILE, "print('合成')")));
		var bytes = new ByteArrayOutputStream();
		download.writeZip(bytes);
		try (var zip = new ZipInputStream(new ByteArrayInputStream(bytes.toByteArray()), StandardCharsets.UTF_8)) {
			assertEquals("空/", zip.getNextEntry().getName());
			assertEquals(0, zip.readAllBytes().length);
			assertEquals("日本語.py", zip.getNextEntry().getName());
			assertEquals("print('合成')", new String(zip.readAllBytes(), StandardCharsets.UTF_8));
			assertNull(zip.getNextEntry());
		}
	}

	@Test
	void rejectsUnsafeArchivePathsBeforeWriting() {
		for (String path : List.of("../evil", "/absolute", "a/../evil", "a\\evil", "a//b")) {
			assertThrows(IllegalArgumentException.class, () -> new ExerciseDownload(
					List.of(entry(path, StudentExerciseEntry.Type.FILE, ""))));
		}
	}

	@Test
	void permitsExistingDottedFoldersWithoutApplyingCreationPolicy() {
		assertDoesNotThrow(() -> new ExerciseDownload(List.of(
				entry("legacy.py/file", StudentExerciseEntry.Type.FILE, ""))));
	}

	@Test
	void selectedDownloadUsesServerNamesAndChoosesTextOrArchiveBySelectionShape() {
		var file = new StudentExerciseEntry(1, 1, null, StudentExerciseEntry.Type.FILE,
				"one.py", "folder/one.py", null, "print(1)", StudentExerciseEntry.Status.ACTIVE, null);
		var folder = entry("folder", StudentExerciseEntry.Type.FOLDER, null);
		var timestamp = java.time.LocalDateTime.of(2026, 10, 4, 9, 45, 6);

		var oneFile = ExerciseDownload.selected("s001", List.of(file), timestamp);
		assertFalse(oneFile.archive());
		assertEquals("one.py", oneFile.fileName());
		assertEquals("text/x-python; charset=UTF-8", oneFile.contentType());
		var oneFolder = ExerciseDownload.selected("s001", List.of(folder), timestamp);
		assertEquals("folder.zip", oneFolder.fileName());
		var multipleFiles = ExerciseDownload.selected("s001",
				List.of(file, new StudentExerciseEntry(2, 1, null, StudentExerciseEntry.Type.FILE,
						"two.py", "two.py", null, "", StudentExerciseEntry.Status.ACTIVE, null)), timestamp);
		assertEquals("s001_20261004-094506.zip", multipleFiles.fileName());
		var folderAndFile = ExerciseDownload.selected("s001", List.of(folder, file), timestamp);
		assertEquals("s001.zip", folderAndFile.fileName());
		assertTrue(oneFile.contentDisposition().contains("filename=\"one.py\""));

		var folderContents = List.of(folder,
				new StudentExerciseEntry(2, 1, 1L, StudentExerciseEntry.Type.FILE,
						"one.py", "folder/one.py", null, "print(1)", StudentExerciseEntry.Status.ACTIVE, null));
		var selectedFolder = ExerciseDownload.selected("s001", List.of(folder), folderContents, timestamp);
		assertEquals("folder.zip", selectedFolder.fileName());
	}

	private StudentExerciseEntry entry(String path, StudentExerciseEntry.Type type, String content) {
		return new StudentExerciseEntry(1, 1, null, type, path, path, null, content,
				StudentExerciseEntry.Status.ACTIVE, null);
	}
}
