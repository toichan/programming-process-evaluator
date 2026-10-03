package entity;

import java.util.List;

import entity.UserCredential.FirstLoginStatus;

public final class StudentAccountDetails {
	private final String studentCode;
	private final int securityLevel;
	private final FirstLoginStatus firstLoginStatus;
	private final boolean mustChangePassword;
	private final List<StudentAffiliation> affiliations;
	private final List<StudentCredentialHistoryEntry> credentialHistory;

	public StudentAccountDetails(
			String studentCode,
			int securityLevel,
			FirstLoginStatus firstLoginStatus,
			boolean mustChangePassword,
			List<StudentAffiliation> affiliations,
			List<StudentCredentialHistoryEntry> credentialHistory) {
		this.studentCode = studentCode;
		this.securityLevel = securityLevel;
		this.firstLoginStatus = firstLoginStatus;
		this.mustChangePassword = mustChangePassword;
		this.affiliations = List.copyOf(affiliations);
		this.credentialHistory = List.copyOf(credentialHistory);
	}

	public String getStudentCode() {
		return studentCode;
	}

	public int getSecurityLevel() {
		return securityLevel;
	}

	public FirstLoginStatus getFirstLoginStatus() {
		return firstLoginStatus;
	}

	public boolean isMustChangePassword() {
		return mustChangePassword;
	}

	public List<StudentAffiliation> getAffiliations() {
		return affiliations;
	}

	public List<StudentCredentialHistoryEntry> getCredentialHistory() {
		return credentialHistory;
	}
}
