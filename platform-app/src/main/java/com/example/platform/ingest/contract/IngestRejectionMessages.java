package com.example.platform.ingest.contract;

import java.util.Map;

public final class IngestRejectionMessages {

    private static final Map<IngestRejectionReasonCode, String> MESSAGES = Map.of(
        IngestRejectionReasonCode.FILE_EMPTY, "The uploaded file is empty.",
        IngestRejectionReasonCode.FILE_TOO_LARGE, "The uploaded file exceeds the allowed size.",
        IngestRejectionReasonCode.CONTENT_TYPE_UNSUPPORTED, "This file type is not supported.",
        IngestRejectionReasonCode.PARSING_TIMEOUT, "The file could not be checked in time. Please try again.",
        IngestRejectionReasonCode.SECURITY_SCAN_REQUIRED, "The file requires additional security checks before it can be used."
    );

    private IngestRejectionMessages() {}

    public static String userSafeMessage(IngestRejectionReasonCode code) {
        return MESSAGES.getOrDefault(code, "The file could not be processed.");
    }
}
