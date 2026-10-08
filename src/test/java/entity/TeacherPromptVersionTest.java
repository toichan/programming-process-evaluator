package entity;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;
import com.google.gson.JsonObject;

class TeacherPromptVersionTest {
	private static final TeacherPromptVersion.FluctuationItem RESOLVED =
			new TeacherPromptVersion.FluctuationItem(1, "Title", "low", "Description", "Example", "Response", "resolved", 1);
	private static final TeacherPromptVersion.EvaluationExample ACTIVE =
			new TeacherPromptVersion.EvaluationExample(1, new JsonObject(), new JsonObject(), "active");

	@Test
	void configuredAndVersionedPromptsWithCompletedArtifactsAreReady() {
		assertTrue(version("configured", "completed", "completed", List.of(RESOLVED), List.of(ACTIVE)).isReadyForApplication());
		assertTrue(version("versioned", "completed", "completed", List.of(RESOLVED), List.of(ACTIVE)).isReadyForApplication());
	}

	@Test
	void incompleteOrInactiveArtifactsCannotBeApplied() {
		assertFalse(version("draft", "completed", "completed", List.of(RESOLVED), List.of(ACTIVE)).isReadyForApplication());
		assertFalse(version("configured", "in_progress", "completed", List.of(RESOLVED), List.of(ACTIVE)).isReadyForApplication());
		assertFalse(version("configured", "completed", "failed", List.of(RESOLVED), List.of(ACTIVE)).isReadyForApplication());
		assertFalse(version("configured", "completed", "completed", List.of(), List.of(ACTIVE)).isReadyForApplication());
		var pending = new TeacherPromptVersion.FluctuationItem(1, "Title", "low", "Description", "Example", "", "pending", 1);
		assertFalse(version("configured", "completed", "completed", List.of(pending), List.of(ACTIVE)).isReadyForApplication());
		assertFalse(version("configured", "completed", "completed", List.of(RESOLVED), List.of()).isReadyForApplication());
		var inactive = new TeacherPromptVersion.EvaluationExample(1, new JsonObject(), new JsonObject(), "inactive");
		assertFalse(version("configured", "completed", "completed", List.of(RESOLVED), List.of(inactive)).isReadyForApplication());
	}

	private static TeacherPromptVersion version(String status, String fluctuations, String examples,
			List<TeacherPromptVersion.FluctuationItem> items, List<TeacherPromptVersion.EvaluationExample> outputs) {
		return new TeacherPromptVersion(1, 1, "v1", "gemini-3.1-pro-preview", "Prompt", "", status,
				fluctuations, examples, 1, "teacher", null, "teacher", null, items, outputs);
	}
}
