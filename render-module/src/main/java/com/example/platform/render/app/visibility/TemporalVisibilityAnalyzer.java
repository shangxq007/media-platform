package com.example.platform.render.app.visibility;

import com.example.platform.render.domain.renderplan.RenderExecutionCoverage;
import com.example.platform.render.domain.renderplan.RenderSampleWindow;
import com.example.platform.shared.time.FrameRate;
import com.example.platform.shared.time.MediaTime;
import com.example.platform.timeline.canonical.TimelineClip;
import com.example.platform.timeline.canonical.TimelineDocument;
import com.example.platform.timeline.canonical.TimelineTrack;
import com.example.platform.timeline.canonical.TrackType;
import com.example.platform.timeline.semantics.temporal.ConstantRateTemporalMapping;
import com.example.platform.timeline.semantics.temporal.PlaybackDirection;
import com.example.platform.timeline.semantics.temporal.TemporalMapping;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Provider-independent, read-only temporal visibility analysis.
 *
 * <p>Uses the frozen #20 types: {@link RenderExecutionCoverage} for timeline
 * coordinates and {@link RenderSampleWindow} for source coordinates. It does not
 * create a RenderPlan, mutate a PhysicalExecutionPlan, call Admission/Grant/Lease,
 * or change a Canonical Revision.</p>
 */
public final class TemporalVisibilityAnalyzer {

    public TemporalVisibilityAnalysis analyze(
            TimelineDocument document, VisibilityRequest request) {
        Objects.requireNonNull(document, "document");
        Objects.requireNonNull(request, "request");
        RenderExecutionCoverage requested = coverage(
                request.start(), request.end(), request.frameRate());
        Set<String> dependentClipIds = new HashSet<>();
        document.getTransitions().forEach(t -> {
            dependentClipIds.add(t.outgoingClipId());
            dependentClipIds.add(t.incomingClipId());
        });
        document.getAutomations().forEach(a -> dependentClipIds.add(a.targetEntityId()));

        List<TrackVisibility> tracks = new ArrayList<>();
        List<String> globalReasons = new ArrayList<>();
        int index = 0;
        for (TimelineTrack track : document.getTracks()) {
            tracks.add(analyzeTrack(track, index++, requested, dependentClipIds, globalReasons));
        }
        globalReasons.add("CROSS_TRACK_PRUNING_DISABLED: canonical z/alpha/ROI proof is absent");
        globalReasons.add("R2A_ANALYSIS_ONLY: no RenderPlan/PhysicalExecutionPlan mutation");
        TemporalVisibilityAnalysis withoutDigest = new TemporalVisibilityAnalysis(
                request.tenantId(), request.projectId(), request.revisionId(),
                request.renderRequest().id().value(), request.start(), request.end(),
                request.frameRate(), false, tracks, List.of(), globalReasons, "");
        return new TemporalVisibilityAnalysis(
                withoutDigest.tenantId(), withoutDigest.projectId(), withoutDigest.revisionId(),
                withoutDigest.requestId(), withoutDigest.requestStart(), withoutDigest.requestEnd(),
                withoutDigest.frameRate(), false, tracks, List.of(), globalReasons,
                digest(withoutDigest));
    }

