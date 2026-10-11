package entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

class StudentSurveyJavaBeansTest {
	@Test
	void optionPropertiesExposeThePersistedValueAndLabelToJspEl() throws Exception {
		var option = new StudentSurveyQuestion.Option("4", "おおむね妥当");
		var properties = properties(StudentSurveyQuestion.Option.class);

		assertEquals(option.value(), properties.get("value").getReadMethod().invoke(option));
		assertEquals(option.label(), properties.get("label").getReadMethod().invoke(option));
	}

	@Test
	void everyQuestionGroupQuestionAndOptionPropertyInTheJspHasAJavaBeanGetter() throws Exception {
		String jsp = Files.readString(Path.of("src/main/webapp/WEB-INF/student/survey/survey.jsp"));
		Map<String, Class<?>> types = Map.of(
				"questionGroup", StudentSurveyQuestionGroup.class,
				"question", StudentSurveyQuestion.class,
				"option", StudentSurveyQuestion.Option.class);

		for (var entry : types.entrySet()) {
			var properties = properties(entry.getValue());
			var references = Pattern.compile("\\b" + entry.getKey() + "\\.(\\w+)").matcher(jsp);
			while (references.find()) {
				String propertyName = references.group(1);
				var descriptor = properties.get(propertyName);
				assertNotNull(descriptor, entry.getKey() + "." + propertyName);
				assertNotNull(descriptor.getReadMethod(), entry.getKey() + "." + propertyName);
			}
		}
	}

	private static Map<String, PropertyDescriptor> properties(Class<?> type) throws Exception {
		return Arrays.stream(Introspector.getBeanInfo(type).getPropertyDescriptors())
				.collect(Collectors.toMap(PropertyDescriptor::getName, descriptor -> descriptor));
	}
}
