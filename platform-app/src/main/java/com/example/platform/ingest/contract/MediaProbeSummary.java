package com.example.platform.ingest.contract;

import java.util.List;

public record MediaProbeSummary(
    MediaProbeProvider provider,
    String providerVersion,
    MediaProbeStatus status,
    Long durationMs,
    Long timeoutMs,
    String probeProfile,
    List<String> warnings
) {}
