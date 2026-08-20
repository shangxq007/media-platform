package com.example.platform.render.domain.renderplan;

import com.example.platform.audio.domain.mix.AudioGain;
import com.example.platform.audio.domain.mix.AudioMix;
import com.example.platform.audio.domain.mix.AudioMixInput;
import com.example.platform.audio.domain.mix.AudioMute;
import com.example.platform.audio.domain.mix.AudioRoute;
import com.example.platform.audio.domain.mix.StereoBalance;
import com.example.platform.extension.domain.CapabilityId;
import com.example.platform.fonttext.resolution.FontFallbackPolicy;
import com.example.platform.fonttext.resolution.ResolvedFontInstance;
import com.example.platform.fonttext.resolution.ResolvedFontRun;
import com.example.platform.fonttext.resource.FaceIndex;
import com.example.platform.fonttext.resource.FontContentDigest;
import com.example.platform.fonttext.resource.FontFormat;
import com.example.platform.fonttext.resource.ValidatedFontExecutionReference;
import com.example.platform.fonttext.security.FontSecurityState;
import com.example.platform.fonttext.text.ParagraphBaseDirection;
import com.example.platform.fonttext.text.RangeDirectionOverride;
import com.example.platform.fonttext.text.ScriptTag;
import com.example.platform.fonttext.text.StyledText;
import com.example.platform.fonttext.text.TextContent;
import com.example.platform.fonttext.text.TextRange;
import com.example.platform.fonttext.text.TextSemanticRun;
import com.example.platform.fonttext.typography.FontFamilyName;
import com.example.platform.fonttext.typography.FontRational;
import com.example.platform.fonttext.typography.FontSelectionIntent;
import com.example.platform.fonttext.typography.FontSize;
import com.example.platform.fonttext.typography.LineHeight;
import com.example.platform.fonttext.typography.OpenTypeFeatureIntent;
import com.example.platform.fonttext.typography.OpticalSizingIntent;
import com.example.platform.fonttext.typography.ParagraphStyle;
import com.example.platform.fonttext.typography.TextFrame;
import com.example.platform.media.domain.identity.MediaAssetId;
import com.example.platform.media.domain.stream.MediaStreamId;
import com.example.platform.shared.digest.ContentDigest;
import com.example.platform.shared.identity.ArtifactId;
import com.example.platform.shared.time.FrameRate;
import com.example.platform.shared.time.MediaTime;
import com.example.platform.timeline.canonical.TextElement;
import com.example.platform.timeline.canonical.TextElementId;
import com.example.platform.timeline.canonical.TimelineClip;
import com.example.platform.timeline.canonical.TimelineContentDigester;
import com.example.platform.timeline.canonical.TimelineDocument;
import com.example.platform.timeline.canonical.TimelineMetadata;
import com.example.platform.timeline.canonical.TimelineTrack;
import com.example.platform.timeline.canonical.TrackType;
import com.example.platform.timeline.semantics.clip.MediaClip;
import com.example.platform.timeline.semantics.clip.MediaStreamSourceBinding;
import com.example.platform.timeline.semantics.effect.AuthoredEffectSemanticAuthority;
import com.example.platform.timeline.semantics.effect.ClipEffectTarget;
import com.example.platform.timeline.semantics.effect.EffectSemanticSnapshot;
import com.example.platform.timeline.semantics.effect.EffectSemanticSnapshotAuthority;
import com.example.platform.timeline.semantics.effect.EffectSemanticSnapshotId;
import com.example.platform.timeline.semantics.effect.EffectSemanticSnapshotReference;
import com.example.platform.timeline.semantics.effect.EffectDefinitionVersionRegistry;
import com.example.platform.timeline.canonicalmodel.TimelineSourceRef;
import com.example.platform.timeline.canonicalmodel.TimelineClipEffect;
import com.example.platform.timeline.canonicalmodel.TimelineCanonicalProfile;
import com.example.platform.timeline.canonicalmodel.TimelineCandidate;
import com.example.platform.timeline.semantics.effect.RevisionOwnedEffectProjection;
import com.example.platform.timeline.semantics.effect.EffectInstance;
import com.example.platform.timeline.semantics.effect.EffectSemanticBinding;
import com.example.platform.timeline.semantics.temporal.ConstantRateTemporalMapping;
import com.example.platform.timeline.semantics.temporal.FreezeTemporalMapping;
import com.example.platform.timeline.semantics.temporal.PlaybackDirection;
import com.example.platform.timeline.version.TimelineRevision;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Package-private canonical test fixtures (brief §13, R2 B1/B2/B3). Builds one
 * canonical {@link RenderPlanningInput}: one MediaClip (t1/c1), one enabled
 * video GAUSSIAN_BLUR effect, one AudioMix route, one TextElement, a
 * RENDER_MASTER output request, fully-resolved source, and a full capability
 * context.
 *
 * <p>R2 B1: all clip/audio/text fragments are integrity-bound inside a
 * {@link VerifiedTimelineRevision} produced by
 * {@link VerifiedTimelineRevisionFactory} from an authoritative
 * {@link TimelineRevision} (canonical content digest verified). Effects and
 * effect definitions are separate explicit planning inputs (repository
 * reality: TimelineDocument does not carry effects). F3: capability context
 * uses platform {@link CapabilityId}s.
 */
final class TestPlans {

    static final String REVISION_ID = "rev-1";
    static final String REQUEST_ID = "req-1";
    static final String TRACK_ID = "t1";
    static final String CLIP_ID = "c1";
    static final String ARTIFACT_ID = "art-1";
    static final String EFFECT_INSTANCE_ID = "eff-1";
    static final String TEXT_ELEMENT_ID = "text-1";

