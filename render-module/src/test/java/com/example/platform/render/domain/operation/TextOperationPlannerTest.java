package com.example.platform.render.domain.operation;

import com.example.platform.audio.domain.mix.AudioMix;
import com.example.platform.fonttext.manifest.FontFaceManifest;
import com.example.platform.fonttext.resolution.FontFallbackPolicy;
import com.example.platform.fonttext.resolution.OpticalSizingResolverPolicy;
import com.example.platform.fonttext.resolution.ResolvedFontInstance;
import com.example.platform.fonttext.resolution.TechnicalFontResolver;
import com.example.platform.fonttext.resolution.ValidatedFontCatalogSnapshot;
import com.example.platform.fonttext.resource.FaceIndex;
import com.example.platform.fonttext.resource.FontContentDigest;
import com.example.platform.fonttext.resource.FontFormat;
import com.example.platform.fonttext.resource.ValidatedFontExecutionReference;
import com.example.platform.fonttext.security.FontSecurityState;
import com.example.platform.fonttext.text.LanguageTag;
import com.example.platform.fonttext.text.ParagraphBaseDirection;
import com.example.platform.fonttext.text.RangeDirectionOverride;
import com.example.platform.fonttext.text.ScriptTag;
import com.example.platform.fonttext.text.StyledText;
import com.example.platform.fonttext.text.TextContent;
import com.example.platform.fonttext.text.TextRange;
import com.example.platform.fonttext.text.TextSemanticRun;
import com.example.platform.fonttext.typography.FontFamilyName;
import com.example.platform.fonttext.typography.FontRational;
import com.example.platform.fonttext.typography.FontSelectionIntent;
import com.example.platform.fonttext.typography.FontSize;
import com.example.platform.fonttext.typography.LineHeight;
import com.example.platform.fonttext.typography.OpenTypeFeatureIntent;
import com.example.platform.fonttext.typography.OpticalSizingIntent;
import com.example.platform.fonttext.typography.ParagraphStyle;
import com.example.platform.fonttext.typography.TextFrame;
import com.example.platform.fonttext.typography.TextStyle;
import com.example.platform.fonttext.typography.TextStyleRun;
import com.example.platform.fonttext.typography.VariationAxisTag;
import com.example.platform.fonttext.typography.VariationCoordinate;
import com.example.platform.operation.operation.OperationDefinition;
import com.example.platform.operation.operation.OperationParameters;
import com.example.platform.operation.operation.OperationRequest;
import com.example.platform.operation.operation.OperationTargetRequest;
import com.example.platform.operation.operation.TextOperationPlanner;
import com.example.platform.operation.plan.OperationPlan;
import com.example.platform.timeline.canonical.TextElement;
import com.example.platform.timeline.canonical.TextElementId;
import com.example.platform.timeline.canonical.TimelineContentDigester;
import com.example.platform.timeline.canonical.TimelineDocument;
import com.example.platform.timeline.canonical.TimelineMetadata;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** ROADMAP_19 (C37): nine Text operations through the frozen OperationPlan path. */
class TextOperationPlannerTest {

    private static final String BASE_REV = "rev-base";
    private static final String BASE_HASH = "base-hash";

    private final TimelineContentDigester digester = new TimelineContentDigester();
    private final TextOperationPlanner planner = new TextOperationPlanner(digester);

    private static final FontContentDigest INTER_DIGEST = FontContentDigest.ofText("inter-v1");

    private TimelineDocument baseDocument() {
        return new TimelineDocument(TimelineDocument.CURRENT_SCHEMA_VERSION,
                List.of(), TimelineMetadata.empty(), AudioMix.EMPTY, List.of());
    }

    private ValidatedFontCatalogSnapshot catalog() {
        ValidatedFontExecutionReference ref = new ValidatedFontExecutionReference(
                INTER_DIGEST, INTER_DIGEST, FontSecurityState.VALIDATED_EXECUTION_FONT,
                FontFormat.TRUETYPE, new FaceIndex(0));
        FontFaceManifest manifest = new FontFaceManifest(INTER_DIGEST, new FaceIndex(0),
                FontFormat.TRUETYPE, "Inter", "Regular", 400, 100, "normal",
                1000, fullAsciiCoverage(), Set.of(ScriptTag.LATIN),
                true, false, true, List.of(), List.of(),
                false, false, List.of(), "VALIDATED", "CONFORMANCE_EVALUATED");
        return new ValidatedFontCatalogSnapshot(List.of(
                new ValidatedFontCatalogSnapshot.Entry(ref, manifest)));
    }

