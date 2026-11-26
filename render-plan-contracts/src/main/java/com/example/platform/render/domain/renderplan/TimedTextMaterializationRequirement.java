package com.example.platform.render.domain.renderplan;

import com.example.platform.fonttext.resolution.FontFallbackPolicy;
import com.example.platform.fonttext.resolution.ResolvedFontRun;
import com.example.platform.fonttext.text.StyledText;
import com.example.platform.fonttext.typography.FontRational;
import com.example.platform.fonttext.typography.TextFrame;
import com.example.platform.timeline.canonical.TextElement;
import com.example.platform.timeline.canonical.SubtitleCueMotion;
import com.example.platform.fonttext.typography.SubtitleFontRun;
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

    /** Render-owned raster request. The worker never receives the canonical text/style model. */
    public record BlockRasterRecipe(String text, int width, int fontSize, String fontProfile, List<SubtitleFontRun> fontRuns) {
        public BlockRasterRecipe(String text, int width, int fontSize, String fontProfile) {
            this(text, width, fontSize, fontProfile, List.of());
        }

        public BlockRasterRecipe {
            SubtitleBlockLayout.validateShape(text, width, fontSize);
            fontProfile = SubtitleBlockLayout.resolveProfile(fontProfile);
            fontRuns = List.copyOf(fontRuns);
            SubtitleFontRun.validate(text, fontRuns);
        }

        public String encodedRunsSuffix() {
            if (fontRuns.isEmpty()) return "";
            return "," + fontRuns.stream().map(SubtitleFontRun::canonical).collect(java.util.stream.Collectors.joining("|"));
        }

        public static BlockRasterRecipe fromEncodedRuns(String text, int width, int size, String profile, String encoded) {
            if (encoded == null) return new BlockRasterRecipe(text, width, size, profile);
            if (encoded.length() > 3200) throw new IllegalArgumentException("Oversized inline subtitle recipe");
            var runs = new java.util.ArrayList<SubtitleFontRun>();
            for (String entry : encoded.split("\\|", -1)) {
                if (!entry.matches("[0-9]{1,3}:[0-9]{1,3}:[a-z0-9-]{1,40}")) {
                    throw new IllegalArgumentException("Malformed inline subtitle recipe");
                }
                var fields = entry.split(":");
                runs.add(new SubtitleFontRun(Integer.parseInt(fields[0]), Integer.parseInt(fields[1]), fields[2]));
            }
            return new BlockRasterRecipe(text, width, size, profile, runs);
        }

        public byte[] png(int destinationWidth) {
            return SubtitleBlockLayout.png(text, width, fontSize, fontProfile, destinationWidth, fontRuns);
        }

        public byte[] png(int destinationWidth, RasterRange selection, boolean selected) {
            var range = selection == null ? null
                    : new com.example.platform.fonttext.typography.SubtitleTextRange(selection.start(), selection.end());
            return SubtitleBlockLayout.png(text, width, fontSize, fontProfile, destinationWidth, fontRuns, range, selected);
        }
    }

    public record RasterRange(int start, int end) {}

    public record FadeRaster(RasterRange range, String time, String duration) {
        public static FadeRaster parse(String encoded) {
            if (encoded == null) return null;
            String[] fields = encoded.split(";", -1);
            if (fields.length != 4 || !fields[0].matches("[0-9]{1,3}") || !fields[1].matches("[0-9]{1,3}")) {
                throw new IllegalArgumentException("Malformed subtitle fade recipe");
            }
            return from(new SubtitleCueMotion.SelectedTextFade(Integer.parseInt(fields[0]), Integer.parseInt(fields[1]),
                    fields[2], fields[3]));
        }

        private static FadeRaster from(SubtitleCueMotion.SelectedTextFade fade) {
            return fade == null ? null : new FadeRaster(new RasterRange(fade.start(), fade.end()), fade.time(), fade.duration());
        }

        public String encoded() {
            return range.start() + ";" + range.end() + ";" + time + ";" + duration;
        }
    }

    public record DecorationRaster(long firstFrame, long endFrame, String assetId) {
        public DecorationRaster {
            if (firstFrame < 0 || firstFrame >= endFrame)
                throw new IllegalArgumentException("Invalid subtitle decoration frame interval");
            SubtitleBlockLayout.decoration(assetId);
        }

        public static DecorationRaster parse(String encoded) {
            if (encoded == null) return null;
            String[] fields = encoded.split(";", -1);
            if (fields.length != 3 || !fields[0].matches("[0-9]{1,10}") || !fields[1].matches("[0-9]{1,10}"))
                throw new IllegalArgumentException("Malformed subtitle decoration recipe");
            return new DecorationRaster(Long.parseLong(fields[0]), Long.parseLong(fields[1]), fields[2]);
        }

        public String encoded() {
            return firstFrame + ";" + endFrame + ";" + assetId;
        }

        public byte[] png(int destinationWidth) {
            return SubtitleBlockLayout.decorationPng(assetId, destinationWidth);
        }

        public int offset(int width, int destinationWidth) {
            var asset = SubtitleBlockLayout.decoration(assetId);
            double scale = (double) destinationWidth / asset.referenceWidth();
            return (int) Math.round(width * scale) + (int) Math.round(asset.gap() * scale);
        }
    }

    /** Provider-facing bounded raster recipe and placement. The time is relative to the render extent. */
    public record CueRaster(long firstFrame, long endFrame, MediaTime start, MediaTime elapsed,
                            String samples, BlockRasterRecipe recipe, FadeRaster fade, DecorationRaster decoration) {
        public CueRaster(long firstFrame, long endFrame, MediaTime start, MediaTime elapsed,
                String samples, BlockRasterRecipe recipe) {
            this(firstFrame, endFrame, start, elapsed, samples, recipe, null, null);
        }

        public String encodedStyleSuffix() {
            String runs = recipe.encodedRunsSuffix();
            if (fade == null && decoration == null) return runs;
            String style = (runs.isEmpty() ? "," : runs) + "," + (fade == null ? "" : fade.encoded());
            return decoration == null ? style : style + "," + decoration.encoded();
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
                visibleStart.subtract(begin.mediaTime()), cueSamples(), new BlockRasterRecipe(styledText.content().value(),
                        width.numerator().intValueExact(), size.numerator().intValueExact(), subtitleCue.effectiveFontProfile(), subtitleCue.fontRuns()),
                FadeRaster.from(subtitleCue.selectedTextFade()), decorationRaster(extent, begin.mediaTime(), firstFrame, endFrame)));
    }

    private DecorationRaster decorationRaster(RenderExtent extent, MediaTime cueStart, long firstFrame, long endFrame) {
        var decoration = subtitleCue.decoration();
        if (decoration == null || !decoration.enabled()) return null;
        var begin = cueStart.add(MediaTime.parse(decoration.start()));
        var finish = cueStart.add(MediaTime.parse(decoration.end()));
        if (!begin.isLessThan(extent.end()) || !finish.isGreaterThan(extent.start())) return null;
        long first = begin.isGreaterThan(extent.start())
                ? Math.max(firstFrame, firstFrameAtOrAfter(begin.subtract(extent.start()), extent.frameRate())) : firstFrame;
        long end = finish.isLessThan(extent.end())
                ? Math.min(endFrame, firstFrameAtOrAfter(finish.subtract(extent.start()), extent.frameRate())) : endFrame;
        return first < end ? new DecorationRaster(first, end, decoration.assetId()) : null;
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
