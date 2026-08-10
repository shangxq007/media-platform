package com.example.platform.workflow.definition.domain;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * K1 (UWD-RED-017 / CROSS_MODULE_REGRESSION). The owned W2 WorkflowNodeType is a
 * self-contained declaration vocabulary (8 node types). The legacy
 * AutomationFlow.NodeType vocabulary that once sourced it is retired (K1): the
 * AutomationFlow family is ABSENT from the repository, so the W2 vocabulary can
 * no longer leak into — or be sourced from — a legacy runtime authority.
 */
class UserWorkflowNodeTypeVocabularyTest {

    /** The W2 declaration vocabulary remains well-defined and self-contained. */
    @Test
    void workflowNodeTypeIsWellDefinedVocabulary() {
        WorkflowNodeType[] w2 = WorkflowNodeType.values();
        assertEquals(8, w2.length);
        Set<String> names = Arrays.stream(w2).map(Enum::name).collect(Collectors.toSet());
        assertEquals(Set.of("ACTION", "EXTENSION_POINT", "CONDITION", "APPROVAL",
                "DELAY", "NOTIFICATION", "WEBHOOK", "HOOK"), names);
    }

    /** K1-RED: the legacy AutomationFlow family that once sourced this vocabulary is retired. */
    @Test
    void automationFlowFamilyAbsentFromRepository() {
        assertFalse(Files.exists(Path.of("shared-kernel/src/main/java/com/example/platform/shared/capability/AutomationFlow.java")),
                "AutomationFlow must be retired (K1)");
    }
}
