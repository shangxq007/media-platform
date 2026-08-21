package com.example.platform.timeline.app;

import com.example.platform.timeline.adapter.TimelineSnapshotService;
import com.example.platform.timeline.canonical.TimelineContentDigester;
import com.example.platform.timeline.canonical.TimelineDocument;
import com.example.platform.timeline.canonicalmodel.TimelineCandidate;
import com.example.platform.timeline.canonicalmodel.TimelineCanonicalNormalizer;
import com.example.platform.timeline.canonicalmodel.TimelineCanonicalValidator;
import com.example.platform.timeline.canonicalmodel.TimelineValidationResult;
import com.example.platform.timeline.version.TimelineConflictException;
import com.example.platform.timeline.version.TimelineRevision;
import java.util.Objects;
import com.fasterxml.jackson.databind.DeserializationFeature;
import org.jooq.DSLContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static com.example.platform.typedschema.jooq.generated.tables.TimelineRevision.TIMELINE_REVISION;

/**
 * E1-gated primary save surface for {@link TimelineDocument} revisions.
 *
 * <p>Contract P (PTCSG_REAL_RENDER_SUBTITLE_VERTICAL_SLICE_V1) completion: the E1 save
 * path now persists the governed snapshot payload through the existing sole authority
 * {@link TimelineSnapshotService} within the existing transaction, so a revision row is
 * never visible without its payload and the render/patch consumers resolve correctly.</p>
 *
 * <p>Frozen transactional order (PTADTF-C contract-p-snapshot-transaction-contract):
 * canonical acceptance (gate, first statement) -> digest/conflict decision -> snapshot
 * payload write -> revision insert -> current-revision update.</p>
 */
@Service
public class TimelineRevisionSaveService {

