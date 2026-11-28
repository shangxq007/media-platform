package com.example.platform.operation.plan;

import com.example.platform.fonttext.resolution.FontFallbackPolicy;
import com.example.platform.fonttext.text.ParagraphBaseDirection;
import com.example.platform.fonttext.text.StyledText;
import com.example.platform.fonttext.text.TextContent;
import com.example.platform.fonttext.text.TextRange;
import com.example.platform.fonttext.typography.*;
import com.example.platform.operation.operation.OperationInstance;
import com.example.platform.operation.operation.OperationParameters;
import com.example.platform.timeline.canonical.SubtitleCueMotion;
import com.example.platform.timeline.canonical.TextElement;
import com.example.platform.timeline.canonical.TextElementId;
import com.example.platform.timeline.canonical.TimelineDocument;
import java.util.ArrayList;
import java.util.List;

final class SubtitleCuePlanner {
    private SubtitleCuePlanner() {}

    static TimelineDocument plan(OperationInstance instance, TimelineDocument base, List<PlannedChange> changes) {
        var parameters = instance.parameters();
        var id = new TextElementId(parameters instanceof OperationParameters.SubtitleCueParameters set
                ? set.cueId() : ((OperationParameters.RemoveSubtitleCueParameters) parameters).cueId());
        var elements = new ArrayList<>(base.getTextElements());
        var previous = elements.stream().filter(e -> e.id().equals(id)).findFirst().orElse(null);
        if (previous != null && previous.subtitleCue() == null) {
            throw new PlanException(PlanErrorCode.INVALID_PLAN, "The target is an existing non-subtitle TextElement");
        }
        if (parameters instanceof OperationParameters.SubtitleCueParameters set) {
            if (elements.stream().anyMatch(e -> e.subtitleCue() == null)) {
                throw new PlanException(PlanErrorCode.INVALID_PLAN, "Editable cues cannot share a render with legacy text elements");
            }
            if (previous == null && elements.stream().filter(e -> e.subtitleCue() != null).count() >= 16) {
                throw new PlanException(PlanErrorCode.INVALID_PLAN, "A timeline can contain at most sixteen editable subtitles");
            }
            validateRaster(set);
            var replacement = element(id, set, resolvedMotion(set, previous));
            if (replacement.equals(previous)) return base;
            elements.remove(previous);
            elements.add(replacement);
            changes.add(previous == null ? new PlannedChange.TextElementAdded(id) : new PlannedChange.TextElementReplaced(id));
        } else {
            if (previous == null) return base;
            elements.remove(previous);
            changes.add(new PlannedChange.TextElementRemoved(id));
        }
        return new TimelineDocument(base.getSchemaVersion(), base.getTracks(), base.getMetadata(), base.getAudioMix(),
                base.getSemanticRelationships(), elements, base.getTransitions(), base.getAutomations());
    }

    static TimelineDocument planNamedText(TimelineDocument base, TextElement saved,
            OperationParameters.SubtitleCueParameters set, TextRange changed, List<PlannedChange> changes) {
        var old = saved.styledText();
        if (old.content().value().equals(set.text())) return base;
        if (base.getTextElements().stream().anyMatch(e -> e.subtitleCue() == null)) {
            throw new PlanException(PlanErrorCode.INVALID_PLAN, "Editable cues cannot share a render with legacy text elements");
        }
        // Canonical cue runs carry the full style. A font-profile projection
        // cannot reconstruct authored tracking, features, axes, or other intent.
        if (!old.semanticRuns().isEmpty() || !saved.resolvedFontRuns().isEmpty()) throw styleConflict();
        var text = new TextContent(set.text());
        var styles = remapStyles(saved, text, changed);
        validateRaster(set);
        var styled = new StyledText(text, old.semanticRuns(), styles, old.paragraphStyle());
        var replacement = new TextElement(saved.id(), saved.start(), saved.duration(), styled,
                saved.frame(), saved.fallbackPolicy(), saved.resolvedFontRuns(), resolvedMotion(set, saved));
        var elements = new ArrayList<>(base.getTextElements());
        elements.replaceAll(e -> e.id().equals(saved.id()) ? replacement : e);
        changes.add(new PlannedChange.TextElementReplaced(saved.id()));
        return new TimelineDocument(base.getSchemaVersion(), base.getTracks(), base.getMetadata(), base.getAudioMix(),
                base.getSemanticRelationships(), elements, base.getTransitions(), base.getAutomations());
    }