    // 64-hex SHA-256 values
    static final String ARTIFACT_DIGEST_HEX =
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    static final String REVISION_DIGEST_HEX =
            "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";

    private TestPlans() {
    }

    /**
     * R4-A2: a canonical Effect semantic reference for test plan construction
     * (authoritative binding over the canonical fixture effect state).
     */
    static EffectSemanticReference testEffectReference() {
        return new EffectSemanticReference(
                effectSnapshotReference(List.of(gaussianBlurEffect()), List.of(effectDefinition())),
                REVISION_ID);
    }

    /**
     * FINAL: domain-only minted Effect semantic snapshot reference over the
     * given authored effect state + definitions (production authority path).
     */
    static EffectSemanticSnapshotReference effectSnapshotReference(
            List<EffectInstance> effects, List<EffectInstance.EffectDefinition> definitions) {
        return effectSnapshot(effects, definitions).reference();
    }

    /** FINAL: domain-only minted snapshot over the given state (authority path). */
    static EffectSemanticSnapshot effectSnapshot(
            List<EffectInstance> effects, List<EffectInstance.EffectDefinition> definitions) {
        return EffectSemanticSnapshotAuthority.mint(
                effects, definitions, TestPlans::clipFor,
                new EffectDefinitionVersionRegistry.InMemory(), EffectSemanticSnapshotId.generate());
    }

    /**
     * FINAL: domain-only minted snapshot with an EXTRA clip in the target
     * context (e.g. clip c2 alongside the canonical c1) — authority path.
     */
    static EffectSemanticSnapshot effectSnapshotWithContext(
            List<EffectInstance> effects, List<EffectInstance.EffectDefinition> definitions,
            List<MediaClip> extraClips) {
        List<MediaClip> clips = new java.util.ArrayList<>(canonicalClips());
        clips.addAll(extraClips);
        return EffectSemanticSnapshotAuthority.mint(
                effects, definitions,
                key -> clips.stream()
                        .filter(c -> c.trackId().equals(key.trackId()) && c.clipId().equals(key.clipId()))
                        .findFirst().orElse(null),
                new EffectDefinitionVersionRegistry.InMemory(), EffectSemanticSnapshotId.generate());
    }

    /** A second clip fixture (same-range c2 on the canonical track) for target tests. */
    static MediaClip secondClip() {
        MediaTime start = MediaTime.ofRational(0, 1);
        MediaTime end = MediaTime.ofRational(2, 1);
        MediaClip.TimeRange timelineRange = new MediaClip.TimeRange(start, end);
        MediaStreamSourceBinding binding = new MediaStreamSourceBinding(
                MediaAssetId.of("asset-2"), MediaStreamId.of("stream-2"),
                artifactId(), artifactDigest(), timelineRange);
        return new MediaClip("c2", TRACK_ID, timelineRange, timelineRange,
                com.example.platform.timeline.semantics.temporal.ConstantRateTemporalMapping.of(
                        1, 1, com.example.platform.timeline.semantics.temporal.PlaybackDirection.FORWARD),
                binding);
    }

    /** Clip context for snapshot minting: resolves (trackId, clipId) -> MediaClip. */
    static MediaClip clipFor(EffectSemanticSnapshotAuthority.ClipTargetKey key) {
        for (MediaClip clip : canonicalClips()) {
            if (clip.trackId().equals(key.trackId()) && clip.clipId().equals(key.clipId())) {
                return clip;
            }
        }
        return null;
    }

    /** Canonical MediaClip list (from the canonical TimelineDocument projection). */
    static List<MediaClip> canonicalClips() {
        return VerifiedTimelineRevisionFactory.verified(timelineRevision(), timelineDigester()).clips();
    }

    /**
     * FINAL: verified effect semantic snapshot through the production verified
     * boundary (mint -> pin -> verify against the pin).
     */
    static VerifiedEffectSemanticSnapshot verifiedEffectSnapshot(
            List<EffectInstance> effects, List<EffectInstance.EffectDefinition> definitions) {
        EffectSemanticSnapshot snapshot = effectSnapshot(effects, definitions);
        return VerifiedEffectSemanticSnapshotFactory.verified(
                snapshot, snapshot.reference(), REVISION_ID);
    }

    /** FINAL: verified effect semantic snapshot against a CUSTOM pin (RP/BI attacks). */
    static VerifiedEffectSemanticSnapshot verifiedEffectSnapshotAgainst(
            EffectSemanticSnapshot snapshot, EffectSemanticSnapshotReference pin) {
        return VerifiedEffectSemanticSnapshotFactory.verified(snapshot, pin, REVISION_ID);
    }

    /** The canonical planning input described in brief §13 (source RESOLVED). */
    static RenderPlanningInput canonicalInput() {
        return new RenderPlanningInput(
                verifiedAuthoredSnapshot(),
                renderRequest(),
                new SourceResolutionInput(Map.of(artifactId(), RenderSourceResolutionState.RESOLVED)),
                fullCapabilityContext());
    }

