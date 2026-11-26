package com.example.platform.render.domain.renderplan;

import com.example.platform.fonttext.resolution.FontFallbackPolicy;
import com.example.platform.fonttext.resolution.ResolvedFontRun;
import com.example.platform.fonttext.text.StyledText;
import com.example.platform.fonttext.typography.FontRational;
import com.example.platform.fonttext.typography.TextFrame;
import com.example.platform.timeline.canonical.TextElement;
import com.example.platform.timeline.canonical.SubtitleCueMotion;
import com.example.platform.fonttext.typography.SubtitleBlockLayout;
import com.example.platform.shared.time.MediaTime;
import com.example.platform.timeline.canonical.TextElementId;
import java.util.List;
import java.util.Objects;

/**
 * ROADMAP20 correction R2 B2: typed logical materialization requirement for a
 * TIMED_TEXT node carrying the COMPLETE authored text rasterization WHAT.
 *
 * <p>Derived MATERIALIZED PROJECTION of the authoritative {@link TextElement}
 * state — the render type does NOT redefine font/text semantics. It preserves
 * the full authored {@link StyledText} (text content, semantic runs, style
 * runs, paragraph style) plus layout, fallback policy, resolved font runs and
 * timing, so a future physical planner can answer WHAT is to be rasterized
 * without re-reading {@code TextElement}:
 * <ul>
 *   <li>what text: {@link #styledText()}.content(),</li>
 *   <li>with what semantics/style: semanticRuns/styleRuns/paragraphStyle,</li>
 *   <li>when: {@link #start()}, {@link #duration()},</li>
 *   <li>into what layout: {@link #frame()},</li>
 *   <li>with what resolved typography: {@link #resolvedFontRuns()},
 *       {@link #fallbackPolicy()} (consumed, never recomputed — Roadmap #19
 *       authority preserved).</li>
 * </ul>
 *
 * <p>No provider-specific raster command or executable renderer; logical
 * materialization only.
 */
public record TimedTextMaterializationRequirement(
        TextElementId id,
        FontRational start,
        FontRational duration,
        StyledText styledText,
        TextFrame frame,
        FontFallbackPolicy fallbackPolicy,
        List<ResolvedFontRun> resolvedFontRuns,
        SubtitleCueMotion subtitleCue) implements RenderMaterializationRequirement {

    public TimedTextMaterializationRequirement(TextElementId id, FontRational start, FontRational duration,
            StyledText styledText, TextFrame frame, FontFallbackPolicy fallbackPolicy, List<ResolvedFontRun> resolvedFontRuns) {
        this(id, start, duration, styledText, frame, fallbackPolicy, resolvedFontRuns, null);
    }

    public TimedTextMaterializationRequirement {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(duration, "duration");
        Objects.requireNonNull(styledText, "styledText");
        Objects.requireNonNull(frame, "frame");
        Objects.requireNonNull(fallbackPolicy, "fallbackPolicy");
        resolvedFontRuns = resolvedFontRuns != null ? List.copyOf(resolvedFontRuns) : List.of();
    }

    /**
     * Builds the typed projection from an authoritative {@link TextElement}.
     * This is the ONLY supported construction path in the materializer — the
     * render layer never invents text/font semantics.
     */
    public static TimedTextMaterializationRequirement from(TextElement element) {
        return new TimedTextMaterializationRequirement(
                element.id(),
                element.start(),
                element.duration(),
                element.styledText(),
                element.frame(),
                element.fallbackPolicy(),
                element.resolvedFontRuns(), element.subtitleCue());
    }

    public boolean editableCue() {
        return subtitleCue != null;
    }

    /** Provider-facing bounded raster recipe and placement. The covered time is relative to the render extent. */
    public record CueRaster(long firstFrame, long endFrame, MediaTime start, MediaTime elapsed, String samples,
                            String text, int width, int fontSize) {
        public CueRaster {
            SubtitleBlockLayout.validateShape(text, width, fontSize);
        }
    }

    public java.util.Optional<CueRaster> cueRaster(RenderExtent extent) {
        if (!editableCue()) throw new IllegalArgumentException("Only a pinned subtitle profile can prepare a block raster");
        var first = ExactTextTimelineTimeProjection.project(start);
        var last = ExactTextTimelineTimeProjection.projectEnd(start, duration);
        if (!(first instanceof ExactTextTimelineTimeProjection.Projected begin)
                || !(last instanceof ExactTextTimelineTimeProjection.Projected finish)) {
            throw new IllegalArgumentException("Subtitle timing cannot be projected exactly");
        }
        if (!begin.mediaTime().isLessThan(extent.end()) || !finish.mediaTime().isGreaterThan(extent.start())) {
            return java.util.Optional.empty();
        }
        var visibleStart = begin.mediaTime().isGreaterThan(extent.start()) ? begin.mediaTime() : extent.start();
        var visibleEnd = finish.mediaTime().isLessThan(extent.end()) ? finish.mediaTime() : extent.end();
        var relativeStart = visibleStart.subtract(extent.start());
        var relativeEnd = visibleEnd.subtract(extent.start());
        long firstFrame = firstFrameAtOrAfter(relativeStart, extent.frameRate());
        long endFrame = firstFrameAtOrAfter(relativeEnd, extent.frameRate());
        if (firstFrame == endFrame) return java.util.Optional.empty();
        subtitleCue.requireInside(finish.mediaTime().subtract(begin.mediaTime()));
        var width = frame.widthConstraint();
        var size = styledText.styleRuns().getFirst().style().fontSize().value();
        if (!width.denominator().equals(java.math.BigInteger.ONE) || !size.denominator().equals(java.math.BigInteger.ONE)) {
            throw new IllegalArgumentException("Subtitle design width and size must be integral");
        }
        return java.util.Optional.of(new CueRaster(firstFrame, endFrame, relativeStart,
                visibleStart.subtract(begin.mediaTime()), cueSamples(), styledText.content().value(),
                width.numerator().intValueExact(), size.numerator().intValueExact()));
    }

    static long firstFrameAtOrAfter(MediaTime time, com.example.platform.shared.time.FrameRate rate) {
        var n = java.math.BigInteger.valueOf(time.ticks()).multiply(rate.numerator());
        var d = java.math.BigInteger.valueOf(time.timeScale()).multiply(java.math.BigInteger.valueOf(rate.denominator()));
        return n.add(d).subtract(java.math.BigInteger.ONE).divide(d).longValueExact();
    }

    public String cueSamples() {
        if (subtitleCue.keys().isEmpty()) return "0;" + SubtitleCueMotion.STATIC_X + ";" + SubtitleCueMotion.STATIC_Y + ";1000000";
        return subtitleCue.keys().stream().map(k -> k.time() + ";" + k.xMicros() + ";" + k.yMicros() + ";" + k.opacityMicros())
                .collect(java.util.stream.Collectors.joining("|"));
    }

    @Override
    public String variantKey() {
        return "TIMED_TEXT";
    }

    /** Exact coverage and a concrete style are required to rasterize a text stream. */
    public boolean hasStyledContentWithin(RenderExtent extent) {
        var first = ExactTextTimelineTimeProjection.project(start);
        var last = ExactTextTimelineTimeProjection.projectEnd(start, duration);
        return first instanceof ExactTextTimelineTimeProjection.Projected begin
                && last instanceof ExactTextTimelineTimeProjection.Projected end
                && !begin.mediaTime().isLessThan(extent.start()) && !end.mediaTime().isGreaterThan(extent.end())
                && !styledText.content().value().isEmpty() && !styledText.styleRuns().isEmpty();
    }
}
