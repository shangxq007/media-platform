package com.example.platform.operation.operation;

import com.example.platform.fonttext.resolution.FontFallbackPolicy;
import com.example.platform.fonttext.resolution.OpticalSizingResolverPolicy;
import com.example.platform.fonttext.resolution.ResolvedFontRun;
import com.example.platform.fonttext.resolution.TechnicalFontResolver;
import com.example.platform.fonttext.resolution.ValidatedFontCatalogSnapshot;
import com.example.platform.fonttext.text.StyledText;
import com.example.platform.fonttext.text.TextContent;
import com.example.platform.fonttext.text.TextRange;
import com.example.platform.fonttext.typography.FontSelectionIntent;
import com.example.platform.fonttext.typography.FontSize;
import com.example.platform.fonttext.typography.OpticalSizingIntent;
import com.example.platform.fonttext.typography.ParagraphStyle;
import com.example.platform.fonttext.typography.TextFrame;
import com.example.platform.fonttext.typography.TextStyle;
import com.example.platform.fonttext.typography.TextStyleRun;
import com.example.platform.operation.plan.OperationPlan;
import com.example.platform.operation.plan.OperationPlanDigest;
import com.example.platform.operation.plan.PlannedChange;
import com.example.platform.timeline.canonical.TextElement;
import com.example.platform.timeline.canonical.TextElementId;
import com.example.platform.timeline.canonical.TimelineContentDigester;
import com.example.platform.timeline.canonical.TimelineDocument;
import com.example.platform.timeline.canonical.TimelineMetadata;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * ROADMAP_19 (C37/FTG19): typed planner for the nine frozen Text operations.
 *
 * <p>Flow: typed OperationRequest → technical font resolution (plan phase,
 * catalog + opsz policy injected here ONLY) → frozen OperationPlan carrying a
 * fully materialized candidate Timeline (with exact ResolvedFontRuns already
 * frozen) → preview (candidate) → atomic apply consumes only the frozen plan.
 * Atomic apply NEVER queries the mutable catalog: resolution inputs are
 * structurally absent from the apply path.
 */
public final class TextOperationPlanner {

    /** Plan-phase font resolution inputs (never available at apply). */
    public record FontResolutionInput(
            TechnicalFontResolver resolver,
            ValidatedFontCatalogSnapshot catalog,
            OpticalSizingResolverPolicy opszPolicy) {
        public FontResolutionInput {
            Objects.requireNonNull(resolver, "resolver");
            Objects.requireNonNull(catalog, "catalog");
        }
    }

    public static final class TextPlanException extends RuntimeException {
        public TextPlanException(String message) {
            super(message);
        }
    }

    private final TimelineContentDigester digester;

    public TextOperationPlanner() {
        this.digester = new TimelineContentDigester();
    }

    public TextOperationPlanner(TimelineContentDigester digester) {
        this.digester = digester;
    }

    /**
     * Build a frozen OperationPlan for one typed Text operation. Throws
     * TextPlanException on invalid ranges/timing/unresolvable AUTO opsz
     * (fail closed; no partial candidate).
     */
    public OperationPlan plan(OperationRequest request, TimelineDocument base,
                              FontResolutionInput resolutionInput) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(base, "base");
        OperationDefinition definition = definitionOf(request.definitionId());

        List<TextElement> textElements = new ArrayList<>(base.getTextElements());
        List<PlannedChange> changes = new ArrayList<>();
        String changeKey;
        boolean noOp = false;

