package control.auth;

import java.io.Serializable;

import entity.UserCredential.UserType;

public record AuthenticatedUser(
		long userId,
		String loginId,
		String displayName,
		UserType userType,
		boolean passwordChangeRequired,
		String sessionAuditId) implements Serializable {

	private static final long serialVersionUID = 1L;

	public AuthenticatedUser withPasswordChangeRequired(boolean required) {
		return new AuthenticatedUser(userId, loginId, displayName, userType, required, sessionAuditId);
	}
}
