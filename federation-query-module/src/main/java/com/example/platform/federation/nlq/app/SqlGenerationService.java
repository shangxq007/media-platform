package com.example.platform.federation.nlq.app;

import com.example.platform.ai.api.AiGatewayPort;
import com.example.platform.federation.nlq.domain.SqlDraft;
import com.example.platform.shared.Ids;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class SqlGenerationService {

    private static final Logger log = LoggerFactory.getLogger(SqlGenerationService.class);

    private final AiGatewayPort aiGatewayPort;

    public SqlGenerationService(AiGatewayPort aiGatewayPort) {
        this.aiGatewayPort = aiGatewayPort;
    }

    public SqlDraft generateSql(String question, Map<String, Object> context, List<String> allowedDatasets) {
        String intent = classifyIntent(question);
        log.info("SqlGenerationService: generating SQL for intent={}, question='{}'", intent, question);

        try {
            String prompt = buildPrompt(question, context, allowedDatasets, intent);
            var result = aiGatewayPort.chat("nlq-sql-generation", prompt);

            if ("stub".equals(result.provider())) {
                return generateDeterministicSql(question, intent, allowedDatasets, context);
            }

            return parseAiResponse(result.content(), question, intent, allowedDatasets);
        } catch (Exception e) {
            log.warn("SqlGenerationService: AI provider returned stub or error, using deterministic fallback", e);
            return generateDeterministicSql(question, intent, allowedDatasets, context);
        }
    }

    String classifyIntent(String question) {
        String lower = question.toLowerCase();
        if (containsAny(lower, "total", "sum", "count", "average", "avg", "how many", "how much")) {
            return "AGGREGATION";
        }
        if (containsAny(lower, "trend", "over time", "daily", "weekly", "monthly", "timeline", "history")) {
            return "TREND";
        }
        if (containsAny(lower, "compare", "versus", "vs", "difference", "between")) {
            return "COMPARISON";
        }
        if (containsAny(lower, "top", "bottom", "highest", "lowest", "rank", "ranking", "best", "worst")) {
            return "RANKING";
        }
        if (containsAny(lower, "distribution", "breakdown", "by category", "by type", "group by")) {
            return "DISTRIBUTION";
        }
        return "DETAIL";
    }

    SqlDraft generateDeterministicSql(String question, String intent, List<String> allowedDatasets,
            Map<String, Object> context) {
        String draftId = Ids.newId("sql");
        String dataset = resolvePrimaryDataset(question, allowedDatasets);
        String sql = buildSqlForIntent(intent, dataset, context);
        Map<String, Object> parameters = buildDefaultParameters(context);
        List<String> assumptions = buildAssumptions(intent, dataset);
        List<String> chartSuggestions = suggestCharts(intent);

        return new SqlDraft(
            draftId, question, intent, List.of(dataset),
            sql, parameters, assumptions,
            List.of("analytics.read"),
            "LOW",
            "Generated via deterministic fallback for intent: " + intent,
            chartSuggestions,
            0.7,
            Instant.now()
        );
    }

    private String buildSqlForIntent(String intent, String dataset, Map<String, Object> context) {
        String timeField = resolveTimeField(dataset);
        int limit = context != null && context.containsKey("limit") ?
            ((Number) context.get("limit")).intValue() : 100;

        return switch (intent) {
            case "AGGREGATION" -> """
                SELECT
                  COUNT(*) AS total_count,
                  SUM(CASE WHEN status = 'completed' THEN 1 ELSE 0 END) AS completed_count,
                  SUM(CASE WHEN status = 'failed' THEN 1 ELSE 0 END) AS failed_count
                FROM %s
                WHERE %s >= CURRENT_DATE - INTERVAL '30 days'
                LIMIT %d
                """.formatted(dataset, timeField, limit);

            case "TREND" -> """
                SELECT
                  DATE_TRUNC('day', %s) AS day,
                  COUNT(*) AS event_count
                FROM %s
                WHERE %s >= CURRENT_DATE - INTERVAL '30 days'
                GROUP BY DATE_TRUNC('day', %s)
                ORDER BY day
                LIMIT %d
                """.formatted(timeField, dataset, timeField, timeField, limit);

            case "COMPARISON" -> """
                SELECT
                  status,
                  COUNT(*) AS count
                FROM %s
                WHERE %s >= CURRENT_DATE - INTERVAL '30 days'
                GROUP BY status
                ORDER BY count DESC
                LIMIT %d
                """.formatted(dataset, timeField, limit);

            case "RANKING" -> """
                SELECT
                  job_id,
                  duration_ms,
                  cost_usd
                FROM %s
                WHERE %s >= CURRENT_DATE - INTERVAL '30 days'
                ORDER BY duration_ms DESC
                LIMIT %d
                """.formatted(dataset, timeField, limit);

            case "DISTRIBUTION" -> """
                SELECT
                  status,
                  COUNT(*) AS count,
                  ROUND(COUNT(*) * 100.0 / SUM(COUNT(*)) OVER (), 2) AS percentage
                FROM %s
                WHERE %s >= CURRENT_DATE - INTERVAL '30 days'
                GROUP BY status
                ORDER BY count DESC
                LIMIT %d
                """.formatted(dataset, timeField, limit);

            default -> """
                SELECT
                  job_id,
                  status,
                  duration_ms,
                  created_at
                FROM %s
                WHERE %s >= CURRENT_DATE - INTERVAL '30 days'
                ORDER BY created_at DESC
                LIMIT %d
                """.formatted(dataset, timeField, limit);
        };
    }

    private String resolvePrimaryDataset(String question, List<String> allowedDatasets) {
        String lower = question.toLowerCase();
        if (allowedDatasets == null || allowedDatasets.isEmpty()) {
            return "render_jobs_report_view";
        }
        if (containsAny(lower, "render", "job")) return findDataset(allowedDatasets, "render_jobs");
        if (containsAny(lower, "artifact", "output")) return findDataset(allowedDatasets, "artifact");
        if (containsAny(lower, "prompt", "ai", "llm", "token")) return findDataset(allowedDatasets, "prompt_execution");
        if (containsAny(lower, "bill", "cost", "usage", "spend")) return findDataset(allowedDatasets, "billing_usage");
        if (containsAny(lower, "entitlement", "access", "permission")) return findDataset(allowedDatasets, "entitlement_decision");
        if (containsAny(lower, "quota", "limit")) return findDataset(allowedDatasets, "quota_usage");
        if (containsAny(lower, "feedback", "rating")) return findDataset(allowedDatasets, "feedback");
        if (containsAny(lower, "provider", "health", "latency")) return findDataset(allowedDatasets, "provider_health");
        if (containsAny(lower, "worker", "node")) return findDataset(allowedDatasets, "worker_status");
        if (containsAny(lower, "feature flag", "feature_flag", "experiment")) return findDataset(allowedDatasets, "feature_flag_evaluation");
        if (containsAny(lower, "extension", "plugin")) return findDataset(allowedDatasets, "extension_execution");
        if (containsAny(lower, "problem", "anomaly", "quality")) return findDataset(allowedDatasets, "problematic_data");
        if (containsAny(lower, "audit", "trail")) return findDataset(allowedDatasets, "audit_event");
        return allowedDatasets.get(0);
    }

    private String findDataset(List<String> allowedDatasets, String prefix) {
        return allowedDatasets.stream()
            .filter(d -> d.startsWith(prefix))
            .findFirst()
            .orElse(allowedDatasets.get(0));
    }

    private String resolveTimeField(String dataset) {
        if (dataset.contains("billing")) return "usage_date";
        if (dataset.contains("entitlement")) return "evaluated_at";
        if (dataset.contains("feedback")) return "submitted_at";
        if (dataset.contains("provider_health")) return "checked_at";
        if (dataset.contains("worker")) return "reported_at";
        if (dataset.contains("feature_flag")) return "evaluated_at";
        if (dataset.contains("extension")) return "executed_at";
        if (dataset.contains("problematic")) return "detected_at";
        if (dataset.contains("audit")) return "occurred_at";
        if (dataset.contains("prompt")) return "started_at";
        return "created_at";
    }

    private Map<String, Object> buildDefaultParameters(Map<String, Object> context) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("time_range_days", context != null && context.containsKey("time_range_days") ?
            context.get("time_range_days") : 30);
        params.put("limit", context != null && context.containsKey("limit") ?
            context.get("limit") : 100);
        if (context != null && context.containsKey("tenant_id")) {
            params.put("tenant_id", context.get("tenant_id"));
        }
        if (context != null && context.containsKey("workspace_id")) {
            params.put("workspace_id", context.get("workspace_id"));
        }
        return params;
    }

    private List<String> buildAssumptions(String intent, String dataset) {
        List<String> assumptions = new ArrayList<>();
        assumptions.add("Query targets dataset: " + dataset);
        assumptions.add("Default time range: last 30 days");
        assumptions.add("Default row limit: 100");
        assumptions.add("Detected intent: " + intent);
        return assumptions;
    }

    private List<String> suggestCharts(String intent) {
        return switch (intent) {
            case "AGGREGATION" -> List.of("metric_card", "pie_chart");
            case "TREND" -> List.of("line_chart", "area_chart");
            case "COMPARISON" -> List.of("bar_chart", "grouped_bar");
            case "RANKING" -> List.of("bar_chart", "table");
            case "DISTRIBUTION" -> List.of("pie_chart", "donut_chart", "treemap");
            default -> List.of("table");
        };
    }

    private String buildPrompt(String question, Map<String, Object> context,
            List<String> allowedDatasets, String intent) {
        return """
            Generate a safe read-only SQL query for the following natural language question.
            Intent: %s
            Question: %s
            Allowed datasets: %s
            Context: %s

            Rules:
            - Only generate SELECT or WITH statements
            - Must include a LIMIT clause
            - Must include a time range filter for time-series datasets
            - Do not use SELECT *; specify explicit columns
            - Do not use CROSS JOIN
            - Do not reference sensitive fields (api_key, password, token, etc.)
            """.formatted(intent, question, allowedDatasets, context);
    }

    private SqlDraft parseAiResponse(String content, String question, String intent,
            List<String> allowedDatasets) {
        String draftId = Ids.newId("sql");
        List<String> chartSuggestions = suggestCharts(intent);
        return new SqlDraft(
            draftId, question, intent, allowedDatasets,
            content, Map.of(), List.of("Parsed from AI response"),
            List.of("analytics.read"), "MEDIUM",
            "Generated by AI provider", chartSuggestions, 0.85, Instant.now()
        );
    }

    private boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) return true;
        }
        return false;
    }
}
