package com.example.platform.workflow.plan;

import static org.junit.jupiter.api.Assertions.*;

import com.example.platform.media.domain.identity.MediaAssetId;
import com.example.platform.media.domain.stream.MediaStreamId;
import com.example.platform.operation.operation.*;
import com.example.platform.shared.digest.ContentDigest;
import com.example.platform.shared.identity.ArtifactId;
import com.example.platform.shared.time.MediaTime;
import com.example.platform.timeline.canonical.TimelineClipId;
import com.example.platform.timeline.semantics.clip.*;
import com.example.platform.timeline.semantics.temporal.*;
import com.fasterxml.jackson.databind.*;

import org.junit.jupiter.api.Test;

class WorkflowOperationValueJsonTest {
    @Test
    void actualMediaOperationRoundTripsExactOwnerValues() throws Exception {
        var mapper = new ObjectMapper().disable(MapperFeature.AUTO_DETECT_IS_GETTERS);
        WorkflowOperationValueJson.configure(mapper);
        var range = new MediaClip.TimeRange(MediaTime.ZERO, MediaTime.ofRational(10, 3));
        var source =
                new MediaStreamSourceBinding(
                        new MediaAssetId("asset"),
                        new MediaStreamId("stream"),
                        new ArtifactId("artifact"),
                        ContentDigest.sha256("a".repeat(64)),
                        range);
        var parameters =
                new OperationParameters.AddMediaClipParameters(
                        "track",
                        new TimelineClipId("clip"),
                        source,
                        range,
                        ConstantRateTemporalMapping.of(1, 1, PlaybackDirection.FORWARD));
        var request =
                new OperationRequest(
                        OperationDefinition.V1.ADD_MEDIA_CLIP.definitionId(),
                        OperationDefinitionVersion.V1_0,
                        new OperationTargetRequest.TimelineTargetRequest("project"),
                        parameters,
                        "revision",
                        "hash",
                        null);
        String json = mapper.writeValueAsString(request);
        assertEquals(request, mapper.readValue(json, OperationRequest.class));
        assertTrue(json.contains("10/3"));
        assertThrows(
                com.fasterxml.jackson.core.JsonProcessingException.class,
                () ->
                        mapper.readValue(
                                json.replace("AddMediaClipParameters", "java.lang.Runtime"),
                                OperationRequest.class));
    }
}
