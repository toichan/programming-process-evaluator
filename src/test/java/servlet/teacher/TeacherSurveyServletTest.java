package servlet.teacher;

import static org.junit.jupiter.api.Assertions.*;
import javax.servlet.http.HttpServletRequest;
import java.nio.file.*;
import org.junit.jupiter.api.Test;

class TeacherSurveyServletTest {
	@Test void rejectsDuplicateMalformedAndOverflowedParameters() {
		for(String value:new String[]{"-1","0","01","1x","9223372036854775808"}) {
			var request=request("schoolId",new String[]{value});
			assertThrows(IllegalArgumentException.class,()->TeacherSurveyServlet.filter(request));
		}
		var request=request("search",new String[]{"one","two"});
		assertThrows(IllegalArgumentException.class,()->TeacherSurveyServlet.filter(request));
	}
	private static HttpServletRequest request(String parameter,String[] values){
		return (HttpServletRequest)java.lang.reflect.Proxy.newProxyInstance(HttpServletRequest.class.getClassLoader(),
				new Class<?>[]{HttpServletRequest.class},(proxy,method,args)->
				method.getName().equals("getParameterValues") && parameter.equals(args[0]) ? values : null);
	}
	@Test void prototypeSurfaceUsesSharedFeedbackSafeDomAndReadOnlyRealData()throws Exception {
		String js=Files.readString(Path.of("src/main/webapp/js/teacher/survey/survey.js"));
		String jsp=Files.readString(Path.of("src/main/webapp/WEB-INF/teacher/survey/survey.jsp"));
		assertTrue(js.contains("PPEFeedback.createPageFeedback"));assertTrue(js.contains("textContent"));
		for(String unsafe:new String[]{"innerHTML","alert(","confirm(","surveyData =","submittedAt = new Date"})assertFalse(js.contains(unsafe),unsafe);
		for(String required:new String[]{"totalResponses","summaryCompletionBar","thinkingValidityExprFilter","surveyTableBody","surveyDetailModal","modalResponseContent"})assertTrue(jsp.contains(required));
		assertTrue(jsp.contains("modal-lg modal-dialog-scrollable"));assertFalse(jsp.contains("onclick="));
		assertTrue(jsp.contains("page-start.jspf"));assertTrue(jsp.contains("page-end.jspf"));
	}
}