    /**
     * R3-B1: the ONE immutable integrity-bound authored semantic snapshot
     * (verified Timeline revision projection + verified authored effect
     * snapshot) built through the production factory.
     */
    static VerifiedRenderSemanticSnapshot verifiedAuthoredSnapshot() {
        TimelineDocument document = canonicalDocument();
        TimelineContentDigester digester = new TimelineContentDigester();
        String digest = digester.digest(document);
        TimelineRevision revision = new TimelineRevision(
                REVISION_ID, "product-1", null, TimelineDocument.CURRENT_SCHEMA_VERSION,
                document, digest, java.time.Instant.EPOCH, "test");
        EffectSemanticSnapshot snap = effectSnapshot(
                List.of(gaussianBlurEffect()), List.of(effectDefinition()));
        return VerifiedRenderSemanticSnapshotFactory.verified(
                revision, digester, snap, snap.reference());
    }

    /** Canonical input with the source in the given resolution state. */
    static RenderPlanningInput inputWithSourceState(RenderSourceResolutionState state) {
        RenderPlanningInput base = canonicalInput();
        return new RenderPlanningInput(
                base.authoredSnapshot(), base.request(),
                new SourceResolutionInput(Map.of(artifactId(), state)),
                base.capabilities());
    }

    /** Canonical input with the given capability context. */
    static RenderPlanningInput inputWithCapabilities(CapabilityContext capabilities) {
        RenderPlanningInput base = canonicalInput();
        return new RenderPlanningInput(
                base.authoredSnapshot(), base.request(),
                base.resolution(), capabilities);
    }

    /** Canonical input with a different request id. */
    static RenderPlanningInput inputWithRequestId(String requestId) {
        RenderPlanningInput base = canonicalInput();
        RenderRequest req = base.request();
        return new RenderPlanningInput(
                base.authoredSnapshot(),
                new RenderRequest(new RenderRequestId(requestId), req.extent(), req.outputs()),
                base.resolution(), base.capabilities());
    }

    /** Canonical input with a custom verified timeline revision projection. */
    static RenderPlanningInput inputWithTimeline(VerifiedTimelineRevision timeline) {
        RenderPlanningInput base = canonicalInput();
        return new RenderPlanningInput(
                new VerifiedRenderSemanticSnapshot(timeline, base.effectSemanticSnapshot()),
                base.request(), base.resolution(), base.capabilities());
    }

    /** Canonical input with a custom verified authored effect snapshot. */
    static RenderPlanningInput inputWithEffects(VerifiedEffectSemanticSnapshot effects) {
        RenderPlanningInput base = canonicalInput();
        return new RenderPlanningInput(
                new VerifiedRenderSemanticSnapshot(base.verifiedRevision(), effects),
                base.request(), base.resolution(), base.capabilities());
    }

    /**
     * Canonical input with custom authored effect state (minted + verified
     * through the production authority + verified boundary).
     */
    static RenderPlanningInput inputWithEffectState(
            List<EffectInstance> effects, List<EffectInstance.EffectDefinition> definitions) {
        RenderPlanningInput base = canonicalInput();
        VerifiedEffectSemanticSnapshot snapshot = verifiedEffectSnapshot(effects, definitions);
        return inputWithEffects(snapshot);
    }

    /**
     * R6-A: planning input with a CUSTOM effect state + a CUSTOM clip context
     * (target clip existence is verified against the supplied context at
     * mint time — the authority path).
     */
    static RenderPlanningInput inputWithEffectStateAndProjection(
            List<EffectInstance> effects, List<EffectInstance.EffectDefinition> definitions,
            RevisionOwnedEffectProjection projection) {
        RenderPlanningInput base = canonicalInput();
        VerifiedEffectSemanticSnapshot snapshot = verifiedEffectSnapshot(effects, definitions);
        return inputWithEffects(snapshot);
    }

    /**
     * R6: plans with the given authored effect state + definitions, using the
     * canonical revision-owned membership projection. Goes through the
     * production authority path only.
     */
    static RenderPlan planForEffects(
            List<EffectInstance> effects, List<EffectInstance.EffectDefinition> definitions) {
        RenderPlanningInput input = inputWithEffectStateAndProjection(
                effects, definitions, revisionOwnedProjection());
        return new DefaultRenderPlanner().plan(input).plan();
    }

    /**
     * R6: plans with the given authored effect state + definitions + a CUSTOM
     * revision-owned membership projection. Goes through the production
     * authority path only.
     */
    static RenderPlan planForEffects(
            List<EffectInstance> effects, List<EffectInstance.EffectDefinition> definitions,
            RevisionOwnedEffectProjection projection) {
        RenderPlanningInput input = inputWithEffectStateAndProjection(
                effects, definitions, projection);
        return new DefaultRenderPlanner().plan(input).plan();
    }

    /**
     * R6: plans with the given authored effect state + definitions + a CUSTOM
     * revision-owned membership projection + CUSTOM TimelineRevision (target
     * clip must exist in the revision's canonical timeline — R6-A5). Goes
     * through the production authority path only.
     */
    static RenderPlan planForEffects(
            List<EffectInstance> effects, List<EffectInstance.EffectDefinition> definitions,
            TimelineRevision timelineRevision, RevisionOwnedEffectProjection projection) {
        RenderPlanningInput base = canonicalInput();
        // mint context derived from the REVISION's own canonical timeline (so
        // target clips must exist in THAT revision — authentic membership).
        List<MediaClip> revisionClips = revisionClipsOf(timelineRevision);
        EffectSemanticSnapshot snapshot = EffectSemanticSnapshotAuthority.mint(
                effects, definitions,
                key -> revisionClips.stream()
                        .filter(c -> c.trackId().equals(key.trackId()) && c.clipId().equals(key.clipId()))
                        .findFirst().orElse(null),
                new EffectDefinitionVersionRegistry.InMemory(), EffectSemanticSnapshotId.generate());
        VerifiedEffectSemanticSnapshot verifiedSnapshot = VerifiedEffectSemanticSnapshotFactory.verified(
                snapshot, snapshot.reference(), timelineRevision.revisionId());
        VerifiedTimelineRevision verifiedRevision =
                VerifiedTimelineRevisionFactory.verified(timelineRevision, timelineDigester());
        RenderPlanningInput input = new RenderPlanningInput(
                new VerifiedRenderSemanticSnapshot(verifiedRevision, verifiedSnapshot),
                base.request(), base.resolution(), base.capabilities());
        return new DefaultRenderPlanner().plan(input).plan();
    }

