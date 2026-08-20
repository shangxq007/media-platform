package com.example.platform.render.domain.renderplan;

import com.example.platform.fonttext.resolution.FontFallbackPolicy;
import com.example.platform.fonttext.resolution.ResolvedFontRun;
import com.example.platform.fonttext.text.TextContent;
import com.example.platform.fonttext.typography.FontRational;
import com.example.platform.fonttext.typography.TextFrame;
import com.example.platform.timeline.canonical.TextElement;
import com.example.platform.timeline.canonical.TextElementId;
import java.util.List;
import java.util.Objects;

/**
 * ROADMAP20 correction F2: typed logical materialization requirement for a
 * TIMED_TEXT node.
 *
 * <p>Derived MATERIALIZED PROJECTION of the authoritative {@link TextElement}
 * state — the render type does NOT redefine font/text semantics. It preserves
 * enough semantic information that a future physical planner can answer:
 * <ul>
 *   <li>what text is being rasterized ({@link #textContent()}),</li>
 *   <li>when ({@link #start()}, {@link #duration()}),</li>
 *   <li>with what authored/resolved typography state
 *       ({@link #resolvedFontRuns()}, {@link #fallbackPolicy()}),</li>
 *   <li>into what authored frame/layout semantics ({@link #frame()}),</li>
 * </ul>
 * without re-reading {@code TextElement}.
 *
 * <p>Exact resolved font semantics frozen by ROADMAP_19 remain authoritative
 * and deterministic: this requirement CONSUMES {@code ResolvedFontRuns}, it
 * never recomputes font resolution. No provider-specific raster command; no
 * FFmpeg/libass invocation. Logical materialization only.
 */
public record TimedTextMaterializationRequirement(
        TextElementId id,
        FontRational start,
        FontRational duration,
        TextContent textContent,
        TextFrame frame,
        FontFallbackPolicy fallbackPolicy,
        List<ResolvedFontRun> resolvedFontRuns) implements RenderMaterializationRequirement {

    public TimedTextMaterializationRequirement {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(duration, "duration");
        Objects.requireNonNull(textContent, "textContent");
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
                element.styledText().content(),
                element.frame(),
                element.fallbackPolicy(),
                element.resolvedFontRuns());
    }

    @Override
    public String variantKey() {
        return "TIMED_TEXT";
    }
}
