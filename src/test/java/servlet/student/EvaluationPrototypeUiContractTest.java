package servlet.student;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class EvaluationPrototypeUiContractTest {
	private String read(String path) throws Exception {
		return Files.readString(Path.of(path));
	}

	@Test
	void studentEvaluationUsesPersistedScoresAndFilterableReasonsWithoutSkeletons() throws Exception {
		String jsp = read("src/main/webapp/WEB-INF/student/evaluation/evaluation.jsp");
		String script = read("src/main/webapp/js/student/evaluation/evaluation.js");

		assertTrue(jsp.contains("/js/student/evaluation/evaluation.js"));
		assertTrue(jsp.contains("class=\"score-meter\""));
		assertTrue(jsp.contains("dimension.score + 0.5"));
		assertTrue(jsp.contains("class=\"dimension-score-toolbar\""));
		assertTrue(jsp.contains("思："));
		assertTrue(jsp.contains("態："));
		assertTrue(jsp.contains("data-reason-filter=\"thinking\""));
		assertTrue(jsp.contains("data-reason-filter=\"attitude\""));
		assertFalse(jsp.contains("data-reason-filter=\"other\""));
		assertFalse(jsp.contains("<h3 class=\"h5\">フィードバック</h3>"));
		assertFalse(jsp.contains("<h3 class=\"h5\">学習過程の分析</h3>"));
		assertTrue(jsp.contains("data-category=\"<c:out value='${reasonCategory}'/>\""));
		assertTrue(jsp.contains("<c:out value=\"${reason.body}\"/>"));
		assertFalse(jsp.contains("ai-wait-skeleton"));
		String style = read("src/main/webapp/css/student/evaluation/evaluation.css");
		assertTrue(style.contains(".dimension-score-toolbar"));
		assertTrue(style.contains("flex-wrap: nowrap"));
		assertTrue(style.contains("white-space: nowrap"));
		assertTrue(script.contains("aria-pressed"));
		assertTrue(script.contains("classList.toggle(\"is-hidden\", hidden)"));
	}

	@Test
	void studentCodeLogKeepsPersistedTimelineAndUsesAccessibleReasonFilters() throws Exception {
		String jsp = read("src/main/webapp/WEB-INF/student/evaluation/log.jsp");
		String script = read("src/main/webapp/js/student/evaluation/log.js");

		assertTrue(jsp.contains("data-log-id=\"<c:out value='${entry.logId}'/>\""));
		assertTrue(jsp.contains("<c:out value=\"${entry.snapshot}\"/>"));
		assertTrue(jsp.contains("<c:out value=\"${entry.standardOutput}\"/>"));
		assertTrue(jsp.contains("data-reason-filter=\"all\""));
		assertTrue(jsp.contains("data-category=\"<c:out value='${reasonCategory}'/>\""));
		assertTrue(jsp.contains(">ログ一覧</button>"));
		assertFalse(jsp.contains("data-reason-filter=\"other\""));
		assertTrue(script.contains("function diffLines(previous, current)"));
		assertTrue(script.contains("execution-stream-${stream}"));
		assertTrue(script.contains("標準入力"));
		assertTrue(script.contains("標準出力"));
		assertTrue(script.contains("標準エラー"));
		assertTrue(script.contains("lineContent.textContent = content || \" \";"));
		assertTrue(script.contains("aria-pressed"));
		assertFalse(script.contains("innerHTML"));
		String style = read("src/main/webapp/css/student/evaluation/log.css");
		assertTrue(style.contains(".execution-stream-content"));
		assertTrue(style.contains("white-space: pre-wrap"));
	}

	@Test
	void teacherEvaluationUsesDimensionScoreTextAndShowsReadOnlyLogDiffMetadata() throws Exception {
		String jsp = read("src/main/webapp/WEB-INF/teacher/evaluation/evaluation.jsp");
		String script = read("src/main/webapp/js/teacher/evaluation/evaluation.js");

		for (String id : new String[] { "evaluationResults", "evaluationReasons", "codeLogId",
				"codeLogTimestamp", "codeLogBoundary", "codeLogSnapshot", "logExecution" }) {
			assertTrue(jsp.contains("id=\"" + id + "\""), id);
		}
		assertTrue(script.contains("dimension.scoreText?.trim()"));
		assertTrue(script.contains("dimension.score"));
		assertFalse(jsp.contains("id=\"feedbackSummary\""));
		assertFalse(jsp.contains("id=\"processAnalysis\""));
		assertFalse(script.contains("set('feedbackSummary'"));
		assertFalse(script.contains("set('processAnalysis'"));
		assertTrue(script.contains("['', 'すべて']"));
		assertTrue(script.contains("['思考力・判断力・表現力', '思考・判断・表現']"));
		assertTrue(script.contains("['主体的に学習に取り組む態度', '態度']"));
		assertFalse(script.contains("categories.set(reason.dimensionLabel"));
		assertTrue(script.contains("function diffLines(previous, current)"));
		assertTrue(script.contains("line.append(node('span', number == null ? '' : number, 'line-number')"));
		assertTrue(script.contains("aria-pressed"));
		assertFalse(script.contains("innerHTML"));
	}

	@Test
	void savedReasonEvidenceLinksOnlyToSubmissionScopedAuthorizedLogs() throws Exception {
		String dao = read("src/main/java/dao/StudentEvaluationDao.java");
		String dto = read("src/main/java/entity/StudentEvaluationPage.java");
		String studentEvaluation = read("src/main/webapp/WEB-INF/student/evaluation/evaluation.jsp");
		String studentLog = read("src/main/webapp/WEB-INF/student/evaluation/log.jsp");
		String studentScript = read("src/main/webapp/js/student/evaluation/log.js");
		String teacherScript = read("src/main/webapp/js/teacher/evaluation/evaluation.js");

		assertTrue(dao.contains("FROM evaluation_evidence ev"));
		assertTrue(dao.contains("ev.evaluation_reason_id = er.evaluation_reason_id"));
		assertTrue(dao.contains("linked_detail.evaluation_reason_id = er.evaluation_reason_id"));
		assertTrue(dao.contains("l.code_log_id = ev.code_log_id"));
		assertTrue(dao.contains("l.participation_id = tp.participation_id"));
		assertTrue(dao.contains("l.submission_id = s.submission_id"));
		assertTrue(dao.contains("l.observed_at <= s.submitted_at"));
		assertTrue(dao.contains("verified_code_log_id"));
		assertTrue(dao.contains("verifiedLogId,"));
		assertTrue(dto.contains("List<EvidenceResult> evidence"));
		assertTrue(dto.contains("getCodeLogId()"));

		for (String jsp : new String[] { studentEvaluation, studentLog }) {
			assertTrue(jsp.contains("<c:param name='logId' value='${evidence.codeLogId}'/>"));
			assertTrue(jsp.contains("${evidence.hasCodeLogReference}"));
			assertTrue(jsp.contains("この理由に紐づく保存済み根拠はありません。"));
		}
		assertTrue(studentScript.contains("entry.dataset.logId === requestedLogId"));
		assertTrue(studentScript.contains("requestedEvidenceStatus.textContent"));
		assertTrue(teacherScript.contains("authorizedLogs.findIndex(log => String(log.logId) === String(item.codeLogId))"));
		assertTrue(teacherScript.contains("この理由に紐づく保存済み根拠はありません。"));
		assertFalse(teacherScript.contains("innerHTML"));
	}

	@Test
	void teacherEvaluationReasonsMatchEvidenceAgainstTheDetailLogs() throws Exception {
		String js = read("src/main/webapp/js/teacher/evaluation/evaluation.js");
		String detailDto = read("src/main/java/entity/TeacherReviewDetail.java");
		String evidenceDto = read("src/main/java/entity/StudentEvaluationPage.java");

		assertTrue(detailDto.contains("List<Log> logs"));
		assertTrue(detailDto.contains("record Log(long logId,"));
		assertTrue(evidenceDto.contains("private final Long codeLogId;"));
		assertTrue(js.contains("renderReasons(evaluation?.reasons || [], 'reasonFilters', 'evaluationReasons', detail.logs || [])"));
		assertTrue(js.contains("String(log.logId) === String(item.codeLogId)"));
	}
}