    private TrackVisibility analyzeTrack(
            TimelineTrack track, int index, RenderExecutionCoverage requested,
            Set<String> dependentClipIds, List<String> globalReasons) {
        List<ClipVisibility> clips = new ArrayList<>();
        List<RenderExecutionCoverage> gaps = new ArrayList<>();
        MediaTime previousEnd = null;
        boolean unsupportedTrack = track.type() == TrackType.TEXT || track.type() == TrackType.EFFECT;
        if (unsupportedTrack) {
            globalReasons.add("UNSUPPORTED_TRACK_TYPE: " + track.trackId() + " " + track.type());
        }
        for (TimelineClip clip : track.clips()) {
            RenderExecutionCoverage timeline = coverage(
                    clip.getStartTime(), clip.getEndTime(), requested.frameRate());
            if (previousEnd != null && timeline.start().isLessThan(previousEnd)) {
                throw new IllegalArgumentException("same-track overlap is not analyzable: "
                        + track.trackId() + " " + clip.getClipId().value());
            }
            MediaTime gapStart = previousEnd == null || previousEnd.isLessThan(requested.start())
                    ? requested.start() : previousEnd;
            if (gapStart.isLessThan(timeline.start())) {
                Optional<RenderExecutionCoverage> gap = intersection(
                        coverage(gapStart, timeline.start(), requested.frameRate()), requested);
                gap.ifPresent(gaps::add);
            }
            clips.add(analyzeClip(track, clip, timeline, requested, dependentClipIds, unsupportedTrack));
            previousEnd = timeline.end();
        }
        MediaTime finalGapStart = previousEnd == null || previousEnd.isLessThan(requested.start())
                ? requested.start() : previousEnd;
        if (finalGapStart.isLessThan(requested.end())) {
            Optional<RenderExecutionCoverage> gap = intersection(
                    coverage(finalGapStart, requested.end(), requested.frameRate()), requested);
            gap.ifPresent(gaps::add);
        }
        return new TrackVisibility(track.trackId(), track.type(), index, false,
                List.copyOf(gaps), clips);
    }

    private ClipVisibility analyzeClip(
            TimelineTrack track, TimelineClip clip, RenderExecutionCoverage timeline,
            RenderExecutionCoverage requested, Set<String> dependentClipIds,
            boolean unsupportedTrack) {
        RenderSampleWindow source = window(
                clip.getTrimStart(), clip.getTrimEnd(), requested.frameRate());
        Optional<RenderExecutionCoverage> coverage = intersection(timeline, requested);
        boolean outside = coverage.isEmpty();
        List<String> reasons = new ArrayList<>();
        Optional<RenderSampleWindow> sourceWindow = Optional.empty();
        List<FrameSample> frameSamples = new ArrayList<>();
        VisibilityContribution contribution = VisibilityContribution.NONE;
        VisibilityDecision decision = outside ? VisibilityDecision.OUTSIDE_REQUEST : VisibilityDecision.CONTRIBUTES;

        if (unsupportedTrack) {
            decision = VisibilityDecision.KEEP_UNKNOWN;
            contribution = VisibilityContribution.UNKNOWN;
            reasons.add("TRACK_TYPE_NOT_TEMPORALLY_ANALYZABLE");
        } else if (dependentClipIds.contains(clip.getClipId().value())) {
            decision = VisibilityDecision.KEEP_UNKNOWN;
            contribution = VisibilityContribution.UNKNOWN;
            reasons.add("TRANSITION_OR_AUTOMATION_DEPENDENCY");
        } else if (!"MEDIA_STREAM".equals(clip.getSourceKind())) {
            decision = VisibilityDecision.KEEP_UNKNOWN;
            contribution = VisibilityContribution.UNKNOWN;
            reasons.add("NON_MEDIA_STREAM_SOURCE");
        } else if (clip.getEffects() != null && !clip.getEffects().isEmpty()) {
            decision = VisibilityDecision.KEEP_UNKNOWN;
            contribution = VisibilityContribution.UNKNOWN;
            reasons.add("CLIP_EFFECT_DEPENDENCY_UNKNOWN");
        } else if (!isIdentityTemporalMapping(clip.getTemporalMapping())) {
            decision = VisibilityDecision.KEEP_UNKNOWN;
            contribution = VisibilityContribution.UNKNOWN;
            reasons.add("NON_IDENTITY_TEMPORAL_MAPPING");
        }

        if (!outside && decision == VisibilityDecision.CONTRIBUTES) {
            RenderExecutionCoverage visible = coverage.orElseThrow();
            MediaTime offset = visible.start().subtract(timeline.start());
            MediaTime sourceStart = source.start().add(offset);
            MediaTime sourceEnd = sourceStart.add(visible.end().subtract(visible.start()));
            if (sourceStart.isLessThan(source.start()) || sourceEnd.isGreaterThan(source.end())) {
                decision = VisibilityDecision.KEEP_UNKNOWN;
                contribution = VisibilityContribution.UNKNOWN;
                reasons.add("SOURCE_WINDOW_OUT_OF_RANGE");
            } else {
                sourceWindow = Optional.of(window(sourceStart, sourceEnd, requested.frameRate()));
                frameSamples = frameSamples(visible, sourceStart, requested, requested.frameRate());
                contribution = visible.start().isEqualTo(timeline.start())
                        && visible.end().isEqualTo(timeline.end())
                        ? VisibilityContribution.FULL : VisibilityContribution.PARTIAL;
                reasons.add(contribution == VisibilityContribution.FULL
                        ? "FULLY_INSIDE_REQUEST" : "PARTIALLY_INSIDE_REQUEST");
            }
        } else if (outside) {
            reasons.add("OUTSIDE_REQUEST_NOT_DELETION");
        }
        if (decision == VisibilityDecision.KEEP_UNKNOWN) {
            reasons.add("UNKNOWN_KEEP");
            coverage = Optional.empty();
            sourceWindow = Optional.empty();
            frameSamples = List.of();
        }
        DecodeDependency decode = new DecodeDependency(source, true,
                "full materialized artifact/GOP extent not proven; source retained");
        return new ClipVisibility(track.trackId(), clip.getClipId().value(),
                clip.getArtifactId(), clip.getContentDigest(), track.type(),
                timeline, source, coverage, sourceWindow, frameSamples,
                contribution, decision, decode, reasons);
    }

