package com.example.platform.render.app.operation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.platform.audio.domain.mix.AudioMix;
import com.example.platform.fonttext.resolution.FontFallbackPolicy;
import com.example.platform.fonttext.resolution.TechnicalFontResolver;
import com.example.platform.fonttext.resolution.ValidatedFontCatalogSnapshot;
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
import com.example.platform.identity.api.authorization.AuthorizationDecisionPort;
import com.example.platform.operation.operation.OperationDefinition;
import com.example.platform.operation.operation.OperationParameters;
import com.example.platform.operation.operation.OperationRequest;
import com.example.platform.operation.operation.OperationTargetRequest;
import com.example.platform.operation.operation.TextOperationPlanner;
import com.example.platform.render.app.plan.OperationPlanApplyService;
import com.example.platform.shared.authorization.AuthorizationDecision;
import com.example.platform.shared.authorization.CanonicalActor;
import com.example.platform.timeline.api.composition.TimelineValidation;
import com.example.platform.timeline.api.revision.TimelineRevisionCommands;
import com.example.platform.timeline.canonical.TextElement;
import com.example.platform.timeline.canonical.TextElementId;
import com.example.platform.timeline.canonical.TimelineContentDigester;
import com.example.platform.timeline.canonical.TimelineDocument;
import com.example.platform.timeline.canonical.TimelineMetadata;
import com.example.platform.timeline.canonicalmodel.TimelineValidationResult;
import com.example.platform.timeline.version.TimelineRevision;
import com.example.platform.timeline.version.TimelineRevisionSemanticContext;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * TEXT-OP-APPLY-NPE: the five font-resolution-dependent text ops must STILL fail
 * closed at plan time (INVALID_PLAN -> 422) with the production empty catalog —
 * i.e. the sourceInstance fix must not "bypass" font resolution.
 */
class TextOperationFontGateTest {

    private static final String TENANT = "tenant-1";
    private static final String PROJECT = "project-1";
    private static final String BASE_REV = "rev-base";
    private static final TimelineContentDigester DIGESTER = new TimelineContentDigester();

    private String baseHash;
    private TextOperationService service;

    @BeforeEach
    void setUp() {
        TimelineDocument baseDocument = documentWith(element("e1"));
        baseHash = DIGESTER.digest(baseDocument);
        TimelineRevisionCommands revisionSaveService = mock(TimelineRevisionCommands.class);
        TimelineValidation timelineValidator = mock(TimelineValidation.class);
        AuthorizationDecisionPort authorizationPort = mock(AuthorizationDecisionPort.class);
        OperationPlanApplyService applyService = new OperationPlanApplyService(revisionSaveService);
        TextOperationPlanner.FontResolutionInput fontInput =
                new TextOperationPlanner.FontResolutionInput(
                        new TechnicalFontResolver(runtime()),
                        new ValidatedFontCatalogSnapshot(List.of()), null);
        service = new TextOperationService(revisionSaveService, timelineValidator,
                authorizationPort, applyService, fontInput);

        TimelineRevisionSemanticContext ctx = mock(TimelineRevisionSemanticContext.class);
        when(ctx.timelineContentDigest()).thenReturn(baseHash);
        TimelineRevision revision = mock(TimelineRevision.class);
        when(revision.productId()).thenReturn(PROJECT);
        when(revision.revisionId()).thenReturn(BASE_REV);
        when(revision.semanticContext()).thenReturn(ctx);
        when(revisionSaveService.findById(TENANT, BASE_REV)).thenReturn(revision);
        when(revisionSaveService.findPayloadDocument(TENANT, BASE_REV))
                .thenReturn(Optional.of(baseDocument));
        when(timelineValidator.validateDocument(any(), any()))
                .thenReturn(new TimelineValidationResult(List.of()));
        AuthorizationDecision allow = mock(AuthorizationDecision.class);
        when(allow.allowed()).thenReturn(true);
        when(allow.ruleRef()).thenReturn("rule");
        when(allow.reasonCode()).thenReturn("reason");
        when(authorizationPort.decide(any())).thenReturn(allow);
    }

    private void assertFontGate(OperationRequest request) {
        TimelineOperationException failure = assertThrows(TimelineOperationException.class,
                () -> service.preview(TENANT, PROJECT, request, actor()));
        assertEquals(TimelineOperationException.Code.INVALID_PLAN, failure.code(),
                "font-dependent op must fail closed as INVALID_PLAN");
        assertTrue(failure.failures().stream().anyMatch(f -> f.contains("FONT_UNAVAILABLE")),
                "failure must be caused by font resolution, not sourceInstance: " + failure.failures());
    }