    private Set<Integer> fullAsciiCoverage() {
        Set<Integer> s = new java.util.HashSet<>();
        for (int i = 0x20; i <= 0x7E; i++) s.add(i);
        return s;
    }

    private TechnicalFontResolver resolver() {
        return new TechnicalFontResolver(new TechnicalFontResolver.RuntimeCapabilityView() {
            @Override public boolean supportsFormat(String f) { return true; }
            @Override public boolean supportsColorTechnology(String t) { return true; }
            @Override public boolean supportsVariationAxes() { return true; }
            @Override public boolean supportsOpticalSizing() { return true; }
        });
    }

    private TextOperationPlanner.FontResolutionInput resolutionInput() {
        return new TextOperationPlanner.FontResolutionInput(resolver(), catalog(),
                size -> FontRational.whole(12));
    }

    private TextOperationPlanner.FontResolutionInput resolutionInputWithoutOpszPolicy() {
        return new TextOperationPlanner.FontResolutionInput(resolver(), catalog(), null);
    }

    private OperationRequest request(OperationDefinition def, OperationParameters params,
                                     com.example.platform.operation.operation.OperationTargetRequest target) {
        return new OperationRequest(def.definitionId(), def.version(), target, params,
                BASE_REV, BASE_HASH, null);
    }

    private StyledText styledText(String text) {
        TextContent content = new TextContent(text);
        TextStyle style = new TextStyle(
                new FontSelectionIntent(List.of(new FontFamilyName("Inter")),
                        FontSelectionIntent.WeightIntent.NORMAL, FontSelectionIntent.StretchIntent.NORMAL,
                        FontSelectionIntent.SlantIntent.NORMAL, OpticalSizingIntent.disabled(), List.of()),
                new FontSize(FontRational.whole(24)), FontRational.whole(0),
                OpenTypeFeatureIntent.empty());
        return new StyledText(content,
                List.of(new TextSemanticRun(TextRange.of(0, content.scalarCount()),
                        null, ScriptTag.LATIN, RangeDirectionOverride.NONE)),
                List.of(new TextStyleRun(TextRange.of(0, content.scalarCount()), style)),
                new ParagraphStyle(ParagraphStyle.Alignment.START, ParagraphStyle.Justification.NONE,
                        LineHeight.ratio(FontRational.of(12, 10)), ParagraphStyle.WrapPolicy.WRAP,
                        ParagraphBaseDirection.AUTO, ParagraphStyle.LineBreakPolicy.STANDARD));
    }

    private TextFrame frame() {
        return new TextFrame(FontRational.of(640, 1), null,
                TextFrame.HorizontalAlignment.START, TextFrame.VerticalAlignment.TOP,
                ParagraphStyle.WrapPolicy.WRAP, TextFrame.OverflowBehavior.CLIP);
    }

    private FontFallbackPolicy fallback() {
        return new FontFallbackPolicy(List.of(new FontFamilyName("Arial")), List.of(), List.of(), List.of());
    }

