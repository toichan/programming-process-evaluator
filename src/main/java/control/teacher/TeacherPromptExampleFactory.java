package control.teacher;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

public final class TeacherPromptExampleFactory {
	private TeacherPromptExampleFactory() {}

	public static JsonArray createSyntheticSamples(JsonObject taskContext) {
		if (taskContext == null || taskContext.size() == 0) {
			throw new IllegalArgumentException("Task context is required to create synthetic examples.");
		}
		JsonArray samples = new JsonArray();
		samples.add(sample("synthetic-001", "task-compliant", "課題の要件と例を満たす基本的な実装"));
		samples.add(sample("synthetic-002", "boundary-case", "基本要件を満たすが、境界条件に課題が残る実装"));
		return samples;
	}

	private static JsonObject sample(String id, String profile, String description) {
		JsonObject sample = new JsonObject();
		sample.addProperty("sample_id", id);
		sample.addProperty("profile", profile);
		sample.addProperty("description", description);
		return sample;
	}
}
