package com.example.platform.audit.domain;

public enum ProblematicDataType {
    // Bug-caused data issues
    MISSING_FIELD,
    FORMAT_ERROR,
    LOGIC_CONFLICT,
    DUPLICATE_ENTRY,
    INVALID_STATE_TRANSITION,
    NULL_REQUIRED_VALUE,

    // Behavior anomalies
    OUTPUT_MISMATCH,
    QUALITY_DEGRADATION,
    COST_ANOMALY,
    PERFORMANCE_ANOMALY,
    UNEXPECTED_OUTPUT_SIZE,
    DURATION_ANOMALY,
    RESOLUTION_MISMATCH,
    CODEC_MISMATCH,

    // KPI/SLA anomalies
    SLA_BREACH,
    KPI_THRESHOLD_EXCEEDED,
    ERROR_RATE_SPIKE,
    LATENCY_SPIKE,

    // Feedback-related
    USER_REPORTED_ISSUE,
    SESSION_REPLAY_ANOMALY
}
