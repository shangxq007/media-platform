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
    // B2: narrow persistence ports for bounded failure injection (production
    // defaults are the single jOOQ writer / CAS head update; tests may
    // substitute failing implementations — no testMode booleans in logic).
    private TimelineRevisionPersistencePort revisionPersistence =
            new DefaultTimelineRevisionPersistence();
    private HeadUpdatePort headUpdatePort;

    /** B2 test-injection point (default = production jOOQ writer). */
    public void setRevisionPersistencePort(TimelineRevisionPersistencePort port) {
        this.revisionPersistence = Objects.requireNonNull(port, "port");
    }

    /** B2 test-injection point (default = production CAS head update). */
    public void setHeadUpdatePort(HeadUpdatePort port) {
        this.headUpdatePort = Objects.requireNonNull(port, "port");
    }

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
                                       com.example.platform.timeline.version.TimelineRevisionSemanticContextStore revisionSemanticContextStore) {
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
        this.headUpdatePort =
                (tx, productId, expected, newRevisionId) ->
                        currentRevisionService.updateCurrentRevisionTx(tx, productId, expected, newRevisionId);
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
            String snapshotId = persistSnapshotPayload(tx.dsl(), productId, document, revisionId);

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

            revisionPersistence.insertRevisionTx(
                    tx.dsl(), revision, productId, snapshotId, revision.timelineSchemaVersion(),
                    nextRevisionNumber, com.example.platform.shared.web.TenantContext.get(), "api");

            headUpdatePort.updateHeadTx(tx.dsl(), productId, expectedCurrentRevisionId, revisionId);

            // ROADMAP20 authority integration: the revision-owned semantic
            // context (exact Effect pin + digests) is persisted in the SAME
            // physical transaction — reload reconstructs the pin without caller
            // input; write failure rolls back the whole revision.
            revisionSemanticContextStore.storeTx(tx.dsl(), productId, revisionId, semanticContext);

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
                        com.example.platform.shared.web.TenantContext.get(),
                        pinsToRegister.stream()
                                .map(p -> new com.example.platform.artifact.app.ArtifactPinService.ArtifactPin(
                                        p.artifactId(), p.contentDigest()))
                                .toList());
            }

            log.info("Saved timeline revision {} for product {}", revisionId, productId);
            return revision;
        });
    }

    @Transactional
    public TimelineRevision restoreRevision(String productId, String historicalRevisionId,
                                           String expectedCurrentRevisionId, String createdBy) {
        String contentHash = dsl.select(TIMELINE_REVISION.CONTENT_HASH)
                .from(TIMELINE_REVISION)
                .where(TIMELINE_REVISION.ID.eq(historicalRevisionId))
                .and(TIMELINE_REVISION.PROJECT_ID.eq(productId))
                .fetchOne(TIMELINE_REVISION.CONTENT_HASH);

        if (contentHash == null) {
            throw new IllegalArgumentException("Historical revision not found: " + historicalRevisionId);
        }

        String schemaVersion = dsl.select(TIMELINE_REVISION.SCHEMA_VERSION)
                .from(TIMELINE_REVISION)
                .where(TIMELINE_REVISION.ID.eq(historicalRevisionId))
                .and(TIMELINE_REVISION.PROJECT_ID.eq(productId))
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
            // Contract P: restore writes a new snapshot row carrying the source revision's
            // governed payload (copy of the historical payload) when one exists, so the restored
            // revision never points at a missing payload. Legacy rows without a payload keep the
            // historical behavior (documented limitation; no backfill, no migration).
            String snapshotId = copyHistoricalSnapshotPayload(tx.dsl(), productId, historicalRevisionId, revisionId);

            // ROADMAP20 authority integration (§10/§35): the restored NEW
            // revision must own exact Effect semantics — load the historical
            // revision's semantic context. Legacy MISSING cannot be silently
            // propagated into a new canonical revision: FAIL CLOSED (no
            // deterministic legacy hydration source exists today).
            com.example.platform.timeline.version.TimelineRevisionSemanticContext historicalContext =
                    revisionSemanticContextStore.findByRevisionId(tx.dsl(), historicalRevisionId).orElse(null);
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

            tx.dsl().insertInto(TIMELINE_REVISION)
                    .set(TIMELINE_REVISION.ID, revisionId)
                    .set(TIMELINE_REVISION.PROJECT_ID, productId)
                    .set(TIMELINE_REVISION.PARENT_REVISION_ID, expectedCurrentRevisionId)
                    // Contract P: record the governing tenant (tenant-guarded lookup parity).
                    .set(TIMELINE_REVISION.TENANT_ID, com.example.platform.shared.web.TenantContext.get())
                    .set(TIMELINE_REVISION.REVISION_NUMBER, nextRevisionNumber)
                    .set(TIMELINE_REVISION.SNAPSHOT_ID, snapshotId)
                    .set(TIMELINE_REVISION.INTERNAL_REVISION, nextRevisionNumber)
                    .set(TIMELINE_REVISION.CONTENT_HASH, revisionSemanticDigest)
                    .set(TIMELINE_REVISION.SCHEMA_VERSION, schemaVersionFinal)
                    .set(TIMELINE_REVISION.CREATED_AT, LocalDateTime.now())
                    .set(TIMELINE_REVISION.SOURCE, "restore")
                    .execute();

            currentRevisionService.updateCurrentRevisionTx(tx.dsl(), productId, expectedCurrentRevisionId, revisionId);

            // ROADMAP20 authority integration: persist the restored revision's
            // own semantic context (same physical transaction).
            revisionSemanticContextStore.storeTx(tx.dsl(), productId, revisionId, newContext);

            // R4-D1: the restored revision is a DISTINCT revision id — it must
            // carry its own artifact-pin protection rows, copied from the
            // historical revision's immutable pins in the SAME transaction.
            // R5-C: no nullable skip — pin persistence authority is required
            // by construction.
            artifactPinService.copyRevisionPinsTx(tx.dsl(), productId, historicalRevisionId, revisionId);

            log.info("Restored revision {} as new revision {} for product {}", historicalRevisionId, revisionId, productId);
            return new TimelineRevision(revisionId, productId, expectedCurrentRevisionId,
                    schemaVersionFinal,
                    null, revisionSemanticDigest, Instant.now(), createdBy, newContext);
        });
    }

    /**
     * Contract P: persist the governed snapshot payload through the sole existing authority
     * ({@link TimelineSnapshotService}) inside the current transaction. Falls back to the
     * legacy SNAPSHOT_ID = revision id behavior only for the backward-compatible 3-arg
     * constructor wiring (no snapshot service available).
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
                                          TimelineDocument document, String fallbackRevisionId) {
        if (timelineSnapshotService == null) {
            return fallbackRevisionId;
        }
        return timelineSnapshotService.saveTx(
                tx, productId,
                null,
                TimelineDocumentJsonSerializer.serializeWithCaptions(document),
                TimelineDocument.CURRENT_SCHEMA_VERSION);
    }

    /**
     * Contract P: restore copies the historical revision's governed payload into a new
     * snapshot row so the restored revision never points at a missing payload. When the
     * historical revision has no payload row (legacy data), the legacy behavior is preserved.
     * R4-D1: runs on the transaction DSL so the snapshot copy joins the restore transaction.
     */
    private String copyHistoricalSnapshotPayload(org.jooq.DSLContext tx, String productId, String historicalRevisionId, String fallbackRevisionId) {
        if (timelineSnapshotService == null) {
            return fallbackRevisionId;
        }
        String historicalSnapshotId = tx.select(TIMELINE_REVISION.SNAPSHOT_ID)
                .from(TIMELINE_REVISION)
                .where(TIMELINE_REVISION.ID.eq(historicalRevisionId))
                .and(TIMELINE_REVISION.PROJECT_ID.eq(productId))
                .fetchOne(TIMELINE_REVISION.SNAPSHOT_ID);
        if (historicalSnapshotId == null) {
            return fallbackRevisionId;
        }
        String payload = timelineSnapshotService.findPayload(historicalSnapshotId).orElse(null);
        if (payload == null) {
            return fallbackRevisionId;
        }
        String schemaVersion = tx.select(TIMELINE_REVISION.SCHEMA_VERSION)
                .from(TIMELINE_REVISION)
                .where(TIMELINE_REVISION.ID.eq(historicalRevisionId))
                .and(TIMELINE_REVISION.PROJECT_ID.eq(productId))
                .fetchOne(TIMELINE_REVISION.SCHEMA_VERSION);
        return timelineSnapshotService.saveTx(tx, productId, null, payload, schemaVersion);
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
                revisionSemanticContextStore.findByRevisionId(revisionId).orElse(null);
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
     * codec ({@link TimelineDocumentJsonSerializer}).
     *
     * <p>The Patch application service consumes the original governed
     * {@link TimelineDocument} through this helper BEFORE invoking the Patch engine.
     * {@code findById} keeps its documented reconstruction contract (canonicalTimeline
     * may be null when loading without the full document); the hydration is explicit
     * and local to the Patch flow.</p>
     *
     * <p>Returns {@link Optional#empty()} when the revision has no snapshot payload row
     * (legacy/GitV1 rows), the payload is missing, or the payload is malformed — the
     * caller keeps the fail-closed TIMELINE_PATCH_PAYLOAD_INVALID classification. No
     * snapshot, revision, or current-revision write ever occurs on this path.</p>
     */
    @Transactional(readOnly = true)
    public Optional<TimelineDocument> findPayloadDocument(String revisionId) {
        if (timelineSnapshotService == null) {
            // Legacy 3-arg wiring (no snapshot authority): no governed payload exists.
            return Optional.empty();
        }
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
