package com.example.platform.render.app.timeline;

import org.jooq.DSLContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

import static com.example.platform.typedschema.jooq.generated.tables.RenderJob.RENDER_JOB;
import static com.example.platform.typedschema.jooq.generated.tables.TimelineRevision.TIMELINE_REVISION;

@Service
public class RenderJobRevisionPinningService {

    private static final Logger log = LoggerFactory.getLogger(RenderJobRevisionPinningService.class);
    private final DSLContext dsl;

    private static final Set<String> CANONICAL_BACKENDS = Set.of(
            "ffmpeg", "remotion", "gpac", "blender"
    );

    public RenderJobRevisionPinningService(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Transactional
    public void createRenderJobWithRevision(String renderJobId, String productId,
                                            String timelineRevisionId, String backend) {
        if (backend == null || !CANONICAL_BACKENDS.contains(backend.toLowerCase())) {
            throw new IllegalArgumentException(
                    "Unknown backend: " + backend + ". Canonical backends: " + CANONICAL_BACKENDS);
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

        dsl.insertInto(RENDER_JOB)
                .set(RENDER_JOB.ID, renderJobId)
                .set(RENDER_JOB.PROJECT_ID, productId)
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

        String productId = dsl.select(RENDER_JOB.PROJECT_ID)
                .from(RENDER_JOB)
                .where(RENDER_JOB.ID.eq(originalRenderJobId))
                .fetchOne(RENDER_JOB.PROJECT_ID);

        dsl.insertInto(RENDER_JOB)
                .set(RENDER_JOB.ID, newJobId)
                .set(RENDER_JOB.PROJECT_ID, productId)
                .set(RENDER_JOB.TIMELINE_REVISION_ID, pinnedRevisionId)
                .execute();

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

    public static Set<String> getCanonicalBackends() {
        return CANONICAL_BACKENDS;
    }
}
