package entity;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public record TeacherDistributionInput(
		long templateId,
		int expectedVersion,
		String name,
		String rootName,
		List<TemplateItem> items,
		List<Target> targets,
		String requestToken) {

	public record TemplateItem(String path, StudentExerciseEntry.Type type, String content) {}
	public record Target(long classroomId, LocalDateTime scheduledAt) {}

	public TeacherDistributionInput {
		if (templateId < 0 || expectedVersion < 0 || (templateId == 0 && expectedVersion != 0)) {
			throw new IllegalArgumentException("テンプレートの更新情報が不正です。画面を読み込み直してください。");
		}
		try {
			java.util.UUID.fromString(requestToken);
		} catch (RuntimeException failure) {
			throw new IllegalArgumentException("送信情報が不正です。画面を読み込み直してください。", failure);
		}
		if (name == null || name.isBlank() || name.codePointCount(0, name.length()) > 255) {
			throw new IllegalArgumentException("テンプレート名は1〜255文字で入力してください。");
		}
		StudentExerciseInput.validateName(name);
		if (rootName == null) {
			throw new IllegalArgumentException("ルートフォルダ名を入力してください。");
		}
		rootName = new StudentExerciseInput(StudentExerciseEntry.Type.FOLDER, rootName).name();
		if (rootName.codePointCount(0, rootName.length()) > 230) {
			throw new IllegalArgumentException("再配信時の名前を確保するため、ルートフォルダ名は230文字以内にしてください。");
		}
		items = normalizeItems(items);
		for (TemplateItem item : items) {
			if ((long) rootName.codePointCount(0, rootName.length()) + 1
					+ item.path().codePointCount(0, item.path().length())
					> StudentExerciseInput.MAX_PATH_CHARACTERS) {
				throw new IllegalArgumentException("配信後のファイルパスは1000文字以内にしてください。");
			}
		}
		targets = targets == null ? List.of() : List.copyOf(targets);
		if (targets.size() > 100) {
			throw new IllegalArgumentException("配信先は100クラス以内にしてください。");
		}
		var classrooms = new java.util.HashSet<Long>();
		for (Target target : targets) {
			if (target == null || target.classroomId() < 1 || !classrooms.add(target.classroomId())) {
				throw new IllegalArgumentException("配信先クラスが重複または不正です。");
			}
			if (target.scheduledAt() != null && !target.scheduledAt().isAfter(LocalDateTime.now())) {
				throw new IllegalArgumentException("配信日時は現在より後に設定してください。");
			}
		}
	}

	private static List<TemplateItem> normalizeItems(List<TemplateItem> input) {
		if (input == null || input.isEmpty() || input.size() > 500) {
			throw new IllegalArgumentException("テンプレート項目は1〜500件にしてください。");
		}
		Map<String, TemplateItem> normalized = new HashMap<>();
		for (TemplateItem item : input) {
			if (item == null || item.type() == null || item.path() == null || item.path().isBlank()) {
				throw new IllegalArgumentException("フォルダ・ファイルのパスと種類を指定してください。");
			}
			String[] segments = item.path().split("/", -1);
			if (segments.length > 100) {
				throw new IllegalArgumentException("フォルダ階層が深すぎます。");
			}
			List<String> path = new ArrayList<>();
			for (int index = 0; index < segments.length; index++) {
				boolean isFile = index == segments.length - 1
						&& item.type() == StudentExerciseEntry.Type.FILE;
				StudentExerciseEntry.Type type = isFile
						? StudentExerciseEntry.Type.FILE : StudentExerciseEntry.Type.FOLDER;
				String segment = new StudentExerciseInput(type, segments[index]).name();
				path.add(segment);
				String joined = String.join("/", path);
				if (joined.codePointCount(0, joined.length()) > StudentExerciseInput.MAX_PATH_CHARACTERS) {
					throw new IllegalArgumentException("テンプレートのパスは1000文字以内にしてください。");
				}
				String key = joined.toLowerCase(Locale.ROOT);
				String content = isFile ? item.content() : null;
				if (isFile) {
					content = StudentExerciseInput.validateCode(content == null ? "" : content);
					if (content.getBytes(StandardCharsets.UTF_8).length > StudentExerciseInput.MAX_CODE_BYTES) {
						throw new IllegalArgumentException("ファイルのコードが上限を超えています。");
					}
				}
				TemplateItem current = new TemplateItem(joined, type, content);
				TemplateItem previous = normalized.putIfAbsent(key, current);
				if (previous != null && (previous.type() != type
						|| (isFile && !previous.content().equals(content)))) {
					throw new IllegalArgumentException("同じパスに異なる項目が指定されています。");
				}
			}
		}
		if (normalized.size() > 500) {
			throw new IllegalArgumentException("フォルダを含むテンプレート項目は500件以内にしてください。");
		}
		return normalized.values().stream()
				.sorted(java.util.Comparator.comparingInt((TemplateItem item) -> item.path().split("/").length)
						.thenComparing(TemplateItem::path, String.CASE_INSENSITIVE_ORDER))
				.toList();
	}
}
