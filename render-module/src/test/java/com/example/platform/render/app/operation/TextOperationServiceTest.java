package com.example.platform.render.app.operation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.platform.audio.domain.mix.AudioMix;
import com.example.platform.fonttext.manifest.FontFaceManifest;
import com.example.platform.fonttext.resolution.FontFallbackPolicy;
import com.example.platform.fonttext.resolution.OpticalSizingResolverPolicy;
import com.example.platform.fonttext.resolution.TechnicalFontResolver;
import com.example.platform.fonttext.resolution.ValidatedFontCatalogSnapshot;
import com.example.platform.fonttext.resource.FaceIndex;
import com.example.platform.fonttext.resource.FontContentDigest;
import com.example.platform.fonttext.resource.FontFormat;
import com.example.platform.fonttext.resource.ValidatedFontExecutionReference;
import com.example.platform.fonttext.security.FontSecurityState;
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
import com.example.platform.shared.authorization.AuthorizationDecision;
import com.example.platform.render.app.plan.OperationPlanApplyService;
import com.example.platform.shared.authorization.CanonicalActor;
import com.example.platform.timeline.api.composition.TimelineValidation;
import com.example.platform.timeline.api.revision.TimelineRevisionCommands;
import com.example.platform.timeline.canonical.TextElement;
import com.example.platform.timeline.canonical.TextElementId;
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

/** P2-5.5: TextOperationService REQUEST->RESOLVE->PLAN->VALIDATE->PREVIEW->APPLY wiring. */
class TextOperationServiceTest {

    private static final String TENANT = "tenant-1";
    private static final String PROJECT = "project-1";
    private static final String BASE_REV = "rev-base";
    private static final String BASE_HASH = "a".repeat(64);
    private static final FontContentDigest INTER_DIGEST = FontContentDigest.ofText("inter-v1");

    private TimelineRevisionCommands revisionSaveService;
    private TimelineValidation timelineValidator;
    private AuthorizationDecisionPort authorizationPort;
    private OperationPlanApplyService applyService;
    private TextOperationService service;

    @BeforeEach
    void setUp() {
        revisionSaveService = mock(TimelineRevisionCommands.class);
        timelineValidator = mock(TimelineValidation.class);
        authorizationPort = mock(AuthorizationDecisionPort.class);
        applyService = mock(OperationPlanApplyService.class);
        service = new TextOperationService(revisionSaveService, timelineValidator,
                authorizationPort, applyService, resolutionInput());

        TimelineRevisionSemanticContext ctx = mock(TimelineRevisionSemanticContext.class);
        when(ctx.timelineContentDigest()).thenReturn(BASE_HASH);
        TimelineRevision revision = mock(TimelineRevision.class);
        when(revision.productId()).thenReturn(PROJECT);
        when(revision.revisionId()).thenReturn(BASE_REV);
        when(revision.semanticContext()).thenReturn(ctx);
        when(revisionSaveService.findById(TENANT, BASE_REV)).thenReturn(revision);

        when(timelineValidator.validateDocument(eq(PROJECT), any()))
                .thenReturn(new TimelineValidationResult(List.of()));

        AuthorizationDecision allow = mock(AuthorizationDecision.class);
        when(allow.allowed()).thenReturn(true);
        when(allow.ruleRef()).thenReturn("rule");
        when(allow.reasonCode()).thenReturn("reason");
        when(authorizationPort.decide(any())).thenReturn(allow);
    }

    @Test
    void addTextElementMaterializesCanonicalTextElement() {
        when(revisionSaveService.findPayloadDocument(TENANT, BASE_REV))
                .thenReturn(Optional.of(emptyDocument()));

        var preview = service.preview(TENANT, PROJECT, addRequest(), actor());

        assertNotNull(preview.planDigest());
        assertTrue(preview.changeKeys().stream().anyMatch(k -> k.startsWith("text-add")));
    }

    @Test
    void removeTextElementPlansAgainstExistingBase() {
        TextElement element = element("e1");
        when(revisionSaveService.findPayloadDocument(TENANT, BASE_REV))
                .thenReturn(Optional.of(documentWith(element)));

        var preview = service.preview(TENANT, PROJECT, removeRequest("e1"), actor());

        assertNotNull(preview.planDigest());
        assertTrue(preview.changeKeys().stream().anyMatch(k -> k.startsWith("text-remove")));
    }

    @Test
    void applyWithMismatchedDigestFailsClosed() {
        when(revisionSaveService.findPayloadDocument(TENANT, BASE_REV))
                .thenReturn(Optional.of(emptyDocument()));

        TimelineOperationException failure = assertThrows(TimelineOperationException.class,
                () -> service.authorizeAndApply(TENANT, PROJECT, addRequest(),
                        "b".repeat(64), "apply-1", actor()));
        assertEquals(TimelineOperationException.Code.PLAN_CHANGED, failure.code());
    }