    private List<FrameSample> frameSamples(
            RenderExecutionCoverage coverage, MediaTime coverageSourceStart,
            RenderExecutionCoverage request, FrameRate frameRate) {
        List<FrameSample> samples = new ArrayList<>();
        MediaTime frameDuration = MediaTime.ofRational(
                frameRate.denominator(), frameRate.numerator().longValueExact());
        MediaTime time = request.start();
        int index = 0;
        while (time.isLessThan(request.end()) && index < 1_000_000) {
            if (!time.isLessThan(coverage.start()) && time.isLessThan(coverage.end())) {
                samples.add(new FrameSample(index, time,
                        coverageSourceStart.add(time.subtract(coverage.start()))));
            }
            time = time.add(frameDuration);
            index++;
        }
        return List.copyOf(samples);
    }

    private static Optional<RenderExecutionCoverage> intersection(
            RenderExecutionCoverage value, RenderExecutionCoverage requested) {
        MediaTime start = value.start().isGreaterThan(requested.start())
                ? value.start() : requested.start();
        MediaTime end = value.end().isLessThan(requested.end())
                ? value.end() : requested.end();
        return start.isLessThan(end)
                ? Optional.of(coverage(start, end, requested.frameRate()))
                : Optional.empty();
    }

    private static RenderExecutionCoverage coverage(MediaTime start, MediaTime end, FrameRate rate) {
        if (!start.isLessThan(end)) {
            throw new IllegalArgumentException("timeline coverage must be non-empty");
        }
        return new RenderExecutionCoverage(start, end, rate);
    }

    private static RenderSampleWindow window(MediaTime start, MediaTime end, FrameRate rate) {
        return new RenderSampleWindow(start, end, rate);
    }

    private static boolean isIdentityTemporalMapping(TemporalMapping mapping) {
        return mapping instanceof ConstantRateTemporalMapping rate
                && rate.direction() == PlaybackDirection.FORWARD
                && rate.rate().numerator() == 1
                && rate.rate().denominator() == 1;
    }

    private static String digest(TemporalVisibilityAnalysis analysis) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(analysis.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException unavailable) {
            throw new IllegalStateException("SHA-256 unavailable", unavailable);
        }
    }
}
