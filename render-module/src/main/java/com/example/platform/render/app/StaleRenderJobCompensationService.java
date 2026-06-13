package com.example.platform.render.app;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.table;

import com.example.platform.render.domain.RenderJobStateMachine;
import com.example.platform.render.domain.RenderJobStatus;
import com.example.platform.shared.events.RenderJobFailedEvent;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

/**
 * Marks stuck {@code render_job} rows as {@link RenderJobStatus#FAILED} (scheduled or post-restart).
 */
@Service
public class StaleRenderJobCompensationService {

    private static final Logger log = LoggerFactory.getLogger(StaleRenderJobCompensationService.class);

    private final DSLContext dsl;
    private final RenderJobStateMachine stateMachine;
    private final RenderJobStatusHistoryRepository historyRepository;
    private final ApplicationEventPublisher eventPublisher;

    public StaleRenderJobCompensationService(
            DSLContext dsl,
            RenderJobStatusHistoryRepository historyRepository,
            ApplicationEventPublisher eventPublisher) {
        this.dsl = dsl;
        this.historyRepository = historyRepository;
        this.eventPublisher = eventPublisher;
        this.stateMachine = new RenderJobStateMachine();
    }

    public CompensationResult compensate(CompensationRequest request) {
        List<String> activeStatuses = new ArrayList<>(List.of(
                RenderJobStatus.SELECTING_PROVIDER.name(),
                RenderJobStatus.EXECUTING.name()));
        if (request.includeQueued()) {
            activeStatuses.add(RenderJobStatus.QUEUED.name());
        }

        Condition condition = field("status").in(activeStatuses);
        if (request.cutoff() != null) {
            condition = condition.and(field("created_at").lessThan(request.cutoff()));
        }

        var staleJobs = dsl.select(field("id"), field("project_id"), field("status"))
                .from(table("render_job"))
                .where(condition)
                .fetch();

        int compensated = 0;
        List<String> errors = new ArrayList<>();
        String reason = request.reasonCode();

        for (Record job : staleJobs) {
            String jobId = job.get(field("id"), String.class);
            String projectId = job.get(field("project_id"), String.class);
            String statusStr = job.get(field("status"), String.class);
            RenderJobStatus currentStatus = RenderJobStatus.valueOf(statusStr);
            try {
                stateMachine.validateTransition(currentStatus, RenderJobStatus.FAILED);
                dsl.update(table("render_job"))
                        .set(field("status"), RenderJobStatus.FAILED.name())
                        .set(field("error_message"), request.errorMessage())
                        .where(field("id").eq(jobId))
                        .execute();
                historyRepository.record(
                        jobId, statusStr, RenderJobStatus.FAILED.name(), reason, "STALE_TIMEOUT");
                eventPublisher.publishEvent(new RenderJobFailedEvent(
                        jobId, projectId, request.errorMessage(), Instant.now()));
                compensated++;
                log.warn("Compensated stale job {} (was {}, reason={})", jobId, statusStr, reason);
            } catch (Exception e) {
                errors.add(jobId + ": " + e.getMessage());
                log.error("Failed to compensate stale job {}", jobId, e);
            }
        }

        if (compensated > 0) {
            log.info("StaleRenderJobCompensation: compensated {} jobs (reason={})", compensated, reason);
        }
        return new CompensationResult(staleJobs.size(), compensated, errors);
    }

    public record CompensationRequest(
            OffsetDateTime cutoff,
            boolean includeQueued,
            String reasonCode,
            String errorMessage) {

        public static CompensationRequest scheduled(Duration threshold) {
            return new CompensationRequest(
                    OffsetDateTime.now().minus(threshold),
                    false,
                    "stale_timeout",
                    "Stale job timed out after " + threshold);
        }

        public static CompensationRequest startup(boolean includeQueued, String executionMode) {
            String msg = "local".equalsIgnoreCase(executionMode)
                    ? "Job interrupted by platform restart (in-flight local execution)"
                    : "Job exceeded startup stale threshold after platform restart";
            return new CompensationRequest(
                    "local".equalsIgnoreCase(executionMode) ? null : OffsetDateTime.now().minus(Duration.ofMinutes(2)),
                    includeQueued,
                    "startup_recovery",
                    msg);
        }
    }

    public record CompensationResult(int scanned, int compensated, List<String> errors) {}
}
