package com.example.platform.testinfra;

import com.example.platform.artifact.infrastructure.ArtifactPinRepository;
import com.example.platform.artifact.app.ArtifactRelationRepository;
import com.example.platform.artifact.infrastructure.ArtifactRepository;
import com.example.platform.artifact.infrastructure.JooqArtifactQueryService;
import com.example.platform.identity.api.authorization.AuthorizationDecisionPort;
import com.example.platform.operation.batch.BatchCommitResult;
import com.example.platform.operation.batch.BatchErrorCode;
import com.example.platform.operation.batch.BatchException;
import com.example.platform.operation.batch.OperationBatchContext;
import com.example.platform.operation.batch.OperationBatchRequest;
import com.example.platform.operation.batch.VersionedAggregateRef;
import com.example.platform.operation.operation.OperationDefinition;
import com.example.platform.operation.operation.OperationParameters;
import com.example.platform.operation.operation.OperationRequest;
import com.example.platform.operation.operation.OperationTargetRequest;
import com.example.platform.render.app.operation.CanonicalBatchInvocationService;
import com.example.platform.render.app.operation.TimelineBatchTargetDomain;
import com.example.platform.shared.authorization.AuthorizationDecision;
import com.example.platform.shared.authorization.AuthorizationRequest;
import com.example.platform.shared.authorization.CanonicalActor;
import com.example.platform.shared.test.PostgresTestContainerSupport;
import com.example.platform.shared.time.MediaTime;
import com.example.platform.timeline.adapter.JdbcEffectDefinitionVersionRegistry;
import com.example.platform.timeline.adapter.JdbcEffectSemanticSnapshotStore;
import com.example.platform.timeline.adapter.JdbcTimelineRevisionSemanticContextStore;
import com.example.platform.timeline.adapter.TimelineRevisionRepository;
import com.example.platform.timeline.adapter.TimelineSnapshotService;
import com.example.platform.timeline.api.composition.TimelineSourceValidation;
import com.example.platform.timeline.api.revision.TimelineMutationContext;
import com.example.platform.timeline.app.DefaultTimelineRevisionPersistence;
import com.example.platform.timeline.app.InternalTimelineValidationService;
import com.example.platform.timeline.app.ProjectRevisionNumberAllocator;
import com.example.platform.timeline.app.TimelineArtifactPinValidator;
import com.example.platform.timeline.app.TimelineRevisionRefHeadUpdateAdapter;
import com.example.platform.timeline.app.TimelineRevisionRefMutation;
import com.example.platform.timeline.app.TimelineRevisionSaveService;
import com.example.platform.timeline.canonical.TimelineClip;
import com.example.platform.timeline.canonical.TimelineClipId;
import com.example.platform.timeline.canonical.TimelineContentDigester;
import com.example.platform.timeline.canonical.TimelineDocument;
import com.example.platform.timeline.canonical.TimelineMetadata;
import com.example.platform.timeline.canonical.TimelineTrack;
import com.example.platform.timeline.canonical.TrackType;
import com.example.platform.timeline.semantics.clip.MediaStreamSourceBinding;
import com.example.platform.timeline.semantics.effect.EffectSemanticSnapshotAuthority;
import com.example.platform.timeline.semantics.selection.SelectionSpec;
import com.example.platform.timeline.semantics.temporal.ConstantRateTemporalMapping;
import com.example.platform.timeline.semantics.temporal.PlaybackDirection;
import com.example.platform.timeline.version.TimelineRevision;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.conf.MappedSchema;
import org.jooq.conf.RenderMapping;
import org.jooq.conf.Settings;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Real PostgreSQL 18 integration matrix for ordered Operation batches.
 *
 * <p>Uses the production Timeline revision authority and the production batch
 * domain adapter. Source validation and authorization are stubbed only where the
 * test target has no real artifact/identity authority; the add-with-real-artifact
 * chain is covered by the product E2E.
 */
class OperationBatchPostgresTest extends PostgresTestContainerSupport {

    private static final String TENANT = "ta";
    private static final String PROJECT = "p-batch";
    private static final String ACTOR = "author-1";
    private static final String SCHEMA = isolatedSchemaName();

    private static JdbcTemplate jdbc;
    private static DataSource dataSource;
    private static DSLContext dsl;
    private static Flyway flyway;

    private TimelineRevisionSaveService saveService;
    private StubAuthorization authorization;
    private CanonicalBatchInvocationService batch;
    private TimelineContentDigester digester;

