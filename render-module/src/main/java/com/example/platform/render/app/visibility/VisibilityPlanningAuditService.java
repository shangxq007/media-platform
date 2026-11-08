package com.example.platform.render.app.visibility;

import com.example.platform.render.domain.renderplan.RenderPlanningResult;
import com.example.platform.render.domain.renderplan.RenderRequest;
import com.example.platform.timeline.canonical.TimelineDocument;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Formal R2B audit boundary after #20 and before any #21 hand-off.
 *
 * <p>This service deliberately performs no graph rewrite and never reseals the
 * plan. R2B has no proven incremental pruning candidate under the current
 * canonical model, so the original fingerprint/identity is returned unchanged.</p>
 */
public final class VisibilityPlanningAuditService {

    private final TemporalVisibilityAnalyzer analyzer;

    public VisibilityPlanningAuditService() {
        this(new TemporalVisibilityAnalyzer());
    }

    public VisibilityPlanningAuditService(TemporalVisibilityAnalyzer analyzer) {
        this.analyzer = Objects.requireNonNull(analyzer, "analyzer");
    }

    public VisibilityPlanningAudit audit(
            String tenantId, String projectId, String revisionId,
            TimelineDocument document, RenderRequest request, RenderPlanningResult plan) {
        Objects.requireNonNull(plan, "plan");
        String fingerprint = plan.plan().fingerprint().sha256Hex();
        String planId = plan.plan().id().value();
        if (document == null) {
            return new VisibilityPlanningAudit(fingerprint, planId, Optional.empty(), false, "",
                    List.of("ANALYSIS_FALLBACK: canonical document unavailable",
                            "NO_SAFE_INCREMENTAL_PRUNING: original plan retained"));
        }
        try {
            TemporalVisibilityAnalysis analysis = analyzer.analyze(
                    document, new VisibilityRequest(tenantId, projectId, revisionId, request));
            return new VisibilityPlanningAudit(fingerprint, planId, Optional.of(analysis), false,
                    analysis.analysisDigest(),
                    List.of("NO_SAFE_INCREMENTAL_PRUNING: existing #20 extent/sample-window filtering is not a new increment",
                            "GRAPH_IDENTITY_UNCHANGED: original fingerprint retained",
                            "NO_CACHE_CHANGE: proof is audit-only"));
        } catch (RuntimeException failure) {
            return new VisibilityPlanningAudit(fingerprint, planId, Optional.empty(), false, "",
                    List.of("ANALYSIS_FALLBACK: " + failure.getClass().getSimpleName(),
                            "NO_SAFE_INCREMENTAL_PRUNING: original plan retained"));
        }
    }
}
