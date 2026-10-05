package com.example.platform.operation.operation;

import com.example.platform.timeline.semantics.relationship.AlignmentId;
import com.example.platform.timeline.semantics.relationship.SourceUnitId;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/** L5-V2: ParameterDigest covers CreateAlignmentParameters deterministically. */
class ParameterDigestAlignmentTest {

    private static final OperationDefinitionVersion V = OperationDefinitionVersion.of(1, 0);

    private static String digest(String alignmentId, String... sourceUnits) {
        Set<SourceUnitId> s = new java.util.TreeSet<>();
        for (String v : sourceUnits) {
            s.add(SourceUnitId.of(v));
        }
        var p = new OperationParameters.CreateAlignmentParameters(
                AlignmentId.of(alignmentId), s, Set.of(SourceUnitId.of("d1")), List.of());
        return ParameterDigest.compute(OperationDefinition.V2.CREATE_ALIGNMENT.definitionId(), V, p);
    }

    @Test
    void sameDescriptorSameDigest() {
        assertEquals(digest("a1", "s1", "s2"), digest("a1", "s1", "s2"));
    }

    @Test
    void differentAlignmentIdDifferentDigest() {
        assertNotEquals(digest("a1", "s1"), digest("a2", "s1"));
    }

    @Test
    void differentEndpointsDifferentDigest() {
        assertNotEquals(digest("a1", "s1"), digest("a1", "s1", "s2"));
    }
}