    private static void validateRaster(OperationParameters.SubtitleCueParameters cue) {
        try {
            SubtitleBlockLayout.png(cue.text(), cue.width(), cue.fontSize(), cue.motion().fontProfile(),
                    SubtitleBlockLayout.REFERENCE_WIDTH, cue.motion().fontRuns());
        } catch (IllegalArgumentException invalid) {
            throw new PlanException(PlanErrorCode.INVALID_PLAN, invalid.getMessage());
        }
    }

    private record StylePiece(int owner, TextStyleRun run) {}

    private static List<TextStyleRun> remapStyles(TextElement saved, TextContent next, TextRange changed) {
        var old = saved.styledText();
        var runs = old.styleRuns();
        int oldLength = old.content().scalarCount();
        int delta = next.scalarCount() - oldLength;
        int from = changed.start(), to = changed.end();
        int cursor = 0;
        try {
            for (var run : runs) {
                var range = run.range();
                if (range.start() != cursor) throw styleConflict();
                new SubtitleTextRange(range.start(), range.end()).requireWholeGraphemes(old.content().value());
                cursor = range.end();
            }
            if (cursor != oldLength) throw styleConflict();
            var owners = new ArrayList<Integer>();
            for (int i = 0; i < runs.size(); i++) {
                var range = runs.get(i).range();
                if (from < to ? range.overlaps(changed) :
                        ((range.start() <= from && from < range.end() && !insideInline(saved, from))
                            || (range.start() < from && from <= range.end() && !insideInline(saved, from - 1)))) {
                    owners.add(i);
                }
            }
            // New text outside a wholly inline old cue has no previously
            // saved base run. Give only the new text the established cue default.
            if (owners.isEmpty() && from < to) throw styleConflict();
            int owner = owners.isEmpty() ? -1 : owners.getFirst();
            var style = owner < 0
                    ? style(saved.subtitleCue().effectiveFontProfile(), runs.getFirst().style().fontSize().value().numerator().intValueExact())
                    : runs.get(owner).style();
            if (owners.stream().anyMatch(i -> !runs.get(i).style().equals(style))) throw styleConflict();
            var remapped = new ArrayList<StylePiece>();
            for (int i = 0; i < runs.size(); i++) {
                var run = runs.get(i);
                append(remapped, i, run.style(), run.range().start(), Math.min(from, run.range().end()));
            }
            append(remapped, owner, style, from, to + delta);
            for (int i = 0; i < runs.size(); i++) {
                var run = runs.get(i);
                append(remapped, i, run.style(), Math.max(to, run.range().start()) + delta, run.range().end() + delta);
            }
            for (var piece : remapped) {
                var range = piece.run().range();
                new SubtitleTextRange(range.start(), range.end()).requireWholeGraphemes(next.value());
            }
            return remapped.stream().map(StylePiece::run).toList();
        } catch (IllegalArgumentException | ArithmeticException invalid) {
            throw styleConflict();
        }
    }

    private static boolean insideInline(TextElement saved, int scalar) {
        return saved.subtitleCue().fontRuns().stream().anyMatch(run -> run.start() <= scalar && scalar < run.end());
    }

    private static void append(List<StylePiece> pieces, int owner, TextStyle style, int start, int end) {
        if (start >= end) return;
        if (!pieces.isEmpty() && pieces.getLast().owner() == owner && pieces.getLast().run().range().end() == start) {
            start = pieces.removeLast().run().range().start();
        }
        pieces.add(new StylePiece(owner, new TextStyleRun(TextRange.of(start, end), style)));
    }

    private static PlanException styleConflict() {
        return new PlanException(PlanErrorCode.INVALID_PLAN,
                "Translation cannot unambiguously preserve the saved canonical text style. Edit within one style or keep its boundary intact.");
    }

