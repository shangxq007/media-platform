package com.example.platform.timeline.app;

import com.example.platform.timeline.adapter.TimelineSnapshotService;
import com.example.platform.shared.authorization.AuthorizableResourceRef;
import com.example.platform.shared.authorization.AuthorizationAction;
import com.example.platform.shared.authorization.AuthorizationContext;
import com.example.platform.shared.authorization.AuthorizationDecisionPort;
import com.example.platform.shared.authorization.AuthorizationRequest;
import com.example.platform.shared.authorization.AuthorizationResourceType;
import com.example.platform.timeline.canonical.TimelineContentDigester;
import com.example.platform.timeline.canonical.TimelineDocument;
import com.example.platform.timeline.canonicalmodel.TimelineCandidate;
import com.example.platform.timeline.canonicalmodel.TimelineCanonicalNormalizer;
import com.example.platform.timeline.canonicalmodel.TimelineCanonicalValidator;
import com.example.platform.timeline.canonicalmodel.TimelineValidationResult;
import com.example.platform.timeline.revisioncommand.RevisionRef;
import com.example.platform.timeline.version.TimelineConflictException;
import com.example.platform.timeline.version.TimelineRevision;
import java.util.Objects;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static com.example.platform.typedschema.jooq.generated.tables.TimelineRevision.TIMELINE_REVISION;
import static com.example.platform.typedschema.jooq.generated.tables.TimelineRevisionParent.TIMELINE_REVISION_PARENT;

/**
 * E1-gated primary save surface for {@link TimelineDocument} revisions.
 *
 * <p>Contract P (PTCSG_REAL_RENDER_SUBTITLE_VERTICAL_SLICE_V1) completion: the E1 save
 * path now persists the governed snapshot payload through the existing sole authority
 * {@link TimelineSnapshotService} within the existing transaction, so a revision row is
 * never visible without its payload and the render/patch consumers resolve correctly.</p>
 *
 * <p>Frozen transactional order (PTADTF-C contract-p-snapshot-transaction-contract):
 * canonical acceptance (gate, first statement) -> digest/reference validation ->
 * optional durable command claim -> shared project-counter allocation -> snapshot payload ->
 * revision/context/pins/parent edge -> canonical ref CAS -> optional command completion.</p>
 */
@Service
public class TimelineRevisionSaveService {

    private static final Logger log = LoggerFactory.getLogger(TimelineRevisionSaveService.class);
    private static final AuthorizationAction MUTATE_TIMELINE = new AuthorizationAction(
            "WRITE", AuthorizationResourceType.PROJECT, "Mutate canonical Timeline");
    private final DSLContext dsl;
    private final TimelineContentDigester contentDigester;
    private final TimelineSnapshotService timelineSnapshotService;
    private final TimelineArtifactPinValidator artifactPinValidator;
    private final com.example.platform.artifact.app.ArtifactPinService artifactPinService;
    private final com.example.platform.timeline.semantics.effect.EffectSemanticSnapshotAuthority effectSnapshotAuthority;
    private final com.example.platform.timeline.version.TimelineRevisionSemanticContextStore revisionSemanticContextStore;
    // F2: canonical write authorities are required BY CONSTRUCTION and
    // immutable after construction — final ports, constructor injection only,
    // no public runtime authority mutation surface.
    private final TimelineRevisionPersistencePort revisionPersistence;
    private final HeadUpdatePort headUpdatePort;
    private final ProjectRevisionNumberAllocator revisionNumberAllocator =
            new ProjectRevisionNumberAllocator();
    private final TimelineRevisionRefMutation revisionRefMutation;
    // R2: bounded restore verification boundary (fail-closed; no new authority).
    private final HistoricalRevisionRestoreVerifier restoreVerifier;
    private final AuthorizationDecisionPort authorizationPort;

    /**
     * SINGLE production constructor: snapshot payload + CHECKPOINT_A artifact-pin
     * invariant boundary (extract → validate → register, same transaction).
     *
     * <p>Fail-closed guard: if the validator/pin-service wiring is absent and the
     * document actually carries artifact pins, saveRevision throws — a no-pin
     * save surface can never commit pinned content. (Test fixtures without pins
     * are unaffected.)
     */
    // R5-C (CHECKPOINT_A Round 5) + CONSTRUCTOR_INJECTION_WITHOUT_EXPLICIT_AUTOWIRED_V1
    // (R5 addendum): exactly ONE public constructor, constructor injection,
    // NO @Autowired (Spring 4.3+ injects the sole constructor automatically),
    // no secondary test convenience constructor. ALL production invariants are
    // REQUIRED BY CONSTRUCTION — no constructor permits a save/restore surface
    // with a missing artifact-pin dependency. A pinned revision can never be
    // committed without pin validation/persistence authority.
    public TimelineRevisionSaveService(DSLContext dsl,
                                       TimelineRevisionRefMutation revisionRefMutation,
                                       TimelineContentDigester contentDigester,
                                       TimelineSnapshotService timelineSnapshotService,
                                       TimelineArtifactPinValidator artifactPinValidator,
                                       com.example.platform.artifact.app.ArtifactPinService artifactPinService,
                                       com.example.platform.timeline.semantics.effect.EffectSemanticSnapshotAuthority effectSnapshotAuthority,
                                       com.example.platform.timeline.version.TimelineRevisionSemanticContextStore revisionSemanticContextStore,
                                       TimelineRevisionPersistencePort revisionPersistence,
                                       HeadUpdatePort headUpdatePort,
                                       AuthorizationDecisionPort authorizationPort) {
        // R5-C (CHECKPOINT_A Round 5): ALL production invariants are REQUIRED
        // BY CONSTRUCTION — no constructor permits a save/restore surface with
        // a missing artifact-pin dependency. A pinned revision can never be
        // committed without pin validation/persistence authority.
        this.dsl = Objects.requireNonNull(dsl, "dsl");
        this.revisionRefMutation = Objects.requireNonNull(revisionRefMutation, "revisionRefMutation");
        this.contentDigester = Objects.requireNonNull(contentDigester, "contentDigester");
        this.timelineSnapshotService = Objects.requireNonNull(timelineSnapshotService, "timelineSnapshotService");
        this.artifactPinValidator = Objects.requireNonNull(artifactPinValidator, "artifactPinValidator");
        this.artifactPinService = Objects.requireNonNull(artifactPinService, "artifactPinService");
        this.effectSnapshotAuthority = Objects.requireNonNull(effectSnapshotAuthority, "effectSnapshotAuthority");
        this.revisionSemanticContextStore = Objects.requireNonNull(revisionSemanticContextStore, "revisionSemanticContextStore");
        this.revisionPersistence = Objects.requireNonNull(revisionPersistence, "revisionPersistence");
        this.headUpdatePort = Objects.requireNonNull(headUpdatePort, "headUpdatePort");
        this.authorizationPort = Objects.requireNonNull(authorizationPort, "authorizationPort");
        this.restoreVerifier = new HistoricalRevisionRestoreVerifier(
                timelineSnapshotService, effectSnapshotAuthority.store(),
                contentDigester);
    }

