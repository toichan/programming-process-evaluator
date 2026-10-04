package control.student;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import control.auth.AuthenticatedUser;
import entity.StandardRubric;
import entity.StandardRubric.Dimension;
import entity.UserCredential.UserType;

class StandardRubricSourceTest {
	static StandardRubric source() throws Exception {
		return StandardRubricSource.parse(
				Files.readString(Path.of("docs/rubric/思考力・判断力・表現力_ルーブリック_0805.md")),
				Files.readString(Path.of("docs/rubric/主体的に学習に取り組む態度_ルーブリック_0805.md")));
	}

	@Test
	void parsesAllSixCriteriaAndThirtyExactDescriptions() throws Exception {
		var rubric = source();
		assertEquals(2, rubric.dimensions().size());
		assertEquals(6, rubric.dimensions().stream().mapToInt(d -> d.criteria().size()).sum());
		assertEquals(30, rubric.dimensions().stream().flatMap(d -> d.criteria().stream())
				.mapToInt(c -> c.levels().size()).sum());
		assertEquals("文法デバッグ能力", rubric.dimensions().get(0).criteria().get(0).name());
		assertEquals("課題解決への意欲", rubric.dimensions().get(1).criteria().get(1).name());
		assertEquals("エラーメッセージを確認し、原因に応じた修正を行い、文法エラーを解消できる。",
				rubric.dimensions().get(0).criteria().get(0).levels().get(0).description());
	}

	@Test
	void rejectsMissingRowsAndWrongLevelOrder() throws Exception {
		String thinking = Files.readString(Path.of("docs/rubric/思考力・判断力・表現力_ルーブリック_0805.md"));
		String attitude = Files.readString(Path.of("docs/rubric/主体的に学習に取り組む態度_ルーブリック_0805.md"));
		assertThrows(IllegalArgumentException.class, () -> StandardRubricSource.parse("", attitude));
		assertThrows(IllegalArgumentException.class,
				() -> StandardRubricSource.parse(thinking.replace("**レベル5**", "**レベル1**"), attitude));
	}

	@Test
	void rejectsPartialDatabaseShape() throws Exception {
		var rubric = source();
		assertThrows(IllegalArgumentException.class, () -> new StandardRubric(rubric.title(), rubric.version(),
				List.of(new Dimension("thinking", "思考", List.of()), rubric.dimensions().get(1))));
	}

	@Test
	void deniesNonStudentAndPasswordChangeBeforeDatabaseAccess() {
		var control = new StudentRubricControl();
		assertThrows(SecurityException.class, () -> control.load(null));
		for (UserType role : List.of(UserType.TEACHER, UserType.ADMIN)) {
			assertThrows(SecurityException.class, () -> control.load(
					new AuthenticatedUser(1, "synthetic", "Synthetic", role, false, "test")));
		}
		assertThrows(SecurityException.class, () -> control.load(
				new AuthenticatedUser(1, "synthetic", "Synthetic", UserType.STUDENT, true, "test")));
	}
}
