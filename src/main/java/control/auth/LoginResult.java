package control.auth;

import entity.UserCredential;

public record LoginResult(UserCredential user, String failureCode, String sessionAuditId) {
	public boolean authenticated() {
		return user != null;
	}

	public static LoginResult success(UserCredential user, String sessionAuditId) {
		return new LoginResult(user, null, sessionAuditId);
	}

	public static LoginResult failure(String failureCode) {
		return new LoginResult(null, failureCode, null);
	}
}
