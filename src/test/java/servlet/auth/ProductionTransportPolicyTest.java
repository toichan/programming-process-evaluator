package servlet.auth;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ProductionTransportPolicyTest {
	@Test
	void productionAndSimulationRejectInsecurePortalTrafficButAllowInternalHealth() {
		for (String mode : new String[] {"production", "simulation"}) {
			boolean required = ProductionTransportPolicy.requiresHttps(Map.of("PPE_ENV", mode));
			assertTrue(required);
			assertFalse(ProductionTransportPolicy.permitted(required, false, "/student/account/login"));
			assertFalse(ProductionTransportPolicy.permitted(required, false, "/teacher/task"));
			assertTrue(ProductionTransportPolicy.permitted(required, false, "/health"));
			assertTrue(ProductionTransportPolicy.permitted(required, true, "/student/editor"));
		}
		assertFalse(ProductionTransportPolicy.requiresHttps(Map.of()));
		assertTrue(ProductionTransportPolicy.permitted(false, false, "/student/editor"));
	}
}
