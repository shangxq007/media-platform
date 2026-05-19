package com.example.platform.federation.nlq.app;

import com.example.platform.federation.nlq.domain.QueryCostEstimate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class SqlCostEstimator {

    private static final Logger log = LoggerFactory.getLogger(SqlCostEstimator.class);

    public static final int DEFAULT_MAX_ROWS = 1000;
    public static final int DEFAULT_ROWS = 100;
    public static final int DEFAULT_TIMEOUT_SECONDS = 10;
    public static final int DEFAULT_MAX_LOOKBACK_DAYS = 90;

    private static final int CRITICAL_THRESHOLD = 80;
    private static final int HIGH_THRESHOLD = 50;
    private static final int MEDIUM_THRESHOLD = 25;

    private static final Pattern DATASET_REF_PATTERN = Pattern.compile("\\bFROM\\s+([\\w.]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern JOIN_PATTERN = Pattern.compile("\\b(INNER|LEFT|RIGHT|FULL)?\\s*JOIN\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern GROUP_BY_PATTERN = Pattern.compile("\\bGROUP\\s+BY\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern ORDER_BY_PATTERN = Pattern.compile("\\bORDER\\s+BY\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern LIMIT_PATTERN = Pattern.compile("\\bLIMIT\\s+(\\d+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern TIME_RANGE_PATTERN = Pattern.compile(
        "(INTERVAL|CURRENT_DATE|BETWEEN)\\s+['\"]?\\s*(\\d+)\\s*(day|week|month|year)s?",
        Pattern.CASE_INSENSITIVE);

    public QueryCostEstimate estimate(String sql, int datasetCount, int daysRange) {
        int limit = extractLimit(sql);
        boolean hasGroupBy = GROUP_BY_PATTERN.matcher(sql).find();
        boolean hasOrderBy = ORDER_BY_PATTERN.matcher(sql).find();
        boolean hasJoin = JOIN_PATTERN.matcher(sql).find();

        int score = computeScore(datasetCount, daysRange, limit, hasGroupBy, hasOrderBy, hasJoin);
        String riskLevel = scoreToRisk(score);
        boolean requiresReview = "HIGH".equals(riskLevel) || "CRITICAL".equals(riskLevel);

        QueryCostEstimate estimate = new QueryCostEstimate(
            datasetCount, daysRange, limit, hasGroupBy, hasOrderBy, hasJoin,
            riskLevel, requiresReview
        );

        log.info("SqlCostEstimator: risk={}, score={}, datasets={}, days={}, limit={}",
            riskLevel, score, datasetCount, daysRange, limit);
        return estimate;
    }

    public QueryCostEstimate estimate(String sql) {
        int datasetCount = countDatasets(sql);
        int daysRange = extractDaysRange(sql);
        return estimate(sql, datasetCount, daysRange);
    }

    public boolean requiresConfirmation(QueryCostEstimate estimate) {
        return "HIGH".equals(estimate.riskLevel());
    }

    public boolean requiresReview(QueryCostEstimate estimate) {
        return estimate.requiresReview();
    }

    public int clampLimit(int requestedLimit) {
        if (requestedLimit <= 0) return DEFAULT_ROWS;
        return Math.min(requestedLimit, DEFAULT_MAX_ROWS);
    }

    public int clampLookbackDays(int requestedDays) {
        if (requestedDays <= 0) return DEFAULT_MAX_LOOKBACK_DAYS;
        return Math.min(requestedDays, DEFAULT_MAX_LOOKBACK_DAYS);
    }

    public int getTimeoutSeconds(QueryCostEstimate estimate) {
        return switch (estimate.riskLevel()) {
            case "CRITICAL" -> DEFAULT_TIMEOUT_SECONDS * 2;
            case "HIGH" -> (int) (DEFAULT_TIMEOUT_SECONDS * 1.5);
            default -> DEFAULT_TIMEOUT_SECONDS;
        };
    }

    private int computeScore(int datasetCount, int daysRange, int limit,
            boolean hasGroupBy, boolean hasOrderBy, boolean hasJoin) {
        int score = 0;

        score += datasetCount * 10;

        if (daysRange > 365) score += 30;
        else if (daysRange > 90) score += 20;
        else if (daysRange > 30) score += 10;

        if (limit > 10000) score += 25;
        else if (limit > 5000) score += 15;
        else if (limit > 1000) score += 5;

        if (hasGroupBy) score += 5;
        if (hasOrderBy) score += 5;
        if (hasJoin) score += 15;

        return score;
    }

    private String scoreToRisk(int score) {
        if (score >= CRITICAL_THRESHOLD) return "CRITICAL";
        if (score >= HIGH_THRESHOLD) return "HIGH";
        if (score >= MEDIUM_THRESHOLD) return "MEDIUM";
        return "LOW";
    }

    private int extractLimit(String sql) {
        Matcher m = LIMIT_PATTERN.matcher(sql);
        if (m.find()) {
            return Integer.parseInt(m.group(1));
        }
        return DEFAULT_ROWS;
    }

    private int extractDaysRange(String sql) {
        Matcher m = TIME_RANGE_PATTERN.matcher(sql);
        if (m.find()) {
            int value = Integer.parseInt(m.group(2));
            String unit = m.group(3).toLowerCase();
            return switch (unit) {
                case "day" -> value;
                case "week" -> value * 7;
                case "month" -> value * 30;
                case "year" -> value * 365;
                default -> value;
            };
        }
        return DEFAULT_MAX_LOOKBACK_DAYS;
    }

    private int countDatasets(String sql) {
        Set<String> found = new LinkedHashSet<>();
        Matcher fromMatcher = DATASET_REF_PATTERN.matcher(sql);
        while (fromMatcher.find()) {
            found.add(fromMatcher.group(1));
        }
        Matcher joinTablePattern = Pattern.compile("\\bJOIN\\s+([\\w.]+)", Pattern.CASE_INSENSITIVE).matcher(sql);
        while (joinTablePattern.find()) {
            found.add(joinTablePattern.group(1));
        }
        return Math.max(found.size(), 1);
    }
}
