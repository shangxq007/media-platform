package com.example.platform.operation.operation;

import com.example.platform.audio.domain.mix.AudioMix;
import com.example.platform.timeline.canonical.TimelineDocument;
import com.example.platform.timeline.canonical.TimelineMetadata;
import com.example.platform.timeline.semantics.relationship.AlignmentId;
import com.example.platform.timeline.semantics.relationship.SourceUnitId;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** L5-V2: CREATE_ALIGNMENT resolves end-to-end (AC-OPS-6 / AC-PARAM-8). */
class OperationV2AlignmentResolveTest {

    private static final String REV = "R100";
    private static final String HASH = "hash-100";

    @Test
    void resolvesCreateAlignmentToTimelineScopedInstance() throws Exception {
        TimelineDocument base = new TimelineDocument(TimelineDocument.CURRENT_SCHEMA_VERSION,
                List.of(), TimelineMetadata.empty(), AudioMix.EMPTY, List.of());
        var params = new OperationParameters.CreateAlignmentParameters(
                AlignmentId.of("align-1"),
                Set.of(SourceUnitId.of("s1")), Set.of(SourceUnitId.of("d1")), List.of());
        OperationRequest request = new OperationRequest(
                OperationDefinition.V2.CREATE_ALIGNMENT.definitionId(),
                OperationDefinition.V2.CREATE_ALIGNMENT.version(),
                new OperationTargetRequest.TimelineTargetRequest("timeline-T"),
                params, REV, HASH, null);

        OperationInstance instance = OperationRequestResolver.resolve(request,
                new OperationRequestResolver.OperationBaseContext(REV, HASH, base, "timeline-T"));

        assertEquals("timeline.alignment.create", instance.definitionId().value());
        assertTrue(instance.target() instanceof OperationTarget.TimelineTarget);
        assertEquals("timeline-T", ((OperationTarget.TimelineTarget) instance.target()).timelineId());
        assertNotNull(instance.parameterDigest());
    }
}
