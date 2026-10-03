package entity;

public record SchoolDetails(long id, String code, String name, Integer securityLevel,
		boolean securityLevelLocked, long version) {
	public long getId() { return id; }
	public String getCode() { return code; }
	public String getName() { return name; }
	public Integer getSecurityLevel() { return securityLevel; }
	public boolean isSecurityLevelLocked() { return securityLevelLocked; }
	public long getVersion() { return version; }
}