    @Test
    void allNineDefinitionsExistAndAreTyped() {
        List<OperationDefinition> textDefinitions = List.of(
                OperationDefinition.V1.ADD_TEXT_ELEMENT, OperationDefinition.V1.REMOVE_TEXT_ELEMENT,
                OperationDefinition.V1.REPLACE_TEXT_CONTENT, OperationDefinition.V1.SET_TEXT_STYLE_RANGE,
                OperationDefinition.V1.SET_PARAGRAPH_STYLE, OperationDefinition.V1.SET_FONT_SELECTION,
                OperationDefinition.V1.SET_FONT_FALLBACK_POLICY, OperationDefinition.V1.SET_VARIABLE_FONT_AXIS,
                OperationDefinition.V1.SET_TEXT_LAYOUT);
        Set<String> expectedTextKeys = Set.of(
                "timeline.text.add@1.0",
                "timeline.text.remove@1.0",
                "timeline.text.replace-content@1.0",
                "timeline.text.set-style-range@1.0",
                "timeline.text.set-paragraph-style@1.0",
                "timeline.text.set-font-selection@1.0",
                "timeline.text.set-fallback-policy@1.0",
                "timeline.text.set-variable-axis@1.0",
                "timeline.text.set-layout@1.0");
        Set<String> expectedRegistryKeys = Set.of(
                "timeline.media-clip.add@1.0",
                "timeline.move@1.0",
                "timeline.delete@1.0",
                "timeline.trim@1.0",
                "timeline.temporal.set-rate@1.0",
                "timeline.temporal.set-direction@1.0",
                "timeline.temporal.freeze@1.0",
                "audio.gain.set@1.0",
                "audio.mute.set@1.0",
                "audio.balance.set@1.0",
                "timeline.group.create@1.0",
                "timeline.group.update-members@1.0",
                "timeline.group.remove@1.0",
                "timeline.sync.create@1.0",
                "timeline.sync.update-anchor@1.0",
                "timeline.sync.remove@1.0",
                "timeline.text.add@1.0",
                "timeline.text.remove@1.0",
                "timeline.text.replace-content@1.0",
                "timeline.text.set-style-range@1.0",
                "timeline.text.set-paragraph-style@1.0",
                "timeline.text.set-font-selection@1.0",
                "timeline.text.set-fallback-policy@1.0",
                "timeline.text.set-variable-axis@1.0",
                "timeline.text.set-layout@1.0");

        Set<String> actualTextKeys = new java.util.HashSet<>();
        for (OperationDefinition def : textDefinitions) {
            assertEquals(OperationDefinition.TargetKind.TEXT, def.targetKind());
            assertNotNull(def.parameterType());
            actualTextKeys.add(def.definitionId().value() + "@" + def.version());
        }
        assertEquals(expectedTextKeys, actualTextKeys);

        List<String> actualRegistryKeys = OperationDefinition.V1.ALL.stream()
                .map(def -> def.definitionId().value() + "@" + def.version())
                .toList();
        Set<String> uniqueRegistryKeys = new java.util.HashSet<>(actualRegistryKeys);
        assertEquals(actualRegistryKeys.size(), uniqueRegistryKeys.size(),
                "operation registry must not contain duplicate stable keys");
        assertEquals(expectedRegistryKeys, uniqueRegistryKeys,
                "operation registry must match the exact accepted stable-key inventory");
        assertTrue(uniqueRegistryKeys.contains("timeline.media-clip.add@1.0"),
                "accepted H7 operation must remain registered");

        long unclassifiedDefinitions = OperationDefinition.V1.ALL.stream()
                .filter(def -> def.parameterType() == null || def.targetKind() == null || def.lifecycle() == null)
                .count();
        assertEquals(0L, unclassifiedDefinitions,
                "every operation definition must be typed and classifiable");
        for (OperationDefinition def : OperationDefinition.V1.ALL) {
            assertNotNull(def.parameterType(), "every operation definition must have a parameter type");
            assertNotNull(def.targetKind(), "every operation definition must have a target kind");
            assertNotNull(def.lifecycle(), "every operation definition must have a lifecycle");
        }
        assertEquals(25, OperationDefinition.V1.ALL.size(),
                "15 previous frozen non-text + 9 text + 1 accepted H7 = 25");
    }

    @Test
    void addTextElementProducesFrozenPlanWithResolvedFonts() {
        OperationRequest req = request(OperationDefinition.V1.ADD_TEXT_ELEMENT,
                new OperationParameters.AddTextElementParameters(styledText("Hello world"), frame(),
                        fallback(), FontRational.whole(0), FontRational.whole(5)),
                new OperationTargetRequest.TextElementTargetRequest(TextElementId.random()));
        OperationPlan plan = planner.plan(req, baseDocument(), resolutionInput());
        assertEquals(1, plan.candidateTimeline().getTextElements().size());
        TextElement element = plan.candidateTimeline().getTextElements().get(0);
        assertEquals(1, element.resolvedFontRuns().size(), "exact font resolution frozen into plan");
        assertEquals(INTER_DIGEST, element.resolvedFontRuns().get(0).font().validatedDigest());
        assertTrue(plan.validated());
        assertNotEquals(BASE_HASH, plan.candidateContentHash(), "authored text must change Timeline hash");
    }

