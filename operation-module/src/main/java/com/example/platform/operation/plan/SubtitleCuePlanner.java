package com.example.platform.operation.plan;

import com.example.platform.fonttext.resolution.FontFallbackPolicy;
import com.example.platform.fonttext.text.ParagraphBaseDirection;
import com.example.platform.fonttext.text.StyledText;
import com.example.platform.fonttext.text.TextContent;
import com.example.platform.fonttext.text.TextRange;
import com.example.platform.fonttext.typography.*;
import com.example.platform.operation.operation.OperationInstance;
import com.example.platform.operation.operation.OperationParameters;
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
                SubtitleBlockLayout.png(set.text(), set.width(), set.fontSize());
            } catch (IllegalArgumentException invalid) {
                throw new PlanException(PlanErrorCode.INVALID_PLAN, invalid.getMessage());
            }
            var replacement = element(id, set);
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

    private static TextElement element(TextElementId id, OperationParameters.SubtitleCueParameters cue) {
        var family = new FontFamilyName("MP Cue Sans");
        var text = new TextContent(cue.text());
        var style = new TextStyle(new FontSelectionIntent(List.of(family), FontSelectionIntent.WeightIntent.NORMAL,
                FontSelectionIntent.StretchIntent.NORMAL, FontSelectionIntent.SlantIntent.NORMAL,
                OpticalSizingIntent.disabled(), List.of()), new FontSize(FontRational.whole(cue.fontSize())),
                FontRational.whole(0), OpenTypeFeatureIntent.empty());
        var paragraph = new ParagraphStyle(ParagraphStyle.Alignment.START, ParagraphStyle.Justification.NONE,
                LineHeight.ratio(FontRational.of(29, 20)), ParagraphStyle.WrapPolicy.WRAP,
                ParagraphBaseDirection.AUTO, ParagraphStyle.LineBreakPolicy.STANDARD);
        var styled = new StyledText(text, List.of(),
                List.of(new TextStyleRun(TextRange.of(0, text.scalarCount()), style)), paragraph);
        var frame = new TextFrame(FontRational.whole(cue.width()), null, TextFrame.HorizontalAlignment.START,
                TextFrame.VerticalAlignment.TOP, ParagraphStyle.WrapPolicy.WRAP, TextFrame.OverflowBehavior.CLIP);
        var fallback = new FontFallbackPolicy(List.of(family), List.of(), List.of(), List.of(new FontFamilyName("Noto Color Emoji")));
        var duration = cue.end().subtract(cue.start());
        return new TextElement(id, FontRational.of(cue.start().ticks(), cue.start().timeScale()),
                FontRational.of(duration.ticks(), duration.timeScale()), styled, frame, fallback, List.of(), cue.motion());
    }
}
