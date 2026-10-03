package control.student;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import dao.StudentDao;
import entity.ConsentSaveResult;
import entity.ConsentStatus;
import lib.mysql.Client;

class ConsentDatabaseTest {
	private final StudentDao dao = new StudentDao();
	private long userId;
	private long documentId;
	private long schoolId;

	@BeforeEach
	void createFixture() throws SQLException {
		Assumptions.assumeTrue(Boolean.parseBoolean(System.getenv("CONSENT_DB_TEST")));
		String database = System.getenv("DB_NAME");
		assertTrue(database != null && database.matches("ppe_consent_test_[a-z0-9_]+"),
				"Use only a dedicated migrated consent test database.");
		try (Connection connection = Client.createConnection()) {
			assertEquals(database, connection.getCatalog());
			try (Statement statement = connection.createStatement();
					ResultSet rows = statement.executeQuery("SELECT COUNT(*) FROM users")) {
				assertTrue(rows.next());
				assertEquals(0, rows.getInt(1));
			}
			try (PreparedStatement statement = connection.prepareStatement("""
					INSERT INTO users (user_type, login_id, password_hash, display_name, account_status, created_at)
					VALUES ('student', ?, 'synthetic-not-a-credential', 'Synthetic consent fixture',
					  'active', CURRENT_TIMESTAMP)
					""", Statement.RETURN_GENERATED_KEYS)) {
				statement.setString(1, "consent-" + UUID.randomUUID());
				statement.executeUpdate();
				try (ResultSet keys = statement.getGeneratedKeys()) {
					assertTrue(keys.next());
					userId = keys.getLong(1);
				}
			}
			try (PreparedStatement statement = connection.prepareStatement("""
					INSERT INTO schools (school_code, name, security_level, school_status, created_at)
					VALUES (?, 'Synthetic consent school', 1, 'active', CURRENT_TIMESTAMP)
					""", Statement.RETURN_GENERATED_KEYS)) {
				statement.setString(1, UUID.randomUUID().toString());
				statement.executeUpdate();
				try (ResultSet keys = statement.getGeneratedKeys()) {
					assertTrue(keys.next());
					schoolId = keys.getLong(1);
				}
			}
			try (PreparedStatement statement = connection.prepareStatement("""
					INSERT INTO student_profiles (user_id, student_code, security_level,
					  first_login_status, must_change_password, school_id) VALUES (?, ?, 1, 'completed', FALSE, ?)
					""")) {
				statement.setLong(1, userId);
				statement.setString(2, UUID.randomUUID().toString().substring(0, 32));
				statement.setLong(3, schoolId);
				statement.executeUpdate();
			}
		}
		documentId = dao.findConsentPage(userId).getDocument().orElseThrow().getId();
	}

	@Test
	void preservesHistoryRequiresConfirmationAndRejectsStaleResponse() throws SQLException {
		assertEquals(ConsentSaveResult.RECORDED,
				dao.saveConsent(userId, documentId, ConsentStatus.AGREED, 0, false));
		long first = dao.findConsentPage(userId).getResponseId();
		assertEquals(ConsentSaveResult.CONFIRMATION_REQUIRED,
				dao.saveConsent(userId, documentId, ConsentStatus.DECLINED, first, false));
		assertEquals(ConsentSaveResult.DOCUMENT_CHANGED,
				dao.saveConsent(userId, documentId + 1, ConsentStatus.DECLINED, first, true));
		assertEquals(ConsentStatus.AGREED, dao.findConsentStatus(userId));
		assertEquals(ConsentSaveResult.RECORDED,
				dao.saveConsent(userId, documentId, ConsentStatus.DECLINED, first, true));
		assertEquals(ConsentStatus.WITHDRAWN, dao.findConsentStatus(userId));
		long withdrawn = dao.findConsentPage(userId).getResponseId();
		assertEquals(ConsentSaveResult.RESPONSE_CHANGED,
				dao.saveConsent(userId, documentId, ConsentStatus.AGREED, first, true));
		assertEquals(ConsentSaveResult.ALREADY_RECORDED,
				dao.saveConsent(userId, documentId, ConsentStatus.DECLINED, withdrawn, true));
		assertEquals(ConsentSaveResult.RECORDED,
				dao.saveConsent(userId, documentId, ConsentStatus.AGREED, withdrawn, true));
		assertEquals(ConsentStatus.AGREED, dao.findConsentStatus(userId));
		try (Connection connection = Client.createConnection();
				PreparedStatement statement = connection.prepareStatement("""
						SELECT consent_status, consent_document_version_id, consented_at, withdrawn_at
						FROM consent_records WHERE user_id = ? ORDER BY consent_id
						""")) {
			statement.setLong(1, userId);
			try (ResultSet rows = statement.executeQuery()) {
				assertTrue(rows.next());
				assertEquals("agreed", rows.getString("consent_status"));
				assertTrue(rows.getTimestamp("consented_at") != null);
				assertTrue(rows.next());
				assertEquals("withdrawn", rows.getString("consent_status"));
				assertTrue(rows.getTimestamp("withdrawn_at") != null);
				assertTrue(rows.next());
				assertEquals("agreed", rows.getString("consent_status"));
				assertEquals(documentId, rows.getLong("consent_document_version_id"));
				assertEquals(false, rows.next());
			}
		}
	}

	@AfterEach
	void removeFixture() throws SQLException {
		if (userId == 0) return;
		try (Connection connection = Client.createConnection()) {
			for (String table : new String[] { "consent_records", "student_profiles", "users" }) {
				try (PreparedStatement statement = connection.prepareStatement(
						"DELETE FROM " + table + " WHERE user_id = ?")) {
					statement.setLong(1, userId);
					statement.executeUpdate();
				}
			}
			try (PreparedStatement statement = connection.prepareStatement("DELETE FROM schools WHERE school_id = ?")) {
				statement.setLong(1, schoolId);
				statement.executeUpdate();
			}
		}
	}
}
