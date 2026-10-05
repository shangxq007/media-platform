package com.example.platform.render.app.operation;

import com.example.platform.operation.operation.OperationDefinition;
import com.example.platform.operation.operation.OperationDefinitionVersion;
import com.example.platform.operation.operation.OperationParameters;
import com.example.platform.operation.operation.OperationRequest;
import com.example.platform.operation.operation.OperationTargetRequest;
import com.example.platform.timeline.semantics.relationship.AlignmentAttribute;
import com.example.platform.timeline.semantics.relationship.AlignmentAttributeValue;
import com.example.platform.timeline.semantics.relationship.AlignmentId;
import com.example.platform.timeline.semantics.relationship.SourceUnitId;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** L5-V2: the alignment command projects to the canonical CREATE_ALIGNMENT OperationRequest. */
class AlignmentOperationServiceCommandTest {

    @Test
    void buildsCanonicalOperationRequest() {
        var command = new AlignmentOperationService.CreateAlignmentCommand(
                AlignmentId.of("align-1"),
                Set.of(SourceUnitId.of("s1")),
                Set.of(SourceUnitId.of("d1")),
                List.of(new AlignmentAttribute("lang", new AlignmentAttributeValue.TextValue("en-US"))),
                "R100", "content-hash");

        OperationRequest request = command.toOperationRequest("timeline-T");

        assertEquals(OperationDefinition.V2.CREATE_ALIGNMENT.definitionId(), request.definitionId());
        assertEquals(OperationDefinitionVersion.of(1, 0), request.version());
        assertTrue(request.target() instanceof OperationTargetRequest.TimelineTargetRequest);
        assertEquals("timeline-T",
                ((OperationTargetRequest.TimelineTargetRequest) request.target()).timelineId());
        assertTrue(request.parameters() instanceof OperationParameters.CreateAlignmentParameters);
        assertEquals("R100", request.baseRevisionId());
        assertEquals("content-hash", request.baseContentHash());
    }
}
