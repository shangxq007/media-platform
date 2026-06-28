package com.example.platform.render.app.timeline.compile;

import com.example.platform.render.app.timeline.TimelineRevisionRenderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Facade that routes TimelineRevision render requests to either the legacy
 * direct FFmpeg path or the plan-based execution path, with deduplication.
 *
 * <p>Internal only — not exposed in public APIs.</p>
 *
 * <p>The routing decision is based on {@link TimelineRenderExecutionProperties}.
 * Default is LEGACY for safe backward compatibility.</p>
 *
 * <p>Before rendering, checks for existing READY Products via
 * {@link RenderDeduplicationService} to prevent duplicate renders.</p>
 */
@Service
public class TimelineRevisionRenderFacade {

    private static final Logger log = LoggerFactory.getLogger(TimelineRevisionRenderFacade.class);

    private final TimelineRevisionRenderService legacyService;
    private final PlanBasedTimelineRevisionRenderService planBasedService;
    private final RenderDeduplicationService deduplicationService;
    private final TimelineRenderExecutionProperties properties;

    public TimelineRevisionRenderFacade(
            TimelineRevisionRenderService legacyService,
            PlanBasedTimelineRevisionRenderService planBasedService,
            RenderDeduplicationService deduplicationService,
            TimelineRenderExecutionProperties properties) {
        this.legacyService = legacyService;
        this.planBasedService = planBasedService;
        this.deduplicationService = deduplicationService;
        this.properties = properties;
    }

    /**
     * Render a TimelineRevision using the configured execution path,
     * with deduplication to prevent duplicate renders.
     *
     * @param projectId     the project identifier
     * @param revisionId    the timeline revision identifier
     * @param outputProfile the render profile
     * @return the render result (same contract for both paths)
     */
    public TimelineRevisionRenderService.RevisionRenderResult render(
            String projectId, String revisionId, String outputProfile) {

        // Dedup check
        RenderDeduplicationDecision dedupDecision = deduplicationService.check(
                projectId, revisionId, outputProfile, properties.executionMode().name());

        if (dedupDecision.shouldReuse()) {
            log.info("Dedup: reusing existing READY product for project={} revision={} profile={}",
                    projectId, revisionId, outputProfile);
            return dedupDecision.reusedResult();
        }

        if (dedupDecision.isFailed()) {
            log.warn("Dedup: lookup failed for project={} revision={}, proceeding with render: {}",
                    projectId, revisionId, dedupDecision.message());
            // Fail closed — do not proceed if dedup is uncertain
            throw new IllegalStateException("Render deduplication failed: " + dedupDecision.message());
        }

        // Proceed with render
        if (properties.isPlanBasedEnabled()) {
            log.info("Rendering via plan-based path: project={} revision={} mode={}",
                    projectId, revisionId, properties.executionMode());
            return planBasedService.render(projectId, revisionId, outputProfile);
        } else {
            log.info("Rendering via legacy path: project={} revision={} mode={}",
                    projectId, revisionId, properties.executionMode());
            return legacyService.render(projectId, revisionId, outputProfile);
        }
    }

    /**
     * Returns the current execution mode.
     */
    public TimelineRenderExecutionMode getExecutionMode() {
        return properties.executionMode();
    }

    /**
     * Returns true if plan-based execution is enabled.
     */
    public boolean isPlanBasedEnabled() {
        return properties.isPlanBasedEnabled();
    }
}
