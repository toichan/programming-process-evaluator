package servlet.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Optional;

import org.junit.jupiter.api.Test;

class PortalHostRoutingTest {
	private final PortalHostRouting routing = new PortalHostRouting(
			"student.ppeval.net", "teacher.ppeval.net");

	@Test
	void redirectsStudentRoutesToTheStudentPortalLogin() {
		assertEquals(Optional.of("https://student.ppeval.net/app/student/account/login"),
				routing.redirectLocation("teacher.ppeval.net", 443, "/student/home", "/app"));
	}

	@Test
	void redirectsTeacherAndAdminRoutesToTheTeacherPortalLogin() {
		assertEquals(Optional.of("https://teacher.ppeval.net/teacher/account/login"),
				routing.redirectLocation("student.ppeval.net", 443, "/teacher/task", ""));
		assertEquals(Optional.of("https://teacher.ppeval.net/teacher/account/login"),
				routing.redirectLocation("student.ppeval.net", 443, "/admin/home", ""));
	}

	@Test
	void doesNotRedirectPortalRoutesOnTheirAssignedHosts() {
		assertEquals(Optional.empty(),
				routing.redirectLocation("STUDENT.PPEVAL.NET.", 443, "/student/home", ""));
		assertEquals(Optional.empty(),
				routing.redirectLocation("teacher.ppeval.net", 443, "/admin/home", ""));
	}

	@Test
	void leavesSharedAndUnrelatedRoutesOnTheCurrentHost() {
		assertEquals(Optional.empty(),
				routing.redirectLocation("student.ppeval.net", 443, "/css/common.css", ""));
		assertEquals(Optional.empty(),
				routing.redirectLocation("teacher.ppeval.net", 443, "/hello", ""));
	}

	@Test
	void usesHttpForLocalhostPortalAliases() {
		PortalHostRouting localRouting = new PortalHostRouting("student.localhost", "teacher.localhost");

		assertEquals(Optional.of("http://student.localhost:8080/student/account/login"),
				localRouting.redirectLocation("localhost", 8080, "/student/home", ""));
		assertEquals(Optional.of("http://teacher.localhost:8081/teacher/account/login"),
				localRouting.redirectLocation("student.localhost", 8081, "/teacher/task", ""));
	}

	@Test
	void rejectsInvalidOrSharedPortalHosts() {
		assertThrows(IllegalStateException.class,
				() -> new PortalHostRouting("student.ppeval.net/path", "teacher.ppeval.net"));
		assertThrows(IllegalStateException.class,
				() -> new PortalHostRouting("same.ppeval.net", "same.ppeval.net"));
	}
}