    @BeforeAll
    static void migrateExactCanonicalV1() {
        String scopedUrl = scopedJdbcUrl(SCHEMA);
        flyway = Flyway.configure()
                .dataSource(scopedUrl, username(), password())
                .schemas(SCHEMA)
                .defaultSchema(SCHEMA)
                .createSchemas(true)
                .locations("classpath:db/migration")
                .load();
        flyway.migrate();
        var source = new DriverManagerDataSource();
        source.setDriverClassName(driverClassName());
        source.setUrl(scopedUrl);
        source.setUsername(username());
        source.setPassword(password());
        source.setConnectionProperties(connectionProperties());
        dataSource = source;
        jdbc = new JdbcTemplate(source);
        dsl = DSL.using(source, SQLDialect.POSTGRES, new Settings()
                .withRenderMapping(new RenderMapping().withSchemata(
                        new MappedSchema().withInput("public").withOutput(SCHEMA))));
    }

    @BeforeEach
    void resetRows() {
        jdbc.execute("""
                truncate table outbox_events, audit_records, notification_delivery, notification_event,
                    apply_command, artifact_pin, timeline_revision_parent, timeline_revision_ref,
                    timeline_revision, timeline_snapshot, project_revision_counter, project,
                    artifact, tenant cascade
                """);
        jdbc.update("insert into tenant(id,name,status,created_at) values (?,'A','ACTIVE',now())", TENANT);
        jdbc.update("insert into project(id,tenant_id,name,created_at) values (?,?,'Batch',now())",
                PROJECT, TENANT);

        digester = new TimelineContentDigester();
        var refMutation = new TimelineRevisionRefMutation(dsl);
        var snapshotService = new TimelineSnapshotService(dsl);
        var artifactQuery = new JooqArtifactQueryService(
                new ArtifactRepository(dsl), new ArtifactRelationRepository(dsl));
        var outbox = new com.example.platform.outbox.app.OutboxEventService(dsl, 3,
                new com.example.platform.outbox.app.PostgresNotificationService(jdbc),
                new com.example.platform.outbox.app.OutboxEventRouter(
                        List.of(new com.example.platform.timeline.api.event.TimelineOutboxEvents())));
        authorization = new StubAuthorization();
        saveService = new TimelineRevisionSaveService(
                dsl,
                refMutation,
                digester,
                snapshotService,
                new TimelineArtifactPinValidator(artifactQuery),
                new com.example.platform.artifact.app.ArtifactPinService(
                        new ArtifactPinRepository(dsl)),
                new EffectSemanticSnapshotAuthority(
                        new JdbcEffectDefinitionVersionRegistry(dsl),
                        new JdbcEffectSemanticSnapshotStore(dsl)),
                new JdbcTimelineRevisionSemanticContextStore(dsl),
                new DefaultTimelineRevisionPersistence(),
                new TimelineRevisionRefHeadUpdateAdapter(refMutation),
                authorization,
                outbox);
        var sourceValidation = new TimelineSourceValidation() {
            @Override
            public ValidationResult validate(MediaStreamSourceBinding binding) {
                return new ValidationResult(true, List.of());
            }

            @Override
            public ValidationResult validate(MediaStreamSourceBinding binding, String expectedTenantId,
                                             String expectedProjectId,
                                             com.example.platform.timeline.canonical.TrackType expectedTrackType) {
                return new ValidationResult(true, List.of());
            }
        };
        var domain = new TimelineBatchTargetDomain(saveService, sourceValidation,
                new InternalTimelineValidationService());
        batch = new CanonicalBatchInvocationService(domain, authorization);
    }

