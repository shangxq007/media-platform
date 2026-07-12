package com.example.platform.ingest.contract;

public record DetectorSummary(
    DetectorProviderName provider,
    DetectorMode mode,
    DetectorResultStatus status,
    Long durationMs
) {}
