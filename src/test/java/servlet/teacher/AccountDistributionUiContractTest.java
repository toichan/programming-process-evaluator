package servlet.teacher;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class AccountDistributionUiContractTest {
	private String read(String path) throws Exception { return Files.readString(Path.of("src/main/webapp/" + path)); }
	@Test void accountStateUsesReadableBadgesWithoutChangingStatusActions() throws Exception {
		String js = read("js/teacher/account/account.js");
		String css = read("css/teacher/account/account.css");
		assertTrue(js.contains("account-status-badge"));
		assertTrue(js.contains("status.dataset.status = account.status"));
		for (String status : new String[] { "active", "suspended", "deleted" }) {
			assertTrue(css.contains(".account-status-badge[data-status=\"" + status + "\"]"));
		}
		assertTrue(css.contains("background: #dcfce7; color: #166534;"));
		assertTrue(css.contains("background: #fee2e2; color: #b91c1c;"));
		assertTrue(js.contains("account.status === 'active' ? 'suspend' : 'activate'"));
	}
	@Test void accountTableKeepsCredentialsMaskedAndExistingActions() throws Exception {
		String js = read("js/teacher/account/account.js");
		assertTrue(js.contains("password-mask"));
		assertTrue(js.contains("account.passwordAvailable ? '••••••••' : '確認不可'"));
		assertTrue(js.contains("security-chip level-"));
		assertTrue(js.contains("status-pill"));
		assertTrue(js.contains("consent-pending"));
		assertTrue(js.contains("button('reset', 'PW再設定', account, 'outline-warning')"));
		assertTrue(js.contains("button('delete', '削除'"));
		assertTrue(js.contains("feedback.confirm"));
	}
	@Test void everyDistributionStateHasReadableColorsAndClassDetailUsesSameBadge() throws Exception {
		String css = read("css/teacher/distribution/distribution.css");
		for (String status : new String[] { "draft", "not_distributed", "scheduled", "in_progress",
				"completed", "distributed", "stopped" }) {
			assertTrue(css.contains(".distribution-status-badge[data-status=\"" + status + "\"]"));
		}
		assertTrue(css.contains("background: #dcfce7; color: #166534;"));
		String jsp = read("WEB-INF/teacher/distribution/distribution.jsp");
		assertTrue(jsp.contains("data-status=\"<c:out value='${target.status}'/>\""));
		assertTrue(jsp.contains("配信設定済み"));
		assertTrue(jsp.contains("停止中"));
	}
	@Test void sharedDistributionHistoryKeepsExecutorAndOperationColumnsReadable() throws Exception {
		String jsp = read("WEB-INF/teacher/distribution/distribution.jsp");
		String css = read("css/teacher/distribution/distribution.css");
		assertTrue(jsp.contains("${distribution.executedBy}"));
		assertTrue(jsp.contains("distribution-operation-history-table"));
		for (String heading : new String[] { "操作</th>", "日時</th>", "実行者</th>", "状態</th>" }) {
			assertTrue(jsp.contains(heading));
		}
		assertTrue(jsp.contains("${entry.actor}"));
		assertTrue(jsp.contains("${entry.occurredAt}"));
		assertTrue(jsp.contains("${entry.result == 'success'}"));
		assertTrue(css.contains(".distribution-operation-history-wrap"));
		assertTrue(css.contains("overflow-wrap: anywhere"));
		assertTrue(css.contains("td.distribution-actions"));
	}
}
