package com.example.platform.timeline.diff;

/**
 * Error codes for semantic diff operations.
 */
public final class TimelineDiffErrors {

    private TimelineDiffErrors() {}

    public static final String REVISION_NOT_FOUND = "TIMELINE_DIFF_REVISION_NOT_FOUND";
    public static final String CROSS_PRODUCT_NOT_ALLOWED = "TIMELINE_DIFF_CROSS_PRODUCT_NOT_ALLOWED";
    public static final String SCHEMA_INCOMPATIBLE = "TIMELINE_DIFF_SCHEMA_INCOMPATIBLE";
    public static final String PAYLOAD_INVALID = "TIMELINE_DIFF_PAYLOAD_INVALID";
    public static final String DIGEST_MISMATCH = "TIMELINE_DIFF_DIGEST_MISMATCH";

    public static class TimelineDiffException extends RuntimeException {
        private final String errorCode;

        public TimelineDiffException(String errorCode, String message) {
            super(message);
            this.errorCode = errorCode;
        }

        public String getErrorCode() {
            return errorCode;
        }
    }

    public static class RevisionNotFoundException extends TimelineDiffException {
        private final String revisionId;

        public RevisionNotFoundException(String revisionId) {
            super(REVISION_NOT_FOUND, "Revision not found: " + revisionId);
            this.revisionId = revisionId;
        }

        public String getRevisionId() {
            return revisionId;
        }
    }

    public static class CrossProductException extends TimelineDiffException {
        private final String expectedProductId;
        private final String actualProductId;

        public CrossProductException(String expectedProductId, String actualProductId) {
            super(CROSS_PRODUCT_NOT_ALLOWED,
                    "Cross-product diff not allowed: expected " + expectedProductId + ", got " + actualProductId);
            this.expectedProductId = expectedProductId;
            this.actualProductId = actualProductId;
        }

        public String getExpectedProductId() {
            return expectedProductId;
        }

        public String getActualProductId() {
            return actualProductId;
        }
    }

    public static class SchemaIncompatibleException extends TimelineDiffException {
        private final String baseSchema;
        private final String targetSchema;

        public SchemaIncompatibleException(String baseSchema, String targetSchema) {
            super(SCHEMA_INCOMPATIBLE,
                    "Schema versions incompatible: " + baseSchema + " vs " + targetSchema);
            this.baseSchema = baseSchema;
            this.targetSchema = targetSchema;
        }

        public String getBaseSchema() {
            return baseSchema;
        }

        public String getTargetSchema() {
            return targetSchema;
        }
    }

    public static class PayloadInvalidException extends TimelineDiffException {
        public PayloadInvalidException(String message) {
            super(PAYLOAD_INVALID, message);
        }
    }

    public static class DigestMismatchException extends TimelineDiffException {
        public DigestMismatchException(String message) {
            super(DIGEST_MISMATCH, message);
        }
    }
}
