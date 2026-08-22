package com.example.platform.web.render;

import com.example.platform.timeline.app.ProductCurrentRevisionService;
import com.example.platform.timeline.app.TimelinePayloadCodec;
import com.example.platform.timeline.app.TimelineRevisionSaveService;
import com.example.platform.timeline.app.TimelineRevisionQueryService;
import com.example.platform.timeline.app.TimelineRevisionDiffQuery;
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
 *   - internalTimelineJson = the restored revision's internal timeline payload
 *   - editorTimelineJson   = TimelinePayloadCodec.toEditorJson(internal payload)
 *
 * The two fields MUST NOT be aliased. A distinguishable internal fixture whose
 * editor projection differs is used so field inversion / aliasing is caught.
 */
class TimelineRevisionControllerRestoreResponseTest {

    // A valid internal-1.0 timeline whose editor projection differs visibly.
    private static final String INTERNAL_FIXTURE =
            "{\"schemaVersion\":\"1.0\",\"id\":\"tl-restore\",\"revision\":3,"
                    + "\"composition\":{\"tracks\":[{\"id\":\"t1\",\"type\":\"VIDEO\","
                    + "\"clips\":[{\"id\":\"c1\",\"assetId\":\"ast-1\","
                    + "\"timelineRange\":{\"start\":{\"frame\":0,\"rate\":{\"num\":30,\"den\":1}},"
                    + "\"duration\":{\"frame\":30,\"rate\":{\"num\":30,\"den\":1}}},"
                    + "\"sourceRange\":{\"start\":{\"frame\":0,\"rate\":{\"num\":30,\"den\":1}},"
                    + "\"duration\":{\"frame\":30,\"rate\":{\"num\":30,\"den\":1}}},"
                    + "\"sourceBinding\":{\"sourceKind\":\"MEDIA_STREAM\",\"mediaStreamId\":\"stream-1\"}}]}]}}";

    // The editor projection is deliberately distinct (editor-2.0 marker).
    private static final String EDITOR_PROJECTION =
            "{\"schemaVersion\":\"2.0.0\",\"id\":\"tl-restore-editor\",\"layers\":[],\"clips\":[]}";