    /**
     * ROADMAP20 authority-integration (ONE_CANONICAL_TIMELINE_REVISION_WRITE_PATH_MODEL_V1):
     * the single canonical revision writer. No-Effect authored state mints the
     * authoritative EMPTY Effect snapshot; Effect-bearing authored state mints
     * the exact non-empty snapshot — both through {@link #saveRevisionInternal}
     * with the SAME Effect authority, SAME semantic context, SAME full revision
     * semantic digest, and SAME physical transaction. There is no parallel
     * semantic authority path.
     */
    @Transactional
    public TimelineRevision saveRevision(
            TimelineMutationContext context,
            String expectedCurrentRevisionId,
            TimelineDocument document) {
        authorizeMutation(context, "save");
        return saveRevisionInternal(context.tenantId(), context.projectId(),
                expectedCurrentRevisionId, document,
                java.util.List.of(), java.util.List.of(), context.authorUserId(), null, null).revision();
    }

    /**
     * Canonical command-aware write used by Operation application coordination.
     * Durable idempotency joins the SAME physical transaction as snapshot,
     * revision context, pins and the final HEAD CAS. Timeline still owns the
     * only revision insert; the command is coordination metadata, never a
     * second canonical state.
     */
    public RevisionWriteResult saveRevisionForCommand(
            TimelineMutationContext context,
            RevisionRef targetRef,
            String expectedCurrentRevisionId,
            TimelineDocument document,
            RevisionWriteCommand command) {
        Objects.requireNonNull(targetRef, "targetRef");
        requireContextMatchesRef(context, targetRef);
        authorizeMutation(context, "operation-apply");
        SaveOutcome outcome = saveRevisionInternal(
                targetRef.tenantId(), targetRef.projectId(), expectedCurrentRevisionId, document,
                java.util.List.of(), java.util.List.of(), context.authorUserId(),
                Objects.requireNonNull(command, "command"), targetRef);
        if (outcome.replayed()) {
            return new RevisionWriteResult(outcome.revisionId(), expectedCurrentRevisionId,
                    outcome.timelineContentHash(), true);
        }
        return new RevisionWriteResult(outcome.revision().revisionId(),
                outcome.revision().parentRevisionId(),
                outcome.revision().semanticContext().timelineContentDigest(), false);
    }

    /**
     * Durable semantic NO_OP: records/replays the command and validates the
     * exact expected canonical Timeline ref through the shared database CAS authority,
     * while creating no Timeline revision.
     */
    public RevisionWriteResult recordNoOpCommand(
            TimelineMutationContext context,
            RevisionRef targetRef,
            String expectedCurrentRevisionId,
            String baseTimelineContentHash,
            RevisionWriteCommand command) {
        Objects.requireNonNull(targetRef, "targetRef");
        Objects.requireNonNull(command, "command");
        requireContextMatchesRef(context, targetRef);
        authorizeMutation(context, "operation-no-op");
        return dsl.transactionResult(tx -> {
            SaveOutcome replay = claimOrReplayCommand(
                    tx.dsl(), command, targetRef, expectedCurrentRevisionId, "NO_OP");
            if (replay != null) {
                return new RevisionWriteResult(null, expectedCurrentRevisionId,
                        replay.timelineContentHash(), true);
            }
            if (!revisionRefMutation.validateExpectedHead(
                    tx.dsl(), targetRef, expectedCurrentRevisionId)) {
                throw staleRef(tx.dsl(), targetRef, expectedCurrentRevisionId);
            }
            completeCommand(tx.dsl(), command.commandId(), null,
                    baseTimelineContentHash, "NO_OP");
            return new RevisionWriteResult(null, expectedCurrentRevisionId,
                    baseTimelineContentHash, false);
        });
    }

