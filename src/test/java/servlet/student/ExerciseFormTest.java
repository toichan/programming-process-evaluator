package servlet.student;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import entity.PythonExecutionInput;

class ExerciseFormTest {
	@Test
	void decodesUtf8AndPreservesCodeWhitespace() throws Exception {
		var form = ExerciseForm.read(new ByteArrayInputStream(
				"name=%E7%B7%B4%E7%BF%92&code=+print%28%27%2B%27%29%0A".getBytes(StandardCharsets.UTF_8)));
		assertEquals("練習", form.get("name"));
		assertEquals(" print('+')\n", form.get("code"));
	}

	@Test
	void rejectsDuplicatesMalformedEscapesAndUtf8() {
		for (String invalid : new String[] {"entryId=1&entryId=2", "code=%", "code=%GG", "code=%FF"}) {
			assertThrows(IllegalArgumentException.class, () -> ExerciseForm.read(
					new ByteArrayInputStream(invalid.getBytes(StandardCharsets.UTF_8))));
		}
	}

	@Test
	void enforcesBodyLimitEvenWithoutContentLength() throws Exception {
		byte[] maximum = ("code=" + "a".repeat(ExerciseForm.MAX_BYTES - 5)).getBytes(StandardCharsets.UTF_8);
		assertEquals(ExerciseForm.MAX_BYTES - 5,
				ExerciseForm.read(new ByteArrayInputStream(maximum)).get("code").length());
		assertThrows(ExerciseForm.TooLargeException.class, () -> ExerciseForm.read(
				new ByteArrayInputStream(new byte[ExerciseForm.MAX_BYTES + 1])));
	}

	@Test
	void requiresExplicitNonnegativeVersionAndPositiveDecimalIds() {
		for (String invalid : new String[] {"", "-1", "+1", " 1", "1.0", "9223372036854775808"}) {
			assertThrows(IllegalArgumentException.class, () -> ExerciseForm.number(invalid, false));
		}
		assertThrows(IllegalArgumentException.class, () -> ExerciseForm.number(null, false));
		assertThrows(IllegalArgumentException.class, () -> ExerciseForm.number("0", true));
		assertEquals(0, ExerciseForm.number("0", false));
		assertEquals(Long.MAX_VALUE - 1, ExerciseForm.number("9223372036854775806", false));
	}

	@Test
	void distinguishesOversizedPythonInputFromInvalidContent() {
		assertThrows(PythonExecutionInput.TooLargeException.class,
				() -> PythonExecutionInput.validateSource("a".repeat(65537)));
		assertThrows(PythonExecutionInput.TooLargeException.class,
				() -> PythonExecutionInput.validateStandardInput("a".repeat(8193)));
		assertThrows(IllegalArgumentException.class, () -> PythonExecutionInput.validateSource(null));
		assertThrows(IllegalArgumentException.class, () -> PythonExecutionInput.validateSource("\0"));
	}
}