    private static SubtitleCueMotion resolvedMotion(OperationParameters.SubtitleCueParameters cue, TextElement previous) {
        var motion = cue.motion();
        if (previous == null) return motion;
        var saved = previous.subtitleCue();
        var fade = motion.selectedTextFade();
        var duration = cue.end().subtract(cue.start());
        if (cue.fadeIntent() == OperationParameters.SubtitleCueParameters.FadeIntent.PRESERVE && saved.selectedTextFade() != null) {
            // Without an edit span a legacy client cannot prove which occurrence survived.
            if (!previous.styledText().content().value().equals(cue.text())) {
                throw new PlanException(PlanErrorCode.INVALID_PLAN,
                        "The saved selected-text fade cannot be preserved across a text change. Supply its new range and timing, or send selectedTextFade: null to remove it.");
            }
            fade = saved.selectedTextFade();
            var finish = com.example.platform.shared.time.MediaTime.parse(fade.time())
                    .add(com.example.platform.shared.time.MediaTime.parse(fade.duration()));
            if (finish.isGreaterThan(duration)) {
                throw new PlanException(PlanErrorCode.INVALID_PLAN,
                        "The saved selected-text fade does not fit the new cue interval. Adjust its timing or send selectedTextFade: null to remove it.");
            }
        }
        var decoration = cue.decorationSupplied() ? motion.decoration() : saved.decoration();
        try {
            if (decoration != null) decoration.requireInside(duration);
        } catch (IllegalArgumentException invalid) {
            throw new PlanException(PlanErrorCode.INVALID_PLAN, invalid.getMessage());
        }
        return new SubtitleCueMotion(motion.fontProfile(), motion.keys(), motion.fontRuns(), fade, decoration);
    }

    private static TextElement element(TextElementId id, OperationParameters.SubtitleCueParameters cue, SubtitleCueMotion motion) {
        var families = SubtitleBlockLayout.fontFamilies(cue.motion().fontProfile());
        var text = new TextContent(cue.text());
        var style = style(cue.motion().effectiveFontProfile(), cue.fontSize());
        var styles = new ArrayList<TextStyleRun>();
        int cursor = 0;
        for (var run : cue.motion().fontRuns()) {
            if (cursor < run.start()) styles.add(new TextStyleRun(TextRange.of(cursor, run.start()), style));
            styles.add(new TextStyleRun(TextRange.of(run.start(), run.end()), style(run.fontProfile(), cue.fontSize())));
            cursor = run.end();
        }
        if (cursor < text.scalarCount()) styles.add(new TextStyleRun(TextRange.of(cursor, text.scalarCount()), style));
        var paragraph = new ParagraphStyle(ParagraphStyle.Alignment.START, ParagraphStyle.Justification.NONE,
                LineHeight.ratio(FontRational.of(29, 20)), ParagraphStyle.WrapPolicy.WRAP,
                ParagraphBaseDirection.AUTO, ParagraphStyle.LineBreakPolicy.STANDARD);
        var styled = new StyledText(text, List.of(), styles, paragraph);
        var frame = new TextFrame(FontRational.whole(cue.width()), null, TextFrame.HorizontalAlignment.START,
                TextFrame.VerticalAlignment.TOP, ParagraphStyle.WrapPolicy.WRAP, TextFrame.OverflowBehavior.CLIP);
        var fallback = new FontFallbackPolicy(families, List.of(), List.of(), List.of(new FontFamilyName("Noto Color Emoji")));
        var duration = cue.end().subtract(cue.start());
        return new TextElement(id, FontRational.of(cue.start().ticks(), cue.start().timeScale()),
                FontRational.of(duration.ticks(), duration.timeScale()), styled, frame, fallback, List.of(), motion);
    }

    private static TextStyle style(String profile, int size) {
        return new TextStyle(new FontSelectionIntent(List.of(SubtitleBlockLayout.fontFamilies(profile).getFirst()), FontSelectionIntent.WeightIntent.NORMAL,
                FontSelectionIntent.StretchIntent.NORMAL, FontSelectionIntent.SlantIntent.NORMAL,
                OpticalSizingIntent.disabled(), List.of()), new FontSize(FontRational.whole(size)),
                FontRational.whole(0), OpenTypeFeatureIntent.empty());
    }
}
