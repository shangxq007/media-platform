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
            try {
                SubtitleBlockLayout.png(set.text(), set.width(), set.fontSize(), set.motion().fontProfile(),
                        SubtitleBlockLayout.REFERENCE_WIDTH, set.motion().fontRuns());
            } catch (IllegalArgumentException invalid) {
                throw new PlanException(PlanErrorCode.INVALID_PLAN, invalid.getMessage());
            }
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

    private static SubtitleCueMotion resolvedMotion(OperationParameters.SubtitleCueParameters cue, TextElement previous) {
        var motion = cue.motion();
        if (cue.fadeIntent() != OperationParameters.SubtitleCueParameters.FadeIntent.PRESERVE || previous == null
                || previous.subtitleCue().selectedTextFade() == null) {
            return motion;
        }
        // A legacy payload supplies no edit span. Even matching substrings cannot prove
        // that the intended occurrence survived a text replacement.
        if (!previous.styledText().content().value().equals(cue.text())) {
            throw new PlanException(PlanErrorCode.INVALID_PLAN,
                    "The saved selected-text fade cannot be preserved across a text change. Supply its new range and timing, or send selectedTextFade: null to remove it.");
        }
        var preserved = new SubtitleCueMotion(motion.fontProfile(), motion.keys(), motion.fontRuns(),
                previous.subtitleCue().selectedTextFade());
        try {
            preserved.requireInside(cue.end().subtract(cue.start()));
        } catch (IllegalArgumentException invalid) {
            throw new PlanException(PlanErrorCode.INVALID_PLAN,
                    "The saved selected-text fade does not fit the new cue interval. Adjust its timing or send selectedTextFade: null to remove it.");
        }
        return preserved;
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
