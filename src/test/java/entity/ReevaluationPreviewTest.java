package entity;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.beans.Introspector;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

class ReevaluationPreviewTest {
	@Test
	void exposesPreviewAndTargetPropertiesAsJavaBeansForJspEl() throws Exception {
		Set<String> previewProperties = properties(ReevaluationPreview.class);
		assertTrue(previewProperties.containsAll(Set.of(
				"previewCode", "status", "participantCount", "targetCount", "expiresAt", "targets")));

		Set<String> targetProperties = properties(ReevaluationPreview.Target.class);
		assertTrue(targetProperties.containsAll(Set.of(
				"displayName", "revisionNumber", "status", "thinkingScore", "attitudeScore",
				"previousThinkingScore", "previousAttitudeScore", "thinkingReason", "attitudeReason",
				"safeErrorMessage")));
	}

	private static Set<String> properties(Class<?> type) throws Exception {
		return Arrays.stream(Introspector.getBeanInfo(type).getPropertyDescriptors())
				.map(property -> property.getName())
				.collect(Collectors.toSet());
	}
}
