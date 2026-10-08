package lib.mysql;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.SQLException;

import org.junit.jupiter.api.Test;

class ClientTest {
	@Test
	void retriesTransientConnectionFailures() {
		assertTrue(Client.isTransientConnectionFailure(new SQLException("offline", "08001")));
	}

	@Test
	void doesNotRetryAuthenticationFailures() {
		assertFalse(Client.isTransientConnectionFailure(new SQLException("denied", "28000")));
	}

	@Test
	void recognizesConnectionFailureInNextExceptionChain() {
		SQLException failure = new SQLException("wrapped", "HY000");
		failure.setNextException(new SQLException("offline", "08S01"));

		assertTrue(Client.isTransientConnectionFailure(failure));
	}
}