    private static TimelineRevisionQueryService.RevisionInfo revisionInfo() {
        return new TimelineRevisionQueryService.RevisionInfo(
                "trev_restored", "prj_r", "ten_r", null, 4, "snap_r", 3,
                "hash", "internal-1.0", "restore", "user-1", null,
                "Restored from revision #2", List.of(), "{}", null, false, null, null, null);
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

    private static TimelineRevisionController controller(
            TimelineRevisionQueryService revisionQueryService,
            TimelineRevisionSaveService saveService,
            ProductCurrentRevisionService currentService,
            TimelinePayloadCodec codec) {
        return new TimelineRevisionController(
                revisionQueryService,
                mock(TimelineRevisionDiffQuery.class),
                null,
                mock(com.example.platform.render.app.event.TimelineReviewEventPublisher.class),
                null, null,
                saveService, currentService, codec);
    }

    @Test
    void restoreResponseCarriesDistinctInternalAndEditorProjection() {
        TimelineRevisionQueryService revisionQueryService = mock(TimelineRevisionQueryService.class);
        TimelineRevisionSaveService saveService = mock(TimelineRevisionSaveService.class);
        ProductCurrentRevisionService currentService = mock(ProductCurrentRevisionService.class);
        TimelinePayloadCodec codec = mock(TimelinePayloadCodec.class);
        when(currentService.getCurrentRevisionId("prj_r")).thenReturn("trev_current");
        when(saveService.restoreRevision("prj_r", "trev_restored", "trev_current", "user-1"))
                .thenReturn(validRestoredRevision("trev_current"));
        when(revisionQueryService.getDetail(eq("prj_r"), eq("ten_r"), eq("trev_restored")))
                .thenReturn(Optional.of(new TimelineRevisionQueryService.RevisionDetail(revisionInfo(), null, null)));
        when(revisionQueryService.getRevisionSnapshotPayload("prj_r", "ten_r", "trev_restored"))
                .thenReturn(Optional.of(new TimelineRevisionQueryService.RevisionSnapshotPayload(
                        "snap_r", INTERNAL_FIXTURE, "internal-1.0")));
        when(codec.toEditorJson(INTERNAL_FIXTURE)).thenReturn(EDITOR_PROJECTION);

        TenantContext.set("ten_r");
        var c = controller(revisionQueryService, saveService, currentService, codec);
        var response = c.restore("prj_r", "trev_restored", "user-1");

        assertEquals(org.springframework.http.HttpStatus.CREATED, response.getStatusCode());
        var body = response.getBody();
        assertNotNull(body);
        assertEquals("trev_restored", body.newRevision().id());
        assertEquals(INTERNAL_FIXTURE, body.internalTimelineJson(),
                "internalTimelineJson must be the restored internal payload");
        assertEquals(EDITOR_PROJECTION, body.editorTimelineJson(),
                "editorTimelineJson must be the editor projection of the internal payload");
        assertNotEquals(body.editorTimelineJson(), body.internalTimelineJson(),
                "RESTORE_RESPONSE_FIELD_ALIASING_COUNT must be 0 (fields must not be aliased)");
    }

    @Test
    void editorProjectionIsProducedThroughTimelinePayloadCodec() {
        TimelineRevisionQueryService revisionQueryService = mock(TimelineRevisionQueryService.class);
        TimelineRevisionSaveService saveService = mock(TimelineRevisionSaveService.class);
        ProductCurrentRevisionService currentService = mock(ProductCurrentRevisionService.class);
        TimelinePayloadCodec codec = mock(TimelinePayloadCodec.class);
        when(currentService.getCurrentRevisionId("prj_r")).thenReturn("trev_current");
        when(saveService.restoreRevision("prj_r", "trev_restored", "trev_current", "user-1"))
                .thenReturn(validRestoredRevision("trev_current"));
        when(revisionQueryService.getDetail(eq("prj_r"), eq("ten_r"), eq("trev_restored")))
                .thenReturn(Optional.of(new TimelineRevisionQueryService.RevisionDetail(revisionInfo(), null, null)));
        when(revisionQueryService.getRevisionSnapshotPayload("prj_r", "ten_r", "trev_restored"))
                .thenReturn(Optional.of(new TimelineRevisionQueryService.RevisionSnapshotPayload(
                        "snap_r", INTERNAL_FIXTURE, "internal-1.0")));
        when(codec.toEditorJson(INTERNAL_FIXTURE)).thenReturn(EDITOR_PROJECTION);

        TenantContext.set("ten_r");
        var c = controller(revisionQueryService, saveService, currentService, codec);
        c.restore("prj_r", "trev_restored", "user-1");

        // the exact restored internal payload must be supplied to the projection port
        verify(codec).toEditorJson(INTERNAL_FIXTURE);
    }

    @Test
    void restoreUsesCanonicalSaveServiceExactlyOnce() {
        TimelineRevisionQueryService revisionQueryService = mock(TimelineRevisionQueryService.class);
        TimelineRevisionSaveService saveService = mock(TimelineRevisionSaveService.class);
        ProductCurrentRevisionService currentService = mock(ProductCurrentRevisionService.class);
        TimelinePayloadCodec codec = mock(TimelinePayloadCodec.class);
        when(currentService.getCurrentRevisionId("prj_r")).thenReturn("trev_current");
        when(saveService.restoreRevision("prj_r", "trev_restored", "trev_current", "user-1"))
                .thenReturn(validRestoredRevision("trev_current"));
        when(revisionQueryService.getDetail(eq("prj_r"), eq("ten_r"), eq("trev_restored")))
                .thenReturn(Optional.of(new TimelineRevisionQueryService.RevisionDetail(revisionInfo(), null, null)));
        when(revisionQueryService.getRevisionSnapshotPayload("prj_r", "ten_r", "trev_restored"))
                .thenReturn(Optional.of(new TimelineRevisionQueryService.RevisionSnapshotPayload(
                        "snap_r", INTERNAL_FIXTURE, "internal-1.0")));
        when(codec.toEditorJson(INTERNAL_FIXTURE)).thenReturn(EDITOR_PROJECTION);

        TenantContext.set("ten_r");
        var c = new TimelineRevisionController(
                revisionQueryService,
                mock(TimelineRevisionDiffQuery.class),
                null,
                mock(com.example.platform.render.app.event.TimelineReviewEventPublisher.class),
                null, null,
                saveService, currentService, codec);
        c.restore("prj_r", "trev_restored", "user-1");

        // canonical restore authority called exactly once; expected-current from canonical CAS
        verify(saveService, times(1)).restoreRevision("prj_r", "trev_restored", "trev_current", "user-1");
        verify(currentService, times(1)).getCurrentRevisionId("prj_r");
        // legacy restore authority is absent by construction (CFRH-I1): the
        // TimelineRevisionService type no longer exposes a restore method, which
        // Cfrhi1LegacyWriteAuthorityGuardTest verifies structurally.
    }
}
