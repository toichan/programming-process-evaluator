package control.dev;

import static org.junit.jupiter.api.Assertions.*;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ReviewDemoBootstrapTest {
	@Test void requiresExplicitLocalDevelopmentTarget() {
		var local = Map.of("PPE_ENV", "development", "DB_HOST", "db",
				"DB_NAME", "programming_process_evaluator", "PPE_SEED_REVIEW_DEMO", "true");
		assertDoesNotThrow(() -> ReviewDemoBootstrap.requireLocalTarget(local));
		for (String key : local.keySet()) {
			var missing = new HashMap<>(local);
			missing.remove(key);
			assertThrows(IllegalStateException.class, () -> ReviewDemoBootstrap.requireLocalTarget(missing));
		}
		var production = new HashMap<>(local);
		production.put("PPE_ENV", "production");
		assertThrows(IllegalStateException.class, () -> ReviewDemoBootstrap.requireLocalTarget(production));
		var remote = new HashMap<>(local);
		remote.put("DB_HOST", "production-db");
		assertThrows(IllegalStateException.class, () -> ReviewDemoBootstrap.requireLocalTarget(remote));
	}
}