        switch (request.parameters()) {
            case OperationParameters.AddTextElementParameters p -> {
                TextElementId id = TextElementId.random();
                if (resolutionInput == null) {
                    throw new TextPlanException("ADD_TEXT_ELEMENT requires font resolution input");
                }
                StyledText styled = p.styledText();
                TextElement element = resolveFonts(id, p.start(), p.duration(), styled,
                        p.frame(), p.fallbackPolicy(), resolutionInput);
                textElements.add(element);
                changes.add(new PlannedChange.TextElementAdded(id));
                changeKey = "text-add(" + id.value() + ")";
            }
            case OperationParameters.RemoveTextElementParameters p -> {
                TextElementId id = p.textElementId();
                if (!removeElement(textElements, id)) {
                    throw new TextPlanException("REMOVE_TEXT_ELEMENT: no such TextElement " + id.value());
                }
                changes.add(new PlannedChange.TextElementRemoved(id));
                changeKey = "text-remove(" + id.value() + ")";
            }
            case OperationParameters.ReplaceTextContentParameters p -> {
                TextElementId id = p.textElementId();
                TextContent content = p.content();
                TextElement existing = requireElement(textElements, id);
                validateRange(new TextRange(0, content.scalarCount()), content);
                TextElement replaced = withContent(existing, content, resolutionInput);
                replaceElement(textElements, replaced);
                changes.add(new PlannedChange.TextElementReplaced(id));
                changeKey = "text-replace(" + id.value() + ")";
            }
            case OperationParameters.SetTextStyleRangeParameters p -> {
                TextElementId id = p.textElementId();
                TextElement existing = requireElement(textElements, id);
                validateRange(p.range(), existing.styledText().content());
                StyledText styled = existing.styledText();
                List<TextStyleRun> runs = new ArrayList<>(styled.styleRuns());
                runs.removeIf(r -> r.range().overlaps(p.range()));
                runs.add(new TextStyleRun(p.range(), p.style()));
                StyledText newStyled = new StyledText(styled.content(), styled.semanticRuns(),
                        runs, styled.paragraphStyle());
                replaceElement(textElements, withStyledText(existing, newStyled, resolutionInput));
                changes.add(new PlannedChange.TextElementReplaced(id));
                changeKey = "text-replace(" + id.value() + ")";
            }
            case OperationParameters.SetParagraphStyleParameters p -> {
                TextElementId id = p.textElementId();
                TextElement existing = requireElement(textElements, id);
                StyledText styled = existing.styledText();
                StyledText newStyled = new StyledText(styled.content(), styled.semanticRuns(),
                        styled.styleRuns(), p.paragraphStyle());
                replaceElement(textElements, withStyledText(existing, newStyled, null));
                changes.add(new PlannedChange.TextElementReplaced(id));
                changeKey = "text-replace(" + id.value() + ")";
            }
            case OperationParameters.SetFontSelectionParameters p -> {
                TextElementId id = p.textElementId();
                TextElement existing = requireElement(textElements, id);
                validateRange(p.range(), existing.styledText().content());
                FontSelectionIntent intent = p.fontSelection();
                if (intent.opticalSizing().kind() == OpticalSizingIntent.Kind.AUTO && resolutionInput == null) {
                    throw new TextPlanException("SET_FONT_SELECTION with AUTO opsz requires resolution input");
                }
                StyledText styled = existing.styledText();
                List<TextStyleRun> runs = new ArrayList<>(styled.styleRuns());
                runs.removeIf(r -> r.range().overlaps(p.range()));
                TextStyle style = new TextStyle(intent, existing.styledText().styleRuns().isEmpty()
                        ? new FontSize(com.example.platform.fonttext.typography.FontRational.whole(24))
                        : existing.styledText().styleRuns().get(0).style().fontSize(),
                        existing.styledText().styleRuns().isEmpty()
                                ? com.example.platform.fonttext.typography.FontRational.whole(0)
                                : existing.styledText().styleRuns().get(0).style().tracking(),
                        existing.styledText().styleRuns().isEmpty()
                                ? com.example.platform.fonttext.typography.OpenTypeFeatureIntent.empty()
                                : existing.styledText().styleRuns().get(0).style().features());
                runs.add(new TextStyleRun(p.range(), style));
                StyledText newStyled = new StyledText(styled.content(), styled.semanticRuns(),
                        runs, styled.paragraphStyle());
                replaceElement(textElements, withStyledText(existing, newStyled, resolutionInput));
                changes.add(new PlannedChange.TextElementReplaced(id));
                changeKey = "text-replace(" + id.value() + ")";
            }
            case OperationParameters.SetFontFallbackPolicyParameters p -> {
                TextElementId id = p.textElementId();
                TextElement existing = requireElement(textElements, id);
                replaceElement(textElements, new TextElement(existing.id(), existing.start(),
                        existing.duration(), existing.styledText(), existing.frame(),
                        p.fallbackPolicy(), existing.resolvedFontRuns()));
                changes.add(new PlannedChange.TextElementReplaced(id));
                changeKey = "text-replace(" + id.value() + ")";
            }
            case OperationParameters.SetVariableFontAxisParameters p -> {
                TextElementId id = p.textElementId();
                TextElement existing = requireElement(textElements, id);
                if (resolutionInput == null) {
                    throw new TextPlanException("SET_VARIABLE_FONT_AXIS requires font resolution input");
                }
                StyledText styled = existing.styledText();
                List<TextStyleRun> runs = new ArrayList<>(styled.styleRuns());
                List<TextStyleRun> newRuns = new ArrayList<>();
                for (TextStyleRun run : runs) {
                    newRuns.add(new TextStyleRun(run.range(), new TextStyle(
                            run.style().fontSelection().withAxisOverride(p.coordinate()),
                            run.style().fontSize(), run.style().tracking(), run.style().features())));
                }
                StyledText newStyled = new StyledText(styled.content(), styled.semanticRuns(),
                        newRuns, styled.paragraphStyle());
                replaceElement(textElements, withStyledText(existing, newStyled, resolutionInput));
                changes.add(new PlannedChange.TextElementReplaced(id));
                changeKey = "text-replace(" + id.value() + ")";
            }
            case OperationParameters.SetTextLayoutParameters p -> {
                TextElementId id = p.textElementId();
                TextElement existing = requireElement(textElements, id);
                replaceElement(textElements, new TextElement(existing.id(), existing.start(),
                        existing.duration(), existing.styledText(), p.frame(),
                        existing.fallbackPolicy(), existing.resolvedFontRuns()));
                changes.add(new PlannedChange.TextElementReplaced(id));
                changeKey = "text-replace(" + id.value() + ")";
            }
            default -> throw new TextPlanException("Not a Text operation: " + request.definitionId().value());
        }

