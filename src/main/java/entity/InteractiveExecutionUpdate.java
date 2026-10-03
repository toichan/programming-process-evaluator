package entity;

import java.util.List;

public record InteractiveExecutionUpdate(
		String sessionId,
		String status,
		Integer exitCode,
		String errorCode,
		List<Event> events,
		long nextCursor,
		String standardInput,
		String standardOutput,
		String standardError,
		boolean standardOutputTruncated,
		boolean standardErrorTruncated,
		long durationMilliseconds) {
	public record Event(long id, String stream, String text) {
	}
}
