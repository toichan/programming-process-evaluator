package entity;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

class ExerciseUploadTest {
	@Test
	void validatesPythonPathsAndLimits() {
		var upload = new ExerciseUpload(List.of(new ExerciseUpload.File("練習/hello.PY", "print('学')")));
		assertEquals("練習/hello.PY", upload.files().get(0).path());
		for (String path : new String[] {"../x.py", "/x.py", "a//x.py", "a.b/x.py", "x.txt", "a\\x.py"}) {
			assertThrows(IllegalArgumentException.class,
					() -> new ExerciseUpload(List.of(new ExerciseUpload.File(path, ""))));
		}
		assertThrows(IllegalArgumentException.class, () -> new ExerciseUpload(List.of()));
		assertThrows(IllegalArgumentException.class, () -> new ExerciseUpload(List.of(
				new ExerciseUpload.File("x.py", ""), new ExerciseUpload.File("x.py", ""))));
		assertThrows(IllegalArgumentException.class, () -> new ExerciseUpload(List.of(
				new ExerciseUpload.File("x.py", "a".repeat(65537)))));
		assertThrows(IllegalArgumentException.class, () -> new ExerciseUpload(
				java.util.stream.IntStream.range(0, 101)
						.mapToObj(i -> new ExerciseUpload.File(i + ".py", "")).toList()));
		assertThrows(IllegalArgumentException.class, () -> new ExerciseUpload(
				java.util.stream.IntStream.range(0, 17)
						.mapToObj(i -> new ExerciseUpload.File(i + ".py", "a".repeat(65536))).toList()));
	}
}
