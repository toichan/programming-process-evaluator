package control.teacher;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import control.evaluation.EvaluationProviderException;
import control.evaluation.GeminiApiDiagnosticSupport;
import control.evaluation.GeminiModelCatalog;
import control.student.StandardRubricSource;

class TeacherPromptApiDiagnosticTest {
	@Test
	void generatesTeacherFluctuationsWithStandardRubricAndOneProviderAttempt() throws Exception {
		GeminiApiDiagnosticSupport.requireEnabled();
		var rubric = StandardRubricSource.parse(
				Files.readString(Path.of("docs/rubric/思考力・判断力・表現力_ルーブリック_0805.md")),
				Files.readString(Path.of("docs/rubric/主体的に学習に取り組む態度_ルーブリック_0805.md")));
		AtomicInteger calls = new AtomicInteger();
		TeacherPromptAiClient teacher = new TeacherPromptAiClient((model, instruction, input, schema) -> {
			if (calls.incrementAndGet() > 1) {
				throw new EvaluationProviderException("Single-attempt diagnostic exhausted.", false);
			}
			try {
				var response = GeminiApiDiagnosticSupport.structured(
						"teacher-fluctuations", model, instruction, input, schema);
				var output = GeminiApiDiagnosticSupport.saveOutput("teacher-fluctuations", response);
				assertTrue(JsonParser.parseString(Files.readString(output)).getAsJsonObject()
						.has("ambiguity_items"));
				return response;
			} catch (EvaluationProviderException failure) {
				if (failure.getHttpStatusCode() == null) {
					throw new EvaluationProviderException(failure.getMessage(), false);
				}
				throw new EvaluationProviderException(failure.getMessage(), false, failure.getHttpStatusCode());
			} catch (java.io.IOException failure) {
				throw new EvaluationProviderException("Diagnostic output persistence failed.", false, failure);
			}
		});
		JsonObject task = new JsonObject();
		task.addProperty("title", "Synthetic API diagnosis");
		task.addProperty("theme", "Synthetic integer input");
		task.addProperty("difficulty", "beginner");
		task.addProperty("description", "Read one integer and print it. This is a synthetic task.");
		task.addProperty("input_constraints", "One integer from 0 to 10.");
		task.addProperty("creation_rules", "Do not use file IO.");
		task.addProperty("initial_code", "");
		JsonArray features = new JsonArray();
		features.add("Read an integer");
		features.add("Print the integer");
		task.add("features", features);
		JsonArray tests = new JsonArray();
		JsonObject test = new JsonObject();
		test.addProperty("title", "Synthetic input");
		test.addProperty("input", "1");
		test.addProperty("expected_output", "1");
		tests.add(test);
		task.add("test_cases", tests);
		var items = teacher.generateFluctuations(GeminiModelCatalog.DEFAULT_MODEL, task,
				"Evaluate only synthetic evidence using the registered rubric. Give concise reasons in Japanese.",
				null, rubric);
		assertFalse(items.isEmpty());
	}
}
