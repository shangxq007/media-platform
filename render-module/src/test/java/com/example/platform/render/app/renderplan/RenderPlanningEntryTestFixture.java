package com.example.platform.render.app.renderplan;

import com.example.platform.audio.domain.mix.AudioMix;
import com.example.platform.render.domain.renderplan.CapabilityContext;
import com.example.platform.render.domain.renderplan.RenderExtent;
import com.example.platform.render.domain.renderplan.RenderOutputRequirement;
import com.example.platform.render.domain.renderplan.RenderOutputRole;
import com.example.platform.render.domain.renderplan.RenderRequest;
import com.example.platform.render.domain.renderplan.RenderRequestId;
import com.example.platform.render.domain.renderplan.RenderSourceResolutionState;
import com.example.platform.render.domain.renderplan.SourceResolutionInput;
import com.example.platform.shared.capability.CapabilityId;
import com.example.platform.shared.identity.ArtifactId;
import com.example.platform.shared.time.FrameRate;
import com.example.platform.shared.time.MediaTime;
import com.example.platform.timeline.canonical.TimelineClip;
import com.example.platform.timeline.canonical.TimelineContentDigester;
import com.example.platform.timeline.canonical.TimelineDocument;
import com.example.platform.timeline.canonical.TimelineMetadata;
import com.example.platform.timeline.canonical.TimelineTrack;
import com.example.platform.timeline.canonical.TrackType;
import com.example.platform.timeline.semantics.effect.EffectDefinitionVersionRegistry;
import com.example.platform.timeline.semantics.effect.EffectSemanticSnapshot;
import com.example.platform.timeline.semantics.effect.EffectSemanticSnapshotAuthority;
import com.example.platform.timeline.semantics.effect.EffectSemanticSnapshotStore;
import com.example.platform.timeline.semantics.effect.TimelineRevisionEffectSemanticCommitment;
import com.example.platform.timeline.semantics.temporal.ConstantRateTemporalMapping;
import com.example.platform.timeline.semantics.temporal.PlaybackDirection;
import com.example.platform.timeline.version.TimelineRevision;
import com.example.platform.timeline.version.TimelineRevisionSemanticContext;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Hermetic fixture for the #20 entry-service tests: one authoritative immutable
 * revision (single MEDIA_STREAM clip, no effects) whose digests are computed by
 * the real timeline authorities — no hand-written digest placeholders.
 */
final class RenderPlanningEntryTestFixture {

    static final String REVISION_ID = "rev-p21";
    static final String PRODUCT_ID = "product-p21";
    static final String TRACK_ID = "track-p21";
    static final String CLIP_ID = "clip-p21";
    static final String ARTIFACT_ID = "art-p21";
    static final String ARTIFACT_DIGEST_HEX = "a".repeat(64);

    /**
     * The single authored EMPTY snapshot pinned by {@link #revision()}. Cached so
     * that a revision and the snapshot handed to the entry service are the SAME
     * object identity — the revision's exact pin must match what is supplied.
     */
    private static final EffectSemanticSnapshot PINNED_EMPTY_SNAPSHOT = mintEmptySnapshot();

    private RenderPlanningEntryTestFixture() {
    }

    static TimelineDocument document() {
        TimelineClip clip = new TimelineClip(
                CLIP_ID,
                "asset-p21",
                "stream-p21",
                ARTIFACT_ID,
                ARTIFACT_DIGEST_HEX,
                MediaTime.ofRational(0, 1),
                MediaTime.ofRational(2, 1),
                MediaTime.ofRational(0, 1),
                MediaTime.ofRational(2, 1),
                "MEDIA_STREAM",
                ConstantRateTemporalMapping.of(1, 1, PlaybackDirection.FORWARD));
        return new TimelineDocument(
                TimelineDocument.CURRENT_SCHEMA_VERSION,
                List.of(new TimelineTrack(TRACK_ID, "v1", TrackType.VIDEO, List.of(clip))),
                TimelineMetadata.empty(),
                AudioMix.EMPTY,
                List.of(),
                List.of());
    }

    /** The authored EMPTY snapshot pinned by {@link #revision()} (stable identity). */
    static EffectSemanticSnapshot emptyEffectSnapshot() {
        return PINNED_EMPTY_SNAPSHOT;
    }

    /**
     * A NEW authoritative EMPTY snapshot with a distinct generated id — used to
     * prove the entry fails closed when a supplied snapshot is not the one the
     * revision pins (RP1/BI2).
     */
    static EffectSemanticSnapshot newEmptyEffectSnapshot() {
        return mintEmptySnapshot();
    }

    private static EffectSemanticSnapshot mintEmptySnapshot() {
        return new EffectSemanticSnapshotAuthority(
                new EffectDefinitionVersionRegistry.InMemory(),
                new EffectSemanticSnapshotStore.InMemory())
                .mintEmpty();
    }

    /** A valid immutable revision owning its exact Effect pin and revision semantic digest. */
    static TimelineRevision revision(TimelineDocument document, EffectSemanticSnapshot snapshot) {
        return revisionWithTimelineDigest(
                new TimelineContentDigester().digest(document), snapshot, document);
    }

    /** Convenience: the canonical fixture revision (single clip, EMPTY effects). */
    static TimelineRevision revision() {
        return revision(document(), emptyEffectSnapshot());
    }

    /**
     * Builds a revision from an explicit timeline digest and canonical document.
     *
     * <p>{@code canonicalTimeline} may be {@code null} to model a NON-hydrated
     * revision, and {@code timelineDigest} may be deliberately wrong to model a
     * content-digest mismatch. In both cases the revision object itself stays
     * internally consistent (contentDigest == revision semantic digest); the #20
     * verification boundary is what must fail closed.
     */
    static TimelineRevision revisionWithTimelineDigest(
            String timelineDigest, EffectSemanticSnapshot snapshot, TimelineDocument canonicalTimeline) {
        String revisionSemanticDigest = TimelineRevisionEffectSemanticCommitment
                .revisionEffectSemanticDigest(timelineDigest, snapshot.reference());
        return new TimelineRevision(
                REVISION_ID,
                PRODUCT_ID,
                null,
                TimelineDocument.CURRENT_SCHEMA_VERSION,
                canonicalTimeline,
                revisionSemanticDigest,
                Instant.EPOCH,
                "p21-fixture",
                new TimelineRevisionSemanticContext(
                        timelineDigest,
                        snapshot.reference(),
                        revisionSemanticDigest,
                        TimelineRevisionSemanticContext.REVISION_SEMANTICS_V1));
    }

    /** SHA-256 (Base64) of the canonical fixture document. */
    static String canonicalTimelineDigest() {
        return new TimelineContentDigester().digest(document());
    }

    static RenderRequest request() {
        return new RenderRequest(
                new RenderRequestId("req-p21"),
                new RenderExtent(
                        MediaTime.ofRational(0, 1),
                        MediaTime.ofRational(2, 1),
                        FrameRate.of(30, 1)),
                List.of(RenderOutputRequirement.of(RenderOutputRole.RENDER_MASTER)));
    }

    static SourceResolutionInput resolvedSources() {
        return new SourceResolutionInput(Map.of(
                new ArtifactId(ARTIFACT_ID), RenderSourceResolutionState.RESOLVED));
    }

    static CapabilityContext capabilities() {
        return new CapabilityContext(Set.of(
                CapabilityId.of("video.decode"),
                CapabilityId.of("render.composite"),
                CapabilityId.of("render.output")));
    }
}
