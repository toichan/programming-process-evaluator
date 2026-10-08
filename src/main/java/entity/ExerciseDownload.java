package entity;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public record ExerciseDownload(
		List<StudentExerciseEntry> entries, String fileName, String contentType, boolean archive,
		int pathPrefixAllowance) {
	private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

	public ExerciseDownload(List<StudentExerciseEntry> entries) {
		this(entries, "exercise.zip", "application/zip", true);
	}

	public ExerciseDownload(List<StudentExerciseEntry> entries, String fileName, String contentType, boolean archive) {
		this(entries, fileName, contentType, archive, 0);
	}

	public static String archiveName(String rootName) {
		StudentExerciseInput.validateName(rootName);
		return rootName + ".zip";
	}

	public static String contentDisposition(String rootName) {
		return contentDisposition(archiveName(rootName), true);
	}

	public static ExerciseDownload selected(String rootName, List<StudentExerciseEntry> entries) {
		return selected(rootName, entries, entries, LocalDateTime.now(ZoneId.of("Asia/Tokyo")));
	}

	public static ExerciseDownload selected(String rootName, List<StudentExerciseEntry> entries,
			LocalDateTime tokyoNow) {
		return selected(rootName, entries, entries, tokyoNow);
	}

	public static ExerciseDownload selected(String rootName, List<StudentExerciseEntry> selectedRoots,
			List<StudentExerciseEntry> entries) {
		return selected(rootName, selectedRoots, entries, LocalDateTime.now(ZoneId.of("Asia/Tokyo")));
	}

	public static ExerciseDownload selected(String rootName, List<StudentExerciseEntry> selectedRoots,
			List<StudentExerciseEntry> entries, LocalDateTime tokyoNow) {
		if (selectedRoots == null || selectedRoots.isEmpty() || entries == null || entries.isEmpty()) {
			throw new IllegalArgumentException("ダウンロード対象を指定してください。");
		}
		if (selectedRoots.size() == 1 && selectedRoots.get(0).type() == StudentExerciseEntry.Type.FILE) {
			String name = selectedRoots.get(0).name();
			return new ExerciseDownload(entries, name, "text/x-python; charset=UTF-8", false);
		}
		boolean hasFolder = selectedRoots.stream()
				.anyMatch(entry -> entry.type() == StudentExerciseEntry.Type.FOLDER);
		String name;
		if (selectedRoots.size() == 1 && hasFolder) {
			name = archiveName(selectedRoots.get(0).name());
		} else if (hasFolder) {
			name = archiveName(rootName);
		} else {
			StudentExerciseInput.validateName(rootName);
			name = rootName + "_" + TIMESTAMP.format(tokyoNow) + ".zip";
		}
		return new ExerciseDownload(entries, name, "application/zip", true);
	}

	public static String contentDisposition(String fileName, boolean archive) {
		StudentExerciseInput.validateName(fileName);
		String fallback = fileName.chars().allMatch(c -> c >= 32 && c < 127)
				? fileName : archive ? "exercise.zip" : "exercise.py";
		String encoded = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20")
				.replace("*", "%2A");
		return "attachment; filename=\"" + fallback.replace("\"", "\\\"") + "\"; filename*=UTF-8''" + encoded;
	}

	public ExerciseDownload {
		if (fileName == null || contentType == null) throw new IllegalArgumentException("ダウンロード情報が正しくありません。");
		if (pathPrefixAllowance < 0 || pathPrefixAllowance > 2 * (StudentExerciseInput.MAX_NAME_CHARACTERS + 1)
				|| !archive && pathPrefixAllowance != 0)
			throw new IllegalArgumentException("ダウンロード対象のパスが正しくありません。");
		StudentExerciseInput.validateName(fileName);
		entries = List.copyOf(entries);
		var paths = new HashSet<String>();
		for (var entry : entries) {
			if (entry.status() != StudentExerciseEntry.Status.ACTIVE || entry.path().startsWith("/")
					|| entry.path().indexOf('\\') >= 0
					|| entry.path().codePointCount(0, entry.path().length()) > StudentExerciseInput.MAX_PATH_CHARACTERS + pathPrefixAllowance
					|| !paths.add(entry.path())) {
				throw new IllegalArgumentException("ダウンロード対象のパスが正しくありません。");
			}
			for (String part : entry.path().split("/", -1)) {
				StudentExerciseInput.validateName(part);
			}
			if (entry.type() == StudentExerciseEntry.Type.FILE) StudentExerciseInput.validateCode(entry.content());
		}
	}

	public String contentDisposition() {
		return contentDisposition(fileName, archive);
	}

	public void write(OutputStream output) throws IOException {
		if (archive) {
			writeZip(output);
		} else {
			output.write(entries.get(0).content().getBytes(StandardCharsets.UTF_8));
		}
	}

	public void writeZip(OutputStream output) throws IOException {
		try (var zip = new ZipOutputStream(output, StandardCharsets.UTF_8)) {
			for (var entry : entries) {
				boolean folder = entry.type() == StudentExerciseEntry.Type.FOLDER;
				zip.putNextEntry(new ZipEntry(entry.path() + (folder ? "/" : "")));
				if (!folder) zip.write(entry.content().getBytes(StandardCharsets.UTF_8));
				zip.closeEntry();
			}
		}
	}
}
