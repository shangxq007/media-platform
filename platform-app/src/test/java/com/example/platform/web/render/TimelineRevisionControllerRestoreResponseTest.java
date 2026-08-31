package com.example.platform.web.render;

import com.example.platform.timeline.app.TimelinePayloadCodec;
import com.example.platform.timeline.app.TimelineRevisionSaveService;
import com.example.platform.timeline.app.TimelineRevisionQueryService;
import com.example.platform.timeline.app.TimelineRevisionDiffQuery;
import com.example.platform.timeline.app.TimelineMutationContext;
import com.example.platform.shared.web.TenantContext;
import com.example.platform.timeline.version.TimelineRevision;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * CFRH-I1 RESTORE RESPONSE SEMANTICS (correction review blocker):
 *
 * RestoreResponse must carry:
 *   - canonicalTimelineJson = the restored revision's TimelineDocument payload
 *   - editorTimelineJson    = TimelinePayloadCodec.toEditorJson(canonical payload)
 *
 * The two fields MUST NOT be aliased. A distinguishable internal fixture whose
 * editor projection differs is used so field inversion / aliasing is caught.
 */
class TimelineRevisionControllerRestoreResponseTest {

    private static final String CANONICAL_FIXTURE =
            com.example.platform.timeline.app.TimelineDocumentJsonSerializer.serialize(
                    new com.example.platform.timeline.canonical.TimelineDocument(
                            com.example.platform.timeline.canonical.TimelineDocument.CURRENT_SCHEMA_VERSION,
                            List.of(),
                            com.example.platform.timeline.canonical.TimelineMetadata.empty()));

    // The editor projection is deliberately distinct (editor-2.0 marker).
    private static final String EDITOR_PROJECTION =
            "{\"schemaVersion\":\"2.0.0\",\"id\":\"tl-restore-editor\",\"layers\":[],\"clips\":[]}";

    private static TimelineRevisionQueryService.RevisionInfo revisionInfo() {
        return new TimelineRevisionQueryService.RevisionInfo(
                "trev_restored", "prj_r", "ten_r", null, 4, "snap_r", 3,
                "hash", "timeline-1.0", "restore", "user-1", null,
                "Restored from revision #2", List.of(), "{}", null, false, null, null, null);
    }

    private static TimelineRevisionQueryService.RevisionInfo headInfo() {
        return new TimelineRevisionQueryService.RevisionInfo(
                "trev_current", "prj_r", "ten_r", null, 3, "snap_current", 2,
                "head-hash", "timeline-1.0", "api", "user-1", null,
                null, List.of(), null, null, false, null, null, null);
    }

    private static TimelineRevision validRestoredRevision(String expectedCurrent) {
        var effectRef = new com.example.platform.timeline.semantics.effect.EffectSemanticSnapshotAuthority(
                new com.example.platform.timeline.semantics.effect.EffectDefinitionVersionRegistry.InMemory(),
                new com.example.platform.timeline.semantics.effect.EffectSemanticSnapshotStore.InMemory())
                .mintEmpty()
                .reference();
        String revisionSemanticDigest = "revision-semantic-digest";
        var ctx = new com.example.platform.timeline.version.TimelineRevisionSemanticContext(
                "timeline-digest",
                effectRef,
                revisionSemanticDigest,
                com.example.platform.timeline.version.TimelineRevisionSemanticContext.REVISION_SEMANTICS_V1);
        return new TimelineRevision("trev_restored", "prj_r", expectedCurrent,
                "1.0", null, revisionSemanticDigest, java.time.Instant.now(), "user-1", ctx);
    }

    private static TimelineMutationContext mutation() {
        return new TimelineMutationContext(
                "ten_r", "prj_r",
                com.example.platform.shared.authorization.CanonicalActor.user(
                        "user-1", "ten_r", java.util.Set.of(), "test"));
    }

    private static TimelineRevisionController controller(
            TimelineRevisionQueryService revisionQueryService,
            TimelineRevisionSaveService saveService,
            TimelinePayloadCodec codec) {
        TimelineProjectAuthorizationService authorization = mock(TimelineProjectAuthorizationService.class);
        when(authorization.requireWrite(anyString(), anyString())).thenReturn(
                com.example.platform.shared.authorization.CanonicalActor.user(
                        "user-1", "ten_r", java.util.Set.of(), "test"));
        return new TimelineRevisionController(
                revisionQueryService,
                mock(TimelineRevisionDiffQuery.class),
                null,
                mock(com.example.platform.render.app.event.TimelineReviewEventPublisher.class),
                null, null,
                saveService, codec, authorization);
    }

