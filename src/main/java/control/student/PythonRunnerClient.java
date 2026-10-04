package control.student;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

import com.google.gson.Gson;

import entity.PythonExecutionResult;

final class PythonRunnerClient {
	private static final Gson GSON = new Gson();
	private final HttpClient httpClient;
	private final URI runnerUri;
	private final URI executionUri;

	PythonRunnerClient() {
		this(configuredRunnerUri());
	}

	private static URI configuredRunnerUri() {
		String runnerUrl = System.getenv("PYTHON_RUNNER_URL");
		if (runnerUrl == null || runnerUrl.isBlank()) {
			throw new IllegalStateException("PYTHON_RUNNER_URL must be configured.");
		}
		return URI.create(runnerUrl.replaceAll("/+$", ""));
	}

	PythonRunnerClient(URI runnerUri) {
		this.runnerUri = runnerUri;
		this.executionUri = runnerUri.resolve("/execute");
		this.httpClient = HttpClient.newBuilder()
				.connectTimeout(Duration.ofSeconds(2))
				.build();
	}

	PythonExecutionResult execute(String source, String standardInput)
			throws IOException, InterruptedException {
		String requestBody = GSON.toJson(new ExecutionRequest(source, standardInput));
		HttpRequest request = HttpRequest.newBuilder(executionUri)
				.timeout(Duration.ofSeconds(65))
				.header("Content-Type", "application/json; charset=UTF-8")
				.header("Accept", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(requestBody))
				.build();
		HttpResponse<String> response = httpClient.send(
				request, HttpResponse.BodyHandlers.ofString(java.nio.charset.StandardCharsets.UTF_8));
		if (response.statusCode() != 200) {
			throw new IOException("The isolated Python execution service returned HTTP "
					+ response.statusCode() + ".");
		}

		RunnerResponse result = GSON.fromJson(response.body(), RunnerResponse.class);
		if (result == null || result.status == null
				|| result.standardOutput == null || result.standardError == null) {
			throw new IOException("The isolated Python execution service returned an invalid response.");
		}
		if ("unavailable".equals(result.status)) {
			throw new IOException("The isolated Python execution service is unavailable.");
		}
		return new PythonExecutionResult(
				result.status,
				result.exitCode,
				result.standardOutput,
				result.standardError,
				result.standardOutputTruncated,
				result.standardErrorTruncated,
				result.errorCode);
	}

	TimedResult executeTimed(String source, String standardInput) throws IOException, InterruptedException {
		long startedAt = System.nanoTime();
		PythonExecutionResult result = execute(source, standardInput);
		int duration = (int) Math.min(
				Integer.MAX_VALUE, Math.max(0, (System.nanoTime() - startedAt) / 1_000_000));
		return new TimedResult(result, duration);
	}

	record TimedResult(PythonExecutionResult result, int durationMilliseconds) {}

	String startSession(String source) throws IOException, InterruptedException {
		RunnerSessionResponse result = sendSessionRequest(
				runnerUri.resolve("/sessions"),
				"POST",
				GSON.toJson(new ExecutionSessionRequest(source)),
				201);
		if (result.sessionId == null || !result.sessionId.matches("[0-9a-f]{32}")) {
			throw new IOException("The isolated Python execution service returned an invalid session.");
		}
		return result.sessionId;
	}

	RunnerSessionResponse pollSession(String sessionId, long cursor)
			throws IOException, InterruptedException {
		if (cursor < 0) {
			throw new IllegalArgumentException("The execution event cursor is invalid.");
		}
		return sendSessionRequest(
				runnerUri.resolve("/sessions/" + sessionId + "?after=" + cursor),
				"GET",
				null,
				200);
	}

	void sendSessionInput(String sessionId, String line) throws IOException, InterruptedException {
		sendSessionRequest(
				runnerUri.resolve("/sessions/" + sessionId + "/input"),
				"POST",
				GSON.toJson(new InputRequest(line)),
				202);
	}

	void cancelSession(String sessionId) throws IOException, InterruptedException {
		sendSessionRequest(
				runnerUri.resolve("/sessions/" + sessionId + "/cancel"),
				"POST",
				"{}",
				200);
	}

	private RunnerSessionResponse sendSessionRequest(URI uri, String method, String body, int expectedStatus)
			throws IOException, InterruptedException {
		HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
				.timeout(Duration.ofSeconds(5))
				.header("Accept", "application/json");
		if (body == null) {
			builder.GET();
		} else {
			builder.header("Content-Type", "application/json; charset=UTF-8")
					.method(method, HttpRequest.BodyPublishers.ofString(body));
		}
		HttpResponse<String> response = httpClient.send(
				builder.build(), HttpResponse.BodyHandlers.ofString(java.nio.charset.StandardCharsets.UTF_8));
		if (response.statusCode() != expectedStatus) {
			if (response.statusCode() == 400 || response.statusCode() == 404
					|| response.statusCode() == 409 || response.statusCode() == 413
					|| response.statusCode() == 429 || response.statusCode() == 503) {
				com.google.gson.JsonElement parsed;
				try {
					parsed = com.google.gson.JsonParser.parseString(response.body());
				} catch (com.google.gson.JsonParseException e) {
					throw new IOException("The isolated Python execution service returned an invalid error response.", e);
				}
				if (parsed.isJsonObject()) {
					var object = parsed.getAsJsonObject();
					var code = object.has("errorCode") ? object.get("errorCode") : object.get("error");
					if (code != null && code.isJsonPrimitive() && code.getAsJsonPrimitive().isString()
							&& code.getAsString().matches("[a-z_]{1,64}")) {
						throw new PythonRunnerRequestException(response.statusCode(), code.getAsString());
					}
				}
			}
			throw new IOException("The isolated Python execution service returned HTTP "
					+ response.statusCode() + ".");
		}
		RunnerSessionResponse result = GSON.fromJson(response.body(), RunnerSessionResponse.class);
		if (result == null) {
			throw new IOException("The isolated Python execution service returned an invalid session response.");
		}
		return result;
	}

	private record ExecutionRequest(String source, String standardInput) {
	}

	private record ExecutionSessionRequest(String source) {
	}

	private record InputRequest(String line) {
	}

	private static final class RunnerResponse {
		private String status;
		private Integer exitCode;
		private String standardOutput;
		private String standardError;
		private boolean standardOutputTruncated;
		private boolean standardErrorTruncated;
		private String errorCode;
	}

	static final class RunnerSessionResponse {
		String sessionId;
		String status;
		Integer exitCode;
		String errorCode;
		List<RunnerEvent> events;
		long nextCursor;
		String standardInput;
		String standardOutput;
		String standardError;
		boolean standardOutputTruncated;
		boolean standardErrorTruncated;
		long durationMilliseconds;
	}

	static final class RunnerEvent {
		long id;
		String stream;
		String text;
	}
}
