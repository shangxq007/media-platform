package com.example.platform.render.app;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.table;

import com.example.platform.render.domain.RenderJobStateMachine;
import com.example.platform.render.domain.RenderJobStatus;
import com.example.platform.shared.events.RenderJobFailedEvent;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class StaleRenderJobCompensator {

    private static final Logger log = LoggerFactory.getLogger(StaleRenderJobCompensator.class);

    private final DSLContext dsl;
    private final RenderJobStateMachine stateMachine;
    private final RenderJobStatusHistoryRepository historyRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final Duration staleThreshold;
    private final boolean enabled;

    public StaleRenderJobCompensator(DSLContext dsl,
            RenderJobStatusHistoryRepository historyRepository,
            ApplicationEventPublisher eventPublisher,
            @Value("${render.stale-compensator.enabled:true}") boolean enabled,
            @Value("${render.stale-compensator.threshold:PT30M}") Duration staleThreshold) {
        this.dsl = dsl;
        this.historyRepository = historyRepository;
        this.eventPublisher = eventPublisher;
        this.stateMachine = new RenderJobStateMachine();
        this.enabled = enabled;
        this.staleThreshold = staleThreshold;
    }

    @Scheduled(fixedDelayString = "${render.stale-compensator.interval:PT5M}")
    public void compensateStaleJobs() {
        if (!enabled) {
            log.debug("StaleRenderJobCompensator is disabled, skipping");
            return;
        }

        log.debug("Running stale render job compensation, threshold={}", staleThreshold);
        OffsetDateTime cutoff = OffsetDateTime.now().minus(staleThreshold);

        var staleJobs = dsl.select(field("id"), field("project_id"), field("status"))
                .from(table("render_job"))
                .where(field("status").in(RenderJobStatus.AI_PROCESSING.name(), RenderJobStatus.RENDERING.name()))
                .and(field("created_at").lessThan(cutoff))
                .fetch();

        for (var job : staleJobs) {
            String jobId = job.get(field("id"), String.class);
            String projectId = job.get(field("project_id"), String.class);
            String statusStr = job.get(field("status"), String.class);
            RenderJobStatus currentStatus = RenderJobStatus.valueOf(statusStr);

            try {
                stateMachine.validateTransition(currentStatus, RenderJobStatus.FAILED);
                dsl.update(table("render_job"))
                        .set(field("status"), RenderJobStatus.FAILED.name())
                        .set(field("error_message"), "Stale job timed out after " + staleThreshold)
                        .where(field("id").eq(jobId))
                        .execute();
                historyRepository.record(jobId, statusStr, RenderJobStatus.FAILED.name(),
                        "stale_timeout", "STALE_TIMEOUT");
                eventPublisher.publishEvent(new RenderJobFailedEvent(
                        jobId, projectId, "Stale job timed out", Instant.now()));
                log.warn("Compensated stale job {} (was {}, threshold={})", jobId, statusStr, staleThreshold);
            } catch (Exception e) {
                log.error("Failed to compensate stale job {}", jobId, e);
            }
        }

        if (!staleJobs.isEmpty()) {
            log.info("StaleRenderJobCompensator: compensated {} stale jobs", staleJobs.size());
        }
    }
}
