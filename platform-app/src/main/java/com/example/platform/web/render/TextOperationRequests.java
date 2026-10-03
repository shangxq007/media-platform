package com.example.platform.web.render;

import com.example.platform.fonttext.resolution.FontFallbackPolicy;
import com.example.platform.fonttext.text.StyledText;
import com.example.platform.fonttext.text.TextContent;
import com.example.platform.fonttext.text.TextRange;
import com.example.platform.fonttext.typography.FontRational;
import com.example.platform.fonttext.typography.FontSelectionIntent;
import com.example.platform.fonttext.typography.ParagraphStyle;
import com.example.platform.fonttext.typography.TextFrame;
import com.example.platform.fonttext.typography.TextStyle;
import com.example.platform.fonttext.typography.VariationCoordinate;
import com.example.platform.operation.operation.OperationDefinition;
import com.example.platform.operation.operation.OperationParameters;
import com.example.platform.operation.operation.OperationRequest;
import com.example.platform.operation.operation.OperationTargetRequest;
import com.example.platform.timeline.canonical.TextElementId;

/**
 * P2-5.5 transport DTOs for the nine canonical timeline.text.* operations.
 *
 * <p>Each record mirrors the components of the corresponding typed
 * {@link OperationParameters} record and exposes {@link #toRequest(String)}
 * which builds the typed {@link OperationRequest}. Transport values reuse the
 * canonical font-text types directly (their existing Jackson creators own the
 * decoding); this layer invents no font/text semantics.
 *
 * <p>{@code expectedPlanDigest} / {@code applyCommandId} are consumed only by the
 * apply endpoints; they are ignored by preview.
 */
public final class TextOperationRequests {

    private TextOperationRequests() {
    }

    /** Common transport fields + typed request projection for both preview and apply. */
    public interface TextOperationRequestFields {
        String baseRevisionId();

        String baseContentHash();

        String expectedPlanDigest();

        String applyCommandId();

        OperationRequest toRequest(String timelineId);
    }

    static OperationRequest build(
            OperationDefinition definition, OperationParameters parameters,
            String timelineId, String baseRevisionId, String baseContentHash) {
        requireBounded(baseRevisionId, "baseRevisionId");
        requireBounded(baseContentHash, "baseContentHash");
        return new OperationRequest(definition.definitionId(), definition.version(),
                new OperationTargetRequest.TimelineTargetRequest(timelineId), parameters,
                baseRevisionId, baseContentHash, null);
    }

    static void requireBounded(String value, String field) {
        if (value == null || value.isBlank() || value.length() > 256) {
            throw new IllegalArgumentException(field + " required and bounded");
        }
    }

    static <T> T requireNonNull(T value, String field) {
        if (value == null) {
            throw new IllegalArgumentException(field + " required");
        }
        return value;
    }

    public record AddTextElementRequest(
            String baseRevisionId, String baseContentHash,
            StyledText styledText, TextFrame frame, FontFallbackPolicy fallbackPolicy,
            FontRational start, FontRational duration,
            String expectedPlanDigest, String applyCommandId) implements TextOperationRequestFields {
        @Override
        public OperationRequest toRequest(String timelineId) {
            return build(OperationDefinition.V1.ADD_TEXT_ELEMENT,
                    new OperationParameters.AddTextElementParameters(
                            requireNonNull(styledText, "styledText"),
                            requireNonNull(frame, "frame"),
                            requireNonNull(fallbackPolicy, "fallbackPolicy"),
                            requireNonNull(start, "start"),
                            requireNonNull(duration, "duration")),
                    timelineId, baseRevisionId, baseContentHash);
        }
    }

    public record RemoveTextElementRequest(
            String baseRevisionId, String baseContentHash, TextElementId textElementId,
            String expectedPlanDigest, String applyCommandId) implements TextOperationRequestFields {
        @Override
        public OperationRequest toRequest(String timelineId) {
            return build(OperationDefinition.V1.REMOVE_TEXT_ELEMENT,
                    new OperationParameters.RemoveTextElementParameters(
                            requireNonNull(textElementId, "textElementId")),
                    timelineId, baseRevisionId, baseContentHash);
        }
    }