    @Test
    void restoreResponseCarriesDistinctInternalAndEditorProjection() {
        TimelineRevisionQueryService revisionQueryService = mock(TimelineRevisionQueryService.class);
        TimelineRevisionSaveService saveService = mock(TimelineRevisionSaveService.class);
        TimelinePayloadCodec codec = mock(TimelinePayloadCodec.class);
        when(revisionQueryService.findHead("prj_r", "ten_r")).thenReturn(Optional.of(headInfo()));
        when(saveService.restoreRevision(mutation(), "trev_restored", "trev_current"))
                .thenReturn(validRestoredRevision("trev_current"));
        when(revisionQueryService.getDetail(eq("prj_r"), eq("ten_r"), eq("trev_restored")))
                .thenReturn(Optional.of(new TimelineRevisionQueryService.RevisionDetail(revisionInfo(), null, null)));
        when(revisionQueryService.getRevisionSnapshotPayload("prj_r", "ten_r", "trev_restored"))
                .thenReturn(Optional.of(new TimelineRevisionQueryService.RevisionSnapshotPayload(
                        "snap_r", CANONICAL_FIXTURE, "timeline-1.0")));
        when(codec.toEditorJson(CANONICAL_FIXTURE)).thenReturn(EDITOR_PROJECTION);

        TenantContext.set("ten_r");
        var c = controller(revisionQueryService, saveService, codec);
        var response = c.restore("prj_r", "trev_restored");

        assertEquals(org.springframework.http.HttpStatus.CREATED, response.getStatusCode());
        var body = response.getBody();
        assertNotNull(body);
        assertEquals("trev_restored", body.newRevision().id());
        assertEquals(CANONICAL_FIXTURE, body.canonicalTimelineJson(),
                "canonicalTimelineJson must be the restored TimelineDocument payload");
        assertEquals(EDITOR_PROJECTION, body.editorTimelineJson(),
                "editorTimelineJson must be the editor projection of the internal payload");
        assertNotEquals(body.editorTimelineJson(), body.canonicalTimelineJson(),
                "RESTORE_RESPONSE_FIELD_ALIASING_COUNT must be 0 (fields must not be aliased)");
    }

    @Test
    void editorProjectionIsProducedThroughTimelinePayloadCodec() {
        TimelineRevisionQueryService revisionQueryService = mock(TimelineRevisionQueryService.class);
        TimelineRevisionSaveService saveService = mock(TimelineRevisionSaveService.class);
        TimelinePayloadCodec codec = mock(TimelinePayloadCodec.class);
        when(revisionQueryService.findHead("prj_r", "ten_r")).thenReturn(Optional.of(headInfo()));
        when(saveService.restoreRevision(mutation(), "trev_restored", "trev_current"))
                .thenReturn(validRestoredRevision("trev_current"));
        when(revisionQueryService.getDetail(eq("prj_r"), eq("ten_r"), eq("trev_restored")))
                .thenReturn(Optional.of(new TimelineRevisionQueryService.RevisionDetail(revisionInfo(), null, null)));
        when(revisionQueryService.getRevisionSnapshotPayload("prj_r", "ten_r", "trev_restored"))
                .thenReturn(Optional.of(new TimelineRevisionQueryService.RevisionSnapshotPayload(
                        "snap_r", CANONICAL_FIXTURE, "timeline-1.0")));
        when(codec.toEditorJson(CANONICAL_FIXTURE)).thenReturn(EDITOR_PROJECTION);

        TenantContext.set("ten_r");
        var c = controller(revisionQueryService, saveService, codec);
        c.restore("prj_r", "trev_restored");

        // the exact restored internal payload must be supplied to the projection port
        verify(codec).toEditorJson(CANONICAL_FIXTURE);
    }

    @Test
    void restoreUsesCanonicalSaveServiceExactlyOnce() {
        TimelineRevisionQueryService revisionQueryService = mock(TimelineRevisionQueryService.class);
        TimelineRevisionSaveService saveService = mock(TimelineRevisionSaveService.class);
        TimelinePayloadCodec codec = mock(TimelinePayloadCodec.class);
        when(revisionQueryService.findHead("prj_r", "ten_r")).thenReturn(Optional.of(headInfo()));
        when(saveService.restoreRevision(mutation(), "trev_restored", "trev_current"))
                .thenReturn(validRestoredRevision("trev_current"));
        when(revisionQueryService.getDetail(eq("prj_r"), eq("ten_r"), eq("trev_restored")))
                .thenReturn(Optional.of(new TimelineRevisionQueryService.RevisionDetail(revisionInfo(), null, null)));
        when(revisionQueryService.getRevisionSnapshotPayload("prj_r", "ten_r", "trev_restored"))
                .thenReturn(Optional.of(new TimelineRevisionQueryService.RevisionSnapshotPayload(
                        "snap_r", CANONICAL_FIXTURE, "timeline-1.0")));
        when(codec.toEditorJson(CANONICAL_FIXTURE)).thenReturn(EDITOR_PROJECTION);

        TenantContext.set("ten_r");
        TimelineProjectAuthorizationService authorization = mock(TimelineProjectAuthorizationService.class);
        when(authorization.requireWrite(anyString(), anyString())).thenReturn(
                com.example.platform.shared.authorization.CanonicalActor.user(
                        "user-1", "ten_r", java.util.Set.of(), "test"));
        var c = new TimelineRevisionController(
                revisionQueryService,
                mock(TimelineRevisionDiffQuery.class),
                null,
                mock(com.example.platform.render.app.event.TimelineReviewEventPublisher.class),
                null, null,
                saveService, codec, authorization);
        c.restore("prj_r", "trev_restored");

        // canonical restore authority called exactly once; expected-current from canonical CAS
        verify(saveService, times(1)).restoreRevision(
                mutation(), "trev_restored", "trev_current");
        verify(revisionQueryService, times(1)).findHead("prj_r", "ten_r");
        // legacy restore authority is absent by construction (CFRH-I1): the
        // TimelineRevisionService type no longer exposes a restore method, which
        // Cfrhi1LegacyWriteAuthorityGuardTest verifies structurally.
    }
}
