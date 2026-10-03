package entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class ConsentDecisionTest {
	@Test
	void agreeingIsAllowedInitiallyAndAfterDecliningOrWithdrawing() {
		for (ConsentStatus status : ConsentStatus.values()) {
			assertEquals(ConsentStatus.AGREED, ConsentDecision.resolve(status, ConsentStatus.AGREED));
		}
	}

	@Test
	void decliningAfterAgreementRecordsWithdrawalWithoutChangingExistingWithdrawal() {
		assertEquals(ConsentStatus.DECLINED,
				ConsentDecision.resolve(ConsentStatus.UNCONFIRMED, ConsentStatus.DECLINED));
		assertEquals(ConsentStatus.DECLINED,
				ConsentDecision.resolve(ConsentStatus.DECLINED, ConsentStatus.DECLINED));
		assertEquals(ConsentStatus.WITHDRAWN,
				ConsentDecision.resolve(ConsentStatus.AGREED, ConsentStatus.DECLINED));
		assertEquals(ConsentStatus.WITHDRAWN,
				ConsentDecision.resolve(ConsentStatus.WITHDRAWN, ConsentStatus.DECLINED));
	}

	@Test
	void rejectsDecisionsNotRepresentedByTheForm() {
		assertThrows(IllegalArgumentException.class,
				() -> ConsentDecision.resolve(ConsentStatus.AGREED, ConsentStatus.UNCONFIRMED));
		assertThrows(IllegalArgumentException.class,
				() -> ConsentDecision.resolve(ConsentStatus.AGREED, ConsentStatus.WITHDRAWN));
	}
}
