package com.example.platform.ingest.contract;

public record ContentTypeSummary(
    String declaredContentType,
    String detectedContentType,
    String normalizedContentType,
    String extension,
    Boolean extensionMatchesDetectedType,
    Boolean declaredMatchesDetectedType
) {}
