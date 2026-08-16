package com.example.platform.render.app.timeline;

import com.example.platform.timeline.app.TimelineMergeEngine;
import com.example.platform.timeline.canonical.TimelineContentDigester;
import com.example.platform.timeline.diff.application.TimelinePatchApplier;
import com.example.platform.timeline.diff.merge.TimelineMergeConflictDetector;
import com.example.platform.timeline.diff.merge.plan.TimelineNonConflictingMergePlanner;
import com.example.platform.timeline.diff.merge.preview.TimelineMergePreviewService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring composition for the canonical timeline semantic-merge authority.
 *
 * <p>Timeline domain primitives ({@code domain/timeline/diff/merge/*},
 * {@code domain/timeline/diff/application/*}) are framework-neutral plain objects by
 * repository convention (cf. {@code TimelineDiffEngine} static utility, inline-constructed
 * {@code CanonicalTimelineDiffCalculator}). This configuration composes them at the
 * application boundary so that the single production merge authority
 * {@link TimelineMergeEngine} can be fully constructed by the real application context
 * without coupling the domain layer to Spring stereotypes.
 *
 * <p>Composition only — no merge semantics, no conflict rules, no validation behavior
 * is defined here.
 */
@Configuration(proxyBeanMethods = false)
public class TimelineMergeConfiguration {

    @Bean
    TimelineMergeConflictDetector timelineMergeConflictDetector() {
        return new TimelineMergeConflictDetector();
    }

    @Bean
    TimelineMergePreviewService timelineMergePreviewService(
            TimelineMergeConflictDetector conflictDetector) {
        return new TimelineMergePreviewService(conflictDetector);
    }

    @Bean
    TimelineNonConflictingMergePlanner timelineNonConflictingMergePlanner(
            TimelineMergePreviewService previewService) {
        return new TimelineNonConflictingMergePlanner(previewService);
    }

    @Bean
    TimelinePatchApplier timelinePatchApplier() {
        return new TimelinePatchApplier();
    }

    @Bean
    TimelineContentDigester timelineContentDigester() {
        return new TimelineContentDigester();
    }
}
