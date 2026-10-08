package entity;

import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;

public record StudentAccountFilter(String query, long schoolId, long classroomId, String security,
		String firstLogin, String consent, String status) implements Predicate<ManagedStudentAccount> {
	public StudentAccountFilter {
		query = query == null ? "" : query.strip();
		if (query.length() > 64 || schoolId < 0 || classroomId < 0) throw new IllegalArgumentException("検索条件が不正です。");
		security = choice(security, List.of("", "1", "2"));
		firstLogin = choice(firstLogin, List.of("", "completed", "pending", "not_required"));
		consent = choice(consent, List.of("", "agreed", "declined", "withdrawn", "unconfirmed"));
		status = choice(status, List.of("", "active", "suspended", "deleted"));
	}
	@Override public boolean test(ManagedStudentAccount account) {
		return account.loginId().toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT))
				&& (schoolId == 0 || schoolId == account.schoolId())
				&& (classroomId == 0 || account.classrooms().stream().anyMatch(row -> row.classroomId() == classroomId))
				&& (security.isEmpty() || security.equals(Integer.toString(account.securityLevel())))
				&& (firstLogin.isEmpty() || firstLogin.equals(account.securityLevel() == 1 ? "not_required"
						: account.mustChangePassword() || !"completed".equals(account.firstLoginStatus()) ? "pending" : "completed"))
				&& (consent.isEmpty() || consent.equals(account.consent()))
				&& (status.isEmpty() || status.equals(account.status()));
	}
	private static String choice(String value, List<String> allowed) {
		value = value == null ? "" : value;
		if (!allowed.contains(value)) throw new IllegalArgumentException("検索条件が不正です。");
		return value;
	}
}
