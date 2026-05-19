package com.example.platform.prompt.domain;

public enum PromptExecutionStatus {
    PENDING,
    RUNNING,
    SUCCEEDED,
    FAILED,
    CANCELLED,
    REQUIRE_REVIEW
}
