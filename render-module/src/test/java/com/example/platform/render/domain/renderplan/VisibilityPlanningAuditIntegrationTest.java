package com.example.platform.render.domain.renderplan;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.platform.render.app.visibility.TemporalVisibilityAnalysis;
import com.example.platform.render.app.visibility.VisibilityPlanningAudit;
import com.example.platform.render.app.visibility.VisibilityPlanningAuditService;
import com.example.platform.shared.time.FrameRate;
import com.example.platform.shared.time.MediaTime;
import com.example.platform.timeline.canonical.TimelineClip;
import com.example.platform.timeline.canonical.TimelineDocument;
import com.example.platform.timeline.canonical.TimelineMetadata;
import com.example.platform.timeline.canonical.TimelineTrack;
import com.example.platform.timeline.canonical.TrackType;
import java.util.List;
import org.junit.jupiter.api.Test;

class VisibilityPlanningAuditIntegrationTest {

    private final VisibilityPlanningAuditService auditService = new VisibilityPlanningAuditService();

    @Test
    void auditNeverChangesTheOriginalPlanIdentity() {
        RenderPlanningInput input = TestPlans.canonicalInput();
        RenderPlanningResult original = new DefaultRenderPlanner().plan(input);
        VisibilityPlanningAudit audit = auditService.audit(
                "tenant", "project", TestPlans.REVISION_ID,
                document(), input.request(), original);

        assertThat(audit.originalPlanFingerprint())
                .isEqualTo(original.plan().fingerprint().sha256Hex());
        assertThat(audit.originalPlanId()).isEqualTo(original.plan().id().value());
        assertThat(audit.graphChanged()).isFalse();
        assertThat(audit.analysis()).isPresent();
        assertThat(audit.analysis().orElseThrow().candidates()).isEmpty();
        assertThat(audit.reasons()).anyMatch(r -> r.startsWith("NO_SAFE_INCREMENTAL_PRUNING"));
    }

    @Test
    void missingCanonicalDocumentFallsBackWithoutPlanChange() {
        RenderPlanningInput input = TestPlans.canonicalInput();
        RenderPlanningResult original = new DefaultRenderPlanner().plan(input);
        VisibilityPlanningAudit audit = auditService.audit(
                "tenant", "project", TestPlans.REVISION_ID, null, input.request(), original);

        assertThat(audit.originalPlanFingerprint())
                .isEqualTo(original.plan().fingerprint().sha256Hex());
        assertThat(audit.graphChanged()).isFalse();
        assertThat(audit.analysis()).isEmpty();
        assertThat(audit.reasons()).anyMatch(r -> r.startsWith("ANALYSIS_FALLBACK"));
    }

    @Test
    void auditIsDeterministicAndRequestSensitive() {
        RenderPlanningInput input = TestPlans.canonicalInput();
        RenderPlanningResult original = new DefaultRenderPlanner().plan(input);
        VisibilityPlanningAudit first = auditService.audit(
                "tenant", "project", TestPlans.REVISION_ID, document(), input.request(), original);
        VisibilityPlanningAudit second = auditService.audit(
                "tenant", "project", TestPlans.REVISION_ID, document(), input.request(), original);
        RenderRequest otherRequest = new RenderRequest(
                new RenderRequestId("other"),
                new RenderExtent(MediaTime.ZERO, MediaTime.ofRational(2, 1), FrameRate.of(1, 1)),
                input.request().outputs());
        VisibilityPlanningAudit other = auditService.audit(
                "tenant", "project", TestPlans.REVISION_ID, document(), otherRequest, original);

        assertThat(first.proofDigest()).isEqualTo(second.proofDigest());
        assertThat(first.proofDigest()).isNotEqualTo(other.proofDigest());
        assertThat(first.originalPlanFingerprint()).isEqualTo(other.originalPlanFingerprint());
    }

    private static TimelineDocument document() {
        TimelineClip clip = new TimelineClip("c1", "art-1", "stream-1", "art-1", "digest-1",
                MediaTime.ZERO, MediaTime.ofRational(1, 1), MediaTime.ZERO, MediaTime.ofRational(1, 1),
                "MEDIA_STREAM");
        return new TimelineDocument("timeline-1.0", List.of(
                new TimelineTrack("t1", "Video", TrackType.VIDEO, List.of(clip))),
                TimelineMetadata.empty());
    }
}
