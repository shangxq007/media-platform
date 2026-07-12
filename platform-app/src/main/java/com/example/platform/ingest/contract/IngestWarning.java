package com.example.platform.ingest.contract;

public record IngestWarning(
    IngestWarningCode code,
    IngestWarningSeverity severity,
    DetectorProviderName sourceProvider,
    String message,
    boolean userVisible
) {}