    @Test
    void addTextElementPlanIsReproducibleAcrossPreviewAndApply() {
        // ADD_TEXT_ELEMENT now derives a deterministic TextElementId from a
        // stable seed, so preview and apply produce the SAME plan digest.
        when(revisionSaveService.findPayloadDocument(TENANT, BASE_REV))
                .thenReturn(Optional.of(emptyDocument()));
        OperationRequest request = addRequest();

        var preview = service.preview(TENANT, PROJECT, request, actor());
        assertEquals(preview.planDigest(),
                service.preview(TENANT, PROJECT, request, actor()).planDigest(),
                "same ADD request must yield the same plan digest");

        when(applyService.apply(any(), any(), eq(PROJECT), any()))
                .thenReturn(ApplyResult.applied(preview.planDigest(), "apply-1", BASE_REV,
                        "rev-new", "hash-new", BASE_REV, "MAIN"));

        var result = service.authorizeAndApply(TENANT, PROJECT, request,
                preview.planDigest(), "apply-1", actor());

        assertEquals(ApplyResult.APPLIED, result.status());
        assertEquals("rev-new", result.newRevisionId());
    }

    @Test
    void addTextElementRequiresResolvableFont() {
        // Empty production catalog -> ADD fails closed (TextPlanException -> INVALID_PLAN).
        TextOperationService emptyCatalogService = new TextOperationService(revisionSaveService,
                timelineValidator, authorizationPort, applyService,
                new TextOperationPlanner.FontResolutionInput(new TechnicalFontResolver(runtime()),
                        new ValidatedFontCatalogSnapshot(List.of()), null));
        when(revisionSaveService.findPayloadDocument(TENANT, BASE_REV))
                .thenReturn(Optional.of(emptyDocument()));

        TimelineOperationException failure = assertThrows(TimelineOperationException.class,
                () -> emptyCatalogService.preview(TENANT, PROJECT, addRequest(), actor()));
        assertEquals(TimelineOperationException.Code.INVALID_PLAN, failure.code());
    }

    // ---- helpers ----

    private CanonicalActor actor() {
        return CanonicalActor.user("actor-1", TENANT, Set.of(), "test");
    }

    private OperationRequest addRequest() {
        return request(OperationDefinition.V1.ADD_TEXT_ELEMENT,
                new OperationParameters.AddTextElementParameters(
                        styledText("Hi"), frame(), fallback(),
                        FontRational.whole(0), FontRational.whole(1)));
    }

    private OperationRequest removeRequest(String id) {
        return request(OperationDefinition.V1.REMOVE_TEXT_ELEMENT,
                new OperationParameters.RemoveTextElementParameters(new TextElementId(id)));
    }

    private OperationRequest request(OperationDefinition definition, OperationParameters parameters) {
        return new OperationRequest(definition.definitionId(), definition.version(),
                new OperationTargetRequest.TimelineTargetRequest(PROJECT), parameters,
                BASE_REV, BASE_HASH, null);
    }

    private TimelineDocument emptyDocument() {
        return new TimelineDocument(TimelineDocument.CURRENT_SCHEMA_VERSION, List.of(),
                TimelineMetadata.empty(), AudioMix.EMPTY, List.of(), List.of());
    }

    private TimelineDocument documentWith(TextElement element) {
        return new TimelineDocument(TimelineDocument.CURRENT_SCHEMA_VERSION, List.of(),
                TimelineMetadata.empty(), AudioMix.EMPTY, List.of(), List.of(element));
    }

    private TextElement element(String id) {
        return new TextElement(new TextElementId(id), FontRational.whole(0), FontRational.whole(1),
                styledText("Hi"), frame(), fallback(), List.of());
    }

    private TextOperationPlanner.FontResolutionInput resolutionInput() {
        return new TextOperationPlanner.FontResolutionInput(new TechnicalFontResolver(runtime()),
                catalog(), size -> FontRational.whole(12));
    }

    private TechnicalFontResolver.RuntimeCapabilityView runtime() {
        return new TechnicalFontResolver.RuntimeCapabilityView() {
            @Override public boolean supportsFormat(String f) { return true; }
            @Override public boolean supportsColorTechnology(String t) { return true; }
            @Override public boolean supportsVariationAxes() { return true; }
            @Override public boolean supportsOpticalSizing() { return true; }
        };
    }

    private ValidatedFontCatalogSnapshot catalog() {
        ValidatedFontExecutionReference ref = new ValidatedFontExecutionReference(
                INTER_DIGEST, INTER_DIGEST, FontSecurityState.VALIDATED_EXECUTION_FONT,
                FontFormat.TRUETYPE, new FaceIndex(0));
        java.util.Set<Integer> ascii = new java.util.HashSet<>();
        for (int i = 0x20; i <= 0x7E; i++) ascii.add(i);
        FontFaceManifest manifest = new FontFaceManifest(INTER_DIGEST, new FaceIndex(0),
                FontFormat.TRUETYPE, "Inter", "Regular", 400, 100, "normal", 1000, ascii,
                Set.of(ScriptTag.LATIN), true, false, true, List.of(), List.of(),
                false, false, List.of(), "VALIDATED", "CONFORMANCE_EVALUATED");
        return new ValidatedFontCatalogSnapshot(
                List.of(new ValidatedFontCatalogSnapshot.Entry(ref, manifest)));
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
}