    @Test
    void moveAndTrimCommitAsExactlyOneRevisionWithOrderedProvenance() {
        TimelineRevision base = saveRevision(twoClipDocument());
        OperationRequest move = move(base, "clip-a", "2/1");
        OperationRequest trim = trim(base, "clip-a", "3/1");
        String batchId = "batch-move-trim";
        BatchCommitResult result = commit(batchId, base, move, trim, "ordered edit");

        assertEquals(BatchCommitResult.APPLIED, result.status());
        assertNotEquals(base.revisionId(), result.newRevisionId());
        assertEquals(base.revisionId(), result.parentRevisionId());
        assertEquals(2, count("timeline_revision"));
        assertEquals(1, count("apply_command"));
        assertEquals(result.newRevisionId(), head());
        assertEquals(base.revisionId(), parentOf(result.newRevisionId()));

        Map<String, Object> command = jdbc.queryForMap(
                "select command_domain, plan_digest, status, result_status from apply_command where apply_command_id = ?",
                batchId);
        assertEquals("OPERATION_BATCH", command.get("command_domain"));
        assertEquals(result.batchDigest(), command.get("plan_digest"));
        assertEquals("COMPLETED", command.get("status"));
        assertEquals("APPLIED", command.get("result_status"));

        Map<String, Object> revision = jdbc.queryForMap(
                "select message, change_summary_json from timeline_revision where id = ?",
                result.newRevisionId());
        assertEquals("ordered edit", revision.get("message"));
        String provenance = (String) revision.get("change_summary_json");
        assertNotNull(provenance);
        assertTrue(provenance.contains("\"formatVersion\":\"operation-batch-provenance-v1\""));
        assertTrue(provenance.contains("\"definitionId\":\"timeline.move\""));
        assertTrue(provenance.contains("\"definitionId\":\"timeline.trim\""));
        assertTrue(provenance.indexOf("timeline.move") < provenance.indexOf("timeline.trim"),
                "provenance must preserve step order");
        assertTrue(provenance.contains("\"batchDigest\":\"" + result.batchDigest() + "\""));

        TimelineDocument committed = saveService.findPayloadDocument(TENANT, result.newRevisionId())
                .orElseThrow();
        TimelineClip clip = committed.getTracks().get(0).clips().get(0);
        assertEquals(MediaTime.ofRational(2, 1), clip.getStartTime());
        assertEquals(MediaTime.ofRational(3, 1), clip.getEndTime());
    }

    @Test
    void laterStepFailureWritesNoRevisionAndKeepsHead() {
        TimelineRevision base = saveRevision(twoClipDocument());
        OperationRequest move = move(base, "clip-a", "2/1");
        OperationRequest deleteMissing = delete(base, "clip-missing");
        BatchException failure = assertThrows(BatchException.class,
                () -> commit("batch-fail", base, move, deleteMissing, null));

        assertEquals(1, failure.stepIndex());
        assertTrue(failure.code() == BatchErrorCode.STEP_RESOLUTION_FAILED
                || failure.code() == BatchErrorCode.STEP_PLAN_FAILED);
        assertEquals(1, count("timeline_revision"));
        assertEquals(0, count("apply_command"));
        assertEquals(base.revisionId(), head());
    }

    @Test
    void intermediateCandidateValidationRejectsTheWholeBatch() {
        TimelineRevision base = saveRevision(twoClipDocument());
        OperationRequest overlappingMove = move(base, "clip-a", "7/1");
        BatchException failure = assertThrows(BatchException.class,
                () -> commit("batch-overlap", base, overlappingMove, null));

        assertEquals(BatchErrorCode.STEP_VALIDATION_FAILED, failure.code());
        assertEquals(0, failure.stepIndex());
        assertEquals(1, count("timeline_revision"));
        assertEquals(0, count("apply_command"));
    }

    @Test
    void staleExpectedHeadLosesTheRefCasWithoutWriting() {
        TimelineRevision base = saveRevision(twoClipDocument());
        commit("batch-first", base, move(base, "clip-a", "1/1"), null);
        assertEquals(2, count("timeline_revision"));

        BatchException failure = assertThrows(BatchException.class,
                () -> commit("batch-stale", base, move(base, "clip-a", "2/1"), null));
        assertEquals(BatchErrorCode.STALE_BASE_REVISION, failure.code());
        assertEquals(2, count("timeline_revision"));
        assertEquals(1, count("apply_command"));
    }

