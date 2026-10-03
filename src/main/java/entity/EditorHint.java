package entity;

public final class EditorHint {
	private final String title;
	private final String content;
	private final String usageSyntax;
	private final String code;

	public EditorHint(String title, String content, String usageSyntax, String code) {
		this.title = title;
		this.content = content;
		this.usageSyntax = usageSyntax;
		this.code = code;
	}

	public String getTitle() {
		return title;
	}

	public String getContent() {
		return content;
	}

	public String getUsageSyntax() {
		return usageSyntax;
	}

	public String getCode() {
		return code;
	}
}
