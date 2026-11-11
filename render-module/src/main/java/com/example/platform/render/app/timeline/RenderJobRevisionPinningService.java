package com.example.platform.render.app.timeline;

import com.example.platform.render.infrastructure.RenderJobRepository;
import com.example.platform.render.api.request.RenderInitiator;
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

    public RenderJobRevisionPinningService(DSLContext dsl) {
        this.dsl = dsl;
    }



    @Transactional(readOnly = true)
    public String getPinnedRevisionId(String renderJobId) {
        return dsl.select(RENDER_JOB.TIMELINE_REVISION_ID)
                .from(RENDER_JOB)
                .where(RENDER_JOB.ID.eq(renderJobId))
                .fetchOne(RENDER_JOB.TIMELINE_REVISION_ID);
    }


}