    /** MediaClip list derived from a TimelineRevision's canonical document. */
    static List<MediaClip> revisionClipsOf(TimelineRevision revision) {
        return VerifiedTimelineRevisionFactory.verified(revision, timelineDigester()).clips();
    }

    /**
     * The canonical VERIFIED revision projection (R2 B1): constructed via the
     * authoritative hydration factory from a {@link TimelineRevision} whose
     * canonical content digest is computed by {@link TimelineContentDigester}
     * and recorded on the revision — the digest MUST match for construction to
     * succeed (fail closed).
     */
    static VerifiedTimelineRevision verifiedRevision() {
        TimelineDocument document = canonicalDocument();
        TimelineContentDigester digester = new TimelineContentDigester();
        String digest = digester.digest(document);
        TimelineRevision revision = new TimelineRevision(
                REVISION_ID, "product-1", null, TimelineDocument.CURRENT_SCHEMA_VERSION,
                document, digest, java.time.Instant.EPOCH, "test");
        return VerifiedTimelineRevisionFactory.verified(revision, digester);
    }

    /**
     * An authoritative TimelineRevision with a deliberately WRONG content digest
     * (for B1 digest-mismatch fail-closed tests).
     */
    static TimelineRevision tamperedRevision() {
        TimelineDocument document = canonicalDocument();
        TimelineRevision revision = new TimelineRevision(
                REVISION_ID, "product-1", null, TimelineDocument.CURRENT_SCHEMA_VERSION,
                document, "tampered-digest-value", java.time.Instant.EPOCH, "test");
        return revision;
    }

    /**
     * Verified revision with a custom clip (for exact-time mapping tests): the
     * authoritative document's digest is recomputed over the custom clip, so
     * verification succeeds against the SAME document the projection is
     * extracted from (B1 invariant).
     */
    static VerifiedTimelineRevision verifiedRevisionWithClip(com.example.platform.timeline.canonical.TimelineClip customClip) {
        TimelineDocument document = new TimelineDocument(
                TimelineDocument.CURRENT_SCHEMA_VERSION,
                List.of(new TimelineTrack(TRACK_ID, "v1",
                        TrackType.VIDEO, List.of(customClip))),
                TimelineMetadata.empty(),
                audioMix(),
                List.of(),
                List.of(textElement()));
        TimelineContentDigester digester = new TimelineContentDigester();
        String digest = digester.digest(document);
        TimelineRevision revision = new TimelineRevision(
                REVISION_ID, "product-1", null, TimelineDocument.CURRENT_SCHEMA_VERSION,
                document, digest, java.time.Instant.EPOCH, "test");
        return VerifiedTimelineRevisionFactory.verified(revision, digester);
    }

    /**
     * R4-A1: the authoritative TimelineRevision built over the canonical
     * document (digest recomputed so verification succeeds).
     */
    static TimelineRevision timelineRevision() {
        TimelineDocument document = canonicalDocument();
        TimelineContentDigester digester = new TimelineContentDigester();
        String digest = digester.digest(document);
        return new TimelineRevision(
                REVISION_ID, "product-1", null, TimelineDocument.CURRENT_SCHEMA_VERSION,
                document, digest, java.time.Instant.EPOCH, "test");
    }

    /** R4-A1: the timeline canonical content digester. */
    static TimelineContentDigester timelineDigester() {
        return new TimelineContentDigester();
    }

    /**
     * R6-A: the revision-owned authored effect membership projection for the
     * canonical fixture — effect {@code eff-1} (wire id) belongs to clip
     * {@code c1} on track {@code t1}, definition key {@code def-blur}.
     * Derived from the revision-owned wire aggregate shape
     * ({@code TimelineCandidate.Track.Clip.effects[]}).
     */
    static RevisionOwnedEffectProjection revisionOwnedProjection() {
        return RevisionOwnedEffectProjection.fromCandidate(
                new TimelineCandidate(
                        "timeline-1", "product-1",
                        TimelineCanonicalProfile.CANONICAL_TIMELINE_FOUNDATION_V1,
                        List.of(new TimelineCandidate.Track(
                                TRACK_ID, TimelineCandidate.TrackType.VIDEO, 0, null,
                                List.of(new TimelineCandidate.Clip(
                                        CLIP_ID, TimelineSourceRef.of("asset-1"),
                                        MediaTime.ofRational(0, 1),
                                        MediaTime.ofRational(0, 1),
                                        MediaTime.ofRational(2, 1),
                                        FrameRate.of(30, 1),
                                        List.of(new TimelineClipEffect(
                                                EFFECT_INSTANCE_ID, "def-blur", Map.of())),
                                        List.of(), null, null)))),
                        List.of(), List.of(), List.of(),
                        com.example.platform.audio.domain.mix.AudioMix.empty(), List.of()));
    }