    /**
     * ROADMAP20 authority-integration (E2E-B): the REAL typed Effect-bearing
     * canonical authoring path. The caller supplies authored Effect state
     * (typed instances + authoritative definitions); the Effect domain
     * authority mints the exact non-empty snapshot, persists it durably,
     * pins it on the revision, and commits the full revision semantic digest
     * in the SAME physical transaction as the revision row.
     *
     * <p>Caller-authority boundary (§5): this is the TRUSTED APPLICATION
     * authoring boundary — it is NOT arbitrary authority substitution. The
     * caller cannot choose the snapshotId (authority-generated), the registry
     * or store implementation (constructor-injected durable dependencies), the
     * target context (derived from the canonical document TrackType), the
     * expected Effect reference (derived from the revision), or the revision
     * semantic digest (computed internally). Caller-supplied definitions are
     * admitted only through the definition-version registry, which is the
     * FINAL semantic authority: (definitionId, version) maps to exactly one
     * content digest forever — a caller cannot redefine a version's semantics
     * after first registration (D1 enforced durably).
     */
    @Transactional
    public TimelineRevision saveRevisionWithEffects(
            TimelineMutationContext context, String expectedCurrentRevisionId,
            TimelineDocument document,
            java.util.List<com.example.platform.timeline.semantics.effect.EffectInstance> effects,
            java.util.List<com.example.platform.timeline.semantics.effect.EffectInstance.EffectDefinition> definitions) {
        authorizeMutation(context, "save-with-effects");
        return saveRevisionInternal(context.tenantId(), context.projectId(),
                expectedCurrentRevisionId, document,
                effects == null ? java.util.List.of() : effects,
                definitions == null ? java.util.List.of() : definitions,
                context.authorUserId(), null, null).revision();
    }

    private SaveOutcome saveRevisionInternal(
            String tenantId, String productId, String expectedCurrentRevisionId,
            TimelineDocument document,
            java.util.List<com.example.platform.timeline.semantics.effect.EffectInstance> effects,
            java.util.List<com.example.platform.timeline.semantics.effect.EffectInstance.EffectDefinition> definitions,
            String createdBy,
            RevisionWriteCommand command,
            RevisionRef commandTargetRef) {
        return saveRevisionInternal(tenantId, productId, expectedCurrentRevisionId,
                document, effects, definitions, createdBy, command, commandTargetRef,
                java.util.List.of(), "api", null);
    }

    /** Sole-boundary two-parent merge persistence: target/main first, source second. */
    public TimelineRevision saveMergeRevision(
            TimelineMutationContext context, String expectedMainHead,
            String sourceRevisionId, String mergeBaseRevisionId,
            TimelineDocument document) {
        if (sourceRevisionId == null || sourceRevisionId.isBlank()) {
            throw new IllegalArgumentException("merge source revision required");
        }
        authorizeMutation(context, "merge");
        return saveRevisionInternal(context.tenantId(), context.projectId(),
                expectedMainHead, document,
                java.util.List.of(), java.util.List.of(), context.authorUserId(), null, null,
                java.util.List.of(sourceRevisionId), "merge", mergeBaseRevisionId).revision();
    }

    private SaveOutcome saveRevisionInternal(
            String tenantId, String productId, String expectedCurrentRevisionId,
            TimelineDocument document,
            java.util.List<com.example.platform.timeline.semantics.effect.EffectInstance> effects,
            java.util.List<com.example.platform.timeline.semantics.effect.EffectInstance.EffectDefinition> definitions,
            String createdBy,
            RevisionWriteCommand command,
            RevisionRef commandTargetRef,
            java.util.List<String> additionalParents,
            String revisionSource,
            String mergeBaseRevisionId) {
        requireOwnedProject(tenantId, productId);
        if (command != null && (commandTargetRef == null
                || !productId.equals(commandTargetRef.projectId())
                || !command.tenantId().equals(commandTargetRef.tenantId())
                || !RevisionRef.MAIN_REF.equals(commandTargetRef.refId()))) {
            throw new IllegalArgumentException("command target ref must belong to the persisted project");
        }
        // Existing canonical callers are authenticated request paths and retain
        // the established TenantContext contract. Operation application carries
        // the authorization-bound tenant explicitly in its immutable command so
        // worker execution never depends on ambient ThreadLocal propagation.
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("explicit tenantId required for canonical revision persistence");
        }
        if (command != null && !tenantId.equals(command.tenantId())) {
            throw new IllegalArgumentException("command tenant must equal canonical revision tenant");
        }
        // NDSF-SCOPE-E1 canonical save gate: first semantic operation (F018) — before
        // revision allocation and before every write or side effect.
        TimelineCandidate candidate = TimelineDocumentCandidateMapper.map(productId, document);
        TimelineValidationResult validation = TimelineCanonicalValidator.validate(candidate);
        if (validation.hasFatalErrors()) {
            throw new TimelineCanonicalRejectionException(validation.diagnostics());
        }
        TimelineCanonicalNormalizer.normalize(candidate)
                .orElseThrow(() -> new TimelineCanonicalRejectionException(validation.diagnostics()));

        // CHECKPOINT_A (Blocker C): EVERY canonical revision write path must
        // enforce the artifact-pin invariant boundary — extract → validate
        // (existence + tenant + digest) → register (same transaction).
        // R5-C: the pin boundary is REQUIRED BY CONSTRUCTION (non-null
        // dependencies); zero pins in the document is a normal no-op, a
        // missing dependency is not — there is no nullable skip.
        java.util.List<TimelineArtifactPinExtractor.ArtifactPin> pins = java.util.Collections.emptyList();
        pins = extractPinsFromDocument(document);
        if (!pins.isEmpty()) {
            TimelineArtifactPinValidator.ValidationResult pinValidation =
                    artifactPinValidator.validate(tenantId, pins);
            if (!pinValidation.valid()) {
                throw new TimelineCanonicalRejectionException(
                        new TimelineCanonicalRejectionException.AdapterDiagnostic(
                                TimelineCanonicalRejectionException.Code.TIMELINE_SOURCE_REF_INVALID,
                                com.example.platform.timeline.canonicalmodel.TimelineModelPath.root().field("sourceBinding"),
                                "Artifact pin reference-integrity: " + String.join("; ", pinValidation.violations())));
            }
        }

