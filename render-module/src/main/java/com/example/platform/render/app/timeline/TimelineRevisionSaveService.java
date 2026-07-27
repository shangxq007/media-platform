package com.example.platform.render.app.timeline;

import com.example.platform.render.domain.timeline.canonical.TimelineContentDigester;
import com.example.platform.render.domain.timeline.canonical.TimelineDocument;
import com.example.platform.render.domain.timeline.version.TimelineConflictException;
import com.example.platform.render.domain.timeline.version.TimelineRevision;
import org.jooq.DSLContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

import static com.example.platform.typedschema.jooq.generated.tables.TimelineRevision.TIMELINE_REVISION;

@Service
public class TimelineRevisionSaveService {

    private static final Logger log = LoggerFactory.getLogger(TimelineRevisionSaveService.class);
    private final DSLContext dsl;
    private final ProductCurrentRevisionService currentRevisionService;
    private final TimelineContentDigester contentDigester;

    public TimelineRevisionSaveService(DSLContext dsl,
                                       ProductCurrentRevisionService currentRevisionService,
                                       TimelineContentDigester contentDigester) {
        this.dsl = dsl;
        this.currentRevisionService = currentRevisionService;
        this.contentDigester = contentDigester;
    }

    @Transactional
    public TimelineRevision saveRevision(String productId, String expectedCurrentRevisionId,
                                         TimelineDocument document, String createdBy) {
        String digest = contentDigester.digest(document);
        String parentRevisionId = currentRevisionService.getCurrentRevisionId(productId);

        if ((expectedCurrentRevisionId == null && parentRevisionId != null) ||
            (expectedCurrentRevisionId != null && !expectedCurrentRevisionId.equals(parentRevisionId))) {
            throw new TimelineConflictException(productId, expectedCurrentRevisionId, parentRevisionId);
        }

        String revisionId = UUID.randomUUID().toString();
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
                .set(TIMELINE_REVISION.SNAPSHOT_ID, revision.revisionId())
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
                .set(TIMELINE_REVISION.REVISION_NUMBER, nextRevisionNumber)
                .set(TIMELINE_REVISION.SNAPSHOT_ID, revisionId)
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