    /**
     * R6-A: revision-owned projection containing the canonical fixture effect
     * (eff-1) AND a fade effect (eff-fade) on the same clip — for tests that
     * vary the effect category / introduce additional authored effects.
     */
    static RevisionOwnedEffectProjection projectionWithFade() {
        return RevisionOwnedEffectProjection.fromCandidate(
                new TimelineCandidate(
                        "timeline-1", "product-1",
                        TimelineCanonicalProfile.CANONICAL_TIMELINE_FOUNDATION_V1,
                        List.of(new TimelineCandidate.Track(
                                TRACK_ID, TimelineCandidate.TrackType.VIDEO, 0, null,
                                List.of(new TimelineCandidate.Clip(
                                        CLIP_ID, TimelineSourceRef.of("asset-1"),
                                        MediaTime.ofRational(0, 1),
                                        MediaTime.ofRational(0, 1),
                                        MediaTime.ofRational(2, 1),
                                        FrameRate.of(30, 1),
                                        List.of(new TimelineClipEffect(
                                                        EFFECT_INSTANCE_ID, "def-blur", Map.of()),
                                                new TimelineClipEffect(
                                                        "eff-fade", "def-fade", Map.of())),
                                        List.of(), null, null)))),
                        List.of(), List.of(), List.of(),
                        com.example.platform.audio.domain.mix.AudioMix.empty(), List.of()));
    }

    /** R5-A: authoritative TimelineRevision with a custom revision id. */
    static TimelineRevision timelineRevisionWithId(String revisionId) {
        TimelineDocument document = canonicalDocument();
        TimelineContentDigester digester = new TimelineContentDigester();
        String digest = digester.digest(document);
        return new TimelineRevision(
                revisionId, "product-1", null, TimelineDocument.CURRENT_SCHEMA_VERSION,
                document, digest, java.time.Instant.EPOCH, "test");
    }

    /**
     * R6-A/N9: revision-owned projection containing TWO authored effects
     * (eff-1 and eff-2) on the same clip — for node-identity locality tests.
     */
    static RevisionOwnedEffectProjection projectionWithSecondEffect() {
        return RevisionOwnedEffectProjection.fromCandidate(
                new TimelineCandidate(
                        "timeline-1", "product-1",
                        TimelineCanonicalProfile.CANONICAL_TIMELINE_FOUNDATION_V1,
                        List.of(new TimelineCandidate.Track(
                                TRACK_ID, TimelineCandidate.TrackType.VIDEO, 0, null,
                                List.of(new TimelineCandidate.Clip(
                                        CLIP_ID, TimelineSourceRef.of("asset-1"),
                                        MediaTime.ofRational(0, 1),
                                        MediaTime.ofRational(0, 1),
                                        MediaTime.ofRational(2, 1),
                                        FrameRate.of(30, 1),
                                        List.of(new TimelineClipEffect(
                                                        EFFECT_INSTANCE_ID, "def-blur", Map.of()),
                                                new TimelineClipEffect(
                                                        "eff-2", "def-blur", Map.of())),
                                        List.of(), null, null)))),
                        List.of(), List.of(), List.of(),
                        com.example.platform.audio.domain.mix.AudioMix.empty(), List.of()));
    }

    /**
     * R6-A/N6: authoritative TimelineRevision whose canonical timeline carries
     * TWO clips (canonical c1 and the given second clip id) so effect target
     * variation can be observed within one plan. Digest recomputed.
     */
    static TimelineRevision timelineRevisionWithTwoClips(String secondClipId) {
        TimelineDocument document = new TimelineDocument(
                TimelineDocument.CURRENT_SCHEMA_VERSION,
                List.of(new TimelineTrack(TRACK_ID, "v1",
                        TrackType.VIDEO,
                        List.of(canonicalTimelineClip(),
                                new TimelineClip(
                                        secondClipId, "asset-1", "stream-1",
                                        ARTIFACT_ID, ARTIFACT_DIGEST_HEX,
                                        MediaTime.ofRational(0, 1), MediaTime.ofRational(2, 1),
                                        MediaTime.ofRational(0, 1), MediaTime.ofRational(2, 1),
                                        "MEDIA_STREAM",
                                        ConstantRateTemporalMapping.of(1, 1, PlaybackDirection.FORWARD))))),
                TimelineMetadata.empty(),
                audioMix(),
                List.of(),
                List.of(textElement()));
        TimelineContentDigester digester = new TimelineContentDigester();
        String digest = digester.digest(document);
        return new TimelineRevision(
                REVISION_ID, "product-1", null, TimelineDocument.CURRENT_SCHEMA_VERSION,
                document, digest, java.time.Instant.EPOCH, "test");
    }

    /**
     * R6-A/N6: revision-owned projection with eff-1 owned by c1 AND eff-2
     * owned by the given second clip — lets the planner observe effect target
     * variation within one plan (both memberships authentic).
     */
    static RevisionOwnedEffectProjection projectionWithClips(String secondClipId) {
        return RevisionOwnedEffectProjection.fromCandidate(
                new TimelineCandidate(
                        "timeline-1", "product-1",
                        TimelineCanonicalProfile.CANONICAL_TIMELINE_FOUNDATION_V1,
                        List.of(new TimelineCandidate.Track(
                                TRACK_ID, TimelineCandidate.TrackType.VIDEO, 0, null,
                                List.of(new TimelineCandidate.Clip(
                                                CLIP_ID, TimelineSourceRef.of("asset-1"),
                                                MediaTime.ofRational(0, 1),
                                                MediaTime.ofRational(0, 1),
                                                MediaTime.ofRational(2, 1),
                                                FrameRate.of(30, 1),
                                                List.of(new TimelineClipEffect(
                                                        EFFECT_INSTANCE_ID, "def-blur", Map.of())),
                                                List.of(), null, null),
                                        new TimelineCandidate.Clip(
                                                secondClipId, TimelineSourceRef.of("asset-1"),
                                                MediaTime.ofRational(0, 1),
                                                MediaTime.ofRational(0, 1),
                                                MediaTime.ofRational(2, 1),
                                                FrameRate.of(30, 1),
                                                List.of(new TimelineClipEffect(
                                                        "eff-2", "def-blur", Map.of())),
                                                List.of(), null, null)))),
                        List.of(), List.of(), List.of(),
                        com.example.platform.audio.domain.mix.AudioMix.empty(), List.of()));
    }

