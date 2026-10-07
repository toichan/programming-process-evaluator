package servlet.teacher;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import entity.EditorHint;
import entity.EditorTestCase;
import entity.TeacherTaskInput;
import entity.TeacherTaskInput.ClassAssignmentInput;
import entity.TeacherTaskInput.HintInput;
import entity.TeacherTaskInput.LateSubmissionPolicy;

public record TeacherTaskForm(
		String action,
		String csrfToken,
		String requestToken,
		long taskId,
		long expectedVersion,
		List<Long> selectedSchoolIds,
		TeacherTaskInput input) {
	private static final Set<String> ALLOWED_FIELDS = Set.of(
			"action", "csrfToken", "requestToken", "taskId", "expectedVersion", "schoolTargets", "classTargets",
			"lateSubmissionPolicy", "taskName", "difficulty", "theme", "description", "features",
			"inputConstraints", "creationRules", "initialCode", "testCaseIds", "testCaseTitles",
			"testCaseInputs", "testCaseOutputs", "hintIds", "hintTitles", "hintContents",
			"hintUsageSyntaxes", "hintCodes", "assignmentIds", "publishAts", "dueAts",
			"publishImmediateClassIds", "dueNoneClassIds");

	public static Map<String, List<String>> read(InputStream input, int maximumBytes) throws IOException {
		byte[] body = input.readNBytes(maximumBytes + 1);
		if (body.length > maximumBytes) {
			throw new RequestTooLargeException();
		}
		String formBody = decodeUtf8(body);
		Map<String, List<String>> values = new LinkedHashMap<>();
		for (String pair : formBody.split("&")) {
			if (pair.isEmpty()) {
				continue;
			}
			int separator = pair.indexOf('=');
			String name = decodeComponent(separator < 0 ? pair : pair.substring(0, separator));
			String value = decodeComponent(separator < 0 ? "" : pair.substring(separator + 1));
			values.computeIfAbsent(name, ignored -> new ArrayList<>()).add(value);
		}
		Map<String, List<String>> immutableValues = new LinkedHashMap<>();
		values.forEach((name, entries) -> immutableValues.put(name, List.copyOf(entries)));
		return Map.copyOf(immutableValues);
	}

	public static TeacherTaskForm parse(Map<String, List<String>> values) {
		for (String name : values.keySet()) {
			if (!ALLOWED_FIELDS.contains(name)) {
				throw new IllegalArgumentException("使用できない入力項目が含まれています。");
			}
		}
		String action = requiredScalar(values, "action");
		if (!"createDraft".equals(action) && !"updateDraft".equals(action)
				&& !"publishTask".equals(action) && !"createRevision".equals(action)) {
			throw new IllegalArgumentException("この操作は現在利用できません。下書き保存を選択してください。");
		}
		String requestToken = requiredScalar(values, "requestToken");
		long taskId = number(requiredScalar(values, "taskId"), false);
		long expectedVersion = number(requiredScalar(values, "expectedVersion"), false);
		if (("createDraft".equals(action) && (taskId != 0 || expectedVersion != 0))
				|| ("publishTask".equals(action)
						&& ((taskId == 0 && expectedVersion != 0) || (taskId > 0 && expectedVersion == 0)))
				|| (("updateDraft".equals(action) || "createRevision".equals(action))
						&& (taskId == 0 || expectedVersion == 0))) {
			throw new IllegalArgumentException("課題または更新情報が不正です。画面を読み込み直してください。");
		}

		List<Long> schoolIds = positiveIds(list(values, "schoolTargets"));
		if (schoolIds.size() != 1) {
			throw new IllegalArgumentException("課題の所属学校を1校選択してください。");
		}
		List<Long> classroomIds = positiveIds(list(values, "classTargets"));
		String rawFeatures = scalar(values, "features");
		List<String> features = rawFeatures == null || rawFeatures.isEmpty()
				? List.of()
				: List.of(rawFeatures.split(";", -1));
		List<EditorTestCase> testCases = parseTestCases(values);
		List<HintInput> hints = parseHints(values);
		List<ClassAssignmentInput> assignments = parseAssignments(values, classroomIds);

		String difficultyValue = scalar(values, "difficulty");
		TeacherTaskInput.Difficulty difficulty = difficultyValue == null || difficultyValue.isEmpty()
				? null
				: TeacherTaskInput.Difficulty.fromDatabaseValue(difficultyValue);
		TeacherTaskInput input = new TeacherTaskInput(
				scalar(values, "taskName"),
				scalar(values, "theme"),
				difficulty,
				scalar(values, "description"),
				scalar(values, "inputConstraints"),
				scalar(values, "creationRules"),
				scalar(values, "initialCode"),
				features,
				testCases,
				hints,
				assignments,
				schoolIds.getFirst());
		return new TeacherTaskForm(
				action,
				requiredScalar(values, "csrfToken"),
				requestToken,
				taskId,
				expectedVersion,
				schoolIds,
				input);
	}

	private static List<EditorTestCase> parseTestCases(Map<String, List<String>> values) {
		List<String> ids = list(values, "testCaseIds");
		List<String> titles = alignedOptional(values, "testCaseTitles", ids.size(), null);
		List<String> inputs = alignedRequired(values, "testCaseInputs", ids.size());
		List<String> outputs = alignedRequired(values, "testCaseOutputs", ids.size());
		List<EditorTestCase> cases = new ArrayList<>(ids.size());
		for (int index = 0; index < ids.size(); index++) {
			cases.add(new EditorTestCase(number(ids.get(index), false), titles.get(index),
					inputs.get(index), outputs.get(index), index + 1));
		}
		return List.copyOf(cases);
	}

	private static List<HintInput> parseHints(Map<String, List<String>> values) {
		List<String> ids = list(values, "hintIds");
		List<String> titles = alignedRequired(values, "hintTitles", ids.size());
		List<String> contents = alignedRequired(values, "hintContents", ids.size());
		List<String> usages = alignedOptional(values, "hintUsageSyntaxes", ids.size(), null);
		List<String> codes = alignedOptional(values, "hintCodes", ids.size(), null);
		List<HintInput> hints = new ArrayList<>(ids.size());
		for (int index = 0; index < ids.size(); index++) {
			hints.add(new HintInput(
					number(ids.get(index), false),
					index + 1,
					new EditorHint(titles.get(index), contents.get(index), usages.get(index), codes.get(index))));
		}
		return List.copyOf(hints);
	}

	private static List<ClassAssignmentInput> parseAssignments(
			Map<String, List<String>> values,
			List<Long> classroomIds) {
		List<String> ids = alignedOptional(values, "assignmentIds", classroomIds.size(), "0");
		List<String> publishAtValues = alignedOptional(values, "publishAts", classroomIds.size(), "");
		List<String> dueAtValues = alignedOptional(values, "dueAts", classroomIds.size(), "");
		String policyValue = scalar(values, "lateSubmissionPolicy");
		if (!classroomIds.isEmpty() && (policyValue == null || policyValue.isEmpty())) {
			throw new IllegalArgumentException("期限後の提出方針を選択してください。");
		}
		LateSubmissionPolicy policy = policyValue == null || policyValue.isEmpty()
				? LateSubmissionPolicy.ALLOW
				: LateSubmissionPolicy.fromDatabaseValue(policyValue);
		List<ClassAssignmentInput> assignments = new ArrayList<>(classroomIds.size());
		for (int index = 0; index < classroomIds.size(); index++) {
			assignments.add(new ClassAssignmentInput(
					number(ids.get(index), false),
					classroomIds.get(index),
					dateTime(publishAtValues.get(index)),
					dateTime(dueAtValues.get(index)),
					policy));
		}
		return List.copyOf(assignments);
	}

	private static List<String> alignedRequired(Map<String, List<String>> values, String name, int size) {
		List<String> entries = list(values, name);
		if (entries.size() != size) {
			throw new IllegalArgumentException("繰り返し入力項目の数が一致しません。");
		}
		return entries;
	}

	private static List<String> alignedOptional(
			Map<String, List<String>> values,
			String name,
			int size,
			String defaultValue) {
		List<String> entries = list(values, name);
		if (entries.isEmpty() && size > 0) {
			return java.util.Collections.nCopies(size, defaultValue);
		}
		if (entries.size() != size) {
			throw new IllegalArgumentException("繰り返し入力項目の数が一致しません。");
		}
		return entries;
	}

	private static List<Long> positiveIds(List<String> values) {
		List<Long> ids = new ArrayList<>(values.size());
		for (String value : values) {
			ids.add(number(value, true));
		}
		return List.copyOf(ids);
	}

	private static LocalDateTime dateTime(String value) {
		if (value == null || value.isEmpty()) {
			return null;
		}
		try {
			return LocalDateTime.parse(value);
		} catch (DateTimeParseException e) {
			throw new IllegalArgumentException("公開日時または提出期限が正しくありません。", e);
		}
	}

	private static String requiredScalar(Map<String, List<String>> values, String name) {
		String value = scalar(values, name);
		if (value == null || value.isEmpty()) {
			throw new IllegalArgumentException("必須のフォーム項目が不足しています。");
		}
		return value;
	}

	private static String scalar(Map<String, List<String>> values, String name) {
		List<String> entries = list(values, name);
		if (entries.size() > 1) {
			throw new IllegalArgumentException("単一入力項目を複数指定しないでください。");
		}
		return entries.isEmpty() ? null : entries.get(0);
	}

	private static List<String> list(Map<String, List<String>> values, String name) {
		return values.getOrDefault(name, List.of());
	}

	private static long number(String value, boolean positive) {
		if (value == null || !value.matches("[0-9]+")) {
			throw new IllegalArgumentException("課題、学校、クラスまたは版番号が不正です。");
		}
		try {
			long number = Long.parseLong(value);
			if (positive && number == 0) {
				throw new NumberFormatException();
			}
			return number;
		} catch (NumberFormatException e) {
			throw new IllegalArgumentException("課題、学校、クラスまたは版番号が不正です。", e);
		}
	}

	private static String decodeComponent(String component) {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		for (int index = 0; index < component.length();) {
			char current = component.charAt(index);
			if (current == '%') {
				if (index + 2 >= component.length()) {
					throw new IllegalArgumentException("送信内容の文字コードが正しくありません。");
				}
				int high = Character.digit(component.charAt(index + 1), 16);
				int low = Character.digit(component.charAt(index + 2), 16);
				if (high < 0 || low < 0) {
					throw new IllegalArgumentException("送信内容の文字コードが正しくありません。");
				}
				bytes.write((high << 4) + low);
				index += 3;
			} else if (current == '+') {
				bytes.write(' ');
				index++;
			} else {
				int codePoint = component.codePointAt(index);
				bytes.writeBytes(new String(Character.toChars(codePoint)).getBytes(StandardCharsets.UTF_8));
				index += Character.charCount(codePoint);
			}
		}
		return decodeUtf8(bytes.toByteArray());
	}

	private static String decodeUtf8(byte[] bytes) {
		try {
			return StandardCharsets.UTF_8.newDecoder()
					.onMalformedInput(CodingErrorAction.REPORT)
					.onUnmappableCharacter(CodingErrorAction.REPORT)
					.decode(ByteBuffer.wrap(bytes))
					.toString();
		} catch (CharacterCodingException e) {
			throw new IllegalArgumentException("送信内容の文字コードが正しくありません。", e);
		}
	}

	public static final class RequestTooLargeException extends IllegalArgumentException {
		private static final long serialVersionUID = 1L;

		RequestTooLargeException() {
			super("課題フォームの送信内容が上限を超えています。");
		}
	}
}
