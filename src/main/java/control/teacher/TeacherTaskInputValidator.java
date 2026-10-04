package control.teacher;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import entity.EditorHint;
import entity.EditorTestCase;
import entity.TeacherTaskInput;
import entity.TeacherTaskInput.ClassAssignmentInput;
import entity.TeacherTaskInput.HintInput;

public final class TeacherTaskInputValidator {
	private static final int VARCHAR_LIMIT = 255;
	private static final int TEXT_BYTE_LIMIT = 65_535;

	public TeacherTaskInput validateAndNormalize(TeacherTaskInput input) {
		if (input == null) {
			throw new IllegalArgumentException("課題情報を指定してください。");
		}
		String title = varchar(input.title(), "課題名", true);
		if (title.isBlank()) {
			throw new IllegalArgumentException("課題名を入力してください。");
		}
		String theme = varchar(input.theme(), "テーマ", false);
		String description = text(input.description() == null ? "" : input.description(), "説明", false);
		String inputConstraints = text(input.inputConstraints(), "入力制約", true);
		String creationRules = text(input.creationRules(), "作成時のルール", true);
		String initialCode = longText(input.initialCode(), "初期コード");

		List<String> features = new ArrayList<>(input.features().size());
		for (String feature : input.features()) {
			features.add(text(feature, "実装機能", false));
		}

		Set<Long> testCaseIds = new HashSet<>();
		List<EditorTestCase> testCases = new ArrayList<>(input.testCases().size());
		for (int index = 0; index < input.testCases().size(); index++) {
			EditorTestCase testCase = input.testCases().get(index);
			requireNonNegativeId(testCase.getTestCaseId(), "テストケース");
			if (testCase.getTestCaseId() > 0 && !testCaseIds.add(testCase.getTestCaseId())) {
				throw new IllegalArgumentException("テストケースが重複しています。");
			}
			testCases.add(new EditorTestCase(
					testCase.getTestCaseId(),
					varchar(testCase.getTitle(), "テストケース名", false),
					text(testCase.getInput() == null ? "" : testCase.getInput(), "テスト入力", false),
					text(testCase.getExpectedOutput() == null ? "" : testCase.getExpectedOutput(), "期待出力", false),
					index + 1));
		}

		Set<Long> hintIds = new HashSet<>();
		List<HintInput> hints = new ArrayList<>(input.hints().size());
		for (int index = 0; index < input.hints().size(); index++) {
			HintInput hint = input.hints().get(index);
			requireNonNegativeId(hint.hintId(), "ヒント");
			if (hint.hintId() > 0 && !hintIds.add(hint.hintId())) {
				throw new IllegalArgumentException("ヒントが重複しています。");
			}
			EditorHint value = hint.hint();
			hints.add(new HintInput(
					hint.hintId(),
					index + 1,
					new EditorHint(
							varchar(value.getTitle() == null ? "" : value.getTitle(), "ヒント名", true),
							text(value.getContent() == null ? "" : value.getContent(), "ヒント本文", false),
							text(value.getUsageSyntax(), "ヒント構文", true),
							longText(value.getCode(), "ヒントコード"))));
		}

		Set<Long> assignmentIds = new HashSet<>();
		Set<Long> classroomIds = new HashSet<>();
		List<ClassAssignmentInput> assignments = new ArrayList<>(input.classAssignments().size());
		for (ClassAssignmentInput assignment : input.classAssignments()) {
			requireNonNegativeId(assignment.assignmentId(), "クラス割当");
			if (assignment.classroomId() < 1 || !classroomIds.add(assignment.classroomId())) {
				throw new IllegalArgumentException("クラス割当には異なる有効なクラスを指定してください。");
			}
			if (assignment.assignmentId() > 0 && !assignmentIds.add(assignment.assignmentId())) {
				throw new IllegalArgumentException("クラス割当が重複しています。");
			}
			LocalDateTime publishAt = assignment.publishAt();
			LocalDateTime dueAt = assignment.dueAt();
			if (publishAt != null && dueAt != null && !dueAt.isAfter(publishAt)) {
				throw new IllegalArgumentException("提出期限は公開予定日時より後にしてください。");
			}
			assignments.add(assignment);
		}

		return new TeacherTaskInput(
				title,
				theme,
				input.difficulty(),
				description,
				inputConstraints,
				creationRules,
				initialCode,
				features,
				testCases,
				hints,
				assignments);
	}

	private static String varchar(String value, String label, boolean required) {
		if (value == null) {
			if (required) {
				throw new IllegalArgumentException(label + "を入力してください。");
			}
			return null;
		}
		validateTextEncoding(value, label);
		if (value.codePointCount(0, value.length()) > VARCHAR_LIMIT) {
			throw new IllegalArgumentException(label + "は255文字以内で入力してください。");
		}
		return value;
	}

	private static String text(String value, String label, boolean nullable) {
		if (value == null) {
			if (nullable) {
				return null;
			}
			throw new IllegalArgumentException(label + "を指定してください。");
		}
		validateTextEncoding(value, label);
		if (value.getBytes(StandardCharsets.UTF_8).length > TEXT_BYTE_LIMIT) {
			throw new IllegalArgumentException(label + "がデータベースの保存可能な容量を超えています。");
		}
		return value;
	}

	private static String longText(String value, String label) {
		if (value != null) {
			validateTextEncoding(value, label);
		}
		return value;
	}

	private static void validateTextEncoding(String value, String label) {
		if (value.indexOf('\0') >= 0 || !StandardCharsets.UTF_8.newEncoder().canEncode(value)) {
			throw new IllegalArgumentException(label + "にデータベースへ保存できない文字が含まれています。");
		}
	}

	private static void requireNonNegativeId(long id, String label) {
		if (id < 0) {
			throw new IllegalArgumentException(label + "IDが不正です。");
		}
	}
}
