package entity;

import entity.UserCredential.FirstLoginStatus;

public record StudentAccountProfile(
		int securityLevel,
		FirstLoginStatus firstLoginStatus,
		boolean mustChangePassword) {
}
