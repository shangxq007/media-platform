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
import com.fasterxml.jackson.databind.DeserializationFeature;
import org.jooq.DSLContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
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

    /**
     * SINGLE production constructor: snapshot payload + CHECKPOINT_A artifact-pin
     * invariant boundary (extract → validate → register, same transaction).
     *
     * <p>Fail-closed guard: if the validator/pin-service wiring is absent and the
     * document actually carries artifact pins, saveRevision throws — a no-pin
     * save surface can never commit pinned content. (Test fixtures without pins
     * are unaffected.)
     */
    @Autowired
    public TimelineRevisionSaveService(DSLContext dsl,
                                       ProductCurrentRevisionService currentRevisionService,
                                       TimelineContentDigester contentDigester,
                                       TimelineSnapshotService timelineSnapshotService,
                                       TimelineArtifactPinValidator artifactPinValidator,
                                       com.example.platform.artifact.app.ArtifactPinService artifactPinService) {
        this.dsl = dsl;
        this.currentRevisionService = currentRevisionService;
        this.contentDigester = contentDigester;
        this.timelineSnapshotService = timelineSnapshotService;
        this.artifactPinValidator = artifactPinValidator;
        this.artifactPinService = artifactPinService;
    }

    @Transactional
    public TimelineRevision saveRevision(String productId, String expectedCurrentRevisionId,
                                         TimelineDocument document, String createdBy) {
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
        // (existence + tenant + digest) → register (same transaction). The
        // production wiring (6-arg constructor) always enforces it; the
        // test-only wiring (no validator injected) skips the gate for legacy
        // direct-wiring tests, documented on the constructor.
        java.util.List<TimelineArtifactPinExtractor.ArtifactPin> pins = java.util.Collections.emptyList();
        if (artifactPinValidator != null) {
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
        } else {
            // CHECKPOINT_A fail-closed guard: a save surface without the pin
            // validator must never commit a document that carries artifact pins.
            pins = extractPinsFromDocument(document);
            if (!pins.isEmpty()) {
                throw new IllegalStateException(
                        "TimelineRevisionSaveService without artifact-pin validator cannot save pinned content");
            }
        }

        String digest = contentDigester.digest(document);
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

            TimelineRevision revision = new TimelineRevision(
                    revisionId, productId, parentRevisionId,
                    TimelineDocument.CURRENT_SCHEMA_VERSION,
                    document, digest, Instant.now(), createdBy);

            // Compute revision number for this product
            Integer maxRevisionNumber = tx.dsl().select(org.jooq.impl.DSL.max(TIMELINE_REVISION.REVISION_NUMBER))
                    .from(TIMELINE_REVISION)
                    .where(TIMELINE_REVISION.PROJECT_ID.eq(productId))
                    .fetchOneInto(Integer.class);
            int nextRevisionNumber = (maxRevisionNumber != null ? maxRevisionNumber : 0) + 1;

            tx.dsl().insertInto(TIMELINE_REVISION)
                    .set(TIMELINE_REVISION.ID, revision.revisionId())
                    .set(TIMELINE_REVISION.PROJECT_ID, productId)
                    .set(TIMELINE_REVISION.PARENT_REVISION_ID, revision.parentRevisionId())
                    .set(TIMELINE_REVISION.REVISION_NUMBER, nextRevisionNumber)
                    .set(TIMELINE_REVISION.TENANT_ID, com.example.platform.shared.web.TenantContext.get())
                    .set(TIMELINE_REVISION.SNAPSHOT_ID, snapshotId)
                    .set(TIMELINE_REVISION.INTERNAL_REVISION, nextRevisionNumber)
                    .set(TIMELINE_REVISION.CONTENT_HASH, revision.contentDigest())
                    .set(TIMELINE_REVISION.SCHEMA_VERSION, revision.timelineSchemaVersion())
                    .set(TIMELINE_REVISION.CREATED_AT, LocalDateTime.now())
                    .set(TIMELINE_REVISION.SOURCE, "api")
                    .execute();

            currentRevisionService.updateCurrentRevisionTx(tx.dsl(), productId, expectedCurrentRevisionId, revisionId);

            // CHECKPOINT_A (Blocker C): register artifact_pin protection rows in
            // the SAME transaction as the revision (PIN_REGISTRATION_FAILURE
            // rolls back the whole write — no visible dangling revision state).
            if (artifactPinService != null && !pinsToRegister.isEmpty()) {
                artifactPinService.registerRevisionPins(productId, revisionId,
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

        // Contract P: restore writes a new snapshot row carrying the source revision's
        // governed payload (copy of the historical payload) when one exists, so the restored
        // revision never points at a missing payload. Legacy rows without a payload keep the
        // historical behavior (documented limitation; no backfill, no migration).
        String snapshotId = copyHistoricalSnapshotPayload(productId, historicalRevisionId, revisionId);

        // Compute revision number for this product
        Integer maxRevisionNumber = dsl.select(org.jooq.impl.DSL.max(TIMELINE_REVISION.REVISION_NUMBER))
                .from(TIMELINE_REVISION)
                .where(TIMELINE_REVISION.PROJECT_ID.eq(productId))
                .fetchOneInto(Integer.class);
        int nextRevisionNumber = (maxRevisionNumber != null ? maxRevisionNumber : 0) + 1;

        dsl.insertInto(TIMELINE_REVISION)
                .set(TIMELINE_REVISION.ID, revisionId)
                .set(TIMELINE_REVISION.PROJECT_ID, productId)
                .set(TIMELINE_REVISION.PARENT_REVISION_ID, expectedCurrentRevisionId)
                // Contract P: record the governing tenant (tenant-guarded lookup parity).
                .set(TIMELINE_REVISION.TENANT_ID, com.example.platform.shared.web.TenantContext.get())
                .set(TIMELINE_REVISION.REVISION_NUMBER, nextRevisionNumber)
                .set(TIMELINE_REVISION.SNAPSHOT_ID, snapshotId)
                .set(TIMELINE_REVISION.INTERNAL_REVISION, nextRevisionNumber)
                .set(TIMELINE_REVISION.CONTENT_HASH, contentHash)
                .set(TIMELINE_REVISION.SCHEMA_VERSION, schemaVersion)
                .set(TIMELINE_REVISION.CREATED_AT, LocalDateTime.now())
                .set(TIMELINE_REVISION.SOURCE, "restore")
                .execute();

        currentRevisionService.updateCurrentRevision(productId, expectedCurrentRevisionId, revisionId);

        log.info("Restored revision {} as new revision {} for product {}", historicalRevisionId, revisionId, productId);

        return new TimelineRevision(revisionId, productId, expectedCurrentRevisionId,
                schemaVersion,
                null, contentHash, Instant.now(), createdBy);
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
     */
    private String copyHistoricalSnapshotPayload(String productId, String historicalRevisionId, String fallbackRevisionId) {
        if (timelineSnapshotService == null) {
            return fallbackRevisionId;
        }
        String historicalSnapshotId = dsl.select(TIMELINE_REVISION.SNAPSHOT_ID)
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
        String schemaVersion = dsl.select(TIMELINE_REVISION.SCHEMA_VERSION)
                .from(TIMELINE_REVISION)
                .where(TIMELINE_REVISION.ID.eq(historicalRevisionId))
                .and(TIMELINE_REVISION.PROJECT_ID.eq(productId))
                .fetchOne(TIMELINE_REVISION.SCHEMA_VERSION);
        return timelineSnapshotService.save(productId, null, payload, schemaVersion);
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

        return new TimelineRevision(
                revisionId, projectId, parentId, schemaVersion,
                null, contentHash,
                createdAt != null ? createdAt.atZone(java.time.ZoneId.systemDefault()).toInstant() : null,
                authorUserId);
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
