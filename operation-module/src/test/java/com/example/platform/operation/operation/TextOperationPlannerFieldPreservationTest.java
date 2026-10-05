package com.example.platform.operation.operation;

import com.example.platform.operation.testsupport.CanonicalFieldsAssert;
import com.example.platform.operation.testsupport.CanonicalFieldsAssert.Field;
import com.example.platform.operation.testsupport.RichTimelineDocument;
import com.example.platform.timeline.canonical.TextElementId;
import com.example.platform.timeline.canonical.TimelineDocument;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * FIELD-PRESERVATION-P0 (Sink 4): the {@link TextOperationPlanner} candidate must preserve
 * all canonical fields it does not author. The candidate is built once for every text
 * operation, so any text op exercises the sink; this asserts on it directly for two ops
 * that need no font-resolution input (ADD needs a full resolution harness).
 */
class TextOperationPlannerFieldPreservationTest {

    private static final String REV = RichTimelineDocument.BASE_REVISION_ID;
    private static final String HASH = RichTimelineDocument.BASE_CONTENT_HASH;

    @Test
    void removeTextElementCandidatePreservesUntouchedFields() {
        TimelineDocument base = RichTimelineDocument.rich();
        OperationRequest request = request(OperationDefinition.V1.REMOVE_TEXT_ELEMENT,
                new OperationParameters.RemoveTextElementParameters(new TextElementId("elem-1")));

        TimelineDocument candidate =
                new TextOperationPlanner().plan(request, base, null).candidateTimeline();

        CanonicalFieldsAssert.assertPreserved(base, candidate, Field.TEXT_ELEMENTS);
        assertTrue(candidate.getTextElements().isEmpty(), "remove authors textElements");
    }

    @Test
    void setTextLayoutCandidatePreservesUntouchedFields() {
        TimelineDocument base = RichTimelineDocument.rich();
        OperationRequest request = request(OperationDefinition.V1.SET_TEXT_LAYOUT,
                new OperationParameters.SetTextLayoutParameters(
                        new TextElementId("elem-1"), RichTimelineDocument.altFrame()));

        TimelineDocument candidate =
                new TextOperationPlanner().plan(request, base, null).candidateTimeline();

        CanonicalFieldsAssert.assertPreserved(base, candidate, Field.TEXT_ELEMENTS);
        assertEquals(1, candidate.getTextElements().size(), "layout keeps one element");
        assertNotEquals(base.getTextElements(), candidate.getTextElements(), "layout authors textElements");
    }

    private static OperationRequest request(OperationDefinition definition, OperationParameters params) {
        return new OperationRequest(definition.definitionId(), definition.version(),
                new OperationTargetRequest.TimelineTargetRequest("timeline-T"),
                params, REV, HASH, null);
    }
}