        TimelineDocument candidate = new TimelineDocument(base.getSchemaVersion(), base.getTracks(),
                base.getMetadata(), base.getAudioMix(), base.getSemanticRelationships(), textElements);
        String candidateHash = digester.digest(candidate);
        String planDigest = OperationPlanDigest.compute(
                request.baseRevisionId(), request.baseContentHash(),
                request.definitionId().value(), request.version().toString(),
                Integer.toHexString(request.parameters().hashCode()),
                List.of(request.target().toString()), List.of(changeKey), candidateHash);
        return new OperationPlan(OperationPlan.FORMAT_VERSION, request.baseRevisionId(),
                request.baseContentHash(), null, changes, candidate, candidateHash,
                true, planDigest, noOp);
    }

    public OperationPlan preview(OperationPlan plan) {
        // Preview is the frozen plan itself: candidate Timeline is fully
        // materialized; preview never mutates canonical state.
        return plan;
    }

    private TextElement resolveFonts(TextElementId id,
                                     com.example.platform.fonttext.typography.FontRational start,
                                     com.example.platform.fonttext.typography.FontRational duration,
                                     StyledText styled, TextFrame frame, FontFallbackPolicy fallback,
                                     FontResolutionInput input) {
        FontSelectionIntent intent = firstIntent(styled);
        if (intent.opticalSizing().kind() == OpticalSizingIntent.Kind.AUTO && input.opszPolicy() == null) {
            throw new TextPlanException("AUTO optical sizing requires an explicit resolution policy (fail closed)");
        }
        TechnicalFontResolver.Result result = input.resolver().resolve(
                styled.content(), intent, fallback, input.catalog(), input.opszPolicy());
        if (!result.diagnostics().isEmpty()) {
            throw new TextPlanException("Font resolution diagnostics for " + id.value() + ": "
                    + result.diagnostics().get(0).code());
        }
        return new TextElement(id, start, duration, styled, frame, fallback, result.runs());
    }

    private TextElement withContent(TextElement existing, TextContent content,
                                    FontResolutionInput input) {
        StyledText styled = existing.styledText();
        int newCount = content.scalarCount();
        List<com.example.platform.fonttext.text.TextSemanticRun> semanticRuns = styled.semanticRuns().stream()
                .map(r -> new com.example.platform.fonttext.text.TextSemanticRun(TextRange.of(0, newCount),
                        r.language(), r.script(), r.directionOverride()))
                .toList();
        StyledText newStyled = new StyledText(content,
                semanticRuns,
                styled.styleRuns().isEmpty() ? List.of()
                        : styled.styleRuns().stream().map(r -> new TextStyleRun(
                        new TextRange(0, newCount), r.style())).toList(),
                styled.paragraphStyle());
        if (input == null) {
            return new TextElement(existing.id(), existing.start(), existing.duration(),
                    newStyled, existing.frame(), existing.fallbackPolicy(), List.of());
        }
        return resolveFonts(existing.id(), existing.start(), existing.duration(),
                newStyled, existing.frame(), existing.fallbackPolicy(), input);
    }

    private TextElement withStyledText(TextElement existing, StyledText styled,
                                       FontResolutionInput input) {
        if (input == null) {
            return new TextElement(existing.id(), existing.start(), existing.duration(),
                    styled, existing.frame(), existing.fallbackPolicy(), existing.resolvedFontRuns());
        }
        return resolveFonts(existing.id(), existing.start(), existing.duration(),
                styled, existing.frame(), existing.fallbackPolicy(), input);
    }

    private static FontSelectionIntent firstIntent(StyledText styled) {
        for (TextStyleRun run : styled.styleRuns()) {
            return run.style().fontSelection();
        }
        throw new TextPlanException("StyledText requires at least one style run");
    }

    private static void validateRange(TextRange range, TextContent content) {
        if (range.start() < 0 || range.end() > content.scalarCount() || range.end() < range.start()) {
            throw new TextPlanException("TextRange out of bounds: " + range + " (scalar count "
                    + content.scalarCount() + ")");
        }
    }

    private static TextElement requireElement(List<TextElement> elements, TextElementId id) {
        for (TextElement e : elements) {
            if (e.id().equals(id)) {
                return e;
            }
        }
        throw new TextPlanException("No such TextElement: " + id.value());
    }

    private static boolean removeElement(List<TextElement> elements, TextElementId id) {
        return elements.removeIf(e -> e.id().equals(id));
    }

    private static void replaceElement(List<TextElement> elements, TextElement replacement) {
        for (int i = 0; i < elements.size(); i++) {
            if (elements.get(i).id().equals(replacement.id())) {
                elements.set(i, replacement);
                return;
            }
        }
        throw new TextPlanException("replaceElement: no such TextElement " + replacement.id().value());
    }

    private static OperationDefinition definitionOf(OperationDefinitionId id) {
        for (OperationDefinition def : OperationDefinition.V1.ALL) {
            if (def.definitionId().equals(id)) {
                return def;
            }
        }
        throw new TextPlanException("Unknown operation definition: " + id.value());
    }
}
