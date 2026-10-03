package entity;

import java.util.Locale;

public enum ConsentStatus {
	UNCONFIRMED("unconfirmed"),
	AGREED("agreed"),
	DECLINED("declined"),
	WITHDRAWN("withdrawn");

	private final String databaseValue;

	ConsentStatus(String databaseValue) {
		this.databaseValue = databaseValue;
	}

	public String getDatabaseValue() {
		return databaseValue;
	}

	public static ConsentStatus fromDatabase(String value) {
		return valueOf(value.toUpperCase(Locale.ROOT));
	}
}
