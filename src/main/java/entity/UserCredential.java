package entity;

import java.util.Objects;
import java.util.Optional;
import java.time.LocalDateTime;

public record UserCredential(
		long userId,
		UserType userType,
		String loginId,
		String passwordHash,
		String displayName,
		AccountStatus accountStatus,
		int consecutiveLoginFailures,
		Optional<LocalDateTime> loginLockedUntil,
		Optional<StudentAccountProfile> studentProfile) {

	public UserCredential {
		Objects.requireNonNull(userType, "userType");
		Objects.requireNonNull(loginId, "loginId");
		Objects.requireNonNull(passwordHash, "passwordHash");
		Objects.requireNonNull(displayName, "displayName");
		Objects.requireNonNull(accountStatus, "accountStatus");
		Objects.requireNonNull(loginLockedUntil, "loginLockedUntil");
		Objects.requireNonNull(studentProfile, "studentProfile");
	}

	@Override
	public String toString() {
		return "UserCredential[userId=" + userId + ", userType=" + userType + ", loginId=" + loginId
				+ ", accountStatus=" + accountStatus + "]";
	}

	public enum UserType {
		STUDENT,
		TEACHER,
		ADMIN
	}

	public enum AccountStatus {
		ACTIVE,
		SUSPENDED,
		DELETED
	}

	public enum FirstLoginStatus {
		NOT_LOGGED_IN,
		PASSWORD_CHANGE_REQUIRED,
		COMPLETED
	}
}
