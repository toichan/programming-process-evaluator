package entity;

public final class ConsentDocument {
	private final long id;
	private final String versionCode;
	private final String title;
	private final String body;

	public ConsentDocument(long id, String versionCode, String title, String body) {
		this.id = id;
		this.versionCode = versionCode;
		this.title = title;
		this.body = body;
	}

	public long getId() {
		return id;
	}

	public String getVersionCode() {
		return versionCode;
	}

	public String getTitle() {
		return title;
	}

	public String getBody() {
		return body;
	}
}
