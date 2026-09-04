package com.example.platform.web.render;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.platform.observability.monitoring.SentryMonitoringService;
import com.example.platform.render.app.timeline.RenderJobRevisionPinningService;
import com.example.platform.shared.authorization.CanonicalActor;
import com.example.platform.shared.web.TenantContext;
import com.example.platform.timeline.app.PatchApplyResult;
import com.example.platform.timeline.app.TimelineCanonicalRejectionException;
import com.example.platform.timeline.app.TimelinePatchApplicationService;
import com.example.platform.timeline.app.TimelineRevisionDiffQuery;
import com.example.platform.timeline.app.TimelineRevisionQueryService;
import com.example.platform.timeline.app.TimelineRevisionSaveService;
import com.example.platform.timeline.canonical.TimelineContentDigester;
import com.example.platform.timeline.canonicalmodel.TimelineModelPath;
import com.example.platform.timeline.patch.PatchError;
import com.example.platform.timeline.patch.PatchErrorCode;
import com.example.platform.timeline.version.TimelineConflictException;
import com.example.platform.web.GlobalExceptionHandler;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class TimelineMutationControllersMockMvcTest {

    private TimelineRevisionSaveService saveService;
    private TimelinePatchApplicationService patchService;
    private TimelineProjectAuthorizationService authorization;
    private MockMvc gitMvc;

    @BeforeEach
    void setUp() {
        TenantContext.set("tenant-a");
        saveService = mock(TimelineRevisionSaveService.class);
        patchService = mock(TimelinePatchApplicationService.class);
        authorization = mock(TimelineProjectAuthorizationService.class);
        when(authorization.requireWrite("tenant-a", "project-a")).thenReturn(actor());
        when(authorization.requireRead("tenant-a", "project-a")).thenReturn(actor());
        var controller = new TimelineGitV1Controller(
                saveService,
                mock(TimelineRevisionQueryService.class),
                mock(RenderJobRevisionPinningService.class),
                new TimelineContentDigester(),
                mock(TimelineRevisionDiffQuery.class),
                patchService,
                authorization,
                () -> Optional.of(actor()));
        gitMvc = mvc(controller);
    }

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    @Test
    void saveCanonicalSemanticRejectionIs422() throws Exception {
        when(saveService.saveRevision(any(), any(), any())).thenThrow(canonicalRejection());

        gitMvc.perform(post("/api/timeline-git/products/project-a/revisions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedCurrentRevisionId\":null,\"tracks\":[]}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("TIMELINE_CANONICAL_REJECTED"));
    }

    @Test
    void saveStaleCasIs409() throws Exception {
        when(saveService.saveRevision(any(), any(), any()))
                .thenThrow(new TimelineConflictException("project-a", "old", "new"));

        gitMvc.perform(post("/api/timeline-git/products/project-a/revisions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedCurrentRevisionId\":\"old\",\"tracks\":[]}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("TIMELINE_CONFLICT"));
    }

    @Test
    void restoreStaleCasIs409() throws Exception {
        when(saveService.restoreRevision(any(), anyString(), anyString()))
                .thenThrow(new TimelineConflictException("project-a", "old", "new"));

        gitMvc.perform(post("/api/timeline-git/products/project-a/revisions/history-1/restore")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedCurrentRevisionId\":\"old\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void patchStaleConflictIs409NotBlanket400() throws Exception {
        when(patchService.apply(any(), any())).thenReturn(PatchApplyResult.failure(
                new PatchError(PatchErrorCode.TIMELINE_PATCH_BASE_NOT_CURRENT,
                        "stale base", null, null)));

        gitMvc.perform(post("/api/timeline-git/products/project-a/patch/apply")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(patchJson()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("TIMELINE_PATCH_BASE_NOT_CURRENT"));
    }

    @Test
    void patchCanonicalPreconditionIs422() throws Exception {
        when(patchService.apply(any(), any())).thenReturn(PatchApplyResult.failure(
                new PatchError(PatchErrorCode.TIMELINE_PATCH_PRECONDITION_FAILED,
                        "semantic mismatch", null, null)));

        gitMvc.perform(post("/api/timeline-git/products/project-a/patch/apply")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(patchJson()))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void gitSaveAuthorizationDenialPrecedesCanonicalMutation() throws Exception {
        when(authorization.requireWrite("tenant-a", "project-a"))
                .thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "denied"));

        gitMvc.perform(post("/api/timeline-git/products/project-a/revisions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedCurrentRevisionId\":null,\"tracks\":[]}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(saveService, patchService);
    }

    @Test
    void gitRestoreAuthorizationDenialPrecedesHistoricalDisclosureAndMutation() throws Exception {
        when(authorization.requireWrite("tenant-a", "project-a"))
                .thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "denied"));

        gitMvc.perform(post("/api/timeline-git/products/project-a/revisions/history-1/restore")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedCurrentRevisionId\":\"old\"}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(saveService, patchService);
    }

    @Test
    void gitPatchAuthorizationDenialPrecedesPatchHydrationAndMutation() throws Exception {
        when(authorization.requireWrite("tenant-a", "project-a"))
                .thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "denied"));

        gitMvc.perform(post("/api/timeline-git/products/project-a/patch/apply")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(patchJson()))
                .andExpect(status().isForbidden());
        verifyNoInteractions(saveService, patchService);
    }

    @Test
    void standaloneSnapshotSurfaceSemanticRejectionIs422() throws Exception {
        var snapshotController = new TimelineSnapshotController(
                saveService,
                mock(TimelineRevisionQueryService.class),
                mock(com.example.platform.render.app.timeline.TimelineConversionService.class),
                authorization);
        MockMvc snapshotMvc = mvc(snapshotController);
        when(saveService.saveRevision(any(), any(), any())).thenThrow(canonicalRejection());
        String payload = com.example.platform.timeline.app.TimelineDocumentJsonSerializer.serialize(
                new com.example.platform.timeline.canonical.TimelineDocument(
                        com.example.platform.timeline.canonical.TimelineDocument.CURRENT_SCHEMA_VERSION,
                        java.util.List.of(),
                        com.example.platform.timeline.canonical.TimelineMetadata.empty()));

        snapshotMvc.perform(post("/api/render/timeline-snapshots")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"projectId\":\"project-a\",\"payloadJson\":"
                                + new com.fasterxml.jackson.databind.ObjectMapper()
                                        .writeValueAsString(payload) + "}"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void snapshotAuthorizationDenialPrecedesPayloadImportAndMutation() throws Exception {
        var conversion = mock(com.example.platform.render.app.timeline.TimelineConversionService.class);
        var snapshotController = new TimelineSnapshotController(
                saveService,
                mock(TimelineRevisionQueryService.class),
                conversion,
                authorization);
        when(authorization.requireWrite("tenant-a", "project-a"))
                .thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "denied"));

        mvc(snapshotController).perform(post("/api/render/timeline-snapshots")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"projectId\":\"project-a\",\"payloadJson\":\"{}\"}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(saveService, conversion);
    }

    @Test
    void mergeStaleCasIs409ThroughGlobalContract() throws Exception {
        var mergeEngine = mock(com.example.platform.timeline.app.TimelineMergeEngine.class);
        when(mergeEngine.merge(any(com.example.platform.timeline.diff.merge.TimelineMergeRequest.class)))
                .thenThrow(new TimelineConflictException("project-a", "target-old", "target-new"));
        var controller = new TimelineRevisionController(
                mock(TimelineRevisionQueryService.class),
                mock(TimelineRevisionDiffQuery.class),
                mergeEngine,
                mock(com.example.platform.render.app.event.TimelineReviewEventPublisher.class),
                null,
                null,
                saveService,
                mock(com.example.platform.timeline.app.TimelinePayloadCodec.class),
                authorization);

        mvc(controller).perform(post("/api/render/projects/project-a/timeline/revisions/merge")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "baseRevisionId": "base-1",
                                  "sourceRevisionId": "source-1",
                                  "targetRevisionId": "target-old",
                                  "message": "merge",
                                  "resolutions": []
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("TIMELINE_CONFLICT"));
    }

    @Test
    void mergeAuthorizationDenialPrecedesMergeHydrationAndMutation() throws Exception {
        var mergeEngine = mock(com.example.platform.timeline.app.TimelineMergeEngine.class);
        var controller = new TimelineRevisionController(
                mock(TimelineRevisionQueryService.class),
                mock(TimelineRevisionDiffQuery.class),
                mergeEngine,
                mock(com.example.platform.render.app.event.TimelineReviewEventPublisher.class),
                null,
                null,
                saveService,
                mock(com.example.platform.timeline.app.TimelinePayloadCodec.class),
                authorization);
        when(authorization.requireWrite("tenant-a", "project-a"))
                .thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "denied"));

        mvc(controller).perform(post("/api/render/projects/project-a/timeline/revisions/merge")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"baseRevisionId":"base-1","sourceRevisionId":"source-1",
                                 "targetRevisionId":"target-1","message":"merge","resolutions":[]}
                                """))
                .andExpect(status().isForbidden());
        verifyNoInteractions(mergeEngine, saveService);
    }

    private static MockMvc mvc(Object controller) {
        return MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler(Optional.<SentryMonitoringService>empty()))
                .build();
    }

    private static CanonicalActor actor() {
        return CanonicalActor.user("server-author", "tenant-a", Set.of(), "test");
    }

    private static TimelineCanonicalRejectionException canonicalRejection() {
        return new TimelineCanonicalRejectionException(
                new TimelineCanonicalRejectionException.AdapterDiagnostic(
                        TimelineCanonicalRejectionException.Code.TIMELINE_SOURCE_REF_INVALID,
                        TimelineModelPath.root().field("sourceBinding"),
                        "invalid source pin"));
    }

    private static String patchJson() {
        return """
                {
                  "patchVersion": "1.0",
                  "patchId": "patch-1",
                  "baseRevisionId": "revision-1",
                  "baseContentDigest": "%s",
                  "expectedCurrentRevisionId": "revision-1",
                  "timelineSchemaVersion": "timeline-1.0",
                  "operations": []
                }
                """.formatted("a".repeat(64));
    }
}
