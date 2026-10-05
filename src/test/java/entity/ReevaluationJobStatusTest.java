package entity;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.beans.Introspector;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

class ReevaluationJobStatusTest {
	@Test
	void exposesJobProgressAsJavaBeanPropertiesForJspEl() throws Exception {
		Set<String> properties = Arrays.stream(
				Introspector.getBeanInfo(ReevaluationJobStatus.class).getPropertyDescriptors())
				.map(property -> property.getName())
				.collect(Collectors.toSet());

		assertTrue(properties.containsAll(Set.of(
				"jobId", "taskId", "promptVersionId", "status", "targetCount", "completedCount",
				"failedCount", "progressPercent", "targetResults")));
	}
}
