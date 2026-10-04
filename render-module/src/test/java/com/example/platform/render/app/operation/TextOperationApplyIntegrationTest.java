package com.example.platform.render.app.operation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.platform.audio.domain.mix.AudioMix;
import com.example.platform.fonttext.resolution.FontFallbackPolicy;
import com.example.platform.fonttext.resolution.OpticalSizingResolverPolicy;
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
import com.example.platform.identity.api.authorization.AuthorizationDecisionPort;
import com.example.platform.operation.operation.OperationDefinition;
import com.example.platform.operation.operation.OperationParameters;
import com.example.platform.operation.operation.OperationRequest;
import com.example.platform.operation.operation.OperationTargetRequest;
import com.example.platform.operation.operation.TextOperationPlanner;
import com.example.platform.operation.plan.ApplyResult;
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
 * TEXT-OP-APPLY-NPE: REAL end-to-end apply (real OperationPlanApplyService, NOT mocked)
 * for the four text ops that do not depend on font resolution. Exercises
 * preview -> authorizeAndApply -> OperationPlanApplyService.apply -> writer.
 */
class TextOperationApplyIntegrationTest {

    private static final String TENANT = "tenant-1";
    private static final String PROJECT = "project-1";
    private static final String BASE_REV = "rev-base";
    private static final TimelineContentDigester DIGESTER = new TimelineContentDigester();

    private String baseHash;
    private TimelineRevisionCommands revisionSaveService;
    private TimelineValidation timelineValidator;
    private AuthorizationDecisionPort authorizationPort;
    private TextOperationService service;

    @BeforeEach
    void setUp() {
        TimelineDocument baseDocument = documentWith(element("e1"));
        baseHash = DIGESTER.digest(baseDocument);
        revisionSaveService = mock(TimelineRevisionCommands.class);
        timelineValidator = mock(TimelineValidation.class);
        authorizationPort = mock(AuthorizationDecisionPort.class);
        // REAL apply service (not mocked) — this is the point of the test.
        OperationPlanApplyService applyService = new OperationPlanApplyService(revisionSaveService);
        // Production-shaped minimal input: empty catalog. The four ops below do not
        // require font resolution, so they must succeed regardless.
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
        // REAL writer stub: echo the candidate digest so apply's commit digest check passes.
        when(revisionSaveService.saveRevisionForCommand(any(), any(), any(), any(), any()))
                .thenAnswer(inv -> {
                    TimelineDocument candidate = inv.getArgument(3);
                    return new TimelineRevisionCommands.RevisionWriteResult(
                            "rev-new", BASE_REV, DIGESTER.digest(candidate), false);
                });
    }

    private void assertApplies(OperationRequest request) {
        var preview = service.preview(TENANT, PROJECT, request, actor());
        var result = service.authorizeAndApply(TENANT, PROJECT, request,
                preview.planDigest(), "apply-1", actor());
        assertEquals(ApplyResult.APPLIED, result.status());
        assertEquals("rev-new", result.newRevisionId());
    }

    @Test
    void removeTextElementApplies() {
        assertApplies(request(OperationDefinition.V1.REMOVE_TEXT_ELEMENT,
                new OperationParameters.RemoveTextElementParameters(new TextElementId("e1"))));
    }

    @Test
    void setParagraphStyleApplies() {
        assertApplies(request(OperationDefinition.V1.SET_PARAGRAPH_STYLE,
                new OperationParameters.SetParagraphStyleParameters(new TextElementId("e1"),
                        paragraphStyle())));
    }

    @Test
    void setFontFallbackPolicyApplies() {
        assertApplies(request(OperationDefinition.V1.SET_FONT_FALLBACK_POLICY,
                new OperationParameters.SetFontFallbackPolicyParameters(new TextElementId("e1"),
                        new FontFallbackPolicy(List.of(new FontFamilyName("Arial")),
                                List.of(), List.of(), List.of()))));
    }

    @Test
    void setTextLayoutApplies() {
        assertApplies(request(OperationDefinition.V1.SET_TEXT_LAYOUT,
                new OperationParameters.SetTextLayoutParameters(new TextElementId("e1"), frame())));
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
                styledText("Hi"), frame(), new FontFallbackPolicy(
                        List.of(new FontFamilyName("Arial")), List.of(), List.of(), List.of()),
                List.of());
    }

    private TechnicalFontResolver.RuntimeCapabilityView runtime() {
        return new TechnicalFontResolver.RuntimeCapabilityView() {
            @Override public boolean supportsFormat(String f) { return true; }
            @Override public boolean supportsColorTechnology(String t) { return true; }
            @Override public boolean supportsVariationAxes() { return true; }
            @Override public boolean supportsOpticalSizing() { return true; }
        };
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
                paragraphStyle());
    }

    private ParagraphStyle paragraphStyle() {
        return new ParagraphStyle(ParagraphStyle.Alignment.START, ParagraphStyle.Justification.NONE,
                LineHeight.ratio(FontRational.of(12, 10)), ParagraphStyle.WrapPolicy.WRAP,
                ParagraphBaseDirection.AUTO, ParagraphStyle.LineBreakPolicy.STANDARD);
    }

    private TextFrame frame() {
        return new TextFrame(FontRational.of(640, 1), null,
                TextFrame.HorizontalAlignment.START, TextFrame.VerticalAlignment.TOP,
                ParagraphStyle.WrapPolicy.WRAP, TextFrame.OverflowBehavior.CLIP);
    }
}
