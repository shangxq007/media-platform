package com.example.platform.workflow.definition.domain;

import com.example.platform.shared.capability.AutomationFlow;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * UWD-RED-017 (CROSS_MODULE_REGRESSION). AutomationFlow boundary: the owned
 * W2 WorkflowNodeType is VOCABULARY_SOURCE_ONLY — a 1:1 mapping to
 * AutomationFlow.NodeType with equal value sets. AutomationFlow never becomes
 * a persisted workflow-definition authority (AR-W2-09).
 */
class UserWorkflowNodeTypeVocabularyTest {

    @Test
    void workflowNodeTypeMapsOneToOneToAutomationFlowNodeType() {
        WorkflowNodeType[] w2 = WorkflowNodeType.values();
        assertEquals(8, w2.length);
        AutomationFlow.NodeType[] source = AutomationFlow.NodeType.values();
        assertEquals(8, source.length);

        Set<String> w2Names = Arrays.stream(w2).map(Enum::name).collect(Collectors.toSet());
        Set<String> sourceNames = Arrays.stream(source).map(Enum::name).collect(Collectors.toSet());
        assertEquals(w2Names, sourceNames, "value sets must be identical (1:1 vocabulary mapping)");

        for (WorkflowNodeType t : w2) {
            AutomationFlow.NodeType.valueOf(t.name()); // must exist with the identical name
            assertTrue(sourceNames.contains(t.name()));
        }
    }
}
