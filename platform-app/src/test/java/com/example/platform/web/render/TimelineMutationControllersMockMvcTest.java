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
import com.example.platform.timeline.api.composition.TimelineCanonicalRejectionException;
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
    void directGitSaveIsRetiredBeforeCanonicalMutation() throws Exception {
        gitMvc.perform(post("/api/timeline-git/products/project-a/revisions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedCurrentRevisionId\":null,\"tracks\":[]}"))
                .andExpect(status().isGone());
        verifyNoInteractions(saveService, patchService);
    }

    @Test
    void directGitSaveCannotBypassOperationWithAnExpectedHead() throws Exception {
        gitMvc.perform(post("/api/timeline-git/products/project-a/revisions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedCurrentRevisionId\":\"old\",\"tracks\":[]}"))
                .andExpect(status().isGone());
        verifyNoInteractions(saveService, patchService);
    }

    @Test
    void directGitRestoreCannotBypassOperation() throws Exception {
        gitMvc.perform(post("/api/timeline-git/products/project-a/revisions/history-1/restore")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedCurrentRevisionId\":\"old\"}"))
                .andExpect(status().isGone());
        verifyNoInteractions(saveService, patchService);
    }

    @Test
    void directGitPatchApplyCannotBypassOperation() throws Exception {
        gitMvc.perform(post("/api/timeline-git/products/project-a/patch/apply")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(patchJson()))
                .andExpect(status().isGone());
        verifyNoInteractions(saveService, patchService);
    }

    @Test
    void directGitPatchApplyCannotUseAnAlternatePrecondition() throws Exception {
        gitMvc.perform(post("/api/timeline-git/products/project-a/patch/apply")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(patchJson()))
                .andExpect(status().isGone());
        verifyNoInteractions(saveService, patchService);
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
        var invocations = mock(com.example.platform.operation.invocation.OperationInvocationPort.class);
        var snapshotController = new TimelineSnapshotController(
                saveService,
                mock(TimelineRevisionQueryService.class),
                authorization, invocations);
        MockMvc snapshotMvc = mvc(snapshotController);
        when(invocations.invoke(any(), any())).thenThrow(canonicalRejection());
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
    void snapshotImportUsesTypedOperationAndReturnsTheCommittedRevision() throws Exception {
        var invocations = mock(com.example.platform.operation.invocation.OperationInvocationPort.class);
        var queries = mock(com.example.platform.timeline.api.revision.TimelineRevisionQueries.class);
        var info = mock(com.example.platform.timeline.api.revision.TimelineRevisionQueries.RevisionInfo.class);
        when(info.snapshotId()).thenReturn("snapshot-1");
        when(queries.findById("project-a", "tenant-a", "revision-1")).thenReturn(Optional.of(info));
        when(invocations.invoke(any(), any())).thenAnswer(call -> {
            com.example.platform.operation.operation.OperationRequest request = call.getArgument(0);
            com.example.platform.operation.invocation.OperationInvocationContext context = call.getArgument(1);
            return new com.example.platform.operation.invocation.OperationInvocationResult.Applied(
                    request.definitionId(), request.version(), "digest", request.baseRevisionId(),
                    "revision-1", "timeline-hash", context.invocationId(), null);
        });
        var controller = new TimelineSnapshotController(saveService, queries,
                authorization, invocations);
        String payload = com.example.platform.timeline.app.TimelineDocumentJsonSerializer.serialize(
                new com.example.platform.timeline.canonical.TimelineDocument(
                        com.example.platform.timeline.canonical.TimelineDocument.CURRENT_SCHEMA_VERSION,
                        java.util.List.of(), com.example.platform.timeline.canonical.TimelineMetadata.empty()));
        mvc(controller).perform(post("/api/render/timeline-snapshots")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"projectId\":\"project-a\",\"payloadJson\":"
                        + new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(payload) + "}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.revisionId").value("revision-1"));
        var request = org.mockito.ArgumentCaptor.forClass(
                com.example.platform.operation.operation.OperationRequest.class);
        org.mockito.Mockito.verify(invocations).invoke(request.capture(), any());
        org.junit.jupiter.api.Assertions.assertEquals(
                com.example.platform.operation.operation.OperationDefinition.V3.IMPORT_TIMELINE.definitionId(),
                request.getValue().definitionId());
        verifyNoInteractions(saveService);
    }

    @Test
    void snapshotAuthorizationDenialPrecedesPayloadImportAndMutation() throws Exception {
        var invocations = mock(com.example.platform.operation.invocation.OperationInvocationPort.class);
        var snapshotController = new TimelineSnapshotController(
                saveService,
                mock(TimelineRevisionQueryService.class),
                authorization, invocations);
        when(authorization.requireWrite("tenant-a", "project-a"))
                .thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "denied"));

        mvc(snapshotController).perform(post("/api/render/timeline-snapshots")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"projectId\":\"project-a\",\"payloadJson\":\"{}\"}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(saveService, invocations);
    }

    @Test
    void directMergeCannotBypassOperation() throws Exception {
        var mergeEngine = mock(com.example.platform.timeline.app.TimelineMergeEngine.class);
        var controller = new TimelineRevisionController(
                mock(TimelineRevisionQueryService.class),
                mock(TimelineRevisionDiffQuery.class),
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
                .andExpect(status().isGone());
        verifyNoInteractions(mergeEngine, saveService);
    }

    @Test
    void directRevisionRestoreCannotBypassOperation() throws Exception {
        var mergeEngine = mock(com.example.platform.timeline.app.TimelineMergeEngine.class);
        var queries = mock(TimelineRevisionQueryService.class);
        var controller = new TimelineRevisionController(queries,
                mock(TimelineRevisionDiffQuery.class),
                authorization);
        mvc(controller).perform(post("/api/render/projects/project-a/timeline/revisions/history-1/restore"))
                .andExpect(status().isGone());
        verifyNoInteractions(queries, mergeEngine, saveService);
    }

    @Test
    void mergeAuthorizationDenialPrecedesMergeHydrationAndMutation() throws Exception {
        var mergeEngine = mock(com.example.platform.timeline.app.TimelineMergeEngine.class);
        var controller = new TimelineRevisionController(
                mock(TimelineRevisionQueryService.class),
                mock(TimelineRevisionDiffQuery.class),
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
