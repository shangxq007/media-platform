package com.example.platform.audit.domain;

/**
 * Risk score for a user's usage pattern.
 */
public record UsageAnomalyScore(
        String tenantId,
        String userId,
        double overallScore,
        java.util.Map<String, Double> scoresByRule,
        String riskLevel) {

    public static final String RISK_LOW = "LOW";
    public static final String RISK_MEDIUM = "MEDIUM";
    public static final String RISK_HIGH = "HIGH";
    public static final String RISK_CRITICAL = "CRITICAL";

    public static String riskLevel(double score) {
        if (score >= 0.8) return RISK_CRITICAL;
        if (score >= 0.6) return RISK_HIGH;
        if (score >= 0.3) return RISK_MEDIUM;
        return RISK_LOW;
    }
}
