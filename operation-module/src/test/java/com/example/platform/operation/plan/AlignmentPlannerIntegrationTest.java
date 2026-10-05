package com.example.platform.operation.plan;

import com.example.platform.operation.operation.OperationDefinition;
import com.example.platform.operation.operation.OperationInstance;
import com.example.platform.operation.operation.OperationParameters;
import com.example.platform.operation.operation.OperationTarget;
import com.example.platform.operation.testsupport.CanonicalFieldsAssert;
import com.example.platform.operation.testsupport.CanonicalFieldsAssert.Field;
import com.example.platform.operation.testsupport.RichTimelineDocument;
import com.example.platform.timeline.canonical.TimelineDocument;
import com.example.platform.timeline.semantics.relationship.AlignmentAttribute;
import com.example.platform.timeline.semantics.relationship.AlignmentAttributeValue;
import com.example.platform.timeline.semantics.relationship.AlignmentId;
import com.example.platform.timeline.semantics.relationship.AlignmentRelationship;
import com.example.platform.timeline.semantics.relationship.SourceUnitId;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * L5-V2 (Phase E): the planner materialises CREATE_ALIGNMENT into a canonical
 * {@link AlignmentRelationship} inside the candidate Timeline, for all cardinalities, while
 * preserving every other canonical field.
 */
class AlignmentPlannerIntegrationTest {

    private static final String REV = RichTimelineDocument.BASE_REVISION_ID;
    private static final String HASH = RichTimelineDocument.BASE_CONTENT_HASH;

    @Test
    void oneToOneAlignment() {
        assertAlignment(List.of("s1"), List.of("d1"));
    }

    @Test
    void oneToManyAlignment() {
        assertAlignment(List.of("s1"), List.of("d1", "d2"));
    }

    @Test
    void manyToOneAlignment() {
        assertAlignment(List.of("s1", "s2"), List.of("d1"));
    }

    @Test
    void manyToManyAlignment() {
        assertAlignment(List.of("s1", "s2"), List.of("d1", "d2"));
    }

    private static void assertAlignment(List<String> source, List<String> derived) {
        TimelineDocument base = RichTimelineDocument.rich();
        Set<SourceUnitId> sourceUnits = units(source);
        Set<SourceUnitId> derivedUnits = units(derived);
        var params = new OperationParameters.CreateAlignmentParameters(
                AlignmentId.of("align-1"), sourceUnits, derivedUnits,
                List.of(new AlignmentAttribute("lang", new AlignmentAttributeValue.TextValue("en-US"))));
        OperationInstance inst = new OperationInstance(
                OperationDefinition.V2.CREATE_ALIGNMENT.definitionId(),
                OperationDefinition.V2.CREATE_ALIGNMENT.version(), REV, HASH,
                new OperationTarget.TimelineTarget("timeline-T"), params, "digest", null);

        OperationPlan plan = new OperationPlanner().plan(inst, REV, base);
        TimelineDocument candidate = plan.candidateTimeline();

        List<AlignmentRelationship> alignments = candidate.getSemanticRelationships().stream()
                .filter(r -> r instanceof AlignmentRelationship)
                .map(r -> (AlignmentRelationship) r).toList();
        assertEquals(1, alignments.size(), "exactly one alignment added");
        AlignmentRelationship rel = alignments.get(0);
        assertEquals(AlignmentId.of("align-1"), rel.alignmentId());
        assertEquals(sourceUnits, rel.sourceUnits());
        assertEquals(derivedUnits, rel.derivedUnits());
        assertEquals(REV, rel.sourceBaseline().sourceRevisionId());
        assertNotNull(rel.sourceBaseline().sourceContentDigest());
        assertEquals(1, rel.attributes().size());

        // Planned change surfaced.
        assertTrue(plan.plannedChanges().stream()
                        .anyMatch(c -> c instanceof PlannedChange.RelationshipAdded added && added.relationship().equals(rel)),
                "planned changes must contain the alignment addition");

        // Every other canonical field preserved (only relationships change).
        CanonicalFieldsAssert.assertPreserved(base, candidate, Field.SEMANTIC_RELATIONSHIPS);
    }

    private static Set<SourceUnitId> units(List<String> values) {
        Set<SourceUnitId> set = new TreeSet<>();
        values.forEach(v -> set.add(SourceUnitId.of(v)));
        return set;
    }
}
