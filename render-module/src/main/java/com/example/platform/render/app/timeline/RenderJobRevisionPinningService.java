package com.example.platform.render.app.timeline;

import com.example.platform.render.infrastructure.RenderJobRepository;
import com.example.platform.shared.events.RenderInitiator;
import java.time.LocalDateTime;
import java.util.Objects;
import org.jooq.DSLContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.example.platform.typedschema.jooq.generated.tables.RenderJob.RENDER_JOB;
import static com.example.platform.typedschema.jooq.generated.tables.TimelineRevision.TIMELINE_REVISION;
import static com.example.platform.typedschema.jooq.generated.tables.Product.PRODUCT;

@Service
public class RenderJobRevisionPinningService {

    private static final Logger log = LoggerFactory.getLogger(RenderJobRevisionPinningService.class);
    private final DSLContext dsl;
    private final RenderJobRepository renderJobRepository;

    public RenderJobRevisionPinningService(DSLContext dsl, RenderJobRepository renderJobRepository) {
        this.dsl = dsl;
        this.renderJobRepository = renderJobRepository;
    }

    @Transactional
    public void createRenderJobWithRevision(String renderJobId, String productId,
                                            String timelineRevisionId, String backend,
                                            RenderInitiator initiator) {
        Objects.requireNonNull(initiator, "initiator must not be null");
        if (!isBoundBackendIdentity(backend)) {
            throw new IllegalArgumentException("A bound backend identity is required: " + backend);
        }

        String revisionId = dsl.select(TIMELINE_REVISION.ID)
                .from(TIMELINE_REVISION)
                .where(TIMELINE_REVISION.ID.eq(timelineRevisionId))
                .and(TIMELINE_REVISION.PROJECT_ID.eq(productId))
                .fetchOne(TIMELINE_REVISION.ID);

        if (revisionId == null) {
            throw new IllegalArgumentException(
                    String.format("Revision %s not found for product %s", timelineRevisionId, productId));
        }
        String tenantId = dsl.select(PRODUCT.TENANT_ID)
                .from(PRODUCT)
                .where(PRODUCT.PRODUCT_ID.eq(productId))
                .fetchOne(PRODUCT.TENANT_ID);
        if (tenantId == null || !tenantId.equals(initiator.tenantId())) {
            throw new IllegalArgumentException("Render initiator tenant does not match product tenant");
        }
        String snapshotId = dsl.select(TIMELINE_REVISION.SNAPSHOT_ID)
                .from(TIMELINE_REVISION)
                .where(TIMELINE_REVISION.ID.eq(timelineRevisionId))
                .fetchOne(TIMELINE_REVISION.SNAPSHOT_ID);

        dsl.insertInto(RENDER_JOB)
                .set(RENDER_JOB.ID, renderJobId)
                .set(RENDER_JOB.PROJECT_ID, productId)
                .set(RENDER_JOB.TENANT_ID, tenantId)
                .set(RENDER_JOB.TIMELINE_SNAPSHOT_ID, snapshotId)
                .set(RENDER_JOB.PROFILE, backend)
                .set(RENDER_JOB.STATUS, "QUEUED")
                .set(RENDER_JOB.CREATED_AT, LocalDateTime.now())
                .set(RENDER_JOB.INITIATOR_TYPE, initiator.actorType().name())
                .set(RENDER_JOB.INITIATOR_ID, initiator.actorId())
                .set(RENDER_JOB.INITIATOR_TENANT_ID, initiator.tenantId())
                .set(RENDER_JOB.TIMELINE_REVISION_ID, timelineRevisionId)
                .execute();

        log.info("Created render job {} pinned to revision {} for product {}",
                renderJobId, timelineRevisionId, productId);
    }

    @Transactional
    public String createRetryJob(String originalRenderJobId, String newJobId) {
        String pinnedRevisionId = dsl.select(RENDER_JOB.TIMELINE_REVISION_ID)
                .from(RENDER_JOB)
                .where(RENDER_JOB.ID.eq(originalRenderJobId))
                .fetchOne(RENDER_JOB.TIMELINE_REVISION_ID);

        if (pinnedRevisionId == null) {
            throw new IllegalArgumentException("Original render job not found: " + originalRenderJobId);
        }

        renderJobRepository.createRetryJob(newJobId, originalRenderJobId);

        log.info("Created retry render job {} from {} with revision {}",
                newJobId, originalRenderJobId, pinnedRevisionId);

        return pinnedRevisionId;
    }

    @Transactional(readOnly = true)
    public String getPinnedRevisionId(String renderJobId) {
        return dsl.select(RENDER_JOB.TIMELINE_REVISION_ID)
                .from(RENDER_JOB)
                .where(RENDER_JOB.ID.eq(renderJobId))
                .fetchOne(RENDER_JOB.TIMELINE_REVISION_ID);
    }

    public static boolean isBoundBackendIdentity(String backend) {
        if (backend == null) {
            return false;
        }
        String normalized = backend.trim();
        return !normalized.isEmpty() && !"provider".equalsIgnoreCase(normalized);
    }
}
