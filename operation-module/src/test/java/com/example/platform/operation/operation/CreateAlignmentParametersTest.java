package com.example.platform.operation.operation;

import com.example.platform.timeline.semantics.relationship.AlignmentAttribute;
import com.example.platform.timeline.semantics.relationship.AlignmentAttributeValue;
import com.example.platform.timeline.semantics.relationship.AlignmentId;
import com.example.platform.timeline.semantics.relationship.SourceUnitId;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** L5-V2: CreateAlignmentParameters descriptor contract. */
class CreateAlignmentParametersTest {

    private static OperationParameters.CreateAlignmentParameters params(
            Set<SourceUnitId> source, Set<SourceUnitId> derived) {
        return new OperationParameters.CreateAlignmentParameters(
                AlignmentId.of("align-1"), source, derived,
                List.of(new AlignmentAttribute("lang", new AlignmentAttributeValue.TextValue("zh-CN"))));
    }

    @Test
    void constructsDescriptor() {
        var p = params(Set.of(SourceUnitId.of("s1")), Set.of(SourceUnitId.of("d1")));
        assertEquals("align-1", p.alignmentId().value());
        assertEquals(1, p.sourceUnits().size());
        assertEquals(1, p.derivedUnits().size());
        assertEquals(1, p.attributes().size());
    }

    @Test
    void rejectsEmptyEndpointSets() {
        assertThrows(IllegalArgumentException.class,
                () -> params(Set.of(), Set.of(SourceUnitId.of("d1"))));
        assertThrows(IllegalArgumentException.class,
                () -> params(Set.of(SourceUnitId.of("s1")), Set.of()));
    }

    @Test
    void doesNotCarrySourceBaseline() {
        // AC-PARAM-2: the descriptor is intent only — no baseline field exists.
        for (var c : OperationParameters.CreateAlignmentParameters.class.getRecordComponents()) {
            org.junit.jupiter.api.Assertions.assertNotEquals("sourceBaseline", c.getName());
        }
    }
}