        String timelineDigest = contentDigester.digest(document);
        // The expected revision is the proposed parent. Command correctness is
        // decided only by the canonical Timeline ref mutation inside the
        // transaction; no mutable-head read becomes semantic authority first.
        String parentRevisionId = expectedCurrentRevisionId;

        // CHECKPOINT_A (Round 3): explicit jOOQ transaction — revision insert +
        // pin registration + head update are ONE atomic unit regardless of any
        // Spring proxy boundary. Pin-registration failure rolls the whole write
        // back (no visible dangling revision), even when this service is invoked
        // directly (ITs, non-proxied wiring).
        String revisionId = UUID.randomUUID().toString();
        final java.util.List<TimelineArtifactPinExtractor.ArtifactPin> pinsToRegister = pins;
        return dsl.transactionResult(tx -> {
            if (command != null) {
                SaveOutcome replay = claimOrReplayCommand(
                        tx.dsl(), command, commandTargetRef,
                        expectedCurrentRevisionId, "APPLIED");
                if (replay != null) {
                    return replay;
                }
            }
            int nextRevisionNumber = Math.toIntExact(
                    revisionNumberAllocator.allocate(tx.dsl(), productId));
            String snapshotId = persistSnapshotPayload(tx.dsl(), productId, tenantId, document);

            // ROADMAP20 authority integration (blockers 1/4): every NEW canonical
            // revision mints authoritative Effect semantics (EMPTY for the
            // document save path — the canonical document carries no Effect
            // authoring today), persists the snapshot durably, pins the exact
            // reference and computes the FULL revision semantic digest
            // H(timelineDigest, contractVersion, effectContentDigest).
            com.example.platform.timeline.semantics.effect.EffectSemanticSnapshot effectSnapshot =
                    effectSnapshotAuthority.mintAndPersistTx(
                            tx.dsl(), productId, tenantId,
                            effects, definitions, document);
            com.example.platform.timeline.semantics.effect.EffectSemanticSnapshotReference effectRef =
                    effectSnapshot.reference();
            String revisionSemanticDigest =
                    com.example.platform.timeline.semantics.effect.TimelineRevisionEffectSemanticCommitment
                            .revisionEffectSemanticDigest(timelineDigest, effectRef);
            com.example.platform.timeline.version.TimelineRevisionSemanticContext semanticContext =
                    new com.example.platform.timeline.version.TimelineRevisionSemanticContext(
                            timelineDigest, effectRef, revisionSemanticDigest,
                            com.example.platform.timeline.version.TimelineRevisionSemanticContext
                                    .REVISION_SEMANTICS_V1);

            TimelineRevision revision = new TimelineRevision(
                    revisionId, productId, parentRevisionId,
                    TimelineDocument.CURRENT_SCHEMA_VERSION,
                    document, revisionSemanticDigest, Instant.now(), createdBy,
                    semanticContext);

            revisionPersistence.insertRevisionTx(
                    tx.dsl(), revision, productId, snapshotId, revision.timelineSchemaVersion(),
                    timelineDigest, nextRevisionNumber, tenantId, revisionSource);

            if (!additionalParents.isEmpty()) {
                tx.dsl().update(TIMELINE_REVISION)
                        .set(TIMELINE_REVISION.IS_MERGE, true)
                        .set(TIMELINE_REVISION.MERGE_PARENT_REVISION_IDS,
                                String.join(",", java.util.stream.Stream.concat(
                                        java.util.stream.Stream.of(parentRevisionId),
                                        additionalParents.stream()).toList()))
                        .set(TIMELINE_REVISION.MERGE_BASE_REVISION_ID, mergeBaseRevisionId)
                        .where(TIMELINE_REVISION.ID.eq(revisionId))
                        .and(TIMELINE_REVISION.TENANT_ID.eq(tenantId))
                        .and(TIMELINE_REVISION.PROJECT_ID.eq(productId))
                        .execute();
            }

            // Canonical state order begins with the complete immutable revision
            // state; H7 then writes its parent edge, publishes the ref, and
            // completes the durable command before commit.
            revisionSemanticContextStore.storeTx(tx.dsl(), productId, tenantId, revisionId, semanticContext);

            // CHECKPOINT_A (Blocker C): register artifact_pin protection rows in
            // the SAME transaction as the revision (PIN_REGISTRATION_FAILURE
            // rolls back the whole write — no visible dangling revision state).
            // R4-D2: registration executes on the transaction's OWN DSLContext
            // (ArtifactPinService.registerRevisionPinsTx) so the pin rows join
            // the same physical DB transaction — proven by real-PG IT, not
            // assumed through Spring proxy participation.
            // R5-C: no nullable skip — pin persistence authority is required
            // by construction; zero pins is a normal no-op.
            if (!pinsToRegister.isEmpty()) {
                artifactPinService.registerRevisionPinsTx(tx.dsl(), productId, revisionId,
                        tenantId,
                        pinsToRegister.stream()
                                .map(p -> new com.example.platform.artifact.app.ArtifactPinService.ArtifactPin(
                                        p.artifactId(), p.contentDigest()))
                                .toList());
            }

            if (parentRevisionId != null) {
                tx.dsl().insertInto(TIMELINE_REVISION_PARENT)
                        .set(TIMELINE_REVISION_PARENT.TENANT_ID, tenantId)
                        .set(TIMELINE_REVISION_PARENT.PROJECT_ID, productId)
                        .set(TIMELINE_REVISION_PARENT.REVISION_ID, revisionId)
                        .set(TIMELINE_REVISION_PARENT.PARENT_REVISION_ID, parentRevisionId)
                        .set(TIMELINE_REVISION_PARENT.PARENT_ORDER, 0)
                        .execute();
            }
            for (int parentOrder = 0; parentOrder < additionalParents.size(); parentOrder++) {
                tx.dsl().insertInto(TIMELINE_REVISION_PARENT)
                        .set(TIMELINE_REVISION_PARENT.TENANT_ID, tenantId)
                        .set(TIMELINE_REVISION_PARENT.PROJECT_ID, productId)
                        .set(TIMELINE_REVISION_PARENT.REVISION_ID, revisionId)
                        .set(TIMELINE_REVISION_PARENT.PARENT_REVISION_ID,
                                additionalParents.get(parentOrder))
                        .set(TIMELINE_REVISION_PARENT.PARENT_ORDER, parentOrder + 1)
                        .execute();
            }
            if (command != null) {
                boolean published = parentRevisionId == null
                        ? revisionRefMutation.bootstrap(tx.dsl(), commandTargetRef, revisionId)
                        : revisionRefMutation.advance(
                                tx.dsl(), commandTargetRef, parentRevisionId, revisionId);
                if (!published) {
                    throw staleRef(tx.dsl(), commandTargetRef, parentRevisionId);
                }
                completeCommand(tx.dsl(), command.commandId(), revisionId,
                        timelineDigest, "APPLIED");
            } else {
                headUpdatePort.updateHeadTx(
                        tx.dsl(), RevisionRef.main(tenantId, productId),
                        expectedCurrentRevisionId, revisionId);
            }

            log.info("Saved timeline revision {} for product {}", revisionId, productId);
            return new SaveOutcome(revision, revisionId, timelineDigest, false);
        });
    }

    private static SaveOutcome claimOrReplayCommand(
            org.jooq.DSLContext tx,
            RevisionWriteCommand command,
            RevisionRef targetRef,
            String expectedHeadRevisionId,
            String expectedResultStatus) {
        int inserted = tx.execute("""
                insert into apply_command
                    (apply_command_id, plan_digest, fingerprint, status, tenant_id, project_id,
                     command_domain, target_ref_id, expected_head_revision_id, expected_result_status)
                values (?, ?, ?, 'IN_PROGRESS', ?, ?, ?, ?, ?, ?)
                on conflict (apply_command_id) do nothing
                """, command.commandId(), command.planDigest(), command.fingerprint(),
                command.tenantId(), targetRef.projectId(), command.commandDomain(), targetRef.refId(),
                expectedHeadRevisionId, expectedResultStatus);
        if (inserted == 1) {
            return null;
        }
        var existing = tx.fetchOne("""
                select plan_digest, fingerprint, status, tenant_id, project_id, command_domain,
                       target_ref_id, expected_head_revision_id, expected_result_status,
                       result_revision_id, result_content_hash, result_status
                from apply_command where apply_command_id = ?
                """, command.commandId());
        if (existing == null
                || !command.planDigest().equals(existing.get("plan_digest", String.class))
                || !command.fingerprint().equals(existing.get("fingerprint", String.class))
                || !command.tenantId().equals(existing.get("tenant_id", String.class))
                || !targetRef.projectId().equals(existing.get("project_id", String.class))
                || !command.commandDomain().equals(existing.get("command_domain", String.class))
                || !targetRef.refId().equals(existing.get("target_ref_id", String.class))
                || !Objects.equals(expectedHeadRevisionId,
                        existing.get("expected_head_revision_id", String.class))
                || !expectedResultStatus.equals(
                        existing.get("expected_result_status", String.class))) {
            throw new TimelineRevisionCommandConflictException(
                    "revision command id reused with different immutable context");
        }
        if (!"COMPLETED".equals(existing.get("status", String.class))
                || !expectedResultStatus.equals(existing.get("result_status", String.class))) {
            throw new TimelineRevisionCommandConflictException(
                    "revision command exists without a completed atomic result");
        }
        return new SaveOutcome(null,
                existing.get("result_revision_id", String.class),
                existing.get("result_content_hash", String.class), true);
    }

    private static void completeCommand(
            org.jooq.DSLContext tx, String commandId,
            String revisionId, String timelineContentHash, String resultStatus) {
        int updated = tx.execute("""
                update apply_command
                set status = 'COMPLETED', result_revision_id = ?, result_content_hash = ?,
                    result_status = ?, completed_at = current_timestamp
                where apply_command_id = ? and status = 'IN_PROGRESS'
                """, revisionId, timelineContentHash, resultStatus, commandId);
        if (updated != 1) {
            throw new TimelineRevisionCommandConflictException(
                    "revision command completion lost its atomic claim");
        }
    }

    public record RevisionWriteCommand(
            String commandId,
            String planDigest,
            String fingerprint,
            String commandDomain,
            String tenantId) {
        public RevisionWriteCommand {
            if (commandId == null || commandId.isBlank()
                    || planDigest == null || planDigest.isBlank()
                    || fingerprint == null || fingerprint.isBlank()
                    || commandDomain == null || commandDomain.isBlank()
                    || tenantId == null || tenantId.isBlank()) {
                throw new IllegalArgumentException("complete revision write command required");
            }
        }
    }

    public record RevisionWriteResult(
            String revisionId,
            String parentRevisionId,
            String timelineContentHash,
            boolean replayed) {
    }

    private record SaveOutcome(
            TimelineRevision revision,
            String revisionId,
            String timelineContentHash,
            boolean replayed) {
    }

    @Transactional
    public TimelineRevision restoreRevision(
            TimelineMutationContext context,
            String historicalRevisionId,
            String expectedCurrentRevisionId) {
        authorizeMutation(context, "restore");
        String tenantId = context.tenantId();
        String productId = context.projectId();
        String canonicalAuthor = context.authorUserId();
        requireOwnedProject(tenantId, productId);
        String contentHash = dsl.select(TIMELINE_REVISION.CONTENT_HASH)
                .from(TIMELINE_REVISION)
                .where(TIMELINE_REVISION.ID.eq(historicalRevisionId))
                .and(TIMELINE_REVISION.PROJECT_ID.eq(productId))
                .and(TIMELINE_REVISION.TENANT_ID.eq(tenantId))
                .fetchOne(TIMELINE_REVISION.CONTENT_HASH);

        if (contentHash == null) {
            throw new IllegalArgumentException("Historical revision not found: " + historicalRevisionId);
        }

        String schemaVersion = dsl.select(TIMELINE_REVISION.SCHEMA_VERSION)
                .from(TIMELINE_REVISION)
                .where(TIMELINE_REVISION.ID.eq(historicalRevisionId))
                .and(TIMELINE_REVISION.PROJECT_ID.eq(productId))
                .and(TIMELINE_REVISION.TENANT_ID.eq(tenantId))
                .fetchOne(TIMELINE_REVISION.SCHEMA_VERSION);

        String revisionId = UUID.randomUUID().toString();
        final String schemaVersionFinal = schemaVersion;
        final String contentHashFinal = contentHash;

        // R4-D1 (CHECKPOINT_A Round 4): the WHOLE restore write — snapshot
        // copy, revision insert, head update, artifact-pin copy for the NEW
        // revision — is ONE explicit jOOQ transaction. A restored revision that
        // references pinned artifacts MUST gain protection rows for its new
        // revision id ((revisionId, artifactId) protection identity), copied
        // from the historical revision's immutable pin contract inside the same
        // transaction. Failure anywhere rolls back the entire restore.
        return dsl.transactionResult(tx -> {
            // Contract P + F4: restore copies the historical governed payload into a new
            // snapshot row; a missing/null payload FAILS CLOSED — no legacy fallback.
            // R2 (RESTORE_VERIFIES_BEFORE_REISSUING_CANONICAL_AUTHORITY_V1):
            // complete historical semantic closure verified BEFORE any new
            // persisted transition state — no temporary rows are created
            // before historical corruption is discovered.
            com.example.platform.timeline.version.TimelineRevisionSemanticContext historicalContext0 =
                    revisionSemanticContextStore.findByRevisionId(
                            tx.dsl(), productId, tenantId, historicalRevisionId).orElseThrow(
                            () -> new IllegalStateException(
                                    "RESTORE FAIL CLOSED (RST3): historical revision '"
                                            + historicalRevisionId + "' has no revision semantic context"));
            String historicalSnapshotId0 = tx.dsl().select(TIMELINE_REVISION.SNAPSHOT_ID)
                    .from(TIMELINE_REVISION)
                    .where(TIMELINE_REVISION.ID.eq(historicalRevisionId))
                    .and(TIMELINE_REVISION.PROJECT_ID.eq(productId))
                    .and(TIMELINE_REVISION.TENANT_ID.eq(tenantId))
                    .fetchOne(TIMELINE_REVISION.SNAPSHOT_ID);
            HistoricalRevisionRestoreVerifier.VerifiedHistoricalRevision verified =
                    restoreVerifier.verify(tx.dsl(), productId, tenantId,
                            historicalSnapshotId0, contentHashFinal, historicalContext0);

            // C1 (RESTORE_REISSUES_EXACTLY_THE_VERIFIED_TIMELINE_PAYLOAD_V1):
            // the new restored snapshot is persisted DIRECTLY from the verified
            // payload/document — one read, one verify, one reissue. There is NO
            // post-verification reread of the historical snapshot
            // (RESTORE_POST_VERIFICATION_HISTORICAL_REREAD_COUNT = 0).
            String snapshotId = timelineSnapshotService.saveTx(
                    tx.dsl(), productId, tenantId,
                    TimelineDocumentJsonSerializer.serializeWithCaptions(verified.document()),
                    verified.timelineSchemaVersion());

            // R2: the restored revision reissues the VERIFIED historical
            // commitment — new revision identity, exact historical semantics
            // (no remint, no EMPTY fallback, no Effect wire hydration). FINAL
            // (C1/§43-44): ALL restored semantic state derives from the single
            // verified result — verified digest, verified Effect reference,
            // verified full commitment — never mixed with a second read.
            String revisionSemanticDigest = verified.fullRevisionSemanticDigest();
            com.example.platform.timeline.version.TimelineRevisionSemanticContext newContext =
                    new com.example.platform.timeline.version.TimelineRevisionSemanticContext(
                            verified.timelineDigest(),
                            verified.effectReference(),
                            revisionSemanticDigest,
                            verified.digestContractVersion());

            int nextRevisionNumber = Math.toIntExact(
                    revisionNumberAllocator.allocate(tx.dsl(), productId));

            revisionPersistence.insertRevisionTx(
                    tx.dsl(), new TimelineRevision(revisionId, productId, expectedCurrentRevisionId,
                            schemaVersionFinal, null, revisionSemanticDigest, java.time.Instant.now(),
                            canonicalAuthor, newContext),
                    productId, snapshotId, schemaVersionFinal, verified.timelineDigest(),
                    nextRevisionNumber, tenantId, "restore");

            // F3: revctx + pins BEFORE the head CAS (final mutation).
            revisionSemanticContextStore.storeTx(tx.dsl(), productId, tenantId, revisionId, newContext);

            if (expectedCurrentRevisionId != null) {
                tx.dsl().insertInto(TIMELINE_REVISION_PARENT)
                        .set(TIMELINE_REVISION_PARENT.TENANT_ID, tenantId)
                        .set(TIMELINE_REVISION_PARENT.PROJECT_ID, productId)
                        .set(TIMELINE_REVISION_PARENT.REVISION_ID, revisionId)
                        .set(TIMELINE_REVISION_PARENT.PARENT_REVISION_ID, expectedCurrentRevisionId)
                        .set(TIMELINE_REVISION_PARENT.PARENT_ORDER, 0)
                        .execute();
            }

            // R4-D1: the restored revision is a DISTINCT revision id — it must
            // carry its own artifact-pin protection rows, copied from the
            // historical revision's immutable pins in the SAME transaction.
            // R5-C: no nullable skip — pin persistence authority is required
            // by construction.
            // F3: the HEAD CAS is the FINAL mutation (after pins).
            artifactPinService.copyRevisionPinsTx(
                    tx.dsl(), tenantId, productId, historicalRevisionId, revisionId);

            // F3: HEAD CAS LAST — publishes only fully persisted restored state.
            headUpdatePort.updateHeadTx(
                    tx.dsl(), RevisionRef.main(tenantId, productId),
                    expectedCurrentRevisionId, revisionId);

            log.info("Restored revision {} as new revision {} for product {}", historicalRevisionId, revisionId, productId);
            return new TimelineRevision(revisionId, productId, expectedCurrentRevisionId,
                    schemaVersionFinal,
                    null, revisionSemanticDigest, Instant.now(), canonicalAuthor, newContext);
        });
    }

    private TimelineConflictException staleRef(
            DSLContext tx, RevisionRef targetRef, String expectedRevisionId) {
        return new TimelineConflictException(targetRef.projectId(), expectedRevisionId,
                revisionRefMutation.currentHead(tx, targetRef));
    }

    private void authorizeMutation(TimelineMutationContext context, String operation) {
        Objects.requireNonNull(context, "mutation context");
        authorizationPort.requireAuthorized(new AuthorizationRequest(
                context.actor(),
                MUTATE_TIMELINE,
                new AuthorizableResourceRef(
                        AuthorizationResourceType.PROJECT,
                        context.projectId(),
                        context.tenantId(),
                        context.projectId(),
                        null),
                new AuthorizationContext(
                        "timeline-canonical-mutation",
                        context.projectId(),
                        java.util.Map.of("operation", operation))));
    }

    private static void requireContextMatchesRef(
            TimelineMutationContext context, RevisionRef targetRef) {
        Objects.requireNonNull(context, "mutation context");
        if (!context.tenantId().equals(targetRef.tenantId())
                || !context.projectId().equals(targetRef.projectId())) {
            throw new IllegalArgumentException(
                    "mutation context must own the exact target ref");
        }
    }

    /**
     * Contract P: persist the governed snapshot payload through the sole existing
     * authority ({@link TimelineSnapshotService}) inside the current transaction.
     */
    /** CHECKPOINT_A: typed pin extraction straight from the canonical document
     *  (no JSON-format coupling; document carries artifactId/contentDigest per clip). */
    private static java.util.List<TimelineArtifactPinExtractor.ArtifactPin> extractPinsFromDocument(
            TimelineDocument document) {
        java.util.LinkedHashMap<String, TimelineArtifactPinExtractor.ArtifactPin> distinct =
                new java.util.LinkedHashMap<>();
        for (com.example.platform.timeline.canonical.TimelineTrack track : document.getTracks()) {
            for (com.example.platform.timeline.canonical.TimelineClip clip : track.clips()) {
                String artifactId = clip.getArtifactId();
                String digest = clip.getContentDigest();
                if (artifactId != null && !artifactId.isBlank() && digest != null && !digest.isBlank()) {
                    distinct.putIfAbsent(artifactId, new TimelineArtifactPinExtractor.ArtifactPin(
                            new com.example.platform.shared.identity.ArtifactId(artifactId),
                            new com.example.platform.shared.digest.ContentDigest(
                                    com.example.platform.shared.digest.ContentDigest.DigestAlgorithm.SHA_256, digest)));
                }
            }
        }
        return java.util.List.copyOf(distinct.values());
    }

    private String persistSnapshotPayload(org.jooq.DSLContext tx, String productId,
                                          String tenantId, TimelineDocument document) {
        return timelineSnapshotService.saveTx(
                tx, productId,
                tenantId,
                TimelineDocumentJsonSerializer.serializeWithCaptions(document),
                TimelineDocument.CURRENT_SCHEMA_VERSION);
    }

    /**
     * FINAL (R3, AUTHORITATIVE_REVISION_ROW_IS_READ_AS_ONE_OWNERSHIP_VALIDATED_UNIT_V1):
     * reads the authoritative revision row as ONE ownership-validated unit.
     *
     * <p>One query with the tenant predicate on the whole row; ALL fields
     * derive from the returned row. Ownership validation applies to the full
     * row, not just projectId discovery — no field of a revision may be read
     * from a query that ignores tenant after ownership validation
     * (REVISION_ROW_AUTHORITATIVE_READ_IS_TENANT_SCOPED_V1).
     *
     * @return the owned row, or {@code null} when absent / not owned
     */
    private OwnedRevisionRow readOwnedRevisionRow(String revisionId, String tenantId) {
        org.jooq.Record row = dsl.select(
                        TIMELINE_REVISION.ID,
                        TIMELINE_REVISION.PROJECT_ID,
                        TIMELINE_REVISION.TENANT_ID,
                        TIMELINE_REVISION.PARENT_REVISION_ID,
                        TIMELINE_REVISION.SCHEMA_VERSION,
                        TIMELINE_REVISION.CONTENT_HASH,
                        TIMELINE_REVISION.SNAPSHOT_ID,
                        TIMELINE_REVISION.CREATED_AT,
                        TIMELINE_REVISION.AUTHOR_USER_ID)
                .from(TIMELINE_REVISION)
                .where(TIMELINE_REVISION.ID.eq(revisionId))
                .and(TIMELINE_REVISION.TENANT_ID.eq(tenantId))
                .fetchOne();
        if (row == null) {
            return null;
        }
        return new OwnedRevisionRow(
                row.get(TIMELINE_REVISION.ID),
                row.get(TIMELINE_REVISION.PROJECT_ID),
                row.get(TIMELINE_REVISION.TENANT_ID),
                row.get(TIMELINE_REVISION.PARENT_REVISION_ID),
                row.get(TIMELINE_REVISION.SCHEMA_VERSION),
                row.get(TIMELINE_REVISION.CONTENT_HASH),
                row.get(TIMELINE_REVISION.SNAPSHOT_ID),
                row.get(TIMELINE_REVISION.CREATED_AT),
                row.get(TIMELINE_REVISION.AUTHOR_USER_ID));
    }

    /** The authoritative revision row read as ONE ownership-validated unit (R3). */
    private record OwnedRevisionRow(
            String id,
            String projectId,
            String tenantId,
            String parentRevisionId,
            String schemaVersion,
            String contentHash,
            String snapshotId,
            LocalDateTime createdAt,
            String authorUserId) {
    }

    @Transactional(readOnly = true)
    public TimelineRevision findById(String tenantId, String revisionId) {
        // FINAL (R3): ONE ownership-scoped row read — all fields derive from
        // the single validated row; no subsequent revisionId-only field
        // lookups (REVISION_ROW_AUTHORITATIVE_READ_IS_TENANT_SCOPED_V1,
        // AUTHORITATIVE_REVISION_ROW_IS_READ_AS_ONE_OWNERSHIP_VALIDATED_UNIT_V1).
        OwnedRevisionRow row = readOwnedRevisionRow(revisionId, tenantId);
        if (row == null) {
            return null;
        }

        // ROADMAP20 authority integration: the revision's semantic context
        // (exact Effect pin) is loaded from revision-owned persisted state —
        // reconstructs the pin WITHOUT caller input.
        com.example.platform.timeline.version.TimelineRevisionSemanticContext semanticContext =
                revisionSemanticContextStore.findByRevisionId(
                        dsl, row.projectId(), tenantId, revisionId)
                        .orElse(null);
        if (semanticContext == null) {
            // CLEAN-FORWARD: a valid canonical revision ALWAYS owns its
            // semantic context — absence is INVALID/CORRUPT and FAILS CLOSED.
            throw new IllegalStateException(
                    "MISSING_SEMANTIC_CONTEXT_IS_INVALID_V1: revision '" + revisionId
                            + "' has no revision semantic context row — corrupt or pre-canonical "
                            + "data; FAIL CLOSED (no legacy read mode)");
        }
        return new TimelineRevision(
                revisionId, row.projectId(), row.parentRevisionId(), row.schemaVersion(),
                null, semanticContext.revisionSemanticDigest(),
                row.createdAt() != null
                        ? row.createdAt().atZone(java.time.ZoneId.systemDefault()).toInstant()
                        : null,
                row.authorUserId(), semanticContext);
    }

    /**
     * PPHR-BIC (PATCH_APPLICATION_PAYLOAD_HYDRATION_DEFECT): resolve the governed
     * snapshot payload for a revision through the existing sole snapshot authority
     * ({@link TimelineSnapshotService}) and deserialize it with the governed payload
     * codec ({@link TimelineDocumentJsonSerializer}). Read helper only — no
     * canonical mutation occurs on this path.
     *
     * <p>FINAL (C2, CANONICAL_TIMELINE_PAYLOAD_READ_IS_PROJECT_AND_TENANT_SCOPED_V1):
     * this is a CANONICAL read and MUST be ownership scoped end to end — the
     * revision row is read as ONE ownership-validated unit (tenant predicate),
     * and the snapshot is resolved ONLY within the row's (projectId, tenantId)
     * via {@link TimelineSnapshotService#findOwnedById}. No global snapshot
     * lookup can enter this path; a foreign snapshot resolves to empty and the
     * caller fails closed (PAYLOAD_INVALID).
     *
     * <p>Returns {@link Optional#empty()} when the revision has no snapshot payload
     * row or the payload is missing/malformed — the caller maps that to explicit
     * fail-closed behavior (F4: missing governed payload never produces a new valid
     * canonical revision).</p>
     */
    @Transactional(readOnly = true)
    public Optional<TimelineDocument> findPayloadDocument(String tenantId, String revisionId) {
        // C2: ONE ownership-validated revision row read (same unit as findById).
        OwnedRevisionRow row = readOwnedRevisionRow(revisionId, tenantId);
        if (row == null || row.snapshotId() == null || row.snapshotId().isBlank()) {
            return Optional.empty();
        }
        // C2: ownership-scoped snapshot hydration — (projectId, tenantId)
        // bound; a foreign snapshot is NOT FOUND (fail closed upstream).
        Optional<TimelineSnapshotService.SnapshotInfo> snapshot =
                timelineSnapshotService.findOwnedById(
                        dsl, row.projectId(), tenantId, row.snapshotId());
        if (snapshot.isEmpty()) {
            return Optional.empty();
        }
        try {
            TimelineDocument document = TimelineDocumentJsonSerializer.deserialize(
                    snapshot.get().payloadJson());
            return Optional.ofNullable(document);
        } catch (Exception e) {
            // Malformed governed payload: fail closed (caller returns PAYLOAD_INVALID).
            log.warn("Failed to deserialize governed payload for revision {}: {}", revisionId, e.getMessage());
            return Optional.empty();
        }
    }

    private void requireOwnedProject(String tenantId, String projectId) {
        if (tenantId == null || tenantId.isBlank()
                || projectId == null || projectId.isBlank()
                || dsl.fetchExists(DSL.selectOne().from("project")
                        .where(DSL.field("id", String.class).eq(projectId))
                        .and(DSL.field("tenant_id", String.class).eq(tenantId))) == false) {
            throw new IllegalArgumentException(
                    "canonical Timeline project is not available in the requested tenant");
        }
    }

}
