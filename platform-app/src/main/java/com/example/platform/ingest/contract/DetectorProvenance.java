package com.example.platform.ingest.contract;

import java.util.List;

public record DetectorProvenance(
    DetectorProviderName provider,
    String providerVersion,
    DetectorMode mode,
    Long inputBytesLimit,
    Long observedBytes,
    boolean usedFilename,
    boolean usedDeclaredContentType,
    boolean usedMagicBytes,
    boolean usedContainerProbe,
    Long durationMs,
    DetectorResultStatus resultStatus,
    List<IngestWarningCode> warnings
) {}