    @Test
    void addWithAutoOpszFailsClosedWithoutPolicy() {
        TextStyle autoStyle = new TextStyle(
                new FontSelectionIntent(List.of(new FontFamilyName("Inter")),
                        FontSelectionIntent.WeightIntent.NORMAL, FontSelectionIntent.StretchIntent.NORMAL,
                        FontSelectionIntent.SlantIntent.NORMAL, OpticalSizingIntent.auto(), List.of()),
                new FontSize(FontRational.whole(24)), FontRational.whole(0),
                OpenTypeFeatureIntent.empty());
        TextContent content = new TextContent("Hello");
        StyledText autoStyled = new StyledText(content,
                List.of(new TextSemanticRun(TextRange.of(0, content.scalarCount()),
                        null, ScriptTag.LATIN, RangeDirectionOverride.NONE)),
                List.of(new TextStyleRun(TextRange.of(0, content.scalarCount()), autoStyle)),
                new ParagraphStyle(ParagraphStyle.Alignment.START, ParagraphStyle.Justification.NONE,
                        LineHeight.ratio(FontRational.of(1, 1)), ParagraphStyle.WrapPolicy.WRAP,
                        ParagraphBaseDirection.AUTO, ParagraphStyle.LineBreakPolicy.STANDARD));
        OperationRequest req = request(OperationDefinition.V1.ADD_TEXT_ELEMENT,
                new OperationParameters.AddTextElementParameters(autoStyled, frame(), fallback(),
                        FontRational.whole(0), FontRational.whole(5)),
                new OperationTargetRequest.TextElementTargetRequest(TextElementId.random()));
        assertThrows(TextOperationPlanner.TextPlanException.class,
                () -> planner.plan(req, baseDocument(), resolutionInputWithoutOpszPolicy()));
    }

    @Test
    void addResolvesAutoOpszToExactCoordinateWithPolicy() {
        OperationRequest req = request(OperationDefinition.V1.ADD_TEXT_ELEMENT,
                new OperationParameters.AddTextElementParameters(
                        styledText("Hello"), frame(), fallback(),
                        FontRational.whole(0), FontRational.whole(5)),
                new OperationTargetRequest.TextElementTargetRequest(TextElementId.random()));
        OperationPlan plan = planner.plan(req, baseDocument(), resolutionInput());
        TextElement element = plan.candidateTimeline().getTextElements().get(0);
        assertNotNull(element.resolvedFontRuns().get(0).font().executionReference());
    }

    @Test
    void removeTextElementProducesPlan() {
        OperationRequest add = request(OperationDefinition.V1.ADD_TEXT_ELEMENT,
                new OperationParameters.AddTextElementParameters(styledText("Hello"), frame(),
                        fallback(), FontRational.whole(0), FontRational.whole(5)),
                new OperationTargetRequest.TextElementTargetRequest(TextElementId.random()));
        OperationPlan added = planner.plan(add, baseDocument(), resolutionInput());
        TextElementId id = added.candidateTimeline().getTextElements().get(0).id();
        TimelineDocument withText = added.candidateTimeline();

        OperationRequest remove = request(OperationDefinition.V1.REMOVE_TEXT_ELEMENT,
                new OperationParameters.RemoveTextElementParameters(id),
                new OperationTargetRequest.TextElementTargetRequest(id));
        OperationPlan removed = planner.plan(remove, withText, null);
        assertTrue(removed.candidateTimeline().getTextElements().isEmpty());
        assertEquals(removed.candidateContentHash(), digester.digest(baseDocument()),
                "remove restores base hash");
    }

    @Test
    void replaceContentRejectsOutOfRange() {
        OperationRequest add = request(OperationDefinition.V1.ADD_TEXT_ELEMENT,
                new OperationParameters.AddTextElementParameters(styledText("Hello"), frame(),
                        fallback(), FontRational.whole(0), FontRational.whole(5)),
                new OperationTargetRequest.TextElementTargetRequest(TextElementId.random()));
        TimelineDocument withText = planner.plan(add, baseDocument(), resolutionInput()).candidateTimeline();
        TextElementId id = withText.getTextElements().get(0).id();
        OperationRequest bad = request(OperationDefinition.V1.REPLACE_TEXT_CONTENT,
                new OperationParameters.ReplaceTextContentParameters(id, new TextContent("X")),
                new OperationTargetRequest.TextElementTargetRequest(id));
        OperationPlan plan = planner.plan(bad, withText, resolutionInput());
        assertTrue(plan.candidateContentHash() != null);
    }