    /**
     * R6-A/T3: revision-owned membership projection with the fixture effect
     * owned by a DIFFERENT clip id (same time range) — proves membership is
     * not temporal equivalence.
     */
    static RevisionOwnedEffectProjection revisionOwnedProjectionWithClipId(String clipId) {
        return RevisionOwnedEffectProjection.fromCandidate(
                new TimelineCandidate(
                        "timeline-1", "product-1",
                        TimelineCanonicalProfile.CANONICAL_TIMELINE_FOUNDATION_V1,
                        List.of(new TimelineCandidate.Track(
                                TRACK_ID, TimelineCandidate.TrackType.VIDEO, 0, null,
                                List.of(new TimelineCandidate.Clip(
                                        clipId, TimelineSourceRef.of("asset-1"),
                                        MediaTime.ofRational(0, 1),
                                        MediaTime.ofRational(0, 1),
                                        MediaTime.ofRational(2, 1),
                                        FrameRate.of(30, 1),
                                        List.of(new TimelineClipEffect(
                                                EFFECT_INSTANCE_ID, "def-blur", Map.of())),
                                        List.of(), null, null)))),
                        List.of(), List.of(), List.of(),
                        com.example.platform.audio.domain.mix.AudioMix.empty(), List.of()));
    }

    /**
     * R6-A/T3: authoritative TimelineRevision whose canonical timeline carries
     * a clip with the given clip id (same time range as the canonical clip).
     * Digest recomputed over the custom document so verification succeeds.
     */
    static TimelineRevision timelineRevisionWithClipId(String clipId) {
        TimelineDocument document = new TimelineDocument(
                TimelineDocument.CURRENT_SCHEMA_VERSION,
                List.of(new TimelineTrack(TRACK_ID, "v1",
                        TrackType.VIDEO,
                        List.of(new TimelineClip(
                                clipId, "asset-1", "stream-1", ARTIFACT_ID, ARTIFACT_DIGEST_HEX,
                                MediaTime.ofRational(0, 1), MediaTime.ofRational(2, 1),
                                MediaTime.ofRational(0, 1), MediaTime.ofRational(2, 1),
                                "MEDIA_STREAM",
                                ConstantRateTemporalMapping.of(1, 1, PlaybackDirection.FORWARD))))),
                TimelineMetadata.empty(),
                audioMix(),
                List.of(),
                List.of(textElement()));
        TimelineContentDigester digester = new TimelineContentDigester();
        String digest = digester.digest(document);
        return new TimelineRevision(
                REVISION_ID, "product-1", null, TimelineDocument.CURRENT_SCHEMA_VERSION,
                document, digest, java.time.Instant.EPOCH, "test");
    }

    /**
     * R3-B1: authored snapshot with a custom timeline clip (exact-time mapping
     * tests): the authoritative document's digest is recomputed over the custom
     * clip, and the effect state is the canonical fixture (verified).
     */
    static VerifiedRenderSemanticSnapshot verifiedAuthoredSnapshotWithClip(
            com.example.platform.timeline.canonical.TimelineClip customClip) {
        TimelineDocument document = new TimelineDocument(
                TimelineDocument.CURRENT_SCHEMA_VERSION,
                List.of(new TimelineTrack(TRACK_ID, "v1",
                        TrackType.VIDEO, List.of(customClip))),
                TimelineMetadata.empty(),
                audioMix(),
                List.of(),
                List.of(textElement()));
        TimelineContentDigester digester = new TimelineContentDigester();
        String digest = digester.digest(document);
        TimelineRevision revision = new TimelineRevision(
                REVISION_ID, "product-1", null, TimelineDocument.CURRENT_SCHEMA_VERSION,
                document, digest, java.time.Instant.EPOCH, "test");
        EffectSemanticSnapshot snap = effectSnapshot(
                List.of(gaussianBlurEffect()), List.of(effectDefinition()));
        return VerifiedRenderSemanticSnapshotFactory.verified(
                revision, digester, snap, snap.reference());
    }

    /** TimelineClip with a REVERSE constant-rate mapping. */
    static com.example.platform.timeline.canonical.TimelineClip reverseTimelineClip() {
        return new TimelineClip(
                CLIP_ID, "asset-1", "stream-1", ARTIFACT_ID, ARTIFACT_DIGEST_HEX,
                MediaTime.ofRational(0, 1), MediaTime.ofRational(2, 1),
                MediaTime.ofRational(0, 1), MediaTime.ofRational(2, 1),
                "MEDIA_STREAM",
                ConstantRateTemporalMapping.of(1, 1, PlaybackDirection.REVERSE));
    }