    @Test
    void addTextElementStillFontGated() {
        assertFontGate(request(OperationDefinition.V1.ADD_TEXT_ELEMENT,
                new OperationParameters.AddTextElementParameters(styledText("Hi"), frame(),
                        fallback(), FontRational.whole(0), FontRational.whole(1))));
    }

    @Test
    void replaceTextContentStillFontGated() {
        assertFontGate(request(OperationDefinition.V1.REPLACE_TEXT_CONTENT,
                new OperationParameters.ReplaceTextContentParameters(
                        new TextElementId("e1"), new TextContent("Bye"))));
    }

    @Test
    void setTextStyleRangeStillFontGated() {
        assertFontGate(request(OperationDefinition.V1.SET_TEXT_STYLE_RANGE,
                new OperationParameters.SetTextStyleRangeParameters(
                        new TextElementId("e1"), TextRange.of(0, 2), textStyle())));
    }

    @Test
    void setFontSelectionStillFontGated() {
        assertFontGate(request(OperationDefinition.V1.SET_FONT_SELECTION,
                new OperationParameters.SetFontSelectionParameters(
                        new TextElementId("e1"), TextRange.of(0, 2), fontSelectionIntent())));
    }

    @Test
    void setVariableFontAxisStillFontGated() {
        assertFontGate(request(OperationDefinition.V1.SET_VARIABLE_FONT_AXIS,
                new OperationParameters.SetVariableFontAxisParameters(
                        new TextElementId("e1"),
                        new VariationCoordinate(VariationAxisTag.WEIGHT, FontRational.whole(400)))));
    }

    // ---- helpers ----

    private CanonicalActor actor() {
        return CanonicalActor.user("actor-1", TENANT, Set.of(), "test");
    }

    private OperationRequest request(OperationDefinition definition, OperationParameters parameters) {
        return new OperationRequest(definition.definitionId(), definition.version(),
                new OperationTargetRequest.TimelineTargetRequest(PROJECT), parameters,
                BASE_REV, baseHash, null);
    }

    private TimelineDocument documentWith(TextElement element) {
        return new TimelineDocument(TimelineDocument.CURRENT_SCHEMA_VERSION, List.of(),
                TimelineMetadata.empty(), AudioMix.EMPTY, List.of(), List.of(element));
    }

    private TextElement element(String id) {
        return new TextElement(new TextElementId(id), FontRational.whole(0), FontRational.whole(1),
                styledText("Hi"), frame(), fallback(), List.of());
    }

    private TechnicalFontResolver.RuntimeCapabilityView runtime() {
        return new TechnicalFontResolver.RuntimeCapabilityView() {
            @Override public boolean supportsFormat(String f) { return true; }
            @Override public boolean supportsColorTechnology(String t) { return true; }
            @Override public boolean supportsVariationAxes() { return true; }
            @Override public boolean supportsOpticalSizing() { return true; }
        };
    }

    private FontFallbackPolicy fallback() {
        return new FontFallbackPolicy(List.of(new FontFamilyName("Arial")), List.of(), List.of(), List.of());
    }

    private TextStyle textStyle() {
        return new TextStyle(fontSelectionIntent(), new FontSize(FontRational.whole(24)),
                FontRational.whole(0), OpenTypeFeatureIntent.empty());
    }

    private FontSelectionIntent fontSelectionIntent() {
        return new FontSelectionIntent(List.of(new FontFamilyName("Inter")),
                FontSelectionIntent.WeightIntent.NORMAL, FontSelectionIntent.StretchIntent.NORMAL,
                FontSelectionIntent.SlantIntent.NORMAL, OpticalSizingIntent.disabled(), List.of());
    }

    private StyledText styledText(String text) {
        TextContent content = new TextContent(text);
        return new StyledText(content,
                List.of(new TextSemanticRun(TextRange.of(0, content.scalarCount()),
                        null, ScriptTag.LATIN, RangeDirectionOverride.NONE)),
                List.of(new TextStyleRun(TextRange.of(0, content.scalarCount()), textStyle())),
                new ParagraphStyle(ParagraphStyle.Alignment.START, ParagraphStyle.Justification.NONE,
                        LineHeight.ratio(FontRational.of(12, 10)), ParagraphStyle.WrapPolicy.WRAP,
                        ParagraphBaseDirection.AUTO, ParagraphStyle.LineBreakPolicy.STANDARD));
    }

    private TextFrame frame() {
        return new TextFrame(FontRational.of(640, 1), null,
                TextFrame.HorizontalAlignment.START, TextFrame.VerticalAlignment.TOP,
                ParagraphStyle.WrapPolicy.WRAP, TextFrame.OverflowBehavior.CLIP);
    }
}
