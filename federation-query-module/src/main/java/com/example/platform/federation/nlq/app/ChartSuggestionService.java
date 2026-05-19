package com.example.platform.federation.nlq.app;

import com.example.platform.federation.nlq.domain.ChartSuggestion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class ChartSuggestionService {

    private static final Logger log = LoggerFactory.getLogger(ChartSuggestionService.class);

    public List<ChartSuggestion> suggest(List<String> columns, List<Map<String, Object>> rows) {
        List<ChartSuggestion> suggestions = new ArrayList<>();

        if (columns == null || columns.isEmpty()) {
            return suggestions;
        }

        int colCount = columns.size();
        int rowCount = rows != null ? rows.size() : 0;

        String timeField = findTimeField(columns);
        String categoryField = findCategoryField(columns);
        List<String> numericFields = findNumericFields(columns, rows);

        if (timeField != null && !numericFields.isEmpty()) {
            suggestions.add(new ChartSuggestion("line", timeField, numericFields.get(0),
                null, "Time series: " + numericFields.get(0) + " over " + timeField,
                "Detected time-based field with numeric values"));
            suggestions.add(new ChartSuggestion("area", timeField, numericFields.get(0),
                null, "Area: " + numericFieldLabel(numericFields) + " over " + timeField,
                "Alternative time series visualization"));
        }

        if (categoryField != null && !numericFields.isEmpty()) {
            suggestions.add(new ChartSuggestion("bar", categoryField, numericFields.get(0),
                null, "Bar: " + numericFields.get(0) + " by " + categoryField,
                "Categorical comparison with numeric values"));
        }

        if (colCount == 2 && !numericFields.isEmpty() && categoryField != null) {
            suggestions.add(new ChartSuggestion("pie", categoryField, numericFields.get(0),
                null, "Pie: " + numericFields.get(0) + " distribution by " + categoryField,
                "Two-column result with category and numeric"));
        }

        if (colCount == 1 && rowCount <= 10 && !numericFields.isEmpty() && categoryField != null) {
            suggestions.add(new ChartSuggestion("donut", categoryField, numericFields.get(0),
                null, "Donut: " + numericFields.get(0) + " by " + categoryField,
                "Compact categorical distribution"));
        }

        if (rowCount == 1 && numericFields.size() >= 1) {
            suggestions.add(new ChartSuggestion("metric", null, numericFields.get(0),
                null, "Metric: " + numericFields.get(0),
                "Single row result suitable for metric card"));
        }

        if (numericFields.size() >= 2 && timeField == null) {
            suggestions.add(new ChartSuggestion("scatter", numericFields.get(0), numericFields.get(1),
                null, "Scatter: " + numericFields.get(0) + " vs " + numericFields.get(1),
                "Two numeric fields suitable for scatter plot"));
        }

        if (categoryField != null && numericFields.size() >= 2) {
            String groupField = columns.stream()
                .filter(c -> !c.equals(categoryField) && !numericFields.contains(c) && !isTimeField(c))
                .findFirst().orElse(null);
            suggestions.add(new ChartSuggestion("stacked_bar", categoryField, numericFields.get(0),
                groupField, "Stacked: " + numericFieldLabel(numericFields) + " by " + categoryField,
                "Multi-value categorical comparison"));
        }

        suggestions.add(new ChartSuggestion("table", null, null, null,
            "Table view", "Always available as fallback"));

        log.debug("ChartSuggestionService: generated {} suggestions for {} columns", suggestions.size(), colCount);
        return suggestions;
    }

    private String findTimeField(List<String> columns) {
        for (String col : columns) {
            if (isTimeField(col)) return col;
        }
        return null;
    }

    private boolean isTimeField(String col) {
        String lower = col.toLowerCase();
        return lower.contains("date") || lower.contains("time") || lower.contains("at")
            || lower.contains("day") || lower.contains("timestamp");
    }

    private String findCategoryField(List<String> columns) {
        for (String col : columns) {
            String lower = col.toLowerCase();
            if (lower.equals("status") || lower.equals("type") || lower.equals("category")
                    || lower.equals("name") || lower.equals("module") || lower.equals("provider")
                    || lower.equals("format") || lower.equals("tier")) {
                return col;
            }
        }
        return null;
    }

    private List<String> findNumericFields(List<String> columns, List<Map<String, Object>> rows) {
        List<String> numericFields = new ArrayList<>();
        if (rows == null || rows.isEmpty()) {
            for (String col : columns) {
                String lower = col.toLowerCase();
                if (lower.contains("count") || lower.contains("total") || lower.contains("sum")
                        || lower.contains("avg") || lower.contains("min") || lower.contains("max")
                        || lower.contains("amount") || lower.contains("cost") || lower.contains("duration")
                        || lower.contains("usage") || lower.contains("limit") || lower.contains("balance")
                        || lower.contains("usd") || lower.contains("ms") || lower.contains("gb")) {
                    numericFields.add(col);
                }
            }
            return numericFields;
        }

        Map<String, Object> sampleRow = rows.get(0);
        for (String col : columns) {
            Object val = sampleRow.get(col);
            if (val instanceof Number) {
                numericFields.add(col);
            }
        }
        return numericFields;
    }

    private String numericFieldLabel(List<String> numericFields) {
        if (numericFields.isEmpty()) return "value";
        if (numericFields.size() == 1) return numericFields.get(0);
        return numericFields.get(0) + " (+" + (numericFields.size() - 1) + " more)";
    }
}