    /** TimelineClip with a FREEZE mapping. */
    static com.example.platform.timeline.canonical.TimelineClip freezeTimelineClip() {
        return new TimelineClip(
                CLIP_ID, "asset-1", "stream-1", ARTIFACT_ID, ARTIFACT_DIGEST_HEX,
                MediaTime.ofRational(0, 1), MediaTime.ofRational(2, 1),
                MediaTime.ofRational(0, 1), MediaTime.ofRational(2, 1),
                "MEDIA_STREAM",
                new FreezeTemporalMapping(MediaTime.ofRational(1, 1)));
    }

    /** Verified revision with a custom TextElement (B2 fingerprint tests). */
    static VerifiedTimelineRevision verifiedRevisionWithText(TextElement customText) {
        TimelineDocument document = new TimelineDocument(
                TimelineDocument.CURRENT_SCHEMA_VERSION,
                List.of(new TimelineTrack(TRACK_ID, "v1",
                        TrackType.VIDEO, List.of(canonicalTimelineClip()))),
                TimelineMetadata.empty(),
                audioMix(),
                List.of(),
                List.of(customText));
        TimelineContentDigester digester = new TimelineContentDigester();
        String digest = digester.digest(document);
        TimelineRevision revision = new TimelineRevision(
                REVISION_ID, "product-1", null, TimelineDocument.CURRENT_SCHEMA_VERSION,
                document, digest, java.time.Instant.EPOCH, "test");
        return VerifiedTimelineRevisionFactory.verified(revision, digester);
    }

    /** Verified revision with a custom AudioMix (F1 audio fingerprint tests). */
    static VerifiedTimelineRevision verifiedRevisionWithAudioMix(AudioMix customMix) {
        TimelineDocument document = new TimelineDocument(
                TimelineDocument.CURRENT_SCHEMA_VERSION,
                List.of(new TimelineTrack(TRACK_ID, "v1",
                        TrackType.VIDEO, List.of(canonicalTimelineClip()))),
                TimelineMetadata.empty(),
                customMix,
                List.of(),
                List.of(textElement()));
        TimelineContentDigester digester = new TimelineContentDigester();
        String digest = digester.digest(document);
        TimelineRevision revision = new TimelineRevision(
                REVISION_ID, "product-1", null, TimelineDocument.CURRENT_SCHEMA_VERSION,
                document, digest, java.time.Instant.EPOCH, "test");
        return VerifiedTimelineRevisionFactory.verified(revision, digester);
    }

    /** Canonical TimelineDocument backing the verified projection (R2 B1). */
    static TimelineDocument canonicalDocument() {
        return new TimelineDocument(
                TimelineDocument.CURRENT_SCHEMA_VERSION,
                List.of(new TimelineTrack(TRACK_ID, "v1",
                        TrackType.VIDEO, List.of(canonicalTimelineClip()))),
                TimelineMetadata.empty(),
                audioMix(),
                List.of(),
                List.of(textElement()));
    }

    static TimelineClip canonicalTimelineClip() {
        return new TimelineClip(
                CLIP_ID,
                "asset-1",
                "stream-1",
                ARTIFACT_ID,
                ARTIFACT_DIGEST_HEX,
                MediaTime.ofRational(0, 1),
                MediaTime.ofRational(2, 1),
                MediaTime.ofRational(0, 1),
                MediaTime.ofRational(2, 1),
                "MEDIA_STREAM",
                ConstantRateTemporalMapping.of(1, 1, PlaybackDirection.FORWARD));
    }

    static TimelineRevisionReference revisionRef() {
        return new TimelineRevisionReference(REVISION_ID, ContentDigest.sha256(REVISION_DIGEST_HEX));
    }

    static ArtifactId artifactId() {
        return new ArtifactId(ARTIFACT_ID);
    }

    static ContentDigest artifactDigest() {
        return ContentDigest.sha256(ARTIFACT_DIGEST_HEX);
    }

    /** Canonical digest of a document variant (for multi-revision fixtures). */
    static String canonicalDigest(String salt) {
        // Build a document with a distinct text element id so the digest differs.
        TimelineDocument doc = new TimelineDocument(
                TimelineDocument.CURRENT_SCHEMA_VERSION,
                List.of(new TimelineTrack(TRACK_ID, "v1",
                        TrackType.VIDEO, List.of(canonicalTimelineClip()))),
                TimelineMetadata.empty(),
                audioMix(),
                List.of(),
                List.of(textElementWithContent(salt)));
        return new TimelineContentDigester().digest(doc);
    }

    static MediaClip mediaClip() {
        MediaTime start = MediaTime.ofRational(0, 1);
        MediaTime end = MediaTime.ofRational(2, 1);
        MediaClip.TimeRange timelineRange = new MediaClip.TimeRange(start, end);
        MediaClip.TimeRange sourceRange = new MediaClip.TimeRange(start, end);
        ConstantRateTemporalMapping mapping = ConstantRateTemporalMapping.of(1, 1, PlaybackDirection.FORWARD);
        MediaStreamSourceBinding binding = new MediaStreamSourceBinding(
                MediaAssetId.of("asset-1"),
                MediaStreamId.of("stream-1"),
                artifactId(),
                artifactDigest(),
                sourceRange);
        return new MediaClip(CLIP_ID, TRACK_ID, timelineRange, sourceRange, mapping, binding);
    }

    static EffectInstance gaussianBlurEffect() {
        return new EffectInstance(
                EFFECT_INSTANCE_ID,
                "def-blur",
                "1",
                EffectInstance.EffectMediaType.VIDEO,
                true,
                new MediaClip.TimeRange(MediaTime.ofRational(0, 1), MediaTime.ofRational(2, 1)),
                // authored effect parameters only (C11); the category is NOT a
                // parameter — it is resolved from the EffectDefinition catalog.
                Map.of("radiusPixels", "4"),
                Map.of(),
                new ClipEffectTarget(TRACK_ID, CLIP_ID),
                new EffectInstance.EffectProvenance("test", "t", 0L));
    }

