package entity;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;

public class TeacherSurveyFilterTest {
	public static TeacherSurveyResponse row(long id, Double value) {
		return new TeacherSurveyResponse(id,"SR-"+id,"REC-"+id,7,"private-login",1,"School",2,"Class",3,"TASK","Title",
				"beginner",4,5,"2026-10-08T09:00:00","submitted","agreed",4.0,3.0,
				List.of(new TeacherSurveyResponse.Answer("q1ThinkingValidity","Question","rating",value==null?"":value.toString(),"=formula\r\n\"reason\"",value,List.of()),
						new TeacherSurveyResponse.Answer("extra","Arbitrary question","text","<script>not executable</script>","Extra reason",null,List.of())));
	}
	@Test void validatesConditionsAndMissingValuesWithoutInventingScores() {
		assertTrue(TeacherSurveyFilter.matches("=4, >3",4.0));
		assertTrue(TeacherSurveyFilter.matches("<=2, >=4.5",5.0));
		assertFalse(TeacherSurveyFilter.matches("=4",null));
		assertTrue(TeacherSurveyFilter.matches("",null));
		for(String input:List.of("4","=0",">6","<NaN","=4,","",">=2x")) {
			if(input.isEmpty())continue;
			assertThrows(IllegalArgumentException.class,()->TeacherSurveyFilter.validateCondition(input),input);
		}
		assertThrows(IllegalArgumentException.class,()->filter("bad","asc",Map.of()));
		assertThrows(IllegalArgumentException.class,()->filter("studentId","bad",Map.of()));
		assertThrows(IllegalArgumentException.class,()->filter("studentId","asc",Map.of("unknown","=4")));
	}
	@Test void filtersAndAllSortableColumnsRemainDeterministic() {
		var rows=List.of(row(1,null),row(2,4.0),row(3,5.0));
		assertEquals(List.of(3L,2L),filter("thinkingValidity","desc",Map.of("q1ThinkingValidity",">=4")).apply(rows).stream().map(TeacherSurveyResponse::responseId).toList());
		assertEquals(1,filter("studentId","asc",Map.of("q1ThinkingValidity","=4")).apply(rows).size());
		for(String sort:TeacherSurveyFilter.SORTS)assertEquals(3,filter(sort,"asc",Map.of()).apply(rows).size());
		assertEquals(0,new TeacherSurveyFilter(null,null,null,"","", "not_agreed","","","",Map.of()).apply(rows).size());
		assertEquals(0,new TeacherSurveyFilter(99L,null,null,"","","","","","",Map.of()).apply(rows).size());
	}
	private TeacherSurveyFilter filter(String sort,String direction,Map<String,String> conditions) {
		return new TeacherSurveyFilter(null,null,null,"","","","",sort,direction,conditions);
	}
}
