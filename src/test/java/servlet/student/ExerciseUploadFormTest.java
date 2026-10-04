package servlet.student;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class ExerciseUploadFormTest {
	@Test
	void decodesStrictUtf8WithoutChangingContents() {
		String code = "print('学')\n";
		String encoded = Base64.getEncoder().encodeToString(code.getBytes(StandardCharsets.UTF_8));
		assertEquals(code, ExerciseUploadForm.read(
				"[{\"path\":\"x.PY\",\"content\":\"" + encoded + "\"}]").files().get(0).code());
	}

	@Test
	void rejectsMalformedFileShapesAndEncoding() {
		for (String value : new String[] {"null", "{}", "[1]", "[{}]", "[{\"path\":1,\"content\":\"\"}]",
				"[{\"path\":\"x.py\",\"content\":\"%%%\"}]", "[{\"path\":\"x.py\",\"content\":\"/w==\"}]", "["}) {
			assertThrows(IllegalArgumentException.class, () -> ExerciseUploadForm.read(value));
		}
		assertThrows(IllegalArgumentException.class, () -> ExerciseUploadForm.read(null));
	}
}