    @Test
    void concurrentWritersProduceOneRevisionAndOneCasLoser() throws Exception {
        TimelineRevision base = saveRevision(twoClipDocument());
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            java.util.concurrent.Callable<Object> attempt = () -> {
                ready.countDown();
                start.await(10, TimeUnit.SECONDS);
                try {
                    return commit("batch-concurrent-" + UUID.randomUUID(), base,
                            move(base, "clip-a", "1/1"), null);
                } catch (BatchException failure) {
                    return failure;
                }
            };
            var first = executor.submit(attempt);
            var second = executor.submit(attempt);
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();
            Object firstResult = first.get(30, TimeUnit.SECONDS);
            Object secondResult = second.get(30, TimeUnit.SECONDS);
            long applied = (firstResult instanceof BatchCommitResult ? 1 : 0)
                    + (secondResult instanceof BatchCommitResult ? 1 : 0);
            long stale = (firstResult instanceof BatchException f
                    && f.code() == BatchErrorCode.STALE_BASE_REVISION ? 1 : 0)
                    + (secondResult instanceof BatchException f
                    && f.code() == BatchErrorCode.STALE_BASE_REVISION ? 1 : 0);
            assertEquals(1, applied);
            assertEquals(1, stale);
        }
        assertEquals(2, count("timeline_revision"));
        assertEquals(1, count("apply_command"));
    }

    @Test
    void sameBatchIdAndFingerprintReplaysTheCommittedRevision() {
        TimelineRevision base = saveRevision(twoClipDocument());
        OperationRequest move = move(base, "clip-a", "2/1");
        String batchId = "batch-replay";
        BatchCommitResult first = commit(batchId, base, move, null);
        BatchCommitResult second = commit(batchId, base, move, null);

        assertEquals(first.newRevisionId(), second.newRevisionId());
        assertTrue(second.replayed());
        assertEquals(2, count("timeline_revision"));
        assertEquals(1, count("apply_command"));
    }

    @Test
    void sameBatchIdWithDifferentContentConflicts() {
        TimelineRevision base = saveRevision(twoClipDocument());
        String batchId = "batch-conflict";
        commit(batchId, base, move(base, "clip-a", "1/1"), null);

        BatchException failure = assertThrows(BatchException.class,
                () -> commit(batchId, base, move(base, "clip-a", "2/1"), null));
        assertEquals(BatchErrorCode.COMMAND_ID_CONFLICT, failure.code());
        assertEquals(2, count("timeline_revision"));
    }

    @Test
    void cancellingOperationsProduceSemanticNoOpWithoutRevision() {
        TimelineRevision base = saveRevision(twoClipDocument());
        BatchCommitResult result = commit("batch-noop", base,
                moveAbsolute(base, "clip-a", 2), moveAbsolute(base, "clip-a", 0), null);

        assertEquals(BatchCommitResult.NO_OP, result.status());
        assertNull(result.newRevisionId());
        assertEquals(1, count("timeline_revision"));
        assertEquals(1, count("apply_command"));
        assertEquals("NO_OP", jdbc.queryForObject(
                "select result_status from apply_command where apply_command_id = 'batch-noop'",
                String.class));
        assertEquals(base.revisionId(), head());
    }

    @Test
    void unauthorizedActorWritesNothing() {
        TimelineRevision base = saveRevision(twoClipDocument());
        authorization.allow = false;
        BatchException failure = assertThrows(BatchException.class,
                () -> commit("batch-denied", base, move(base, "clip-a", "1/1"), null));

        assertEquals(BatchErrorCode.AUTHORIZATION_DENIED, failure.code());
        assertEquals(1, count("timeline_revision"));
        assertEquals(0, count("apply_command"));
    }

    @Test
    void textOperationsAreRejectedAsUnsupported() {
        TimelineRevision base = saveRevision(twoClipDocument());
        OperationRequest text = new OperationRequest(
                OperationDefinition.V1.ADD_TEXT_ELEMENT.definitionId(),
                OperationDefinition.V1.ADD_TEXT_ELEMENT.version(),
                new OperationTargetRequest.TimelineTargetRequest(PROJECT),
                new OperationParameters.NoParameters(),
                base.revisionId(), hash(base), null);
        BatchException failure = assertThrows(BatchException.class,
                () -> commit("batch-text", base, text, null));

        assertEquals(BatchErrorCode.UNSUPPORTED_BATCH_OPERATION, failure.code());
        assertEquals(1, count("timeline_revision"));
    }

    @Test
    void crossAggregateTargetIsRejectedBeforeAnyWrite() {
        TimelineRevision base = saveRevision(twoClipDocument());
        OperationRequest foreign = new OperationRequest(
                OperationDefinition.V1.MOVE.definitionId(),
                OperationDefinition.V1.MOVE.version(),
                new OperationTargetRequest.ClipSelectionTargetRequest("other-project",
                        new SelectionSpec.ExplicitObjectSelection(
                                List.of(TimelineClipId.of("clip-a"))),
                        SelectionSpec.ExpansionPolicy.EXACT),
                new OperationParameters.MoveParameters(MediaTime.ofRational(1, 1), false),
                base.revisionId(), hash(base), null);
        BatchException failure = assertThrows(BatchException.class,
                () -> commit("batch-cross", base, foreign, null));

        assertEquals(BatchErrorCode.CROSS_AGGREGATE_BATCH_UNSUPPORTED, failure.code());
        assertEquals(1, count("timeline_revision"));
    }

    // ---- fixtures ----

    private BatchCommitResult commit(String batchId, TimelineRevision base,
                                     OperationRequest only, String message) {
        return commit(batchId, base, only, null, message);
    }

    private BatchCommitResult commit(String batchId, TimelineRevision base,
                                     OperationRequest first, OperationRequest second,
                                     String message) {
        List<OperationRequest> operations = second == null
                ? List.of(first) : List.of(first, second);
        VersionedAggregateRef target = new VersionedAggregateRef(
                "TIMELINE", TENANT, PROJECT, PROJECT, "main");
        OperationBatchRequest request = new OperationBatchRequest(
                batchId, target, base.revisionId(), operations, message);
        CanonicalActor actor = CanonicalActor.user(ACTOR, TENANT, Set.of("EDITOR"), "test");
        return batch.commit(request,
                OperationBatchContext.apply(batchId, actor, null, "test"));
    }

    private TimelineRevision saveRevision(TimelineDocument document) {
        return saveService.saveRevision(mutation(), null, document);
    }

    private TimelineMutationContext mutation() {
        return new TimelineMutationContext(TENANT, PROJECT,
                CanonicalActor.user(ACTOR, TENANT, Set.of("EDITOR"), "test"));
    }

    private static String hash(TimelineRevision revision) {
        return revision.semanticContext().timelineContentDigest();
    }

    private static OperationRequest move(TimelineRevision base, String clipId, String delta) {
        return clipRequest(OperationDefinition.V1.MOVE, base, clipId,
                new OperationParameters.MoveParameters(time(delta), false));
    }

    private static OperationRequest moveAbsolute(TimelineRevision base, String clipId, long start) {
        return clipRequest(OperationDefinition.V1.MOVE, base, clipId,
                new OperationParameters.MoveParameters(MediaTime.ofRational(start, 1), true));
    }

    private static OperationRequest trim(TimelineRevision base, String clipId, String delta) {
        return clipRequest(OperationDefinition.V1.TRIM, base, clipId,
                new OperationParameters.TrimParameters(
                        OperationParameters.TrimParameters.TrimEdge.END, time(delta)));
    }

    private static OperationRequest delete(TimelineRevision base, String clipId) {
        return clipRequest(OperationDefinition.V1.DELETE, base, clipId,
                new OperationParameters.NoParameters());
    }

    private static OperationRequest clipRequest(OperationDefinition definition,
                                                TimelineRevision base, String clipId,
                                                OperationParameters parameters) {
        return new OperationRequest(definition.definitionId(), definition.version(),
                new OperationTargetRequest.ClipSelectionTargetRequest(PROJECT,
                        new SelectionSpec.ExplicitObjectSelection(
                                List.of(TimelineClipId.of(clipId))),
                        SelectionSpec.ExpansionPolicy.EXACT),
                parameters, base.revisionId(), hash(base), null);
    }

    private static MediaTime time(String value) {
        return MediaTime.parse(value);
    }

    private static TimelineDocument twoClipDocument() {
        TimelineClip first = clip("clip-a", 0, 4);
        TimelineClip second = clip("clip-b", 6, 10);
        return new TimelineDocument(
                TimelineDocument.CURRENT_SCHEMA_VERSION,
                List.of(new TimelineTrack("video-main", "Video", TrackType.VIDEO,
                        List.of(first, second))),
                new TimelineMetadata("batch", "exact-v1", Map.of("matrix", "batch")));
    }

    private static TimelineClip clip(String clipId, long start, long end) {
        return new TimelineClip(clipId, "asset-" + clipId, null, null, null,
                MediaTime.ofRational(start, 1), MediaTime.ofRational(end, 1),
                MediaTime.ZERO, MediaTime.ofRational(end - start, 1), "MEDIA_STREAM",
                ConstantRateTemporalMapping.of(1, 1, PlaybackDirection.FORWARD));
    }

    private long count(String table) {
        Long value = jdbc.queryForObject(
                "select count(*) from " + table + " where project_id = ?", Long.class, PROJECT);
        return value == null ? 0 : value;
    }

    private String head() {
        return jdbc.query(
                "select head_revision_id from timeline_revision_ref where project_id = ?",
                rs -> rs.next() ? rs.getString(1) : null, PROJECT);
    }

    private String parentOf(String revisionId) {
        return jdbc.queryForObject(
                "select parent_revision_id from timeline_revision where id = ?",
                String.class, revisionId);
    }

    /** Test-only authorization double; the real Identity authority is exercised by the product E2E. */
    private static final class StubAuthorization implements AuthorizationDecisionPort {
        private volatile boolean allow = true;

        @Override
        public AuthorizationDecision decide(AuthorizationRequest request) {
            return allow ? AuthorizationDecision.allow("test-allow")
                    : AuthorizationDecision.deny("DENIED", "test-deny");
        }
    }
}
