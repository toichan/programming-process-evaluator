package servlet.auth;

import java.util.Map;

public final class ProductionTransportPolicy {
	private ProductionTransportPolicy() {}

	static boolean requiresHttps(Map<String, String> environment) {
		String mode = environment.get("PPE_ENV");
		return "production".equals(mode) || "simulation".equals(mode);
	}

	static boolean permitted(boolean requireHttps, boolean secure, String path) {
		return !requireHttps || secure || "/health".equals(path);
	}
}
