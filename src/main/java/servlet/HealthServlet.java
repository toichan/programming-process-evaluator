package servlet;

import java.io.IOException;
import java.sql.SQLException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import lib.mysql.Client;

@WebServlet("/health")
public final class HealthServlet extends HttpServlet {
	@Override
	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
		response.setContentType("application/json");
		response.setHeader("Cache-Control", "no-store");
		try (var connection = Client.createConnection()) {
			if (!connection.isValid(1)) throw new SQLException("Database readiness check failed.");
			response.getWriter().write("{\"status\":\"ok\"}");
		} catch (SQLException failure) {
			getServletContext().log("Application readiness check failed.", failure);
			response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
			response.getWriter().write("{\"status\":\"unavailable\"}");
		}
	}
}
