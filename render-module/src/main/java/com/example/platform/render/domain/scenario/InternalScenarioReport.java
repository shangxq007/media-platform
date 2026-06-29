package com.example.platform.render.domain.scenario;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Aggregated report of multiple scenario executions.
 * Immutable record. Internal domain model.
 * Results are ordered deterministically by scenario id.
 */
public record InternalScenarioReport(
        String reportId,
        int totalScenarios,
        int passed,
        int passedWithWarnings,
        int failed,
        int blocked,
        int unsupported,
        int notRun,
        List<InternalScenarioResult> results,
        Map<String, String> safeMetadata) {

    public InternalScenarioReport {
        Objects.requireNonNull(reportId, "reportId");
        results = results == null ? List.of() : List.copyOf(results);
        safeMetadata = safeMetadata == null ? Map.of() : Map.copyOf(safeMetadata);
    }

    /**
     * Build a deterministic report from a list of results.
     * Results are sorted by scenario id for deterministic ordering.
     */
    public static InternalScenarioReport aggregate(String reportId, List<InternalScenarioResult> results) {
        Objects.requireNonNull(reportId, "reportId");
        List<InternalScenarioResult> sorted = results == null
                ? List.of()
                : results.stream()
                    .sorted(Comparator.comparing(r -> r.scenarioId().value()))
                    .collect(Collectors.toUnmodifiableList());

        int total = sorted.size();
        int p = 0, pw = 0, f = 0, b = 0, u = 0, nr = 0;
        for (InternalScenarioResult r : sorted) {
            switch (r.status()) {
                case PASS -> p++;
                case PASS_WITH_WARNINGS -> pw++;
                case FAIL -> f++;
                case BLOCKED -> b++;
                case UNSUPPORTED -> u++;
                case NOT_RUN -> nr++;
            }
        }

        return new InternalScenarioReport(reportId, total, p, pw, f, b, u, nr, sorted, Map.of());
    }

    public boolean allPassed() {
        return failed == 0 && blocked == 0 && notRun == 0;
    }
}
