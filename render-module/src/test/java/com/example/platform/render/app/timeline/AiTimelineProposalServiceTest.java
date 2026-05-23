package com.example.platform.render.app.timeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.platform.render.app.TimelinePatchService;
import com.example.platform.render.app.TimelineValidationService;
import com.example.platform.render.domain.timeline.TimelineExtensionsReader;
import com.fasterxml.jackson.databind.node.TextNode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AiTimelineProposalServiceTest {

    private AiTimelineProposalService service;
    private String baseTimeline;

    @BeforeEach
    void setUp() throws Exception {
        TimelinePatchService patchService = new TimelinePatchService(
                new TimelineValidationService(new InternalTimelineValidationService()),
                TimelineTestSupport.internalTimelineAdapter(),
                new TimelineCanonicalizer());
        service = new AiTimelineProposalService(patchService);
        baseTimeline = loadSample();
    }

    @Test
    void appendAdoptAndRejectProposal() {
        String withPending = service.appendPendingPatchProposal(
                baseTimeline,
                "lower bgm",
                List.of(new TimelinePatchService.PatchOperation(
                        "replace", "/metadata/platform.ai.lastStubEdit", new TextNode("true"))));
        assertEquals(1, service.listProposals(withPending).size());
        assertEquals("PENDING", service.listProposals(withPending).get(0).status());

        String proposalId = service.listProposals(withPending).get(0).id();
        var adopted = service.adopt(withPending, proposalId);
        assertTrue(adopted.applied());
        assertEquals("ACCEPTED", service.listProposals(adopted.timelineJson()).get(0).status());

        String withPending2 = service.appendPendingPatchProposal(
                baseTimeline,
                "another",
                List.of(new TimelinePatchService.PatchOperation(
                        "replace", "/metadata/platform.ai.pendingFlag", new TextNode("yes"))));
        String proposalId2 = service.listProposals(withPending2).get(0).id();
        var rejected = service.reject(withPending2, proposalId2);
        assertEquals("REJECTED", service.listProposals(rejected.timelineJson()).get(0).status());
    }

    private static String loadSample() throws Exception {
        Path path = Path.of("../../docs/media-rendering/examples/timeline-v1-full-sample.json");
        if (!Files.exists(path)) {
            path = Path.of("docs/media-rendering/examples/timeline-v1-full-sample.json");
        }
        return Files.readString(path);
    }
}
