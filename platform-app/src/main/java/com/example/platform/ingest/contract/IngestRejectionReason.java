package com.example.platform.ingest.contract;

public record IngestRejectionReason(
    IngestRejectionReasonCode code,
    UploadPreflightPhase sourcePhase,
    boolean retryable,
    String userSafeMessage
) {}
