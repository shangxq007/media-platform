package com.example.platform.operation.operation;

import com.example.platform.timeline.semantics.relationship.AlignmentAttribute;
import com.example.platform.timeline.semantics.relationship.AlignmentAttributeValue;
import com.example.platform.timeline.semantics.relationship.AlignmentId;
import com.example.platform.timeline.semantics.relationship.SourceUnitId;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** L5-V2: descriptor serialization is order-insensitive for endpoints and attributes. */
class ParameterDigestAlignmentDeterminismTest {

    private static final OperationDefinitionVersion V = OperationDefinitionVersion.of(1, 0);

    private static String digest(Set<SourceUnitId> source, Set<SourceUnitId> derived,
                                 List<AlignmentAttribute> attrs) {
        var p = new OperationParameters.CreateAlignmentParameters(
                AlignmentId.of("a1"), source, derived, attrs);
        return ParameterDigest.compute(OperationDefinition.V2.CREATE_ALIGNMENT.definitionId(), V, p);
    }

    @Test
    void endpointInsertionOrderDoesNotAffectDigest() {
        Set<SourceUnitId> forward = new LinkedHashSet<>(List.of(SourceUnitId.of("s1"), SourceUnitId.of("s2")));
        Set<SourceUnitId> reverse = new LinkedHashSet<>(List.of(SourceUnitId.of("s2"), SourceUnitId.of("s1")));
        Set<SourceUnitId> derived = Set.of(SourceUnitId.of("d1"));

        assertEquals(digest(forward, derived, List.of()), digest(reverse, derived, List.of()));
    }

    @Test
    void attributeOrderDoesNotAffectDigest() {
        Set<SourceUnitId> source = Set.of(SourceUnitId.of("s1"));
        Set<SourceUnitId> derived = Set.of(SourceUnitId.of("d1"));
        List<AlignmentAttribute> forward = new ArrayList<>(List.of(
                new AlignmentAttribute("alpha", new AlignmentAttributeValue.BooleanValue(true)),
                new AlignmentAttribute("zeta", new AlignmentAttributeValue.TextValue("z"))));
        List<AlignmentAttribute> reverse = new ArrayList<>(List.of(
                new AlignmentAttribute("zeta", new AlignmentAttributeValue.TextValue("z")),
                new AlignmentAttribute("alpha", new AlignmentAttributeValue.BooleanValue(true))));

        assertEquals(digest(source, derived, forward), digest(source, derived, reverse));
    }
}
