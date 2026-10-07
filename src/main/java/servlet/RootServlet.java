package servlet;

import java.io.IOException;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import servlet.auth.PortalHostRouting;

@WebServlet({ "", "/student", "/student/", "/teacher", "/teacher/", "/admin", "/admin/" })
public final class RootServlet extends HttpServlet {
	private static final long serialVersionUID = 1L;

	@Override
	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
		String destination = switch (request.getServletPath()) {
			case "/student", "/student/" -> "/student/home";
			case "/teacher", "/teacher/" -> "/teacher/home";
			case "/admin", "/admin/" -> "/admin/home";
			default -> PortalHostRouting.fromEnvironment().loginPathForHost(request.getServerName());
		};
		response.sendRedirect(response.encodeRedirectURL(request.getContextPath() + destination));
	}
}