    public record ReplaceTextContentRequest(
            String baseRevisionId, String baseContentHash, TextElementId textElementId,
            TextContent content, String expectedPlanDigest, String applyCommandId)
            implements TextOperationRequestFields {
        @Override
        public OperationRequest toRequest(String timelineId) {
            return build(OperationDefinition.V1.REPLACE_TEXT_CONTENT,
                    new OperationParameters.ReplaceTextContentParameters(
                            requireNonNull(textElementId, "textElementId"),
                            requireNonNull(content, "content")),
                    timelineId, baseRevisionId, baseContentHash);
        }
    }

    public record SetTextStyleRangeRequest(
            String baseRevisionId, String baseContentHash, TextElementId textElementId,
            TextRange range, TextStyle style, String expectedPlanDigest, String applyCommandId)
            implements TextOperationRequestFields {
        @Override
        public OperationRequest toRequest(String timelineId) {
            return build(OperationDefinition.V1.SET_TEXT_STYLE_RANGE,
                    new OperationParameters.SetTextStyleRangeParameters(
                            requireNonNull(textElementId, "textElementId"),
                            requireNonNull(range, "range"),
                            requireNonNull(style, "style")),
                    timelineId, baseRevisionId, baseContentHash);
        }
    }

    public record SetParagraphStyleRequest(
            String baseRevisionId, String baseContentHash, TextElementId textElementId,
            ParagraphStyle paragraphStyle, String expectedPlanDigest, String applyCommandId)
            implements TextOperationRequestFields {
        @Override
        public OperationRequest toRequest(String timelineId) {
            return build(OperationDefinition.V1.SET_PARAGRAPH_STYLE,
                    new OperationParameters.SetParagraphStyleParameters(
                            requireNonNull(textElementId, "textElementId"),
                            requireNonNull(paragraphStyle, "paragraphStyle")),
                    timelineId, baseRevisionId, baseContentHash);
        }
    }

    public record SetFontSelectionRequest(
            String baseRevisionId, String baseContentHash, TextElementId textElementId,
            TextRange range, FontSelectionIntent fontSelection,
            String expectedPlanDigest, String applyCommandId) implements TextOperationRequestFields {
        @Override
        public OperationRequest toRequest(String timelineId) {
            return build(OperationDefinition.V1.SET_FONT_SELECTION,
                    new OperationParameters.SetFontSelectionParameters(
                            requireNonNull(textElementId, "textElementId"),
                            requireNonNull(range, "range"),
                            requireNonNull(fontSelection, "fontSelection")),
                    timelineId, baseRevisionId, baseContentHash);
        }
    }

    public record SetFontFallbackPolicyRequest(
            String baseRevisionId, String baseContentHash, TextElementId textElementId,
            FontFallbackPolicy fallbackPolicy, String expectedPlanDigest, String applyCommandId)
            implements TextOperationRequestFields {
        @Override
        public OperationRequest toRequest(String timelineId) {
            return build(OperationDefinition.V1.SET_FONT_FALLBACK_POLICY,
                    new OperationParameters.SetFontFallbackPolicyParameters(
                            requireNonNull(textElementId, "textElementId"),
                            requireNonNull(fallbackPolicy, "fallbackPolicy")),
                    timelineId, baseRevisionId, baseContentHash);
        }
    }

    public record SetVariableFontAxisRequest(
            String baseRevisionId, String baseContentHash, TextElementId textElementId,
            VariationCoordinate coordinate, String expectedPlanDigest, String applyCommandId)
            implements TextOperationRequestFields {
        @Override
        public OperationRequest toRequest(String timelineId) {
            return build(OperationDefinition.V1.SET_VARIABLE_FONT_AXIS,
                    new OperationParameters.SetVariableFontAxisParameters(
                            requireNonNull(textElementId, "textElementId"),
                            requireNonNull(coordinate, "coordinate")),
                    timelineId, baseRevisionId, baseContentHash);
        }
    }

    public record SetTextLayoutRequest(
            String baseRevisionId, String baseContentHash, TextElementId textElementId,
            TextFrame frame, String expectedPlanDigest, String applyCommandId)
            implements TextOperationRequestFields {
        @Override
        public OperationRequest toRequest(String timelineId) {
            return build(OperationDefinition.V1.SET_TEXT_LAYOUT,
                    new OperationParameters.SetTextLayoutParameters(
                            requireNonNull(textElementId, "textElementId"),
                            requireNonNull(frame, "frame")),
                    timelineId, baseRevisionId, baseContentHash);
        }
    }
}
