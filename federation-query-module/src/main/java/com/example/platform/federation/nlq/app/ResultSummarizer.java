package com.example.platform.federation.nlq.app;

import com.example.platform.ai.api.AiGatewayPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class ResultSummarizer {

    private static final Logger log = LoggerFactory.getLogger(ResultSummarizer.class);

    private final AiGatewayPort aiGatewayPort;

    public ResultSummarizer(AiGatewayPort aiGatewayPort) {
        this.aiGatewayPort = aiGatewayPort;
    }

    public String summarize(List<String> columns, List<Map<String, Object>> rows) {
        if (rows == null || rows.isEmpty()) {
            return "No results found.";
        }

        try {
            String prompt = buildSummaryPrompt(columns, rows);
            var result = aiGatewayPort.chat("nlq-result-summary", prompt);

            if (!"stub".equals(result.provider()) && result.content() != null && !result.content().isBlank()) {
                return result.content().trim();
            }
        } catch (Exception e) {
            log.warn("ResultSummarizer: AI provider unavailable, using rule-based fallback", e);
        }

        return generateRuleBasedSummary(columns, rows);
    }

    private String buildSummaryPrompt(List<String> columns, List<Map<String, Object>> rows) {
        StringBuilder sb = new StringBuilder();
        sb.append("Summarize the following query results in 2-3 sentences.\n");
        sb.append("Columns: ").append(columns).append("\n");
        sb.append("Row count: ").append(rows.size()).append("\n");
        sb.append("Sample rows (up to 5):\n");
        int limit = Math.min(rows.size(), 5);
        for (int i = 0; i < limit; i++) {
            sb.append("  ").append(rows.get(i)).append("\n");
        }
        sb.append("\nProvide a concise summary focusing on key patterns, totals, or notable values.");
        return sb.toString();
    }

    private String generateRuleBasedSummary(List<String> columns, List<Map<String, Object>> rows) {
        int rowCount = rows.size();
        int colCount = columns.size();

        StringBuilder summary = new StringBuilder();
        summary.append("Query returned ").append(rowCount).append(" row");
        if (rowCount != 1) summary.append("s");
        summary.append(" with ").append(colCount).append(" column");
        if (colCount != 1) summary.append("s");
        summary.append(".");

        for (String col : columns) {
            String lower = col.toLowerCase();
            if (lower.contains("count") || lower.contains("total") || lower.contains("sum")) {
                try {
                    double total = 0;
                    for (Map<String, Object> row : rows) {
                        Object val = row.get(col);
                        if (val instanceof Number) {
                            total += ((Number) val).doubleValue();
                        }
                    }
                    if (total > 0) {
                        summary.append(" Total ").append(col).append(": ");
                        if (total == Math.floor(total)) {
                            summary.append(String.format("%.0f", total));
                        } else {
                            summary.append(String.format("%.2f", total));
                        }
                        summary.append(".");
                    }
                } catch (Exception ignored) {}
            }
        }

        return summary.toString();
    }
}
