package entity;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class PythonExecutionInputTest {
	@Test
	void preservesExistingEditorLimitsAndMessages() {
		assertEquals("", PythonExecutionInput.validateSource(""));
		assertEquals("a".repeat(65536), PythonExecutionInput.validateSource("a".repeat(65536)));
		assertEquals("a".repeat(8192), PythonExecutionInput.validateStandardInput("a".repeat(8192)));
		assertEquals("コードは64 KiB以下で入力してください。",
				assertThrows(IllegalArgumentException.class,
						() -> PythonExecutionInput.validateSource("a".repeat(65537))).getMessage());
		assertEquals("入力は8 KiB以下で指定してください。",
				assertThrows(IllegalArgumentException.class,
						() -> PythonExecutionInput.validateStandardInput("a".repeat(8193))).getMessage());
		for (String text : new String[] {null, "a\0b"}) {
			assertThrows(IllegalArgumentException.class, () -> PythonExecutionInput.validateSource(text));
			assertThrows(IllegalArgumentException.class, () -> PythonExecutionInput.validateStandardInput(text));
		}
	}

	@Test
	void sharesUtf8ByteLimitsWithoutChangingExistingEditorUnicodePolicy() {
		assertEquals("学".repeat(21845), PythonExecutionInput.validateSource("学".repeat(21845)));
		assertThrows(IllegalArgumentException.class,
				() -> PythonExecutionInput.validateSource("学".repeat(21846)));
		assertEquals("\ud800", PythonExecutionInput.validateSource("\ud800"));
		assertThrows(IllegalArgumentException.class, () -> StudentExerciseInput.validateCode("\ud800"));
	}
}
