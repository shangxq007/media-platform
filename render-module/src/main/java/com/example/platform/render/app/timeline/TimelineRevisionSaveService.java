package com.example.platform.render.app.timeline;

import com.example.platform.render.app.TimelineSnapshotService;
import com.example.platform.render.domain.timeline.canonical.TimelineContentDigester;
import com.example.platform.render.domain.timeline.canonical.TimelineDocument;
import com.example.platform.render.domain.timeline.canonicalmodel.TimelineCandidate;
import com.example.platform.render.domain.timeline.canonicalmodel.TimelineCanonicalNormalizer;
import com.example.platform.render.domain.timeline.canonicalmodel.TimelineCanonicalValidator;
import com.example.platform.render.domain.timeline.canonicalmodel.TimelineValidationResult;
import com.example.platform.render.domain.timeline.version.TimelineConflictException;
import com.example.platform.render.domain.timeline.version.TimelineRevision;
import org.jooq.DSLContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
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

    /**
     * Backward-compatible constructor for existing direct-wiring tests. When the snapshot
     * service is absent the legacy behavior is preserved (SNAPSHOT_ID = revision id, no
     * payload row) so pre-existing E1 tests remain valid.
     */
    public TimelineRevisionSaveService(DSLContext dsl,
                                       ProductCurrentRevisionService currentRevisionService,
                                       TimelineContentDigester contentDigester) {
        this(dsl, currentRevisionService, contentDigester, null);
    }

    /** Production constructor: enables the Contract P snapshot-payload completion. */
    @Autowired
    public TimelineRevisionSaveService(DSLContext dsl,
                                       ProductCurrentRevisionService currentRevisionService,
                                       TimelineContentDigester contentDigester,
                                       TimelineSnapshotService timelineSnapshotService) {
        this.dsl = dsl;
        this.currentRevisionService = currentRevisionService;
        this.contentDigester = contentDigester;
        this.timelineSnapshotService = timelineSnapshotService;
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

        String digest = contentDigester.digest(document);
        String parentRevisionId = currentRevisionService.getCurrentRevisionId(productId);

        if ((expectedCurrentRevisionId == null && parentRevisionId != null) ||
            (expectedCurrentRevisionId != null && !expectedCurrentRevisionId.equals(parentRevisionId))) {
            throw new TimelineConflictException(productId, expectedCurrentRevisionId, parentRevisionId);
        }

        String revisionId = UUID.randomUUID().toString();

        // Contract P: governed snapshot payload write through the existing sole authority
        // (TimelineSnapshotService), inside the same transaction, AFTER canonical acceptance
        // and AFTER the conflict decision, and BEFORE the revision row becomes visible.
        String snapshotId = persistSnapshotPayload(productId, document, revisionId);

        TimelineRevision revision = new TimelineRevision(
                revisionId, productId, parentRevisionId,
                TimelineDocument.CURRENT_SCHEMA_VERSION,
                document, digest, Instant.now(), createdBy);

        // Compute revision number for this product
        Integer maxRevisionNumber = dsl.select(org.jooq.impl.DSL.max(TIMELINE_REVISION.REVISION_NUMBER))
                .from(TIMELINE_REVISION)
                .where(TIMELINE_REVISION.PROJECT_ID.eq(productId))
                .fetchOneInto(Integer.class);
        int nextRevisionNumber = (maxRevisionNumber != null ? maxRevisionNumber : 0) + 1;

        dsl.insertInto(TIMELINE_REVISION)
                .set(TIMELINE_REVISION.ID, revision.revisionId())
                .set(TIMELINE_REVISION.PROJECT_ID, productId)
                .set(TIMELINE_REVISION.PARENT_REVISION_ID, revision.parentRevisionId())
                .set(TIMELINE_REVISION.REVISION_NUMBER, nextRevisionNumber)
                // Contract P: record the governing tenant so the tenant-guarded
                // render/patch lookups can resolve E1-saved revisions.
                .set(TIMELINE_REVISION.TENANT_ID, com.example.platform.shared.web.TenantContext.get())
                .set(TIMELINE_REVISION.SNAPSHOT_ID, snapshotId)
                .set(TIMELINE_REVISION.INTERNAL_REVISION, nextRevisionNumber)
                .set(TIMELINE_REVISION.CONTENT_HASH, revision.contentDigest())
                .set(TIMELINE_REVISION.SCHEMA_VERSION, revision.timelineSchemaVersion())
                .set(TIMELINE_REVISION.CREATED_AT, LocalDateTime.now())
                .set(TIMELINE_REVISION.SOURCE, "api")
                .execute();

        currentRevisionService.updateCurrentRevision(productId, expectedCurrentRevisionId, revisionId);

        log.info("Saved timeline revision {} for product {}", revisionId, productId);
        return revision;
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
    private String persistSnapshotPayload(String productId, TimelineDocument document, String fallbackRevisionId) {
        if (timelineSnapshotService == null) {
            return fallbackRevisionId;
        }
        return timelineSnapshotService.save(
                productId,
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
}
