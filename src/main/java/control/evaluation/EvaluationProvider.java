package control.evaluation;

import com.google.gson.JsonObject;

public interface EvaluationProvider {
	JsonObject generate(String modelId, JsonObject payload) throws EvaluationProviderException;

	String extractOutputText(JsonObject response) throws EvaluationProviderException;
}
