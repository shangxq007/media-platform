package com.example.platform.testinfra;

import com.example.platform.artifact.infrastructure.ArtifactPinRepository;
import com.example.platform.audio.domain.mix.AudioGain;
import com.example.platform.audio.domain.mix.AudioMasterBus;
import com.example.platform.audio.domain.mix.AudioMix;
import com.example.platform.audio.domain.mix.AudioMixInput;
import com.example.platform.audio.domain.mix.AudioRoute;
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
import com.example.platform.operation.application.CanonicalBatchInvocationService;
import com.example.platform.operation.application.TimelineBatchTargetDomain;
import com.example.platform.outbox.app.OutboxEventRouter;
import com.example.platform.outbox.app.OutboxEventService;
import com.example.platform.outbox.app.PostgresNotificationService;
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
import com.example.platform.timeline.api.event.TimelineOutboxEvents;
import com.example.platform.timeline.api.event.TimelineRestoredEvent;
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
import com.example.platform.timeline.semantics.clip.MediaClip;
import com.example.platform.timeline.semantics.effect.ClipEffectTarget;
import com.example.platform.timeline.semantics.effect.EffectInstance;
import com.example.platform.timeline.semantics.effect.EffectSemanticSnapshotAuthority;
import com.example.platform.timeline.semantics.effect.EffectSemanticSnapshotCanonicalSemantics;
import com.example.platform.timeline.semantics.relationship.GroupId;
import com.example.platform.timeline.semantics.relationship.GroupRelationship;
import com.example.platform.timeline.semantics.relationship.SyncRelationship;
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
    private OutboxEventService outbox;
    private OutboxEventRouter eventRouter;
    private StubAuthorization authorization;
    private CanonicalBatchInvocationService batch;
    private TimelineContentDigester digester;

    @BeforeAll
    static void migrateExactCanonicalV1() {
        String scopedUrl = scopedJdbcUrl(SCHEMA);
        flyway = Flyway.configure()
                .dataSource(scopedUrl, username(), password())
                .schemas("public")
                .defaultSchema("public")
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
                truncate table infrastructure.outbox_events, policy.audit_records, notification.notification_delivery, notification.notification_event,
                    timeline.apply_command, artifacts.artifact_pin, timeline.timeline_revision_parent, timeline.timeline_revision_ref,
                    timeline.timeline_revision, timeline.timeline_snapshot, timeline.project_revision_counter, identity.project,
                    artifacts.artifact, identity.tenant cascade
                """);
        jdbc.update("insert into identity.tenant(id,name,status,created_at) values (?,'A','ACTIVE',now())", TENANT);
        jdbc.update("insert into identity.project(id,tenant_id,name,created_at) values (?,?,'Batch',now())",
                PROJECT, TENANT);

        digester = new TimelineContentDigester();
        var refMutation = new TimelineRevisionRefMutation(dsl);
        var snapshotService = new TimelineSnapshotService(dsl);
        var artifactQuery = new JooqArtifactQueryService(
                new ArtifactRepository(dsl), new ArtifactRelationRepository(dsl));
        eventRouter = new OutboxEventRouter(List.of(new TimelineOutboxEvents()));
        outbox = org.mockito.Mockito.spy(new OutboxEventService(dsl, 3,
                new PostgresNotificationService(jdbc), eventRouter));
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
        assertEquals(2, count("timeline.timeline_revision"));
        assertEquals(1, count("timeline.apply_command"));
        assertEquals(result.newRevisionId(), head());
        assertEquals(base.revisionId(), parentOf(result.newRevisionId()));

        Map<String, Object> command = jdbc.queryForMap(
                "select command_domain, plan_digest, status, result_status from timeline.apply_command where apply_command_id = ?",
                batchId);
        assertEquals("OPERATION_BATCH", command.get("command_domain"));
        assertEquals(result.batchDigest(), command.get("plan_digest"));
        assertEquals("COMPLETED", command.get("status"));
        assertEquals("APPLIED", command.get("result_status"));

        Map<String, Object> revision = jdbc.queryForMap(
                "select message, change_summary_json from timeline.timeline_revision where id = ?",
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
        assertEquals(1, count("timeline.timeline_revision"));
        assertEquals(0, count("timeline.apply_command"));
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
        assertEquals(1, count("timeline.timeline_revision"));
        assertEquals(0, count("timeline.apply_command"));
    }

    @Test
    void staleExpectedHeadLosesTheRefCasWithoutWriting() {
        TimelineRevision base = saveRevision(twoClipDocument());
        commit("batch-first", base, move(base, "clip-a", "1/1"), null);
        assertEquals(2, count("timeline.timeline_revision"));

        BatchException failure = assertThrows(BatchException.class,
                () -> commit("batch-stale", base, move(base, "clip-a", "2/1"), null));
        assertEquals(BatchErrorCode.STALE_BASE_REVISION, failure.code());
        assertEquals(2, count("timeline.timeline_revision"));
        assertEquals(1, count("timeline.apply_command"));
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
        assertEquals(2, count("timeline.timeline_revision"));
        assertEquals(1, count("timeline.apply_command"));
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
        assertEquals(2, count("timeline.timeline_revision"));
        assertEquals(1, count("timeline.apply_command"));
    }

    @Test
    void sameBatchIdWithDifferentContentConflicts() {
        TimelineRevision base = saveRevision(twoClipDocument());
        String batchId = "batch-conflict";
        commit(batchId, base, move(base, "clip-a", "1/1"), null);

        BatchException failure = assertThrows(BatchException.class,
                () -> commit(batchId, base, move(base, "clip-a", "2/1"), null));
        assertEquals(BatchErrorCode.COMMAND_ID_CONFLICT, failure.code());
        assertEquals(2, count("timeline.timeline_revision"));
    }

    @Test
    void cancellingOperationsProduceSemanticNoOpWithoutRevision() {
        TimelineRevision base = saveRevision(twoClipDocument());
        BatchCommitResult result = commit("batch-noop", base,
                moveAbsolute(base, "clip-a", 2), moveAbsolute(base, "clip-a", 0), null);

        assertEquals(BatchCommitResult.NO_OP, result.status());
        assertNull(result.newRevisionId());
        assertEquals(1, count("timeline.timeline_revision"));
        assertEquals(1, count("timeline.apply_command"));
        assertEquals("NO_OP", jdbc.queryForObject(
                "select result_status from timeline.apply_command where apply_command_id = 'batch-noop'",
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
        assertEquals(1, count("timeline.timeline_revision"));
        assertEquals(0, count("timeline.apply_command"));
    }

    @Test
    void removeTextElementResolvesItsIdentityAndCommitsOneRevision() throws Exception {
        var doc = twoClipDocument();
        var element = new com.fasterxml.jackson.databind.ObjectMapper().readValue("""
                {"id":"title","start":{"numerator":0,"denominator":1},"duration":{"numerator":1,"denominator":1},
                 "styledText":{"content":"Title","semanticRuns":[],"styleRuns":[],"paragraphStyle":{
                    "alignment":"START","justification":"NONE","lineHeight":{"form":"RATIO","value":{"numerator":1,"denominator":1}},
                    "wrapPolicy":"WRAP","baseDirection":"AUTO","lineBreakPolicy":"STANDARD"}},
                 "frame":{"widthConstraint":{"numerator":100,"denominator":1},"horizontalAlignment":"START",
                    "verticalAlignment":"TOP","wrapBehavior":"WRAP","overflowBehavior":"CLIP"},
                 "fallbackPolicy":{"defaultChain":["Arial"],"scriptOverrides":[],"languageOverrides":[],"emojiChain":[]},
                 "resolvedFontRuns":[]}
                """, com.example.platform.timeline.canonical.TextElement.class);
        TimelineRevision base = saveRevision(new TimelineDocument(doc.getSchemaVersion(), doc.getTracks(), doc.getMetadata(),
                doc.getAudioMix(), doc.getSemanticRelationships(), List.of(element), doc.getTransitions(), doc.getAutomations()));
        OperationRequest remove = new OperationRequest(OperationDefinition.V1.REMOVE_TEXT_ELEMENT.definitionId(),
                OperationDefinition.V1.REMOVE_TEXT_ELEMENT.version(),
                new OperationTargetRequest.TextElementTargetRequest(PROJECT, element.id()),
                new OperationParameters.RemoveTextElementParameters(element.id()), base.revisionId(), hash(base), null);
        var result = commit("text-remove", base, remove, null);
        assertEquals(2, count("timeline.timeline_revision"));
        assertTrue(saveService.findPayloadDocument(TENANT, result.newRevisionId()).orElseThrow().getTextElements().isEmpty());
        assertEquals(element.id(), saveService.findPayloadDocument(TENANT, base.revisionId())
                .orElseThrow().getTextElements().getFirst().id());
    }

    @Test
    void splitThenMoveAndTrimRightClipInOneBatch() {
        TimelineRevision base = saveRevision(twoClipDocument());
        OperationRequest split = new OperationRequest(
                OperationDefinition.V3.SPLIT_CLIP.definitionId(),
                OperationDefinition.V3.SPLIT_CLIP.version(),
                new OperationTargetRequest.ClipSelectionTargetRequest(PROJECT,
                        new SelectionSpec.ExplicitObjectSelection(List.of(TimelineClipId.of("clip-a"))),
                        SelectionSpec.ExpansionPolicy.EXACT),
                new OperationParameters.SplitParameters(TimelineClipId.of("clip-a-right"),
                        MediaTime.ofRational(2, 1)),
                base.revisionId(), hash(base), null);
        OperationRequest moveRight = new OperationRequest(
                OperationDefinition.V1.MOVE.definitionId(),
                OperationDefinition.V1.MOVE.version(),
                new OperationTargetRequest.ClipSelectionTargetRequest(PROJECT,
                        new SelectionSpec.ExplicitObjectSelection(
                                List.of(TimelineClipId.of("clip-a-right"))),
                        SelectionSpec.ExpansionPolicy.EXACT),
                new OperationParameters.MoveParameters(MediaTime.ofRational(3, 1), true),
                base.revisionId(), hash(base), null);
        OperationRequest trimRight = new OperationRequest(
                OperationDefinition.V1.TRIM.definitionId(),
                OperationDefinition.V1.TRIM.version(),
                new OperationTargetRequest.ClipSelectionTargetRequest(PROJECT,
                        new SelectionSpec.ExplicitObjectSelection(
                                List.of(TimelineClipId.of("clip-a-right"))),
                        SelectionSpec.ExpansionPolicy.EXACT),
                new OperationParameters.TrimParameters(
                        OperationParameters.TrimParameters.TrimEdge.END, MediaTime.ofRational(1, 1)),
                base.revisionId(), hash(base), null);

        BatchCommitResult result = commitThree("batch-split-move-trim", base, split, moveRight, trimRight);

        assertEquals(BatchCommitResult.APPLIED, result.status());
        assertEquals(2, count("timeline.timeline_revision"));
        assertEquals(result.newRevisionId(), head());
        TimelineDocument committed = saveService.findPayloadDocument(TENANT, result.newRevisionId())
                .orElseThrow();
        List<TimelineClip> clips = committed.getTracks().get(0).clips();
        assertEquals(List.of("clip-a", "clip-a-right", "clip-b"),
                clips.stream().map(clip -> clip.getClipId().value()).toList());
        TimelineClip left = clips.get(0);
        TimelineClip right = clips.get(1);
        assertEquals(MediaTime.ofRational(0, 1), left.getStartTime());
        assertEquals(MediaTime.ofRational(2, 1), left.getEndTime());
        assertEquals(MediaTime.ofRational(2, 1), left.getTrimEnd());
        assertEquals(MediaTime.ofRational(3, 1), right.getStartTime());
        assertEquals(MediaTime.ofRational(4, 1), right.getEndTime());
        assertEquals(MediaTime.ofRational(2, 1), right.getTrimStart());
        assertEquals(MediaTime.ofRational(3, 1), right.getTrimEnd());
        assertEquals(left.getMediaAssetId(), right.getMediaAssetId());
    }

    @Test
    void splitAtClipBoundaryFailsWithoutWriting() {
        TimelineRevision base = saveRevision(twoClipDocument());
        OperationRequest split = new OperationRequest(
                OperationDefinition.V3.SPLIT_CLIP.definitionId(),
                OperationDefinition.V3.SPLIT_CLIP.version(),
                new OperationTargetRequest.ClipSelectionTargetRequest(PROJECT,
                        new SelectionSpec.ExplicitObjectSelection(List.of(TimelineClipId.of("clip-a"))),
                        SelectionSpec.ExpansionPolicy.EXACT),
                new OperationParameters.SplitParameters(TimelineClipId.of("clip-a-right"),
                        MediaTime.ofRational(0, 1)),
                base.revisionId(), hash(base), null);

        BatchException failure = assertThrows(BatchException.class,
                () -> commit("batch-split-boundary", base, split, null));

        assertEquals(BatchErrorCode.STEP_PLAN_FAILED, failure.code());
        assertEquals(0, failure.stepIndex());
        assertEquals(1, count("timeline.timeline_revision"));
        assertEquals(0, count("timeline.apply_command"));
    }

    @Test
    void groupCreateAndRemoveInOneBatchIsSemanticNoOp() {
        TimelineRevision base = saveRevision(twoClipDocument());
        OperationRequest create = new OperationRequest(
                OperationDefinition.V1.CREATE_GROUP.definitionId(),
                OperationDefinition.V1.CREATE_GROUP.version(),
                new OperationTargetRequest.ClipSelectionTargetRequest(PROJECT,
                        new SelectionSpec.ExplicitObjectSelection(List.of(
                                TimelineClipId.of("clip-a"), TimelineClipId.of("clip-b"))),
                        SelectionSpec.ExpansionPolicy.EXACT),
                new OperationParameters.CreateGroupParameters(GroupId.of("group-1"),
                        List.of(TimelineClipId.of("clip-a"), TimelineClipId.of("clip-b"))),
                base.revisionId(), hash(base), null);
        OperationRequest remove = new OperationRequest(
                OperationDefinition.V1.REMOVE_GROUP.definitionId(),
                OperationDefinition.V1.REMOVE_GROUP.version(),
                new OperationTargetRequest.GroupTargetRequest(GroupId.of("group-1")),
                new OperationParameters.NoParameters(),
                base.revisionId(), hash(base), null);

        BatchCommitResult result = commit("batch-group", base, create, remove, null);

        assertEquals(BatchCommitResult.NO_OP, result.status());
        assertEquals(1, count("timeline.timeline_revision"));
        assertEquals(1, count("timeline.apply_command"));
        assertEquals(base.revisionId(), head());
    }

    @Test
    void syncCreateAndRemoveInOneBatchIsSemanticNoOp() {
        TimelineRevision base = saveRevision(twoClipDocument());
        TimelineClipId endpointA = TimelineClipId.of("clip-a");
        TimelineClipId endpointB = TimelineClipId.of("clip-b");
        String identityKey = SyncRelationship.of(endpointA, MediaTime.ZERO,
                endpointB, MediaTime.ZERO).identityKey();
        OperationRequest create = new OperationRequest(
                OperationDefinition.V1.CREATE_SYNC.definitionId(),
                OperationDefinition.V1.CREATE_SYNC.version(),
                new OperationTargetRequest.SyncTargetRequest(identityKey),
                new OperationParameters.CreateSyncParameters(endpointA, MediaTime.ZERO,
                        endpointB, MediaTime.ZERO),
                base.revisionId(), hash(base), null);
        OperationRequest remove = new OperationRequest(
                OperationDefinition.V1.REMOVE_SYNC.definitionId(),
                OperationDefinition.V1.REMOVE_SYNC.version(),
                new OperationTargetRequest.SyncTargetRequest(identityKey),
                new OperationParameters.NoParameters(),
                base.revisionId(), hash(base), null);

        BatchCommitResult result = commit("batch-sync", base, create, remove, null);

        assertEquals(BatchCommitResult.NO_OP, result.status());
        assertEquals(1, count("timeline.timeline_revision"));
        assertEquals(1, count("timeline.apply_command"));
    }

    @Test
    void audioGainBatchCommitsOneRevision() {
        TimelineRevision base = saveRevision(twoClipDocumentWithAudio());
        OperationRequest gain = new OperationRequest(
                OperationDefinition.V1.SET_AUDIO_GAIN.definitionId(),
                OperationDefinition.V1.SET_AUDIO_GAIN.version(),
                new OperationTargetRequest.AudioTargetRequest(
                        AudioMixInput.of("video-main", "clip-a")),
                new OperationParameters.AudioGainParameters(new AudioGain(0.5)),
                base.revisionId(), hash(base), null);

        BatchCommitResult result = commit("batch-audio", base, gain, null);

        assertEquals(BatchCommitResult.APPLIED, result.status());
        assertEquals(2, count("timeline.timeline_revision"));
        TimelineDocument committed = saveService.findPayloadDocument(TENANT, result.newRevisionId())
                .orElseThrow();
        assertEquals(0.5, committed.getAudioMix().routes().get(0).gain().linear());
    }

    @Test
    void semanticNoOpReplayReturnsTheCompletedNoOpCommand() {
        TimelineRevision base = saveRevision(twoClipDocument());
        String batchId = "batch-noop-replay";
        BatchCommitResult first = commit(batchId, base,
                moveAbsolute(base, "clip-a", 2), moveAbsolute(base, "clip-a", 0), null);
        BatchCommitResult second = commit(batchId, base,
                moveAbsolute(base, "clip-a", 2), moveAbsolute(base, "clip-a", 0), null);

        assertEquals(BatchCommitResult.NO_OP, first.status());
        assertEquals(BatchCommitResult.NO_OP, second.status());
        assertTrue(second.replayed());
        assertNull(second.newRevisionId());
        assertEquals(1, count("timeline.timeline_revision"));
        assertEquals(1, count("timeline.apply_command"));
    }

    @Test
    void restoreEffectOnlyChangeAndReplayPublishExactlyOneScopedFact() {
        TimelineRevision a = revisionWithEffect(null, "4");
        TimelineRevision b = revisionWithEffect(a.revisionId(), "99");
        assertEquals(hash(a), hash(b));
        assertNotEquals(a.semanticContext().revisionSemanticDigest(), b.semanticContext().revisionSemanticDigest());
        var request = restoreRequest("restore-effect-only", b, a);
        var actor = mutation().actor();
        var preview = batch.preview(request, OperationBatchContext.preview(actor, "test"));
        assertEquals(hash(a), preview.finalCandidateHash());
        assertEquals(false, preview.noOp());
        assertEquals("APPLIED", preview.outcomes().getFirst().status());
        var context = OperationBatchContext.apply(request.batchId(), actor, null, "test", preview.batchDigest());
        var result = batch.commit(request, context);
        assertEquals(BatchCommitResult.APPLIED, result.status());
        assertEquals(result.newRevisionId(), head());
        assertEquals(b.revisionId(), parentOf(result.newRevisionId()));
        var c = saveService.findById(TENANT, result.newRevisionId());
        assertEquals(a.semanticContext(), c.semanticContext());
        assertEquals(3, count("timeline.timeline_revision"));
        var replay = batch.commit(request, context);
        assertTrue(replay.replayed());
        assertEquals(c.revisionId(), replay.newRevisionId());
        assertEquals(3, count("timeline.timeline_revision"));
        var events = jdbc.queryForList("""
                select event_version, aggregate_id, aggregate_type, payload
                from infrastructure.outbox_events where event_type='timeline.restored'
                """);
        assertEquals(1, events.size());
        var row = events.getFirst();
        assertEquals(2, ((Number) row.get("event_version")).intValue());
        var fact = (TimelineRestoredEvent) eventRouter.decode("timeline.restored", 2,
                (String) row.get("aggregate_type"), (String) row.get("aggregate_id"), row.get("payload").toString()).payload();
        assertEquals(c.revisionId(), fact.newRevisionId());
        assertEquals(a.revisionId(), fact.restoredFromRevisionId());
        assertEquals(TENANT, fact.tenantId());
        assertEquals(PROJECT, fact.projectId());
        assertEquals(ACTOR, fact.authorUserId());
    }

    @Test
    void restoreIdenticalCompleteSemanticsIsNoOpEvenWithDifferentSnapshotHandles() {
        TimelineRevision a = revisionWithEffect(null, "4");
        TimelineRevision b = revisionWithEffect(a.revisionId(), "4");
        assertNotEquals(a.semanticContext().effectReference().snapshotId(), b.semanticContext().effectReference().snapshotId());
        assertEquals(a.semanticContext().revisionSemanticDigest(), b.semanticContext().revisionSemanticDigest());
        var request = restoreRequest("restore-equal-effect", b, a);
        var actor = mutation().actor();
        var preview = batch.preview(request, OperationBatchContext.preview(actor, "test"));
        assertTrue(preview.noOp());
        assertEquals("NO_OP", preview.outcomes().getFirst().status());
        var result = batch.commit(request, OperationBatchContext.apply(
                request.batchId(), actor, null, "test", preview.batchDigest()));
        assertEquals(BatchCommitResult.NO_OP, result.status());
        assertEquals(b.revisionId(), head());
        assertEquals(2, count("timeline.timeline_revision"));
        assertEquals(0, eventCount("timeline.restored"));
    }

    @Test
    void restoreEventAppendFailureRollsBackRevisionHeadCommandAndOutbox() {
        TimelineRevision a = revisionWithEffect(null, "4");
        TimelineRevision b = revisionWithEffect(a.revisionId(), "99");
        var request = restoreRequest("restore-effect-rollback", b, a);
        var actor = mutation().actor();
        var preview = batch.preview(request, OperationBatchContext.preview(actor, "test"));
        int created = eventCount("timeline.revision.created");
        long snapshots = count("timeline.timeline_snapshot");
        org.mockito.Mockito.doAnswer(call -> {
            assertNotNull(call.callRealMethod());
            DSLContext transaction = call.getArgument(1);
            assertEquals(1, transaction.fetchOne("select count(*) from infrastructure.outbox_events where event_type='timeline.restored'")
                    .get(0, Integer.class));
            throw new IllegalStateException("injected failure after restore outbox insert");
        }).when(outbox).appendInTransaction(org.mockito.ArgumentMatchers.argThat(
                item -> item.type() == TimelineOutboxEvents.RESTORED), org.mockito.ArgumentMatchers.any());

        var failure = assertThrows(IllegalStateException.class, () -> batch.commit(request, OperationBatchContext.apply(
                request.batchId(), actor, null, "test", preview.batchDigest())));
        assertEquals("injected failure after restore outbox insert", failure.getMessage());
        assertEquals(b.revisionId(), head());
        assertEquals(2, count("timeline.timeline_revision"));
        assertEquals(snapshots, count("timeline.timeline_snapshot"));
        assertEquals(0, count("timeline.apply_command"));
        assertEquals(0, eventCount("timeline.restored"));
        assertEquals(created, eventCount("timeline.revision.created"));
    }

    private int eventCount(String type) {
        return jdbc.queryForObject("select count(*) from infrastructure.outbox_events where event_type=?", Integer.class, type);
    }

    private OperationBatchRequest restoreRequest(String id, TimelineRevision base, TimelineRevision source) {
        var definition = OperationDefinition.V3.RESTORE_REVISION;
        var step = new OperationRequest(definition.definitionId(), definition.version(),
                new OperationTargetRequest.TimelineTargetRequest(PROJECT),
                new OperationParameters.RestoreRevisionParameters(source.revisionId()), base.revisionId(), hash(base), null);
        return new OperationBatchRequest(id, new VersionedAggregateRef("TIMELINE", TENANT, PROJECT, PROJECT, "main"),
                base.revisionId(), List.of(step), null);
    }

    @Test
    void namedBranchTrimPreservesOrderedEffectSemanticsAndExactReplay() {
        TimelineRevision a = revisionWithEffects(null, List.of(blur("z-first", "4"), blur("a-second", "13")));
        TimelineRevision b = revisionWithEffect(a.revisionId(), "99");
        var actor = mutation().actor();
        var target = new VersionedAggregateRef("TIMELINE", TENANT, PROJECT, PROJECT, "alternate");
        long before = count("timeline.timeline_revision");
        saveService.createEditingRef(mutation(), "alternate", a.revisionId(), "fork-effects").orElseThrow();
        assertEquals(before, count("timeline.timeline_revision"));
        assertEquals(a.revisionId(), refHead("alternate"));
        assertEquals(b.revisionId(), refHead("main"));

        var request = new OperationBatchRequest("trim-unrelated", target, a.revisionId(),
                List.of(trim(a, "clip-b", "1/1")), null);
        var preview = batch.preview(request, OperationBatchContext.preview(actor, "test"));
        var context = OperationBatchContext.apply(request.batchId(), actor, null, "test", preview.batchDigest());
        var result = batch.commit(request, context);
        assertEquals(BatchCommitResult.APPLIED, result.status());
        assertEquals(a.revisionId(), parentOf(result.newRevisionId()));
        assertEquals(result.newRevisionId(), refHead("alternate"));
        assertEquals(b.revisionId(), refHead("main"));
        assertEquals(before + 1, count("timeline.timeline_revision"));
        TimelineRevision c = saveService.findById(TENANT, result.newRevisionId());
        var store = new JdbcEffectSemanticSnapshotStore(dsl);
        var original = store.findById(PROJECT, TENANT, a.semanticContext().effectReference().snapshotId()).orElseThrow();
        var inherited = store.findById(PROJECT, TENANT, c.semanticContext().effectReference().snapshotId()).orElseThrow();
        assertEquals(a.semanticContext().effectReference(), c.semanticContext().effectReference());
        assertEquals(original.entries(), inherited.entries());
        assertEquals(List.of("z-first", "a-second"), inherited.entries().stream().map(entry -> entry.effectInstanceId()).toList());
        assertEquals(List.of("4", "13"), inherited.entries().stream().map(entry -> entry.parameters().getFirst().value()).toList());
        assertTrue(inherited.entries().stream().allMatch(entry -> entry.target().equals(new ClipEffectTarget("video-main", "clip-a"))));
        EffectSemanticSnapshotCanonicalSemantics.verifySnapshotDigest(inherited);
        assertNotEquals(a.semanticContext().revisionSemanticDigest(), c.semanticContext().revisionSemanticDigest());
        var clips = saveService.findPayloadDocument(TENANT, c.revisionId()).orElseThrow().getTracks().getFirst().clips();
        assertEquals(twoClipDocument().getTracks().getFirst().clips().getFirst().getEndTime(), clips.getFirst().getEndTime());
        assertEquals(MediaTime.ofRational(9, 1), clips.get(1).getEndTime());
        var replay = batch.commit(request, context);
        assertTrue(replay.replayed());
        assertEquals(c.revisionId(), replay.newRevisionId());
        assertEquals(before + 1, count("timeline.timeline_revision"));
        assertEquals(0, eventCount("timeline.restored"));

        var trimBound = new OperationBatchRequest("trim-bound", target, c.revisionId(),
                List.of(trim(c, "clip-a", "1/1")), null);
        var boundPlan = batch.preview(trimBound, OperationBatchContext.preview(actor, "test"));
        var boundResult = batch.commit(trimBound,
                OperationBatchContext.apply(trimBound.batchId(), actor, null, "test", boundPlan.batchDigest()));
        TimelineRevision d = saveService.findById(TENANT, boundResult.newRevisionId());
        assertEquals(c.revisionId(), parentOf(d.revisionId()));
        assertEquals(original.reference(), d.semanticContext().effectReference());
        assertEquals(MediaTime.ofRational(3, 1), saveService.findPayloadDocument(TENANT, d.revisionId())
                .orElseThrow().getTracks().getFirst().clips().getFirst().getEndTime());
        var invalid = new OperationBatchRequest("delete-bound", target, d.revisionId(),
                List.of(delete(d, "clip-a")), null);
        assertEquals(BatchErrorCode.FINAL_VALIDATION_FAILED, assertThrows(BatchException.class,
                () -> batch.preview(invalid, OperationBatchContext.preview(actor, "test"))).code());
        assertEquals(before + 2, count("timeline.timeline_revision"));
        assertEquals(d.revisionId(), refHead("alternate"));
        assertEquals(b.revisionId(), refHead("main"));
        System.out.printf("N02_EFFECT_PROOF a=%s b=%s c=%s d=%s parentC=%s main=%s alternate=%s "
                        + "rowsBefore=%d rowsAfterC=%d rowsAfterReplay=%d rowsAfterD=%d "
                        + "snapshot=%s digest=%s order=z-first,a-second parameters=4,13 target=video-main/clip-a%n",
                a.revisionId(), b.revisionId(), c.revisionId(), d.revisionId(), result.parentRevisionId(),
                refHead("main"), refHead("alternate"), before, before + 1, before + 1,
                count("timeline.timeline_revision"), inherited.id().value(), inherited.contentDigest());
    }

    @Test
    void confirmedTrimsAndHistoryReplayPreserveCompleteOrderedSemantics() {
        TimelineRevision a = revisionWithEffects(null, List.of(blur("z-first", "4"), blur("a-second", "13")));
        TimelineRevision b = saveService.findById(TENANT,
                commit("hist-trim-1", a, trim(a, "clip-b", "1/1"), null).newRevisionId());
        TimelineRevision c = saveService.findById(TENANT,
                commit("hist-trim-2", b, trim(b, "clip-b", "1/1"), null).newRevisionId());
        var reference = a.semanticContext().effectReference();
        assertEquals(reference, b.semanticContext().effectReference());
        assertEquals(reference, c.semanticContext().effectReference());
        var entries = new JdbcEffectSemanticSnapshotStore(dsl)
                .findById(PROJECT, TENANT, reference.snapshotId()).orElseThrow().entries();
        assertEquals(List.of("z-first", "a-second"), entries.stream().map(e -> e.effectInstanceId()).toList());
        assertEquals(List.of("4", "13"), entries.stream().map(e -> e.parameters().getFirst().value()).toList());
        assertTrue(entries.stream().allMatch(e -> e.target().equals(new ClipEffectTarget("video-main", "clip-a"))));
        var actor = mutation().actor();
        TimelineRevision cursor = c;
        long countBefore = count("timeline.timeline_revision");
        int index = 0;
        for (TimelineRevision source : List.of(b, a, b, c)) {
            var request = restoreRequest("hist-step-" + ++index, cursor, source);
            var plan = batch.preview(request, OperationBatchContext.preview(actor, "test"));
            var command = OperationBatchContext.apply(request.batchId(), actor, null, "test", plan.batchDigest());
            var result = batch.commit(request, command);
            assertEquals(BatchCommitResult.APPLIED, result.status());
            var next = saveService.findById(TENANT, result.newRevisionId());
            assertEquals(cursor.revisionId(), next.parentRevisionId());
            assertEquals(source.semanticContext(), next.semanticContext());
            assertEquals(next.revisionId(), head());
            assertEquals(countBefore + index, count("timeline.timeline_revision"));
            var reply = batch.commit(request, command);
            assertTrue(reply.replayed());
            assertEquals(next.revisionId(), reply.newRevisionId());
            assertEquals(countBefore + index, count("timeline.timeline_revision"));
            System.out.printf("N03_SEMANTICS step=%d source=%s revision=%s parent=%s digest=%s effects=%s rows=%d%n",
                    index, source.revisionId(), next.revisionId(), cursor.revisionId(),
                    next.semanticContext().revisionSemanticDigest(), reference.contentDigest(), countBefore + index);
            cursor = next;
        }
        assertEquals(4, eventCount("timeline.restored"));
    }

    @Test
    void pasteRejectsSeparateEffectBindingWithoutWriting() {
        TimelineRevision source = revisionWithEffect(null, "4");
        var def = OperationDefinition.V3.PASTE_CLIPS;
        var step = new OperationRequest(def.definitionId(), def.version(),
                new OperationTargetRequest.TimelineTargetRequest(PROJECT),
                new OperationParameters.PasteClipsParameters("video-main", List.of(TimelineClipId.of("clip-a")),
                        List.of(TimelineClipId.of("clip-new")), time("12/1")), source.revisionId(), hash(source), null);
        var request = new OperationBatchRequest("unsupported-paste", new VersionedAggregateRef("TIMELINE",
                TENANT, PROJECT, PROJECT, "main"), source.revisionId(), List.of(step), null);
        long rows = count("timeline.timeline_revision");
        var rejected = assertThrows(BatchException.class,
                () -> batch.preview(request, OperationBatchContext.preview(mutation().actor(), "test")));
        assertEquals(BatchErrorCode.STEP_RESOLUTION_FAILED, rejected.code());
        assertTrue(rejected.getMessage().contains("separate effect binding on clip clip-a"));
        assertEquals(source.revisionId(), head());
        assertEquals(rows, count("timeline.timeline_revision"));
        assertEquals(0, count("timeline.apply_command"));
    }

    @Test
    void multiMoveRetainsParentEffectsAndWritesOneRevision() {
        var source = revisionWithEffects(null, List.of(blur("z-first", "4"), blur("a-second", "13")));
        var request = multiMoveRequest("n05-keep-effects", source);
        var actor = mutation().actor();
        var plan = batch.preview(request, OperationBatchContext.preview(actor, "test"));
        assertEquals(1, count("timeline.timeline_revision"));
        assertEquals(0, count("timeline.apply_command"));
        var context = OperationBatchContext.apply(request.batchId(), actor, null, "test", plan.batchDigest());
        var applied = batch.commit(request, context);
        assertEquals(source.revisionId(), parentOf(applied.newRevisionId()));
        assertEquals(applied.newRevisionId(), head());
        var moved = saveService.findById(TENANT, applied.newRevisionId());
        assertEquals(source.semanticContext().effectReference(), moved.semanticContext().effectReference());
        assertNotEquals(source.semanticContext().revisionSemanticDigest(), moved.semanticContext().revisionSemanticDigest());
        var replay = batch.commit(request, context);
        assertEquals(applied.newRevisionId(), replay.newRevisionId());
        assertEquals(2, count("timeline.timeline_revision"));
        assertEquals(1, count("timeline.apply_command"));
        System.out.printf("N05_EFFECTS source=%s moved=%s parent=%s snapshot=%s digest=%s rows=2 commands=1%n",
                source.revisionId(), moved.revisionId(), moved.parentRevisionId(),
                moved.semanticContext().effectReference().snapshotId(),
                moved.semanticContext().effectReference().contentDigest());
    }

    @Test
    void multiMoveRetainsLocalSyncAndAudioRoutes() {
        var sourceDocument = twoClipDocumentWithAudio();
        var first = TimelineClipId.of("clip-a");
        var second = TimelineClipId.of("clip-b");
        var synced = new TimelineDocument(sourceDocument.getSchemaVersion(), sourceDocument.getTracks(),
                sourceDocument.getMetadata(), sourceDocument.getAudioMix(),
                List.of(SyncRelationship.of(first, time("1/1"), second, time("2/1"))),
                sourceDocument.getTextElements(), sourceDocument.getTransitions(), sourceDocument.getAutomations());
        var source = saveRevision(synced);
        var request = multiMoveRequest("n05-preserve-relationships", source);
        var actor = mutation().actor();
        var preview = batch.preview(request, OperationBatchContext.preview(actor, "test"));
        var result = batch.commit(request,
                OperationBatchContext.apply(request.batchId(), actor, null, "test", preview.batchDigest()));
        var persisted = saveService.findPayloadDocument(TENANT, result.newRevisionId()).orElseThrow();
        assertEquals(Set.copyOf(synced.getSemanticRelationships()), Set.copyOf(persisted.getSemanticRelationships()));
        assertEquals(synced.getAudioMix(), persisted.getAudioMix());
        assertEquals(source.revisionId(), parentOf(result.newRevisionId()));
        assertEquals(2, count("timeline.timeline_revision"));
        assertEquals(1, count("timeline.apply_command"));
    }

    @Test
    void multiMoveRejectsGroupedClipsBeforeWriting() {
        var original = twoClipDocument();
        var group = GroupRelationship.of(GroupId.of("group-1"),
                List.of(TimelineClipId.of("clip-a"), TimelineClipId.of("clip-b")));
        var grouped = new TimelineDocument(original.getSchemaVersion(), original.getTracks(), original.getMetadata(),
                original.getAudioMix(), List.of(group), List.of(), List.of(), List.of());
        var source = saveRevision(grouped);
        var request = multiMoveRequest("n05-reject-group", source);
        var actor = mutation().actor();
        var preview = assertThrows(BatchException.class,
                () -> batch.preview(request, OperationBatchContext.preview(actor, "test")));
        assertTrue(preview.getMessage().contains("group binding group-1"), preview.getMessage());
        var rejected = assertThrows(BatchException.class, () -> batch.commit(request,
                OperationBatchContext.apply(request.batchId(), actor, null, "test", "0".repeat(64))));
        assertTrue(rejected.getMessage().contains("group binding group-1"), rejected.getMessage());
        assertEquals(source.revisionId(), head());
        assertEquals(1, count("timeline.timeline_revision"));
        assertEquals(0, count("timeline.apply_command"));
    }

    private static OperationBatchRequest multiMoveRequest(String id, TimelineRevision source) {
        var selected = new SelectionSpec.ExplicitObjectSelection(List.of(TimelineClipId.of("clip-a"), TimelineClipId.of("clip-b")));
        var op = new OperationRequest(OperationDefinition.V1.MOVE.definitionId(), OperationDefinition.V1.MOVE.version(),
                new OperationTargetRequest.ClipSelectionTargetRequest(PROJECT, selected, SelectionSpec.ExpansionPolicy.EXACT),
                new OperationParameters.MoveParameters(time("1/30"), false), source.revisionId(), hash(source), null);
        return new OperationBatchRequest(id, new VersionedAggregateRef("TIMELINE", TENANT, PROJECT, PROJECT, "main"),
                source.revisionId(), List.of(op), null);
    }

    @Test
    void rippleDeletePreservesMovedSidecarEffectsAndRestoresFullSemantics() {
        var first = clip("clip-b", 0, 4);
        var later = clip("clip-a", 6, 10);
        var sourceDocument = new TimelineDocument(TimelineDocument.CURRENT_SCHEMA_VERSION,
                List.of(new TimelineTrack("video-main", "Video", TrackType.VIDEO, List.of(first, later))),
                twoClipDocument().getMetadata());
        var source = saveWithEffects(sourceDocument, null, List.of(blur("z-first", "4"), blur("a-second", "13")));
        var request = rippleRequest("ripple-with-effects", source, "clip-b");
        var actor = mutation().actor();
        var plan = batch.preview(request, OperationBatchContext.preview(actor, "test"));
        assertEquals(1, count("timeline.timeline_revision"));
        assertEquals(0, count("timeline.apply_command"));
        var context = OperationBatchContext.apply(request.batchId(), actor, null, "test", plan.batchDigest());
        var applied = batch.commit(request, context);
        var moved = saveService.findById(TENANT, applied.newRevisionId());
        assertEquals(source.revisionId(), moved.parentRevisionId());
        assertEquals(source.semanticContext().effectReference(), moved.semanticContext().effectReference());
        var snapshot = new JdbcEffectSemanticSnapshotStore(dsl).findById(PROJECT, TENANT,
                moved.semanticContext().effectReference().snapshotId()).orElseThrow();
        assertEquals(List.of("z-first", "a-second"), snapshot.entries().stream().map(e -> e.effectInstanceId()).toList());
        assertEquals(List.of("4", "13"), snapshot.entries().stream().map(e -> e.parameters().getFirst().value()).toList());
        var placed = saveService.findPayloadDocument(TENANT, moved.revisionId()).orElseThrow()
                .getTracks().getFirst().clips();
        assertEquals(1, placed.size());
        assertEquals("clip-a", placed.getFirst().getClipId().value());
        assertEquals(time("2/1"), placed.getFirst().getStartTime());
        assertEquals(time("6/1"), placed.getFirst().getEndTime());
        assertEquals(applied.newRevisionId(), batch.commit(request, context).newRevisionId());
        assertEquals(2, count("timeline.timeline_revision"));
        assertEquals(1, count("timeline.apply_command"));
        var undo = restoreRequest("undo-ripple-effects", moved, source);
        var restored = saveService.findById(TENANT, applyPrepared(undo).newRevisionId());
        assertEquals(moved.revisionId(), restored.parentRevisionId());
        assertEquals(source.semanticContext(), restored.semanticContext());
        var redo = restoreRequest("redo-ripple-effects", restored, moved);
        var restoredMove = saveService.findById(TENANT, applyPrepared(redo).newRevisionId());
        assertEquals(restored.revisionId(), restoredMove.parentRevisionId());
        assertEquals(moved.semanticContext(), restoredMove.semanticContext());
        assertEquals(4, count("timeline.timeline_revision"));
        System.out.printf("N07_EFFECTS source=%s ripple=%s undo=%s redo=%s effectDigest=%s rows=4%n",
                source.revisionId(), moved.revisionId(), restored.revisionId(), restoredMove.revisionId(),
                snapshot.contentDigest());
    }

    @Test
    void rippleDeleteRejectsRelationshipOnLaterClipAndSeparationOfDeletedEffect() {
        var original = twoClipDocument();
        var foreign = new TimelineTrack("other-video", "Other video", TrackType.VIDEO, List.of(clip("clip-c", 6, 10)));
        var sync = SyncRelationship.of(TimelineClipId.of("clip-b"), time("1/1"),
                TimelineClipId.of("clip-c"), time("1/1"));
        var synced = new TimelineDocument(original.getSchemaVersion(), List.of(original.getTracks().getFirst(), foreign),
                original.getMetadata(), original.getAudioMix(), List.of(sync), List.of(), List.of(), List.of());
        var source = saveRevision(synced);
        assertRippleRejected(rippleRequest("ripple-cross-track-sync", source, "clip-a"), "on clip clip-b");
        var effectSource = revisionWithEffect(source.revisionId(), "4");
        assertRippleRejected(rippleRequest("ripple-deleted-effect", effectSource, "clip-a"), "video-main/clip-a");
    }

    @Test
    void rippleDeleteRollsBackChildHeadCommandAndOutboxAfterInsertFailure() {
        var source = saveRevision(twoClipDocument());
        var request = rippleRequest("ripple-fail-after-insert", source, "clip-a");
        assertRippleRollsBack(source, request);
    }

    @Test
    void rippleInsertRollsBackCopyShiftPinsHeadLedgerAndOutbox() {
        String pin = "a".repeat(64);
        jdbc.update("""
                insert into artifacts.artifact(id,tenant_id,project_id,content_digest,byte_length,
                    media_type,artifact_kind,state,schema_version,created_at)
                values ('insert-source',?,?,?,1024,'VIDEO','SOURCE_MEDIA','AVAILABLE',1,now())
                """, TENANT, PROJECT, pin);
        var original = new TimelineClip("copy-me", "insert-source", "video-stream", "insert-source", pin,
                time("0/1"), time("2/1"), time("0/1"), time("2/1"), "MEDIA_STREAM",
                ConstantRateTemporalMapping.identity());
        var track = new TimelineTrack("video-main", "Video", TrackType.VIDEO, List.of(original, clip("later", 2, 5)));
        var source = saveRevision(new TimelineDocument(TimelineDocument.CURRENT_SCHEMA_VERSION,
                List.of(track), TimelineMetadata.empty()));
        var def = OperationDefinition.V3.RIPPLE_INSERT;
        var op = new OperationRequest(def.definitionId(), def.version(), new OperationTargetRequest.TimelineTargetRequest(PROJECT),
                new OperationParameters.PasteClipsParameters(track.trackId(), List.of(original.getClipId()),
                        List.of(TimelineClipId.of("copy")), time("2/1")), source.revisionId(), hash(source), null);
        var request = new OperationBatchRequest("failed-ripple-insert", new VersionedAggregateRef("TIMELINE",
                TENANT, PROJECT, PROJECT, "main"), source.revisionId(), List.of(op), null);
        long pins = count("artifacts.artifact_pin");
        assertRippleRollsBack(source, request);
        assertEquals(pins, count("artifacts.artifact_pin"));
    }

    private TimelineRevision slipFixture() {
        String pin = "a".repeat(64);
        jdbc.update("""
                insert into artifacts.artifact(id,tenant_id,project_id,content_digest,byte_length,
                    media_type,artifact_kind,state,schema_version,created_at)
                values ('slip-source',?,?,?,1024,'VIDEO','SOURCE_MEDIA','AVAILABLE',1,now())
                """, TENANT, PROJECT, pin);
        var clip = new TimelineClip("clip-a", "slip-source", "video-stream", "slip-source", pin, time("5/1"),
                time("8/1"), time("2/1"), time("5/1"), "MEDIA_STREAM", ConstantRateTemporalMapping.identity());
        var track = new TimelineTrack("video-main", "Video", TrackType.VIDEO, List.of(clip));
        return saveWithEffects(new TimelineDocument(TimelineDocument.CURRENT_SCHEMA_VERSION, List.of(track),
                TimelineMetadata.empty()), null, List.of(blur("z-first", "4"), blur("a-second", "13")));
    }

    private OperationBatchRequest slipRequest(TimelineRevision base) {
        var def = OperationDefinition.V3.SLIP_CLIP;
        var target = new OperationTargetRequest.ClipSelectionTargetRequest(PROJECT,
                new SelectionSpec.ExplicitObjectSelection(List.of(TimelineClipId.of("clip-a"))), SelectionSpec.ExpansionPolicy.EXACT);
        var op = new OperationRequest(def.definitionId(), def.version(), target, OperationParameters.SlipParameters.parse("+1"),
                base.revisionId(), hash(base), null);
        return new OperationBatchRequest("slip-one", new VersionedAggregateRef("TIMELINE", TENANT, PROJECT, PROJECT, "main"),
                base.revisionId(), List.of(op), null);
    }

    @Test
    void slipRetainsOrderedBoundEffectsAndRestoresFullSemantics() {
        var base = slipFixture();
        var accepted = saveService.findById(TENANT, applyPrepared(slipRequest(base)).newRevisionId());
        assertEquals(base.revisionId(), accepted.parentRevisionId());
        assertEquals(base.semanticContext().effectReference(), accepted.semanticContext().effectReference());
        var effects = new JdbcEffectSemanticSnapshotStore(dsl).findById(PROJECT, TENANT,
                accepted.semanticContext().effectReference().snapshotId()).orElseThrow();
        assertEquals(List.of("z-first", "a-second"), effects.entries().stream().map(e -> e.effectInstanceId()).toList());
        assertEquals(List.of("4", "13"), effects.entries().stream().map(e -> e.parameters().getFirst().value()).toList());
        var clip = saveService.findPayloadDocument(TENANT, accepted.revisionId()).orElseThrow().getTracks().getFirst().clips().getFirst();
        assertEquals(time("3/1"), clip.getTrimStart());
        assertEquals(time("6/1"), clip.getTrimEnd());
        var undone = saveService.findById(TENANT, applyPrepared(restoreRequest("undo-slip", accepted, base)).newRevisionId());
        var redone = saveService.findById(TENANT, applyPrepared(restoreRequest("redo-slip", undone, accepted)).newRevisionId());
        assertEquals(base.semanticContext(), undone.semanticContext());
        assertEquals(accepted.semanticContext(), redone.semanticContext());
    }

    @Test
    void slipRollsBackSourceRangePinsHeadLedgerAndEventsAfterWriteFailure() {
        var source = slipFixture();
        var snapshots = jdbc.queryForList("select id, payload_json::text from timeline.timeline_snapshot order by id");
        var pins = jdbc.queryForList("select * from artifacts.artifact_pin order by revision_id, artifact_id");
        assertRippleRollsBack(source, slipRequest(source));
        assertEquals(snapshots, jdbc.queryForList("select id, payload_json::text from timeline.timeline_snapshot order by id"));
        assertEquals(pins, jdbc.queryForList("select * from artifacts.artifact_pin order by revision_id, artifact_id"));
    }

    @Test
    void rollRollsBackBothRangesPinsHeadLedgerAndOutbox() {
        String pin = "a".repeat(64);
        jdbc.update("""
                insert into artifacts.artifact(id,tenant_id,project_id,content_digest,byte_length,
                    media_type,artifact_kind,state,schema_version,created_at)
                values ('roll-source',?,?,?,1024,'VIDEO','SOURCE_MEDIA','AVAILABLE',1,now())
                """, TENANT, PROJECT, pin);
        var left = new TimelineClip("left", "roll-source", "video-stream", "roll-source", pin, time("2/1"),
                time("5/1"), time("1/1"), time("4/1"), "MEDIA_STREAM", ConstantRateTemporalMapping.identity());
        var right = new TimelineClip("right", "roll-source", "video-stream", "roll-source", pin, time("5/1"),
                time("9/1"), time("2/1"), time("6/1"), "MEDIA_STREAM", ConstantRateTemporalMapping.identity());
        var track = new TimelineTrack("video-main", "Video", TrackType.VIDEO, List.of(left, right));
        var source = saveRevision(new TimelineDocument(TimelineDocument.CURRENT_SCHEMA_VERSION,
                List.of(track), TimelineMetadata.empty()));
        var def = OperationDefinition.V3.ROLL_CUT;
        var op = new OperationRequest(def.definitionId(), def.version(), new OperationTargetRequest.TimelineTargetRequest(PROJECT),
                new OperationParameters.RollCutParameters(track.trackId(), left.getClipId(), right.getClipId(), time("6/1")),
                source.revisionId(), hash(source), null);
        var request = new OperationBatchRequest("failed-roll", new VersionedAggregateRef("TIMELINE",
                TENANT, PROJECT, PROJECT, "main"), source.revisionId(), List.of(op), null);
        var snapshots = jdbc.queryForList("select id, payload_json::text from timeline.timeline_snapshot order by id");
        long pins = count("artifacts.artifact_pin");
        assertRippleRollsBack(source, request);
        assertEquals(snapshots, jdbc.queryForList("select id, payload_json::text from timeline.timeline_snapshot order by id"));
        assertEquals(pins, count("artifacts.artifact_pin"));
    }

    private void assertRippleRollsBack(TimelineRevision source, OperationBatchRequest request) {
        var actor = mutation().actor();
        var plan = batch.preview(request, OperationBatchContext.preview(actor, "test"));
        long snapshots = count("timeline.timeline_snapshot");
        int events = eventCount("timeline.revision.created");
        org.mockito.Mockito.doAnswer(call -> {
            assertNotNull(call.callRealMethod());
            DSLContext transaction = call.getArgument(1);
            assertEquals(events + 1, transaction.fetchOne("select count(*) from infrastructure.outbox_events where "
                    + "event_type='timeline.revision.created'").get(0, Integer.class));
            assertEquals(2, transaction.fetchOne("select count(*) from timeline.timeline_revision where project_id=?",
                    PROJECT).get(0, Integer.class));
            throw new IllegalStateException("injected failure after ripple revision event");
        }).when(outbox).appendInTransaction(org.mockito.ArgumentMatchers.argThat(
                item -> item.type() == TimelineOutboxEvents.REVISION_CREATED), org.mockito.ArgumentMatchers.any());
        var failure = assertThrows(IllegalStateException.class, () -> batch.commit(request,
                OperationBatchContext.apply(request.batchId(), actor, null, "test", plan.batchDigest())));
        assertEquals("injected failure after ripple revision event", failure.getMessage());
        assertEquals(source.revisionId(), head());
        assertEquals(1, count("timeline.timeline_revision"));
        assertEquals(snapshots, count("timeline.timeline_snapshot"));
        assertEquals(0, count("timeline.apply_command"));
        assertEquals(events, eventCount("timeline.revision.created"));
        System.out.printf("RIPPLE_ROLLBACK command=%s head=%s rows=1 snapshots=%d commands=0 events=%d%n",
                request.batchId(), source.revisionId(), snapshots, events);
    }

    private void assertRippleRejected(OperationBatchRequest request, String diagnostic) {
        var actor = mutation().actor();
        long rows = count("timeline.timeline_revision");
        long commands = count("timeline.apply_command");
        var planned = assertThrows(BatchException.class,
                () -> batch.preview(request, OperationBatchContext.preview(actor, "test")));
        assertTrue(planned.getMessage().contains(diagnostic), planned.getMessage());
        var applied = assertThrows(BatchException.class, () -> batch.commit(request,
                OperationBatchContext.apply(request.batchId(), actor, null, "test", "0".repeat(64))));
        assertTrue(applied.getMessage().contains(diagnostic), applied.getMessage());
        assertEquals(request.baseRevisionId(), head());
        assertEquals(rows, count("timeline.timeline_revision"));
        assertEquals(commands, count("timeline.apply_command"));
    }

    private BatchCommitResult applyPrepared(OperationBatchRequest request) {
        var actor = mutation().actor();
        var plan = batch.preview(request, OperationBatchContext.preview(actor, "test"));
        return batch.commit(request, OperationBatchContext.apply(request.batchId(), actor, null, "test", plan.batchDigest()));
    }

    private static OperationBatchRequest rippleRequest(String id, TimelineRevision source, String clip) {
        var definition = OperationDefinition.V3.RIPPLE_DELETE;
        var target = new OperationTargetRequest.ClipSelectionTargetRequest(PROJECT,
                new SelectionSpec.ExplicitObjectSelection(List.of(TimelineClipId.of(clip))), SelectionSpec.ExpansionPolicy.EXACT);
        var operation = new OperationRequest(definition.definitionId(), definition.version(), target,
                new OperationParameters.NoParameters(), source.revisionId(), hash(source), null);
        return new OperationBatchRequest(id, new VersionedAggregateRef("TIMELINE", TENANT, PROJECT, PROJECT, "main"),
                source.revisionId(), List.of(operation), null);
    }

    private TimelineRevision revisionWithEffect(String parent, String radius) {
        return revisionWithEffects(parent, List.of(blur("blur", radius)));
    }

    private static EffectInstance blur(String id, String radius) {
        return new EffectInstance(id, "definition-blur", "1", EffectInstance.EffectMediaType.VIDEO, true,
                new MediaClip.TimeRange(MediaTime.ZERO, MediaTime.ofRational(2, 1)),
                Map.of("radiusPixels", radius), Map.of(), new ClipEffectTarget("video-main", "clip-a"),
                EffectInstance.EffectProvenance.untracked());
    }

    private TimelineRevision revisionWithEffects(String parent, List<EffectInstance> effects) {
        return saveWithEffects(twoClipDocument(), parent, effects);
    }

    private TimelineRevision saveWithEffects(TimelineDocument document, String parent, List<EffectInstance> effects) {
        var definition = new EffectInstance.EffectDefinition("definition-blur", "1", EffectInstance.EffectCategory.GAUSSIAN_BLUR,
                List.of(EffectInstance.EffectMediaType.VIDEO),
                Map.of("radiusPixels", new EffectInstance.ParameterSchema("radiusPixels", "string", null, null, "4", List.of())),
                EffectInstance.EffectTemporalBehavior.PRESERVE_DURATION, List.of("radiusPixels"),
                List.of("video.effect.gaussian-blur"), List.of());
        return saveService.saveRevisionWithEffects(mutation(), parent, document, effects, List.of(definition));
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
        assertEquals(1, count("timeline.timeline_revision"));
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
        return commitMany(batchId, base, operations, message);
    }

    private BatchCommitResult commitThree(String batchId, TimelineRevision base,
                                          OperationRequest first, OperationRequest second,
                                          OperationRequest third) {
        return commitMany(batchId, base, List.of(first, second, third), null);
    }

    private BatchCommitResult commitMany(String batchId, TimelineRevision base,
                                         List<OperationRequest> operations, String message) {
        VersionedAggregateRef target = new VersionedAggregateRef(
                "TIMELINE", TENANT, PROJECT, PROJECT, "main");
        OperationBatchRequest request = new OperationBatchRequest(
                batchId, target, base.revisionId(), operations, message);
        CanonicalActor actor = CanonicalActor.user(ACTOR, TENANT, Set.of("EDITOR"), "test");
        var preview = batch.preview(request, OperationBatchContext.preview(actor, "test"));
        return batch.commit(request, OperationBatchContext.apply(batchId, actor, null, "test", preview.batchDigest()));
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

    private static TimelineDocument twoClipDocumentWithAudio() {
        TimelineDocument video = twoClipDocument();
        AudioMix mix = new AudioMix(AudioMasterBus.master(),
                List.of(AudioRoute.of(AudioMixInput.of("video-main", "clip-a"))));
        return new TimelineDocument(video.getSchemaVersion(), video.getTracks(),
                video.getMetadata(), mix, video.getSemanticRelationships(),
                video.getTextElements(), video.getTransitions(), video.getAutomations());
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
        return refHead("main");
    }

    private String refHead(String refId) {
        return jdbc.query(
                "select head_revision_id from timeline.timeline_revision_ref where project_id = ? and ref_id = ?",
                rs -> rs.next() ? rs.getString(1) : null, PROJECT, refId);
    }

    private String parentOf(String revisionId) {
        return jdbc.queryForObject(
                "select parent_revision_id from timeline.timeline_revision where id = ?",
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
