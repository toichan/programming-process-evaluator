package control.auth;

import java.util.UUID;

public record RequestMetadata(String ipAddress, String userAgent, String requestId, String sessionId) {
	public static RequestMetadata from(String ipAddress, String userAgent) {
		return new RequestMetadata(ipAddress, userAgent, UUID.randomUUID().toString(), null);
	}

	public RequestMetadata withSessionId(String currentSessionId) {
		return new RequestMetadata(ipAddress, userAgent, requestId, currentSessionId);
	}
}
