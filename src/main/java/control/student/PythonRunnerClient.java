package control.student;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;

import entity.PythonExecutionResult;

public final class PythonRunnerClient {
	private static final Gson GSON = new Gson();
	private final HttpClient httpClient;
	private final URI runnerUri;
	private final URI executionUri;
	private final String token;

	public PythonRunnerClient() {
		this(configuredRunnerUri(), configuredToken(System.getenv()));
	}

	private static URI configuredRunnerUri() {
		String runnerUrl = System.getenv("PYTHON_RUNNER_URL");
		if (runnerUrl == null || runnerUrl.isBlank()) {
			throw new IllegalStateException("PYTHON_RUNNER_URL must be configured.");
		}
		return URI.create(runnerUrl.replaceAll("/+$", ""));
	}

	PythonRunnerClient(URI runnerUri) {
		this(runnerUri, "");
	}

	PythonRunnerClient(URI runnerUri, String token) {
		this.runnerUri = runnerUri;
		this.executionUri = runnerUri.resolve("/execute");
		this.token = token;
		this.httpClient = HttpClient.newBuilder()
				.connectTimeout(Duration.ofSeconds(2))
				.build();
	}

	static String configuredToken(Map<String, String> environment) {
		String file = environment.get("PYTHON_RUNNER_TOKEN_FILE");
		String value;
		if (file != null && !file.isBlank()) {
			try (var input = Files.newInputStream(Path.of(file))) {
				byte[] bytes = input.readNBytes(4097);
				if (bytes.length > 4096) {
					throw new IllegalStateException("The runner token is invalid.");
				}
				value = new String(bytes, StandardCharsets.UTF_8).strip();
			} catch (IOException | java.nio.file.InvalidPathException e) {
				throw new IllegalStateException("The runner token file cannot be read.", e);
			}
		} else {
			value = environment.getOrDefault("PYTHON_RUNNER_TOKEN", "").strip();
		}
		if (!value.isEmpty() && (value.length() > 4096
				|| !value.chars().allMatch(character -> character >= 33 && character <= 126))) {
			throw new IllegalStateException("The runner token is invalid.");
		}
		if (value.isEmpty() && ((file != null && !file.isBlank())
				|| "production".equals(environment.get("PPE_ENV")))) {
			throw new IllegalStateException("The runner token must be configured.");
		}
		return value;
	}

	private HttpRequest.Builder authorizedRequest(URI uri) {
		HttpRequest.Builder builder = HttpRequest.newBuilder(uri);
		if (!token.isEmpty()) {
			builder.header("Authorization", "Bearer " + token);
		}
		return builder;
	}

	PythonExecutionResult execute(String source, String standardInput)
			throws IOException, InterruptedException {
		String requestBody = GSON.toJson(new ExecutionRequest(source, standardInput));
		HttpRequest request = authorizedRequest(executionUri)
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

	public PythonExecutionResult executePreview(String source) throws IOException, InterruptedException {
		return executePreview(source, "");
	}

	public PythonExecutionResult executePreview(String source, String standardInput) throws IOException, InterruptedException {
		String validatedSource = entity.StudentExerciseInput.validateCode(source);
		String validatedInput = entity.StudentExerciseInput.validateStandardInput(standardInput);
		return execute(validatedSource, validatedInput);
	}

	TimedResult executeTimed(String source, String standardInput) throws IOException, InterruptedException {
		long startedAt = System.nanoTime();
		PythonExecutionResult result = execute(source, standardInput);
		int duration = (int) Math.min(
				Integer.MAX_VALUE, Math.max(0, (System.nanoTime() - startedAt) / 1_000_000));
		return new TimedResult(result, duration);
	}

	record TimedResult(PythonExecutionResult result, int durationMilliseconds) {}

	public String startSession(String source) throws IOException, InterruptedException {
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

	public RunnerSessionResponse pollSession(String sessionId, long cursor)
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

	public void sendSessionInput(String sessionId, String line) throws IOException, InterruptedException {
		sendSessionRequest(
				runnerUri.resolve("/sessions/" + sessionId + "/input"),
				"POST",
				GSON.toJson(new InputRequest(line)),
				202);
	}

	public void cancelSession(String sessionId) throws IOException, InterruptedException {
		sendSessionRequest(
				runnerUri.resolve("/sessions/" + sessionId + "/cancel"),
				"POST",
				"{}",
				200);
	}

	private RunnerSessionResponse sendSessionRequest(URI uri, String method, String body, int expectedStatus)
			throws IOException, InterruptedException {
		HttpRequest.Builder builder = authorizedRequest(uri)
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

	public static final class RunnerSessionResponse {
		public String sessionId;
		public String status;
		public Integer exitCode;
		public String errorCode;
		public List<RunnerEvent> events;
		public long nextCursor;
		public String standardInput;
		public String standardOutput;
		public String standardError;
		public boolean standardOutputTruncated;
		public boolean standardErrorTruncated;
		public long durationMilliseconds;
	}

	public static final class RunnerEvent {
		public long id;
		public String stream;
		public String text;
	}
}