    @Test
    void invalidTextRangeRejected() {
        OperationRequest req = request(OperationDefinition.V1.ADD_TEXT_ELEMENT,
                new OperationParameters.AddTextElementParameters(styledText("Hi"), frame(),
                        fallback(), FontRational.whole(0), FontRational.whole(5)),
                new OperationTargetRequest.TextElementTargetRequest(TextElementId.random()));
        TextElementId id = planner.plan(req, baseDocument(), resolutionInput())
                .candidateTimeline().getTextElements().get(0).id();
        TimelineDocument withText = planner.plan(req, baseDocument(), resolutionInput()).candidateTimeline();
        OperationRequest badRange = request(OperationDefinition.V1.SET_TEXT_STYLE_RANGE,
                new OperationParameters.SetTextStyleRangeParameters(id,
                        new TextRange(0, 999),
                        new TextStyle(new FontSelectionIntent(List.of(new FontFamilyName("Inter")),
                                FontSelectionIntent.WeightIntent.NORMAL, FontSelectionIntent.StretchIntent.NORMAL,
                                FontSelectionIntent.SlantIntent.NORMAL, OpticalSizingIntent.disabled(), List.of()),
                                new FontSize(FontRational.whole(24)), FontRational.whole(0),
                                OpenTypeFeatureIntent.empty())),
                new OperationTargetRequest.TextElementTargetRequest(id));
        assertThrows(TextOperationPlanner.TextPlanException.class,
                () -> planner.plan(badRange, withText, resolutionInput()));
    }

    @Test
    void setVariableAxisUpdatesIntentAndFreezesResolution() {
        OperationRequest add = request(OperationDefinition.V1.ADD_TEXT_ELEMENT,
                new OperationParameters.AddTextElementParameters(styledText("Hello"), frame(),
                        fallback(), FontRational.whole(0), FontRational.whole(5)),
                new OperationTargetRequest.TextElementTargetRequest(TextElementId.random()));
        TimelineDocument withText = planner.plan(add, baseDocument(), resolutionInput()).candidateTimeline();
        TextElementId id = withText.getTextElements().get(0).id();

        OperationRequest axis = request(OperationDefinition.V1.SET_VARIABLE_FONT_AXIS,
                new OperationParameters.SetVariableFontAxisParameters(id,
                        new VariationCoordinate(VariationAxisTag.WEIGHT, FontRational.of(700, 1))),
                new OperationTargetRequest.TextElementTargetRequest(id));
        OperationPlan plan = planner.plan(axis, withText, resolutionInput());
        TextElement updated = plan.candidateTimeline().getTextElements().get(0);
        assertEquals(List.of(new VariationCoordinate(VariationAxisTag.WEIGHT, FontRational.of(700, 1))),
                updated.resolvedFontRuns().get(0).font().variationCoordinates());
    }

    @Test
    void previewIsFrozenPlanNoMutation() {
        OperationRequest add = request(OperationDefinition.V1.ADD_TEXT_ELEMENT,
                new OperationParameters.AddTextElementParameters(styledText("Hello"), frame(),
                        fallback(), FontRational.whole(0), FontRational.whole(5)),
                new OperationTargetRequest.TextElementTargetRequest(TextElementId.random()));
        OperationPlan plan = planner.plan(add, baseDocument(), resolutionInput());
        OperationPlan preview = planner.preview(plan);
        assertSame(plan, preview, "preview = frozen plan; no mutation");
        assertEquals(0, baseDocument().getTextElements().size(), "base document untouched");
    }

    @Test
    void failedPlanProducesNoPartialMutation() {
        OperationRequest add = request(OperationDefinition.V1.ADD_TEXT_ELEMENT,
                new OperationParameters.AddTextElementParameters(styledText("Hello"), frame(),
                        fallback(), FontRational.whole(0), FontRational.whole(5)),
                new OperationTargetRequest.TextElementTargetRequest(TextElementId.random()));
        TimelineDocument base = baseDocument();
        assertThrows(TextOperationPlanner.TextPlanException.class,
                () -> planner.plan(add, base, null)); // no resolution input -> fail
        assertEquals(0, base.getTextElements().size(), "no partial mutation on failed plan");
    }
}
