package com.example.platform.operation.operation;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Operation V2 (Gate 1 Design Freeze): V2 is a vocabulary snapshot = V1 + additions; the frozen
 * V1 vocabulary (25 definitions, all @1.0) is literally unchanged.
 */
class OperationDefinitionV2Test {

    /** The frozen V1 inventory (AC-OPS-2: ids unchanged). */
    private static final List<String> FROZEN_V1_IDS = List.of(
            "timeline.media-clip.add",
            "timeline.move", "timeline.delete", "timeline.trim",
            "timeline.temporal.set-rate", "timeline.temporal.set-direction", "timeline.temporal.freeze",
            "audio.gain.set", "audio.mute.set", "audio.balance.set",
            "timeline.group.create", "timeline.group.update-members", "timeline.group.remove",
            "timeline.sync.create", "timeline.sync.update-anchor", "timeline.sync.remove",
            "timeline.text.add", "timeline.text.remove", "timeline.text.replace-content",
            "timeline.text.set-style-range", "timeline.text.set-paragraph-style",
            "timeline.text.set-font-selection", "timeline.text.set-fallback-policy",
            "timeline.text.set-variable-axis", "timeline.text.set-layout");

    @Test
    void v1VocabularyIsFrozenAt25() {
        assertEquals(25, OperationDefinition.V1.ALL.size(), "AC-OPS-1");
        assertTrue(OperationDefinition.V1.ALL.stream().allMatch(d ->
                        d.version().equals(OperationDefinitionVersion.of(1, 0))
                                && d.lifecycle() == OperationDefinition.Lifecycle.ACTIVE),
                "every V1 definition stays @1.0 ACTIVE");
    }

    @Test
    void v1IdsAreLiterallyUnchanged() {
        List<String> actual = OperationDefinition.V1.ALL.stream()
                .map(d -> d.definitionId().value()).sorted().toList();
        assertEquals(FROZEN_V1_IDS.stream().sorted().toList(), actual, "AC-OPS-2");
    }

    @Test
    void v2AddsExactlyCreateAlignment() {
        assertEquals(26, OperationDefinition.V2.ALL.size(), "AC-OPS-3");
        assertTrue(OperationDefinition.V2.ALL.containsAll(OperationDefinition.V1.ALL),
                "V2 must be a superset of V1");
        assertEquals(List.of(OperationDefinition.V2.CREATE_ALIGNMENT),
                OperationDefinition.V2.ALL.stream()
                        .filter(d -> !OperationDefinition.V1.ALL.contains(d)).toList(),
                "exactly one added definition");
    }

    @Test
    void createAlignmentContract() {
        OperationDefinition d = OperationDefinition.V2.CREATE_ALIGNMENT;
        assertEquals("timeline.alignment.create", d.definitionId().value(), "AC-OPS-5");
        assertEquals(OperationDefinitionVersion.of(1, 0), d.version(), "AC-OPS-4");
        assertEquals(OperationDefinition.TargetKind.ALIGNMENT, d.targetKind(), "AC-OPS-7");
        assertEquals(OperationDefinition.Lifecycle.ACTIVE, d.lifecycle());
        assertEquals(OperationParameters.CreateAlignmentParameters.class, d.parameterType());
    }
}
