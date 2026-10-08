package servlet.teacher;

import java.io.IOException;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import javax.servlet.*;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.*;
import com.google.gson.Gson;
import control.auth.AuthenticatedUser;
import control.teacher.*;
import dao.TeacherSurveyDao.NotFoundException;
import entity.*;
import servlet.auth.CsrfTokens;

@WebServlet("/teacher/surveys")
public final class TeacherSurveyServlet extends HttpServlet {
	private static final long serialVersionUID=1L;
	private static final Gson JSON=new Gson();
	private final TeacherSurveyControl surveys=new TeacherSurveyControl();
	@Override protected void doGet(HttpServletRequest request,HttpServletResponse response)throws ServletException,IOException{
		response.setHeader("Cache-Control","no-store");response.setHeader("X-Content-Type-Options","nosniff");
		try{
			var user=(AuthenticatedUser)request.getAttribute("authenticatedUser");
			if(user==null || user.userType()!=entity.UserCredential.UserType.TEACHER)throw new SecurityException("Teacher authentication required.");
			var filter=filter(request);String view=parameter(request,"view");
			if("list".equals(view))json(response,surveys.list(user,filter));
			else if("detail".equals(view)){
				Long id=id(request,"responseId");if(id==null)throw new IllegalArgumentException("回答IDが必要です。");
				json(response,surveys.detail(user,id));
			}else if("csv".equals(view)||"text".equals(view)){
				boolean text="text".equals(view);String data=surveys.export(user,filter,text);
				response.setContentType(text?"text/plain; charset=UTF-8":"text/csv; charset=UTF-8");
				response.setHeader("Content-Disposition","attachment; filename=\"survey-results."+(text?"txt":"csv")+"\"");
				response.getWriter().write(data);
			}else if(view==null){
				surveys.list(user,filter);
				request.setAttribute("teacherNavigationSummary",new TeacherNavigationControl().load(user));
				request.setAttribute("teacherNavigationActiveItem","surveys");request.setAttribute("teacherId",user.loginId());
				request.setAttribute("csrfToken",CsrfTokens.getOrCreate(request.getSession(false)));
				request.setAttribute("screenDesign","teacher");request.setAttribute("screenBodyClass","teacher-survey-screen");
				request.setAttribute("screenPageTitle","アンケート結果確認");
				request.setAttribute("screenStylesheet","/css/teacher/survey/survey.css");
				request.setAttribute("screenScript","/js/teacher/survey/survey.js");
				request.getRequestDispatcher("/WEB-INF/teacher/survey/survey.jsp").forward(request,response);
			}else throw new IllegalArgumentException("操作が不正です。");
		}catch(PythonExecutionInput.TooLargeException failure){error(response,413,failure.getMessage());}
		catch(IllegalArgumentException failure){error(response,400,failure.getMessage());}
		catch(NotFoundException failure){error(response,404,"対象の回答を参照できません。");}
		catch(SecurityException failure){error(response,403,"アンケート結果確認の権限がありません。");}
		catch(SQLException failure){getServletContext().log("Teacher survey read/export failed.",failure);error(response,503,"アンケート結果を取得できませんでした。再試行してください。");}
	}
	@Override protected void doPost(HttpServletRequest request,HttpServletResponse response)throws IOException{
		response.setHeader("Cache-Control","no-store");response.setHeader("X-Content-Type-Options","nosniff");
		error(response,405,"アンケート結果確認は参照専用です。");
	}
	private static void json(HttpServletResponse response,Object data)throws IOException{response.setContentType("application/json; charset=UTF-8");JSON.toJson(data,response.getWriter());}
	private static void error(HttpServletResponse response,int status,String message)throws IOException{response.setStatus(status);json(response,java.util.Map.of("message",message));}
	static String parameter(HttpServletRequest request,String name){
		var values=request.getParameterValues(name);if(values==null)return null;
		if(values.length!=1)throw new IllegalArgumentException("同じ条件を複数指定できません。");return values[0];
	}
	static Long id(HttpServletRequest request,String name){
		String value=parameter(request,name);if(value==null||value.isEmpty())return null;
		if(!value.matches("[1-9][0-9]{0,18}"))throw new IllegalArgumentException("対象IDが不正です。");
		try{return Long.valueOf(value);}catch(NumberFormatException failure){throw new IllegalArgumentException("対象IDが不正です。",failure);}
	}
	static TeacherSurveyFilter filter(HttpServletRequest request){
		var conditions=new LinkedHashMap<String,String>();
		for(String metric:TeacherSurveyResponse.METRICS){String value=parameter(request,metric);if(value!=null)conditions.put(metric,value);}
		return new TeacherSurveyFilter(id(request,"schoolId"),id(request,"classroomId"),id(request,"taskId"),
				parameter(request,"difficulty"),parameter(request,"completion"),parameter(request,"consent"),parameter(request,"search"),
				parameter(request,"sort"),parameter(request,"direction"),conditions);
	}
}