    private static final Logger log = LoggerFactory.getLogger(TimelineRevisionSaveService.class);
    private final DSLContext dsl;
    private final ProductCurrentRevisionService currentRevisionService;
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
                                       ProductCurrentRevisionService currentRevisionService,
                                       TimelineContentDigester contentDigester,
                                       TimelineSnapshotService timelineSnapshotService,
                                       TimelineArtifactPinValidator artifactPinValidator,
                                       com.example.platform.artifact.app.ArtifactPinService artifactPinService,
                                       com.example.platform.timeline.semantics.effect.EffectSemanticSnapshotAuthority effectSnapshotAuthority,
                                       com.example.platform.timeline.version.TimelineRevisionSemanticContextStore revisionSemanticContextStore,
                                       TimelineRevisionPersistencePort revisionPersistence,
                                       HeadUpdatePort headUpdatePort) {
        // R5-C (CHECKPOINT_A Round 5): ALL production invariants are REQUIRED
        // BY CONSTRUCTION — no constructor permits a save/restore surface with
        // a missing artifact-pin dependency. A pinned revision can never be
        // committed without pin validation/persistence authority.
        this.dsl = Objects.requireNonNull(dsl, "dsl");
        this.currentRevisionService = Objects.requireNonNull(currentRevisionService, "currentRevisionService");
        this.contentDigester = Objects.requireNonNull(contentDigester, "contentDigester");
        this.timelineSnapshotService = Objects.requireNonNull(timelineSnapshotService, "timelineSnapshotService");
        this.artifactPinValidator = Objects.requireNonNull(artifactPinValidator, "artifactPinValidator");
        this.artifactPinService = Objects.requireNonNull(artifactPinService, "artifactPinService");
        this.effectSnapshotAuthority = Objects.requireNonNull(effectSnapshotAuthority, "effectSnapshotAuthority");
        this.revisionSemanticContextStore = Objects.requireNonNull(revisionSemanticContextStore, "revisionSemanticContextStore");
        this.revisionPersistence = Objects.requireNonNull(revisionPersistence, "revisionPersistence");
        this.headUpdatePort = Objects.requireNonNull(headUpdatePort, "headUpdatePort");
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
    public TimelineRevision saveRevision(String productId, String expectedCurrentRevisionId,
                                         TimelineDocument document, String createdBy) {
        return saveRevisionInternal(productId, expectedCurrentRevisionId, document,
                java.util.List.of(), java.util.List.of(), createdBy);
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
            String productId, String expectedCurrentRevisionId,
            TimelineDocument document,
            java.util.List<com.example.platform.timeline.semantics.effect.EffectInstance> effects,
            java.util.List<com.example.platform.timeline.semantics.effect.EffectInstance.EffectDefinition> definitions,
            String createdBy) {
        return saveRevisionInternal(productId, expectedCurrentRevisionId, document,
                effects == null ? java.util.List.of() : effects,
                definitions == null ? java.util.List.of() : definitions,
                createdBy);
    }

    private TimelineRevision saveRevisionInternal(
            String productId, String expectedCurrentRevisionId,
            TimelineDocument document,
            java.util.List<com.example.platform.timeline.semantics.effect.EffectInstance> effects,
            java.util.List<com.example.platform.timeline.semantics.effect.EffectInstance.EffectDefinition> definitions,
            String createdBy) {
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
                    artifactPinValidator.validate(com.example.platform.shared.web.TenantContext.get(), pins);
            if (!pinValidation.valid()) {
                throw new TimelineCanonicalRejectionException(
                        new TimelineCanonicalRejectionException.AdapterDiagnostic(
                                TimelineCanonicalRejectionException.Code.TIMELINE_SOURCE_REF_INVALID,
                                com.example.platform.timeline.canonicalmodel.TimelineModelPath.root().field("sourceBinding"),
                                "Artifact pin reference-integrity: " + String.join("; ", pinValidation.violations())));
            }
        }

        String timelineDigest = contentDigester.digest(document);
        String parentRevisionId = currentRevisionService.getCurrentRevisionId(productId);

        if ((expectedCurrentRevisionId == null && parentRevisionId != null) ||
            (expectedCurrentRevisionId != null && !expectedCurrentRevisionId.equals(parentRevisionId))) {
            throw new TimelineConflictException(productId, expectedCurrentRevisionId, parentRevisionId);
        }

        // CHECKPOINT_A (Round 3): explicit jOOQ transaction — revision insert +
        // pin registration + head update are ONE atomic unit regardless of any
        // Spring proxy boundary. Pin-registration failure rolls the whole write
        // back (no visible dangling revision), even when this service is invoked
        // directly (ITs, non-proxied wiring).
        String revisionId = UUID.randomUUID().toString();
        final java.util.List<TimelineArtifactPinExtractor.ArtifactPin> pinsToRegister = pins;
        return dsl.transactionResult(tx -> {
            String snapshotId = persistSnapshotPayload(tx.dsl(), productId, document);

            // ROADMAP20 authority integration (blockers 1/4): every NEW canonical
            // revision mints authoritative Effect semantics (EMPTY for the
            // document save path — the canonical document carries no Effect
            // authoring today), persists the snapshot durably, pins the exact
            // reference and computes the FULL revision semantic digest
            // H(timelineDigest, contractVersion, effectContentDigest).
            com.example.platform.timeline.semantics.effect.EffectSemanticSnapshot effectSnapshot =
                    effectSnapshotAuthority.mintAndPersistTx(
                            tx.dsl(), productId,
                            com.example.platform.shared.web.TenantContext.get(),
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

            // Compute revision number for this product
            Integer maxRevisionNumber = tx.dsl().select(org.jooq.impl.DSL.max(TIMELINE_REVISION.REVISION_NUMBER))
                    .from(TIMELINE_REVISION)
                    .where(TIMELINE_REVISION.PROJECT_ID.eq(productId))
                    .fetchOneInto(Integer.class);
            int nextRevisionNumber = (maxRevisionNumber != null ? maxRevisionNumber : 0) + 1;

            String tenantId = com.example.platform.shared.web.TenantContext.get();
            revisionPersistence.insertRevisionTx(
                    tx.dsl(), revision, productId, snapshotId, revision.timelineSchemaVersion(),
                    nextRevisionNumber, tenantId, "api");

            // F3: canonical state order — revision row, then semantic context,
            // then artifact pins; the HEAD CAS is the FINAL transactional
            // mutation (HEAD_ADVANCE_PUBLISHES_ONLY_FULLY_PERSISTED_REVISION_STATE_V1).
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

            // HEAD CAS LAST (F3): publishes only a fully persisted revision state.
            headUpdatePort.updateHeadTx(tx.dsl(), productId, expectedCurrentRevisionId, revisionId);

            log.info("Saved timeline revision {} for product {}", revisionId, productId);
            return revision;
        });
    }

    @Transactional
    public TimelineRevision restoreRevision(String productId, String historicalRevisionId,
                                           String expectedCurrentRevisionId, String createdBy) {
        String tenantId = com.example.platform.shared.web.TenantContext.get();
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
            String snapshotId = copyHistoricalSnapshotPayload(
                    tx.dsl(), productId, tenantId, historicalRevisionId, revisionId);

            // ROADMAP20 authority integration (§10/§35): the restored NEW
            // revision must own exact Effect semantics — load the historical
            // revision's semantic context. Legacy MISSING cannot be silently
            // propagated into a new canonical revision: FAIL CLOSED (no
            // deterministic legacy hydration source exists today).
            com.example.platform.timeline.version.TimelineRevisionSemanticContext historicalContext =
                    revisionSemanticContextStore.findByRevisionId(
                            tx.dsl(), productId, tenantId, historicalRevisionId).orElse(null);
            if (historicalContext == null) {
                throw new IllegalArgumentException(
                        "RESTORE FAIL CLOSED (§10/§35/CLEAN-FORWARD): historical revision '"
                                + historicalRevisionId + "' has no revision semantic context — a new "
                                + "canonical revision must never be created without authoritative "
                                + "Effect semantics");
            }
            // The restored revision pins the SAME immutable Effect snapshot
            // (historical binding untouched; semantic equivalence exact).
            String revisionSemanticDigest =
                    com.example.platform.timeline.semantics.effect.TimelineRevisionEffectSemanticCommitment
                            .revisionEffectSemanticDigest(
                                    historicalContext.timelineContentDigest(),
                                    historicalContext.effectReference());
            com.example.platform.timeline.version.TimelineRevisionSemanticContext newContext =
                    new com.example.platform.timeline.version.TimelineRevisionSemanticContext(
                            historicalContext.timelineContentDigest(),
                            historicalContext.effectReference(),
                            revisionSemanticDigest,
                            historicalContext.digestContractVersion());

            // Compute revision number for this product
            Integer maxRevisionNumber = tx.dsl().select(org.jooq.impl.DSL.max(TIMELINE_REVISION.REVISION_NUMBER))
                    .from(TIMELINE_REVISION)
                    .where(TIMELINE_REVISION.PROJECT_ID.eq(productId))
                    .fetchOneInto(Integer.class);
            int nextRevisionNumber = (maxRevisionNumber != null ? maxRevisionNumber : 0) + 1;

            revisionPersistence.insertRevisionTx(
                    tx.dsl(), new TimelineRevision(revisionId, productId, expectedCurrentRevisionId,
                            schemaVersionFinal, null, revisionSemanticDigest, java.time.Instant.now(),
                            createdBy, newContext),
                    productId, snapshotId, schemaVersionFinal, nextRevisionNumber, tenantId, "restore");

            // F3: revctx + pins BEFORE the head CAS (final mutation).
            revisionSemanticContextStore.storeTx(tx.dsl(), productId, tenantId, revisionId, newContext);

            // R4-D1: the restored revision is a DISTINCT revision id — it must
            // carry its own artifact-pin protection rows, copied from the
            // historical revision's immutable pins in the SAME transaction.
            // R5-C: no nullable skip — pin persistence authority is required
            // by construction.
            // F3: the HEAD CAS is the FINAL mutation (after pins).
            artifactPinService.copyRevisionPinsTx(tx.dsl(), productId, historicalRevisionId, revisionId);

            // F3: HEAD CAS LAST — publishes only fully persisted restored state.
            headUpdatePort.updateHeadTx(tx.dsl(), productId, expectedCurrentRevisionId, revisionId);

            log.info("Restored revision {} as new revision {} for product {}", historicalRevisionId, revisionId, productId);
            return new TimelineRevision(revisionId, productId, expectedCurrentRevisionId,
                    schemaVersionFinal,
                    null, revisionSemanticDigest, Instant.now(), createdBy, newContext);
        });
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
                                          TimelineDocument document) {
        return timelineSnapshotService.saveTx(
                tx, productId,
                null,
                TimelineDocumentJsonSerializer.serializeWithCaptions(document),
                TimelineDocument.CURRENT_SCHEMA_VERSION);
    }

    /**
     * Contract P + F4 (RESTORE_ONLY_ACCEPTS_COMPLETE_FINAL_CANONICAL_REVISION_V1):
     * restore copies the historical revision's governed payload into a NEW snapshot
     * row so the restored revision never points at a missing payload. A historical
     * revision with a null/blank SNAPSHOT_ID or a missing payload row FAILS CLOSED —
     * NO legacy restore payload fallback exists
     * (RESTORE_MISSING_GOVERNED_PAYLOAD_FAILS_CLOSED_V1,
     * NO_LEGACY_RESTORE_PAYLOAD_FALLBACK_V1).
     */
    private String copyHistoricalSnapshotPayload(org.jooq.DSLContext tx, String projectId,
                                                 String tenantId, String historicalRevisionId,
                                                 String newRevisionId) {
        String historicalSnapshotId = tx.select(TIMELINE_REVISION.SNAPSHOT_ID)
                .from(TIMELINE_REVISION)
                .where(TIMELINE_REVISION.ID.eq(historicalRevisionId))
                .and(TIMELINE_REVISION.PROJECT_ID.eq(projectId))
                .and(TIMELINE_REVISION.TENANT_ID.eq(tenantId))
                .fetchOne(TIMELINE_REVISION.SNAPSHOT_ID);
        if (historicalSnapshotId == null || historicalSnapshotId.isBlank()) {
            throw new IllegalStateException(
                    "RESTORE FAIL CLOSED (RST1): historical revision '" + historicalRevisionId
                            + "' has no governed snapshot id — NO legacy restore fallback (F4)");
        }
        String payload = timelineSnapshotService.findPayload(historicalSnapshotId).orElse(null);
        if (payload == null) {
            throw new IllegalStateException(
                    "RESTORE FAIL CLOSED (RST2): historical snapshot '" + historicalSnapshotId
                            + "' payload row is missing — NO legacy restore fallback (F4)");
        }
        String schemaVersion = tx.select(TIMELINE_REVISION.SCHEMA_VERSION)
                .from(TIMELINE_REVISION)
                .where(TIMELINE_REVISION.ID.eq(historicalRevisionId))
                .and(TIMELINE_REVISION.PROJECT_ID.eq(projectId))
                .and(TIMELINE_REVISION.TENANT_ID.eq(tenantId))
                .fetchOne(TIMELINE_REVISION.SCHEMA_VERSION);
        if (schemaVersion == null) {
            throw new IllegalStateException(
                    "RESTORE FAIL CLOSED: historical revision '" + historicalRevisionId
                            + "' has no schema version");
        }
        return timelineSnapshotService.saveTx(tx, projectId, null, payload, schemaVersion);
    }

    @Transactional(readOnly = true)
    public TimelineRevision findById(String revisionId) {
        String projectId = dsl.select(TIMELINE_REVISION.PROJECT_ID)
                .from(TIMELINE_REVISION)
                .where(TIMELINE_REVISION.ID.eq(revisionId))
                .fetchOne(TIMELINE_REVISION.PROJECT_ID);

        if (projectId == null) return null;

        String parentId = dsl.select(TIMELINE_REVISION.PARENT_REVISION_ID)
                .from(TIMELINE_REVISION)
                .where(TIMELINE_REVISION.ID.eq(revisionId))
                .fetchOne(TIMELINE_REVISION.PARENT_REVISION_ID);

        String schemaVersion = dsl.select(TIMELINE_REVISION.SCHEMA_VERSION)
                .from(TIMELINE_REVISION)
                .where(TIMELINE_REVISION.ID.eq(revisionId))
                .fetchOne(TIMELINE_REVISION.SCHEMA_VERSION);

        String contentHash = dsl.select(TIMELINE_REVISION.CONTENT_HASH)
                .from(TIMELINE_REVISION)
                .where(TIMELINE_REVISION.ID.eq(revisionId))
                .fetchOne(TIMELINE_REVISION.CONTENT_HASH);

        LocalDateTime createdAt = dsl.select(TIMELINE_REVISION.CREATED_AT)
                .from(TIMELINE_REVISION)
                .where(TIMELINE_REVISION.ID.eq(revisionId))
                .fetchOne(TIMELINE_REVISION.CREATED_AT);

        String authorUserId = dsl.select(TIMELINE_REVISION.AUTHOR_USER_ID)
                .from(TIMELINE_REVISION)
                .where(TIMELINE_REVISION.ID.eq(revisionId))
                .fetchOne(TIMELINE_REVISION.AUTHOR_USER_ID);

        // ROADMAP20 authority integration: the revision's semantic context
        // (exact Effect pin) is loaded from revision-owned persisted state —
        // reconstructs the pin WITHOUT caller input.
        com.example.platform.timeline.version.TimelineRevisionSemanticContext semanticContext =
                revisionSemanticContextStore.findByRevisionId(
                        dsl, projectId, com.example.platform.shared.web.TenantContext.get(), revisionId)
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
                revisionId, projectId, parentId, schemaVersion,
                null, semanticContext.revisionSemanticDigest(),
                createdAt != null ? createdAt.atZone(java.time.ZoneId.systemDefault()).toInstant() : null,
                authorUserId, semanticContext);
    }

    /**
     * PPHR-BIC (PATCH_APPLICATION_PAYLOAD_HYDRATION_DEFECT): resolve the governed
     * snapshot payload for a revision through the existing sole snapshot authority
     * ({@link TimelineSnapshotService}) and deserialize it with the governed payload
     * codec ({@link TimelineDocumentJsonSerializer}). Read helper only — no
     * canonical mutation occurs on this path.
     *
     * <p>Returns {@link Optional#empty()} when the revision has no snapshot payload
     * row or the payload is missing/malformed — the caller maps that to explicit
     * fail-closed behavior (F4: missing governed payload never produces a new valid
     * canonical revision).</p>
     */
    @Transactional(readOnly = true)
    public Optional<TimelineDocument> findPayloadDocument(String revisionId) {
        String snapshotId = dsl.select(TIMELINE_REVISION.SNAPSHOT_ID)
                .from(TIMELINE_REVISION)
                .where(TIMELINE_REVISION.ID.eq(revisionId))
                .fetchOne(TIMELINE_REVISION.SNAPSHOT_ID);
        if (snapshotId == null || snapshotId.isBlank()) {
            return Optional.empty();
        }
        Optional<String> payload = timelineSnapshotService.findPayload(snapshotId);
        if (payload.isEmpty()) {
            return Optional.empty();
        }
        try {
            TimelineDocument document = TimelineDocumentJsonSerializer.mapper()
                    .readerFor(TimelineDocument.class)
                    .without(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                    .readValue(payload.get());
            return Optional.ofNullable(document);
        } catch (Exception e) {
            // Malformed governed payload: fail closed (caller returns PAYLOAD_INVALID).
            log.warn("Failed to deserialize governed payload for revision {}: {}", revisionId, e.getMessage());
            return Optional.empty();
        }
    }
}
