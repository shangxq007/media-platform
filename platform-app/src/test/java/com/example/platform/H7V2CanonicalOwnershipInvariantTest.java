package com.example.platform;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.platform.artifact.app.ArtifactPinService;
import com.example.platform.artifact.app.ArtifactRelationRepository;
import com.example.platform.artifact.infrastructure.ArtifactPinRepository;
import com.example.platform.artifact.infrastructure.ArtifactRepository;
import com.example.platform.artifact.infrastructure.JooqArtifactQueryService;
import com.example.platform.shared.time.MediaTime;
import com.example.platform.timeline.adapter.JdbcEffectDefinitionVersionRegistry;
import com.example.platform.timeline.adapter.JdbcEffectSemanticSnapshotStore;
import com.example.platform.timeline.adapter.JdbcTimelineRevisionSemanticContextStore;
import com.example.platform.timeline.adapter.TimelineRevisionRepository;
import com.example.platform.timeline.adapter.TimelineSnapshotService;
import com.example.platform.timeline.app.DefaultTimelineRevisionPersistence;
import com.example.platform.timeline.app.PatchApplyResult;
import com.example.platform.timeline.app.PatchPreviewResult;
import com.example.platform.timeline.app.TimelineArtifactPinValidator;
import com.example.platform.timeline.app.TimelineDocumentJsonSerializer;
import com.example.platform.timeline.app.TimelineMergeEngine;
import com.example.platform.timeline.app.TimelineMutationContext;
import com.example.platform.timeline.app.TimelinePatchApplicationService;
import com.example.platform.timeline.app.ProjectRevisionNumberAllocator;
import com.example.platform.timeline.app.TimelineRevisionCommandConflictException;
import com.example.platform.timeline.app.TimelineRevisionDiffQuery;
import com.example.platform.timeline.app.TimelineRevisionDiffService;
import com.example.platform.timeline.app.TimelineRevisionRefHeadUpdateAdapter;
import com.example.platform.timeline.app.TimelineRevisionRefMutation;
import com.example.platform.timeline.app.TimelineRevisionSaveService;
import com.example.platform.timeline.canonical.TimelineClip;
import com.example.platform.timeline.canonical.TimelineContentDigester;
import com.example.platform.timeline.canonical.TimelineDocument;
import com.example.platform.timeline.canonical.TimelineMetadata;
import com.example.platform.timeline.canonical.TimelineTrack;
import com.example.platform.timeline.canonical.TrackType;
import com.example.platform.timeline.diff.application.TimelinePatchApplier;
import com.example.platform.timeline.diff.merge.TimelineMergeConflictDetector;
import com.example.platform.timeline.diff.merge.TimelineMergeRequest;
import com.example.platform.timeline.diff.merge.TimelineMergeResult;
import com.example.platform.timeline.diff.merge.plan.TimelineNonConflictingMergePlanner;
import com.example.platform.timeline.diff.merge.preview.TimelineMergePreviewService;
import com.example.platform.timeline.patch.TimelinePatch;
import com.example.platform.timeline.patch.TimelinePatchOperation;
import com.example.platform.timeline.revisioncommand.RevisionRef;
import com.example.platform.timeline.semantics.effect.EffectSemanticSnapshotAuthority;
import com.example.platform.timeline.version.TimelineConflictException;
import com.example.platform.timeline.version.TimelineRevision;
import java.sql.Connection;
import java.sql.ResultSet;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * H7 V2 ownership and production-path matrix against PostgreSQL 16 and the exact
 * classpath Flyway V1 schema.
 *
 * <p>HTTP authorization preparation is intentionally not faked here. Exact coverage is
 * {@code TimelineProjectAuthorizationServiceTest.tenantMismatchFailsBeforeAuthorizationDecision},
 * {@code TimelineControllerAuthorizationPreparationTest.currentControllerRejectsBeforeHistoryDisclosure},
 * and {@code TimelineControllerAuthorizationPreparationTest.legacyControllerRejectsBeforeHeadDisclosure}.</p>
 */
