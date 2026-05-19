package com.example.platform.audit.domain;

public enum ProblematicDataStatus {
    DETECTED,
    AUTO_FIXING,
    AUTO_FIXED,
    QUARANTINED,
    HUMAN_REVIEW_REQUIRED,
    HUMAN_REVIEW_IN_PROGRESS,
    RESOLVED,
    FALSE_POSITIVE,
    IGNORED
}
