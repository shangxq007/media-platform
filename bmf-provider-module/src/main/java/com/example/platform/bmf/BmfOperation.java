package com.example.platform.bmf;

/**
 * Bounded BMF operations the provider can honestly lower in V1.
 *
 * <p>Each operation maps to exactly one platform capability. The mapping is provider-internal:
 * the platform capability identity is canonical and the BMF operation key is the physical plan
 * unit key produced by #21. An operation that is not listed here is rejected by the lowerer before
 * any runtime process starts.
 */
public enum BmfOperation {
    DECODE("decode", "video.decode"),
    AUDIO_DECODE("audio-decode", "audio.decode"),
    MULTITRACK_COMPOSITE("multitrack-composite", "render.multitrack-composite"),
    MULTITRACK_ENCODE("encode-multitrack", "render.output"),
    COMPOSITE("composite", "render.composite"),
    ENCODE("encode", "render.output"),
    TRANSCODE("transcode", "media.transcode");

    private final String operationKey;
    private final String capabilityId;

    BmfOperation(String operationKey, String capabilityId) {
        this.operationKey = operationKey;
        this.capabilityId = capabilityId;
    }

    public String operationKey() {
        return operationKey;
    }

    public String capabilityId() {
        return capabilityId;
    }

    /** Returns the bounded operation for a #21 unit key, or throws (fail closed). */
    public static BmfOperation ofOperationKey(String operationKey) {
        for (BmfOperation operation : values()) {
            if (operation.operationKey.equals(operationKey)) {
                return operation;
            }
        }
        throw new IllegalArgumentException("unsupported BMF operation key: " + operationKey);
    }

    /** True when the capability is one this provider declares and can execute. */
    public static boolean declaresCapability(String capabilityId) {
        for (BmfOperation operation : values()) {
            if (operation.capabilityId.equals(capabilityId)) {
                return true;
            }
        }
        return false;
    }

    public static java.util.List<String> capabilityIds() {
        return java.util.Arrays.stream(values()).map(BmfOperation::capabilityId).distinct().toList();
    }
}