@Testcontainers
class H7V2CanonicalOwnershipInvariantTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    private static JdbcTemplate jdbc;
    private static DataSource dataSource;
    private static DSLContext dsl;
    private static Flyway flyway;

    private TimelineContentDigester digester;
    private TimelineRevisionRefMutation revisionRefMutation;
    private TimelineSnapshotService snapshotService;
    private TimelineRevisionRepository revisionRepository;
    private TimelineRevisionSaveService saveService;
    private TimelinePatchApplicationService patchService;
    private TimelineRevisionDiffQuery diffQuery;
    private TimelineMergeEngine mergeEngine;

    @BeforeAll
    static void migrateExactCanonicalV1() {
        String schema = "public";
        String separator = POSTGRES.getJdbcUrl().contains("?") ? "&" : "?";
        String scopedUrl = POSTGRES.getJdbcUrl() + separator + "currentSchema=" + schema;
        flyway = Flyway.configure()
                .dataSource(scopedUrl, POSTGRES.getUsername(), POSTGRES.getPassword())
                .schemas(schema)
                .defaultSchema(schema)
                .createSchemas(true)
                .locations("classpath:db/migration")
                .load();
        flyway.migrate();
        var ds = new DriverManagerDataSource();
        ds.setDriverClassName(POSTGRES.getDriverClassName());
        ds.setUrl(scopedUrl);
        ds.setUsername(POSTGRES.getUsername());
        ds.setPassword(POSTGRES.getPassword());
        dataSource = ds;
        jdbc = new JdbcTemplate(ds);
        dsl = DSL.using(ds, org.jooq.SQLDialect.POSTGRES);
    }

    @BeforeEach
    void resetCanonicalRows() {
        jdbc.execute("""
                truncate table apply_command, artifact_pin, timeline_revision_parent,
                    timeline_revision_ref, timeline_revision, timeline_snapshot,
                    project_revision_counter, project, artifact, tenant cascade
                """);
        jdbc.update("insert into tenant(id,name,status,created_at) values ('ta','A','ACTIVE',now())");
        jdbc.update("insert into tenant(id,name,status,created_at) values ('tb','B','ACTIVE',now())");
        jdbc.update("insert into project(id,tenant_id,name,created_at) values ('pa','ta','A1',now())");
        jdbc.update("insert into project(id,tenant_id,name,created_at) values ('pc','ta','A2',now())");
        jdbc.update("insert into project(id,tenant_id,name,created_at) values ('pb','tb','B1',now())");

        digester = new TimelineContentDigester();
        revisionRefMutation = new TimelineRevisionRefMutation(dsl);
        snapshotService = new TimelineSnapshotService(dsl);
        revisionRepository = new TimelineRevisionRepository(dsl);
        saveService = newSaveService(
                request -> com.example.platform.shared.authorization.AuthorizationDecision.allow("test"));
        patchService = new TimelinePatchApplicationService(
                saveService, revisionRefMutation, digester);
        diffQuery = new TimelineRevisionDiffQuery(
                revisionRepository, snapshotService, new TimelineRevisionDiffService());
        var preview = new TimelineMergePreviewService(new TimelineMergeConflictDetector());
        mergeEngine = new TimelineMergeEngine(
                revisionRepository,
                snapshotService,
                saveService,
                preview,
                new TimelineNonConflictingMergePlanner(preview),
                new TimelinePatchApplier(),
                TimelineDocumentJsonSerializer.mapper(),
                new TimelineArtifactPinValidator(new JooqArtifactQueryService(
                        new ArtifactRepository(dsl), new ArtifactRelationRepository(dsl))),
                new ArtifactPinService(new ArtifactPinRepository(dsl)),
                dsl);
    }

    private TimelineRevisionSaveService newSaveService(
            com.example.platform.shared.authorization.AuthorizationDecisionPort authorization) {
        var artifactQuery = new JooqArtifactQueryService(
                new ArtifactRepository(dsl), new ArtifactRelationRepository(dsl));
        return new TimelineRevisionSaveService(
                dsl,
                revisionRefMutation,
                digester,
                snapshotService,
                new TimelineArtifactPinValidator(artifactQuery),
                new ArtifactPinService(new ArtifactPinRepository(dsl)),
                new EffectSemanticSnapshotAuthority(
                        new JdbcEffectDefinitionVersionRegistry(dsl),
                        new JdbcEffectSemanticSnapshotStore(dsl)),
                new JdbcTimelineRevisionSemanticContextStore(dsl),
                new DefaultTimelineRevisionPersistence(),
                new TimelineRevisionRefHeadUpdateAdapter(revisionRefMutation),
                authorization);
    }

    @Test
    @DisplayName("REAL_CANONICAL_V1_POSTGRES_TESTING=PASS (server major 16, exact classpath Flyway V1)")
    void postgresqlServerMajorIs16AndOnlyExactClasspathFlywayV1IsApplied() {
        Integer serverMajor = jdbc.queryForObject(
                "select current_setting('server_version_num')::integer / 10000", Integer.class);
        assertEquals(16, serverMajor, "H7 V2 matrix requires PostgreSQL server major 16");

        var applied = Arrays.stream(flyway.info().applied())
                .filter(info -> info.getVersion() != null)
                .toList();
        assertEquals(1, applied.size(), "the classpath contains exactly consolidated Flyway V1");
        assertEquals("1", applied.getFirst().getVersion().getVersion());
        assertEquals("V1__initial_schema.sql", applied.getFirst().getScript());
    }

    @Test
    @DisplayName("CONCURRENT_REVISION_ALLOCATION=PASS (independent connections, one project counter row)")
    void concurrentAllocatorUsesIndependentConnectionsAndAllocatesDistinctProjectNumbers()
            throws Exception {
        String project = createOwnedProject("p-concurrent-allocator");
        ProjectRevisionNumberAllocator allocator = new ProjectRevisionNumberAllocator();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            java.util.concurrent.Callable<Long> allocation = () -> {
                try (Connection connection = dataSource.getConnection()) {
                    connection.setAutoCommit(false);
                    DSLContext threadDsl = DSL.using(connection, org.jooq.SQLDialect.POSTGRES);
                    ready.countDown();
                    assertTrue(start.await(10, TimeUnit.SECONDS));
                    long number = allocator.allocate(threadDsl, project);
                    connection.commit();
                    return number;
                }
            };
            var first = executor.submit(allocation);
            var second = executor.submit(allocation);
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();
            assertEquals(Set.of(1L, 2L), Set.of(
                    first.get(10, TimeUnit.SECONDS),
                    second.get(10, TimeUnit.SECONDS)));
        }
        assertEquals(1, jdbc.queryForObject(
                "select count(*) from project_revision_counter where project_id = ?",
                Integer.class, project));
        assertEquals(2L, jdbc.queryForObject(
                "select next_revision_number from project_revision_counter where project_id = ?",
                Long.class, project));
    }

    @Test
    @DisplayName("FORGED_CANONICAL_AUTHOR=REJECTED_BEFORE_MUTATION")
    void forgedActorCannotSelectCanonicalRevisionAuthor() {
        String project = createOwnedProject("p-forged-author");
        TimelineRevisionSaveService guarded = newSaveService(request ->
                "server-author".equals(request.actor().actorId())
                        ? com.example.platform.shared.authorization.AuthorizationDecision.allow("test")
                        : com.example.platform.shared.authorization.AuthorizationDecision.deny(
                                "RBAC_DENY", "test"));

        assertThrows(com.example.platform.shared.authorization.AuthorizationDeniedException.class,
                () -> guarded.saveRevision(
                        mutation("ta", project, "forged-author"),
                        null,
                        document("forged", "track-forged")));

        assertEquals(0L, count("timeline_revision", project));
        assertEquals(0L, count("timeline_snapshot", project));
        assertEquals(0, jdbc.queryForObject(
                "select count(*) from timeline_revision_ref where project_id = ?",
                Integer.class, project));
    }

    @Test
    @DisplayName("PRODUCTION_JSON_ROUNDTRIP=PASS")
    void canonicalTimelineDocumentProductionSerializerPersistedJsonRepositoryReadReloadHasExactDigest() {
        String project = createOwnedProject("p-json-roundtrip");
        TimelineDocument document = documentWithClip("roundtrip", "clip-roundtrip", 0, 10);
        String expectedPayload = TimelineDocumentJsonSerializer.serializeWithCaptions(document);
        String expectedDigest = digester.digest(document);

        TimelineRevision persisted = saveRevision(
                "ta", project, null, document, "server-author");
        var revisionRow = revisionRepository.findOwnedById(
                persisted.revisionId(), project, "ta").orElseThrow();
        var snapshot = snapshotService.findOwnedById(
                project, "ta", revisionRow.snapshotId()).orElseThrow();

        assertEquals(expectedPayload, snapshot.payloadJson(),
                "production serializer bytes are the persisted snapshot JSON");
        TimelineDocument deserialized = TimelineDocumentJsonSerializer.deserialize(snapshot.payloadJson());
        assertEquals(expectedDigest, digester.digest(deserialized));
        assertEquals(expectedDigest, revisionRow.contentHash());
        assertEquals("server-author", revisionRow.authorUserId(),
                "the authenticated server actor must survive production persistence/reload exactly");
        assertCanonicalReload(persisted.revisionId(), expectedDigest);
    }

    @Test
    @DisplayName("H7_TYPED_SOURCE_BINDING_ARTIFACT_PIN_OPERATION_DIGEST_ROUNDTRIP=PASS")
    void completeH7OperationPreviewApplyReloadPreservesTypedBindingPinsAndDigests() {
        String project = createOwnedProject("p-h7-complete-roundtrip");
        String mediaAssetId = "media-h7";
        String mediaStreamId = "stream-h7-video";
        String artifactId = java.util.UUID.randomUUID().toString();
        String contentDigest = "c".repeat(64);
        jdbc.update("""
                insert into artifact(id,tenant_id,project_id,content_digest,byte_length,
                    media_type,artifact_kind,state,schema_version,created_at)
                values (?, 'ta', ?, ?, 1024, 'VIDEO', 'SOURCE_MEDIA', 'AVAILABLE', 1, now())
                """, artifactId, project, contentDigest);
        jdbc.update("""
                insert into media_asset(id,tenant_id,project_id,storage_key,media_type,
                    media_version,created_at,publish_status)
                values (?, 'ta', ?, 'source/key', 'VIDEO', 'v1', now(), 'DRAFT')
                """, mediaAssetId, project);
        jdbc.update("""
                insert into media_stream(id,media_asset_id,stream_index,stream_kind,codec,
                    timebase_num,timebase_den,rate_num,rate_den,is_vfr,width,height)
                values (?, ?, 0, 'VIDEO', 'h264', 1, 1000, 24, 1, false, 1920, 1080)
                """, mediaStreamId, mediaAssetId);

        TimelineDocument baseDocument = new TimelineDocument(
                TimelineDocument.CURRENT_SCHEMA_VERSION,
                List.of(new TimelineTrack("video-main", "Video", TrackType.VIDEO, List.of())),
                new TimelineMetadata("H7", "exact-v1", Map.of("path", "complete")));
        TimelineRevision base = saveRevision(
                "ta", project, null, baseDocument, "server-author");
        var actor = com.example.platform.shared.authorization.CanonicalActor.user(
                "server-author", "ta", Set.of("EDITOR"), "test-authenticated");
        var sourceValidator = new com.example.platform.timeline.app.TimelineSourceReferenceValidator(
                new com.example.platform.media.infrastructure.persistence.JooqMediaAssetRepository(dsl),
                new com.example.platform.media.infrastructure.persistence.JooqMediaStreamRepository(dsl));
        var operationService = new com.example.platform.render.app.operation.TimelineMediaClipOperationService(
                saveService,
                sourceValidator,
                new com.example.platform.timeline.app.InternalTimelineValidationService(),
                request -> com.example.platform.shared.authorization.AuthorizationDecision.allow("test"),
                new com.example.platform.render.app.plan.OperationPlanApplyService(saveService));
        var command = new com.example.platform.render.app.operation.AddMediaClipCommand(
                base.revisionId(), base.semanticContext().timelineContentDigest(),
                "video-main", "clip-h7", mediaAssetId, mediaStreamId,
                artifactId, contentDigest,
                "10/1", "20/1", "0/1", "10/1", 1, 1,
                com.example.platform.render.app.operation.AddMediaClipCommand.Direction.FORWARD);

        var preview = operationService.preview("ta", project, command, actor);
        assertEquals(mediaAssetId, preview.sourceBinding().mediaAssetId().value());
        assertEquals(mediaStreamId, preview.sourceBinding().mediaStreamId().value());
        assertEquals(artifactId, preview.sourceBinding().artifactId().value());
        assertEquals(contentDigest, preview.sourceBinding().contentDigest().canonicalValue());
        var typedParameters = new com.example.platform.operation.operation.OperationParameters.AddMediaClipParameters(
                "video-main",
                com.example.platform.timeline.canonical.TimelineClipId.of("clip-h7"),
                preview.sourceBinding(), preview.placement(), preview.temporalMapping());
        String parameterDigest = com.example.platform.operation.operation.ParameterDigest.compute(
                com.example.platform.operation.operation.OperationDefinition.V1.ADD_MEDIA_CLIP.definitionId(),
                com.example.platform.operation.operation.OperationDefinition.V1.ADD_MEDIA_CLIP.version(),
                typedParameters);
        String expectedFingerprint = com.example.platform.render.app.plan.OperationPlanApplyService.fingerprint(
                preview.planDigest(),
                new com.example.platform.operation.plan.TargetRevisionRef(RevisionRef.MAIN_REF),
                base.revisionId(), project, "ta", actor.actorId(),
                com.example.platform.operation.operation.OperationDefinition.V1.ADD_MEDIA_CLIP
                        .definitionId().value(),
                parameterDigest);

        var applied = operationService.authorizeAndApply(
                "ta", project, command, preview.planDigest(), "apply-h7-complete", actor);

        assertEquals("APPLIED", applied.status());
        assertEquals(preview.planDigest(), applied.planDigest());
        assertEquals(preview.candidateContentHash(), applied.newTimelineContentHash());
        TimelineDocument reloaded = assertCanonicalReload(
                applied.newRevisionId(), applied.newTimelineContentHash());
        TimelineClip clip = reloaded.getTracks().getFirst().clips().getFirst();
        assertEquals(mediaAssetId, clip.getMediaAssetId());
        assertEquals(mediaStreamId, clip.getMediaStreamId());
        assertEquals(artifactId, clip.getArtifactId());
        assertEquals(contentDigest, clip.getContentDigest());
        assertEquals(1, jdbc.queryForObject(
                "select count(*) from artifact_pin where tenant_id='ta' and project_id=? "
                        + "and revision_id=? and artifact_id=? and content_digest=?",
                Integer.class, project, applied.newRevisionId(), artifactId, contentDigest));
        assertEquals(preview.planDigest(), jdbc.queryForObject(
                "select plan_digest from apply_command where apply_command_id='apply-h7-complete'",
                String.class));
        assertEquals(expectedFingerprint, jdbc.queryForObject(
                "select fingerprint from apply_command where apply_command_id='apply-h7-complete'",
                String.class));
        assertEquals(actor.actorId(), jdbc.queryForObject(
                "select author_user_id from timeline_revision where id=?",
                String.class, applied.newRevisionId()));
    }

    @Test
    @DisplayName("SAVE_REVISION_H7_CANONICAL_RELOAD=PASS")
    void saveRevisionCanonicalReloadHasExactContentDigest() {
        String project = createOwnedProject("p-save-reload");
        TimelineDocument document = document("saved", "track-saved");

        TimelineRevision saved = saveRevision(
                "ta", project, null, document, "server-author");

        assertCanonicalReload(saved.revisionId(), digester.digest(document));
        assertEquals(saved.revisionId(), revisionRefMutation.currentHead(RevisionRef.main("ta", project)));
    }

    @Test
    @DisplayName("PATCH_REVISION_H7_CANONICAL_RELOAD=PASS (preview -> apply -> reload)")
    void patchCreatedRevisionCanonicalReloadMatchesPreviewAndApplyDigest() {
        String project = createOwnedProject("p-patch-reload");
        TimelineDocument baseDocument = document("patch-base", "track-base");
        TimelineRevision base = saveRevision(
                "ta", project, null, baseDocument, "server-author");
        TimelinePatch patch = new TimelinePatch(
                TimelinePatch.CURRENT_PATCH_VERSION,
                "patch-exact-v1",
                project,
                base.revisionId(),
                base.semanticContext().timelineContentDigest(),
                base.revisionId(),
                TimelineDocument.CURRENT_SCHEMA_VERSION,
                List.of(new TimelinePatchOperation.AddTrack(
                        "add-patched-track",
                        new TimelineTrack("track-patched", "Patched", TrackType.AUDIO, List.of()),
                        1)),
                null,
                null);

        PatchPreviewResult preview = patchService.preview("ta", patch);
        assertTrue(preview instanceof PatchPreviewResult.Success);
        PatchApplyResult apply = patchService.apply(mutation("ta", project, "server-author"), patch);
        assertTrue(apply instanceof PatchApplyResult.Success);
        var success = (PatchApplyResult.Success) apply;
        assertEquals(((PatchPreviewResult.Success) preview).resultDigest(), success.resultDigest());

        TimelineDocument reloaded = assertCanonicalReload(
                success.newRevisionId(), success.resultDigest());
        assertEquals(Set.of("track-base", "track-patched"), reloaded.getTracks().stream()
                .map(TimelineTrack::trackId).collect(java.util.stream.Collectors.toSet()));
    }

    @Test
    @DisplayName("RESTORE_REVISION_H7_CANONICAL_RELOAD=PASS; RESTORE_PARENT_ORDER=PASS")
    void restoreCreatedRevisionCanonicalReloadAndParentZeroIsPreRestoreMainHead() {
        String project = createOwnedProject("p-restore-reload");
        TimelineDocument historicalDocument = document("historical", "track-historical");
        TimelineRevision historical = saveRevision(
                "ta", project, null, historicalDocument, "server-author");
        TimelineRevision preRestoreHead = saveRevision(
                "ta", project, historical.revisionId(),
                document("current", "track-current"), "server-author");

        TimelineRevision restored = restoreRevision(
                "ta", project, historical.revisionId(), preRestoreHead.revisionId(), "server-author");

        assertCanonicalReload(restored.revisionId(), digester.digest(historicalDocument));
        assertEquals(List.of(preRestoreHead.revisionId()), orderedParents(restored.revisionId()));
        assertEquals(preRestoreHead.revisionId(), restored.parentRevisionId());
        assertEquals(restored.revisionId(), revisionRefMutation.currentHead(RevisionRef.main("ta", project)));
    }

    @Test
    @DisplayName("MERGE_REVISION_H7_CANONICAL_RELOAD=PASS; MERGE_PARENT_ORDER=PASS")
    void mergeCreatedRevisionCanonicalReloadAndParentsAreTargetThenSource() {
        String project = createOwnedProject("p-merge-reload");
        TimelineDocument baseDocument = document("base", "track-base");
        TimelineRevision base = saveRevision(
                "ta", project, null, baseDocument, "server-author");
        TimelineDocument sourceDocument = document("base", "track-base", "track-source");
        TimelineRevision source = saveRevision(
                "ta", project, base.revisionId(), sourceDocument, "server-author");
        assertTrue(revisionRefMutation.advance(
                dsl, RevisionRef.main("ta", project), source.revisionId(), base.revisionId()),
                "production ref authority creates the target sibling from the merge base");
        TimelineRevision target = saveRevision(
                "ta", project, base.revisionId(), baseDocument, "server-author");

        TimelineMergeResult result = mergeEngine.merge(new TimelineMergeRequest(
                mutation("ta", project, "server-author"),
                base.revisionId(), source.revisionId(), target.revisionId(),
                "exact-v1 merge"));

        assertEquals(TimelineMergeResult.MergeStatus.MERGED, result.status());
        assertNotNull(result.mergedRevisionId());
        TimelineDocument merged = assertCanonicalReload(
                result.mergedRevisionId(),
                digester.digest(TimelineDocumentJsonSerializer.deserialize(result.mergedPayloadJson())));
        assertTrue(merged.getTracks().stream().anyMatch(track -> "track-source".equals(track.trackId())));
        assertEquals(List.of(target.revisionId(), source.revisionId()),
                orderedParents(result.mergedRevisionId()));
        assertEquals(result.mergedRevisionId(),
                revisionRefMutation.currentHead(RevisionRef.main("ta", project)));

        CanonicalState afterFirst = canonicalState(project);
        TimelineMergeResult replay = mergeEngine.merge(new TimelineMergeRequest(
                mutation("ta", project, "server-author"),
                base.revisionId(), source.revisionId(), target.revisionId(),
                "exact-v1 merge replay"));
        assertEquals(TimelineMergeResult.MergeStatus.MERGED, replay.status());
        assertEquals(result.mergedRevisionId(), replay.mergedRevisionId(),
                "durable duplicate merge returns the already persisted deterministic result");
        assertEquals(afterFirst, canonicalState(project),
                "durable merge replay creates no duplicate canonical state");
        assertCanonicalReload(replay.mergedRevisionId(),
                revisionRepository.findOwnedById(
                                replay.mergedRevisionId(), project, "ta")
                        .orElseThrow().contentHash());
    }

    @Test
    @DisplayName("PERSISTENT_MERGE_STALE_MAIN=REJECTED_WITHOUT_PARTIAL_STATE")
    void persistentMergeRejectsStaleTargetMainAndRollsBackAllState() {
        String project = createOwnedProject("p-merge-stale-main");
        TimelineDocument baseDocument = document("base", "track-base");
        TimelineRevision base = saveRevision(
                "ta", project, null, baseDocument, "server-author");
        TimelineRevision source = saveRevision(
                "ta", project, base.revisionId(),
                document("base", "track-base", "track-source"), "server-author");
        assertTrue(revisionRefMutation.advance(
                dsl, RevisionRef.main("ta", project), source.revisionId(), base.revisionId()));
        TimelineRevision target = saveRevision(
                "ta", project, base.revisionId(), baseDocument, "server-author");
        TimelineRevision newerMain = saveRevision(
                "ta", project, target.revisionId(),
                document("base", "track-base", "track-newer"), "server-author");
        CanonicalState before = canonicalState(project);

        assertThrows(TimelineConflictException.class, () -> mergeEngine.merge(
                new TimelineMergeRequest(
                        mutation("ta", project, "server-author"),
                        base.revisionId(), source.revisionId(), target.revisionId(),
                        "stale merge")));

        assertEquals(before, canonicalState(project));
        assertEquals(newerMain.revisionId(),
                revisionRefMutation.currentHead(RevisionRef.main("ta", project)));
    }

    @Test
    @DisplayName("PERSISTENT_MERGE_CONFLICT=NO_MUTATION")
    void persistentMergeConflictReturnsTypedConflictsWithoutMutation() {
        String project = createOwnedProject("p-merge-conflict");
        TimelineDocument baseDoc = documentWithClip("base", "clip-shared", 0, 10);
        TimelineRevision base = saveRevision(
                "ta", project, null, baseDoc, "server-author");
        TimelineRevision source = saveRevision(
                "ta", project, base.revisionId(),
                documentWithClip("source", "clip-shared", 1, 10), "server-author");
        assertTrue(revisionRefMutation.advance(
                dsl, RevisionRef.main("ta", project), source.revisionId(), base.revisionId()));
        TimelineRevision target = saveRevision(
                "ta", project, base.revisionId(),
                documentWithClip("target", "clip-shared", 2, 10), "server-author");
        CanonicalState before = canonicalState(project);

        TimelineMergeResult conflict = mergeEngine.merge(new TimelineMergeRequest(
                mutation("ta", project, "server-author"),
                base.revisionId(), source.revisionId(), target.revisionId(),
                "conflicting merge"));

        assertEquals(TimelineMergeResult.MergeStatus.CONFLICTS, conflict.status());
        assertTrue(conflict.hasConflicts());
        assertEquals(before, canonicalState(project));
        assertEquals(target.revisionId(),
                revisionRefMutation.currentHead(RevisionRef.main("ta", project)));
    }

    @Test
    @DisplayName("STALE_REF_CAS=REJECTED; NO_ORPHAN_CANONICAL_STATE=PASS")
    void staleMainRefCasIsRejectedWithoutOrphanCanonicalState() {
        String project = createOwnedProject("p-stale-cas");
        TimelineRevision root = saveRevision(
                "ta", project, null, document("root", "track-root"), "server-author");
        TimelineRevision head = saveRevision(
                "ta", project, root.revisionId(), document("head", "track-head"), "server-author");
        CanonicalState before = canonicalState(project);

        assertThrows(TimelineConflictException.class, () -> saveRevision(
                "ta", project, root.revisionId(), document("stale", "track-stale"), "server-author"));

        assertEquals(before, canonicalState(project));
        assertEquals(head.revisionId(), revisionRefMutation.currentHead(RevisionRef.main("ta", project)));
    }

    @Test
    @DisplayName("IMMUTABLE_COMMAND_CONFLICT=REJECTED (same key, different project scope)")
    void immutableCommandSameKeyDifferentScopeConflictsOnProductionPath() {
        String projectA = createOwnedProject("p-command-a");
        String projectB = createOwnedProject("p-command-b");
        TimelineRevision rootA = saveRevision(
                "ta", projectA, null, document("root-a", "track-a"), "server-author");
        TimelineRevision rootB = saveRevision(
                "ta", projectB, null, document("root-b", "track-b"), "server-author");
        var command = new TimelineRevisionSaveService.RevisionWriteCommand(
                "same-command-key", "same-plan", "same-fingerprint", "OPERATION_PLAN", "ta");
        saveRevisionForCommand(
                RevisionRef.main("ta", projectA), rootA.revisionId(),
                document("candidate-a", "track-a-candidate"), "server-author", command);
        CanonicalState projectBBefore = canonicalState(projectB);

        assertThrows(TimelineRevisionCommandConflictException.class, () ->
                saveRevisionForCommand(
                        RevisionRef.main("ta", projectB), rootB.revisionId(),
                        document("candidate-b", "track-b-candidate"), "server-author", command));

        assertEquals(projectBBefore, canonicalState(projectB));
        assertEquals(projectA, jdbc.queryForObject(
                "select project_id from apply_command where apply_command_id = 'same-command-key'",
                String.class));
    }

    @Test
    @DisplayName("COMPARE_FROM_TO_ACTUAL_PAIR_SEMANTICS=PASS")
    void compareFromToDescribesTheActualRequestedCanonicalPair() {
        String project = createOwnedProject("p-compare-pair");
        TimelineRevision from = saveRevision(
                "ta", project, null, document("from", "track-from"), "server-author");
        TimelineRevision to = saveRevision(
                "ta", project, from.revisionId(), document("to", "track-to"), "server-author");
        saveRevision(
                "ta", project, to.revisionId(), document("unrequested", "track-unrequested"),
                "server-author");

        TimelineRevisionDiffQuery.CompareResult compare = diffQuery.compareRevisions(
                project, "ta", from.revisionId(), to.revisionId());

        assertEquals(from.revisionId(), compare.fromRevision().id());
        assertEquals(to.revisionId(), compare.toRevision().id());
        assertTrue(compare.entityChanges().contains(
                new TimelineRevisionDiffService.EntityChange("track", "track-from", "removed")));
        assertTrue(compare.entityChanges().contains(
                new TimelineRevisionDiffService.EntityChange("track", "track-to", "added")));
        assertFalse(compare.entityChanges().stream()
                .anyMatch(change -> "track-unrequested".equals(change.entityId())));
    }

    @Test
    void sameOwnerSnapshotRefHeadAndParentEdgesAreAccepted() throws Exception {
        assertV2OwnerColumnsPresent();
        snapshot("sa", "ta", "pa");
        revision("ra", "ta", "pa", "sa", 1);
        snapshot("sa2", "ta", "pa");
        revision("ra2", "ta", "pa", "sa2", 2);
        assertDoesNotThrow(() -> jdbc.update(
                "insert into timeline_revision_ref(tenant_id,project_id,ref_id,head_revision_id) values ('ta','pa','main','ra2')"));
        assertDoesNotThrow(() -> jdbc.update(
                "insert into timeline_revision_parent(tenant_id,project_id,revision_id,parent_revision_id,parent_order) values ('ta','pa','ra2','ra',0)"));
    }

    @Test
    void parentTargetsRemainEnforcedAndInitiallyDeferredForFinalHeadCas() {
        Integer deferredParentTargets = jdbc.queryForObject("""
                select count(*)
                from pg_constraint c
                join pg_class r on r.oid = c.conrelid
                join pg_namespace n on n.oid = r.relnamespace
                where conname in ('fk_timeline_revision_parent',
                                   'fk_timeline_revision_parent_parent')
                  and n.nspname = current_schema()
                  and contype = 'f'
                  and condeferrable
                  and condeferred
                """, Integer.class);
        assertEquals(2, deferredParentTargets,
                "both composite parent targets stay enforced but defer classification to commit/CAS");
    }

    @Test
    void crossProjectSnapshotReferenceIsRejected() throws Exception {
        assertV2OwnerColumnsPresent();
        snapshot("s_pc", "ta", "pc");
        assertThrows(Exception.class, () -> revision("r_bad", "ta", "pa", "s_pc", 1));
    }

    @Test
    void crossTenantSnapshotReferenceIsRejected() throws Exception {
        assertV2OwnerColumnsPresent();
        snapshot("s_tb", "tb", "pb");
        assertThrows(Exception.class, () -> revision("r_bad", "ta", "pb", "s_tb", 1));
    }

    @Test
    void crossProjectRefHeadIsRejected() throws Exception {
        assertV2OwnerColumnsPresent();
        snapshot("s_pc", "ta", "pc");
        revision("r_pc", "ta", "pc", "s_pc", 1);
        assertThrows(Exception.class, () -> jdbc.update(
                "insert into timeline_revision_ref(tenant_id,project_id,ref_id,head_revision_id) values ('ta','pa','main','r_pc')"));
    }

    @Test
    void crossTenantRefHeadIsRejected() throws Exception {
        assertV2OwnerColumnsPresent();
        snapshot("s_tb", "tb", "pb");
        revision("r_tb", "tb", "pb", "s_tb", 1);
        assertThrows(Exception.class, () -> jdbc.update(
                "insert into timeline_revision_ref(tenant_id,project_id,ref_id,head_revision_id) values ('ta','pb','main','r_tb')"));
    }

    @Test
    void crossProjectParentEdgeIsRejected() throws Exception {
        assertV2OwnerColumnsPresent();
        snapshot("s_pa", "ta", "pa");
        snapshot("s_pc", "ta", "pc");
        revision("r_pa", "ta", "pa", "s_pa", 1);
        revision("r_pc", "ta", "pc", "s_pc", 1);
        assertThrows(Exception.class, () -> jdbc.update(
                "insert into timeline_revision_parent(tenant_id,project_id,revision_id,parent_revision_id,parent_order) values ('ta','pc','r_pa','r_pc',0)"));
    }

    @Test
    void crossTenantParentEdgeIsRejected() throws Exception {
        assertV2OwnerColumnsPresent();
        snapshot("s_pa", "ta", "pa");
        snapshot("s_pb", "tb", "pb");
        revision("r_pa", "ta", "pa", "s_pa", 1);
        revision("r_pb", "tb", "pb", "s_pb", 1);
        assertThrows(Exception.class, () -> jdbc.update(
                "insert into timeline_revision_parent(tenant_id,project_id,revision_id,parent_revision_id,parent_order) values ('ta','pb','r_pa','r_pb',0)"));
    }

    @Test
    void crossTenantArtifactPinIsRejected() throws Exception {
        snapshot("s_pa", "ta", "pa");
        revision("r_pa", "ta", "pa", "s_pa", 1);
        artifact("artifact-b", "tb");
        assertThrows(Exception.class, () -> jdbc.update(
                "insert into artifact_pin(pin_id,tenant_id,project_id,revision_id,artifact_id,content_digest,pinned_at) values ('pin-bad','ta','pa','r_pa','artifact-b',?,now())",
                "b".repeat(64)));
    }

    @Test
    void crossProjectArtifactPinRevisionIsRejected() throws Exception {
        snapshot("s_pc", "ta", "pc");
        revision("r_pc", "ta", "pc", "s_pc", 1);
        artifact("artifact-a", "ta");
        assertThrows(Exception.class, () -> jdbc.update(
                "insert into artifact_pin(pin_id,tenant_id,project_id,revision_id,artifact_id,content_digest,pinned_at) values ('pin-bad','ta','pa','r_pc','artifact-a',?,now())",
                "a".repeat(64)));
    }

    private TimelineRevision saveRevision(
            String tenantId, String projectId, String expectedHead,
            TimelineDocument document, String actorId) {
        return saveService.saveRevision(
                mutation(tenantId, projectId, actorId), expectedHead, document);
    }

    private TimelineRevision restoreRevision(
            String tenantId, String projectId, String historicalRevisionId,
            String expectedHead, String actorId) {
        return saveService.restoreRevision(
                mutation(tenantId, projectId, actorId), historicalRevisionId, expectedHead);
    }

    private TimelineRevisionSaveService.RevisionWriteResult saveRevisionForCommand(
            RevisionRef targetRef, String expectedHead, TimelineDocument document,
            String actorId, TimelineRevisionSaveService.RevisionWriteCommand command) {
        return saveService.saveRevisionForCommand(
                mutation(targetRef.tenantId(), targetRef.projectId(), actorId),
                targetRef, expectedHead, document, command);
    }

    private static TimelineMutationContext mutation(
            String tenantId, String projectId, String actorId) {
        return new TimelineMutationContext(
                tenantId,
                projectId,
                com.example.platform.shared.authorization.CanonicalActor.user(
                        actorId, tenantId, Set.of(), "test-authenticated"));
    }

    private static String createOwnedProject(String projectId) {
        jdbc.update("insert into project(id,tenant_id,name,created_at) values (?,'ta',?,now())",
                projectId, projectId);
        return projectId;
    }

    private static TimelineDocument document(String title, String... trackIds) {
        List<TimelineTrack> tracks = Arrays.stream(trackIds)
                .map(trackId -> new TimelineTrack(
                        trackId, trackId, TrackType.VIDEO, List.of()))
                .toList();
        return new TimelineDocument(
                TimelineDocument.CURRENT_SCHEMA_VERSION,
                tracks,
                new TimelineMetadata(title, "exact-v1", Map.of("matrix", "h7-v2")));
    }

    private static TimelineDocument documentWithClip(
            String title, String clipId, long startSeconds, long endSeconds) {
        TimelineClip clip = new TimelineClip(
                clipId,
                "asset-" + clipId,
                null,
                null,
                null,
                MediaTime.ofRational(startSeconds, 1),
                MediaTime.ofRational(endSeconds, 1),
                MediaTime.ZERO,
                MediaTime.ofRational(endSeconds - startSeconds, 1),
                "MEDIA_STREAM");
        return new TimelineDocument(
                TimelineDocument.CURRENT_SCHEMA_VERSION,
                List.of(new TimelineTrack("track-" + clipId, "Video", TrackType.VIDEO, List.of(clip))),
                new TimelineMetadata(title, "exact-v1", Map.of("matrix", "h7-v2")));
    }

    private TimelineDocument assertCanonicalReload(String revisionId, String expectedTimelineDigest) {
        TimelineRevision reloadedRevision = saveService.findById("ta", revisionId);
        assertNotNull(reloadedRevision, "production revision repository reload");
        TimelineDocument reloadedDocument = saveService.findPayloadDocument("ta", revisionId)
                .orElseThrow();
        TimelineRevision hydrated = reloadedRevision.hydrate(reloadedDocument);
        assertNotNull(hydrated.canonicalTimeline());
        assertEquals(expectedTimelineDigest, digester.digest(hydrated.canonicalTimeline()));
        assertEquals(expectedTimelineDigest, hydrated.semanticContext().timelineContentDigest());
        return hydrated.canonicalTimeline();
    }

    private static List<String> orderedParents(String revisionId) {
        return dsl.fetch(
                        "select parent_revision_id from timeline_revision_parent "
                                + "where revision_id = ? order by parent_order",
                        revisionId)
                .getValues(0, String.class);
    }

    private static CanonicalState canonicalState(String projectId) {
        return new CanonicalState(
                count("timeline_snapshot", projectId),
                count("timeline_revision", projectId),
                count("timeline_revision_parent", projectId),
                count("apply_command", projectId),
                revisionRefHead(projectId),
                jdbc.queryForObject(
                        "select next_revision_number from project_revision_counter where project_id = ?",
                        Long.class,
                        projectId));
    }

    private static long count(String table, String projectId) {
        Long value = jdbc.queryForObject(
                "select count(*) from " + table + " where project_id = ?", Long.class, projectId);
        return value == null ? 0 : value;
    }

    private static String revisionRefHead(String projectId) {
        return jdbc.queryForObject(
                "select head_revision_id from timeline_revision_ref "
                        + "where tenant_id = 'ta' and project_id = ? and ref_id = 'main'",
                String.class,
                projectId);
    }

    private record CanonicalState(
            long snapshots,
            long revisions,
            long parentEdges,
            long commands,
            String mainHead,
            long nextRevisionNumber) {
    }

    private static void snapshot(String id, String tenant, String project) {
        jdbc.update("insert into timeline_snapshot(id,tenant_id,project_id,payload_json,schema_version) values (?,?,?,?,?)",
                id, tenant, project, "{\"schemaVersion\":\"timeline-1.0\",\"tracks\":[]}", "timeline-1.0");
    }

    private static void revision(String id, String tenant, String project, String snapshot, int number) {
        jdbc.update("insert into timeline_revision(id,tenant_id,project_id,revision_number,snapshot_id,content_hash,schema_version,source,created_at) values (?,?,?,?,?,?,?,?,now())",
                id, tenant, project, number, snapshot, "a".repeat(64), "timeline-1.0", "h7-v2-test");
    }

    private static void artifact(String id, String tenant) {
        jdbc.update("insert into artifact(id,tenant_id,content_digest,byte_length,media_type,artifact_kind,state,created_at) values (?,?,?,?,?,?,?,now())",
                id, tenant, id + "-digest", 1L, "video/mp4", "RENDER_OUTPUT", "ACTIVE");
    }

    private static void assertV2OwnerColumnsPresent() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            assertTrue(hasColumn(connection, "timeline_revision_parent", "tenant_id"),
                    "ordered parent edges must carry tenant ownership");
            assertTrue(hasColumn(connection, "timeline_snapshot", "tenant_id"),
                    "snapshot tenant ownership column required");
        }
    }

    private static boolean hasColumn(Connection connection, String table, String column) throws Exception {
        try (ResultSet columns = connection.getMetaData().getColumns(null, null, table, column)) {
            return columns.next();
        }
    }
}