    /** Canonical GAUSSIAN_BLUR effect definition (C11 category authority). */
    static EffectInstance.EffectDefinition effectDefinition() {
        return new EffectInstance.EffectDefinition(
                "def-blur",
                "1",
                EffectInstance.EffectCategory.GAUSSIAN_BLUR,
                List.of(EffectInstance.EffectMediaType.VIDEO),
                Map.of(),
                EffectInstance.EffectTemporalBehavior.PRESERVE_DURATION,
                List.of(),
                List.of(),
                List.of());
    }

    /**
     * R6-B: canonical effect definition with an ADDITIONAL definition-required
     * capability (lowered to a typed platform CapabilityRequirement).
     */
    static EffectInstance.EffectDefinition effectDefinitionWithRequiredCapability(String capability) {
        return new EffectInstance.EffectDefinition(
                "def-blur",
                "1",
                EffectInstance.EffectCategory.GAUSSIAN_BLUR,
                List.of(EffectInstance.EffectMediaType.VIDEO),
                Map.of(),
                EffectInstance.EffectTemporalBehavior.PRESERVE_DURATION,
                List.of(),
                List.of(capability),
                List.of());
    }

    static AudioMix audioMix() {
        return audioMixWithGain(0.8);
    }

    /** Audio mix with the given route gain (for fingerprint-change tests). */
    static AudioMix audioMixWithGain(double gain) {
        return new AudioMix(
                com.example.platform.audio.domain.mix.AudioMasterBus.master(),
                List.of(new AudioRoute(
                        AudioMixInput.of(TRACK_ID, CLIP_ID),
                        AudioGain.of(gain),
                        AudioMute.of(false),
                        StereoBalance.of(0.0),
                        List.of())));
    }

    static TextElement textElement() {
        return textElementWithContent("Hello");
    }

    /** TextElement with the given text content (for fingerprint-change tests). */
    static TextElement textElementWithContent(String contentText) {
        TextContent content = new TextContent(contentText);
        FontContentDigest digest = FontContentDigest.ofText("inter-v1");
        ValidatedFontExecutionReference ref = new ValidatedFontExecutionReference(
                digest, digest, FontSecurityState.VALIDATED_EXECUTION_FONT,
                FontFormat.TRUETYPE, new FaceIndex(0));
        ResolvedFontInstance font = new ResolvedFontInstance(ref, List.of());
        StyledText styled = new StyledText(
                content,
                List.of(new TextSemanticRun(TextRange.of(0, content.scalarCount()),
                        null, ScriptTag.LATIN, RangeDirectionOverride.NONE)),
                List.of(new com.example.platform.fonttext.typography.TextStyleRun(
                        TextRange.of(0, content.scalarCount()), sampleStyle())),
                new ParagraphStyle(ParagraphStyle.Alignment.START, ParagraphStyle.Justification.NONE,
                        LineHeight.ratio(FontRational.of(12, 10)),
                        ParagraphStyle.WrapPolicy.WRAP, ParagraphBaseDirection.AUTO,
                        ParagraphStyle.LineBreakPolicy.STANDARD));
        return new TextElement(
                new TextElementId(TEXT_ELEMENT_ID),
                FontRational.whole(0),
                FontRational.whole(5),
                styled,
                new TextFrame(FontRational.of(640, 1), null,
                        TextFrame.HorizontalAlignment.START, TextFrame.VerticalAlignment.TOP,
                        ParagraphStyle.WrapPolicy.WRAP, TextFrame.OverflowBehavior.CLIP),
                new FontFallbackPolicy(List.of(new FontFamilyName("Arial")), List.of(), List.of(), List.of()),
                List.of(new ResolvedFontRun(TextRange.of(0, content.scalarCount()), font)));
    }

    private static com.example.platform.fonttext.typography.TextStyle sampleStyle() {
        return new com.example.platform.fonttext.typography.TextStyle(
                new FontSelectionIntent(List.of(new FontFamilyName("Inter")),
                        FontSelectionIntent.WeightIntent.NORMAL, FontSelectionIntent.StretchIntent.NORMAL,
                        FontSelectionIntent.SlantIntent.NORMAL, OpticalSizingIntent.disabled(), List.of()),
                new FontSize(FontRational.of(24, 1)),
                FontRational.of(0, 1), OpenTypeFeatureIntent.empty());
    }

    static RenderRequest renderRequest() {
        return new RenderRequest(
                new RenderRequestId(REQUEST_ID),
                new RenderExtent(MediaTime.ofRational(0, 1), MediaTime.ofRational(2, 1), FrameRate.of(30, 1)),
                List.of(RenderOutputRequirement.of(RenderOutputRole.RENDER_MASTER)));
    }

    /** Capability context that supports every capability used by the canonical fixture (F3). */
    static CapabilityContext fullCapabilityContext() {
        return new CapabilityContext(Set.of(
                CapabilityId.of("video.decode"),
                CapabilityId.of("video.effect.gaussian-blur"),
                CapabilityId.of("audio.process"),
                CapabilityId.of("audio.mix"),
                CapabilityId.of("subtitle.rasterize"),
                CapabilityId.of("render.composite"),
                CapabilityId.of("render.output")));
    }
}
