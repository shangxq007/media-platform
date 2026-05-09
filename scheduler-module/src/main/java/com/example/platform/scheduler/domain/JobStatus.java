package com.example.platform.scheduler.domain;

public enum JobStatus {
    PENDING,
    RUNNING,
    COMPLETED,
    FAILED,
    DEAD_LETTER
}
