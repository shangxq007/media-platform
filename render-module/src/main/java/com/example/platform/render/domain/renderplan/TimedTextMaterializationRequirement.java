package com.example.platform.render.domain.renderplan;

import com.example.platform.fonttext.resolution.FontFallbackPolicy;
import com.example.platform.fonttext.resolution.ResolvedFontRun;
import com.example.platform.fonttext.text.StyledText;
import com.example.platform.fonttext.typography.FontRational;
import com.example.platform.fonttext.typography.TextFrame;
import com.example.platform.timeline.canonical.TextElement;
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
 * <p>No provider-specific raster command; no FFmpeg/libass; logical
 * materialization only.
 */
public record TimedTextMaterializationRequirement(
        TextElementId id,
        FontRational start,
        FontRational duration,
        StyledText styledText,
        TextFrame frame,
        FontFallbackPolicy fallbackPolicy,
        List<ResolvedFontRun> resolvedFontRuns) implements RenderMaterializationRequirement {

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
                element.resolvedFontRuns());
    }

    @Override
    public String variantKey() {
        return "TIMED_TEXT";
    }
}
