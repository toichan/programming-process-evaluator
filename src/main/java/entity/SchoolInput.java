package entity;

public record SchoolInput(String name, int securityLevel) {
	public SchoolInput {
		if (name == null || name.isBlank()) {
			throw new IllegalArgumentException("学校名を入力してください。");
		}
		name = name.strip();
		if (name.codePointCount(0, name.length()) > 200) {
			throw new IllegalArgumentException("学校名は200文字以内で入力してください。");
		}
		if (securityLevel != 1 && securityLevel != 2) {
			throw new IllegalArgumentException("セキュリティレベル1または2を選択してください。");
		}
	}
}
