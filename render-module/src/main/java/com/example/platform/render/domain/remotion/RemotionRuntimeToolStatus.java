package com.example.platform.render.domain.remotion;

/**
 * Status of a single Remotion runtime tool.
 * Internal only.
 */
public record RemotionRuntimeToolStatus(
        String toolName,
        RemotionRuntimeAvailabilityStatus status,
        String version,
        String issue) {

    public boolean isAvailable() {
        return status == RemotionRuntimeAvailabilityStatus.AVAILABLE;
    }

    public static RemotionRuntimeToolStatus available(String name, String version) {
        return new RemotionRuntimeToolStatus(name,
                RemotionRuntimeAvailabilityStatus.AVAILABLE, version, null);
    }

    public static RemotionRuntimeToolStatus missing(String name) {
        return new RemotionRuntimeToolStatus(name,
                RemotionRuntimeAvailabilityStatus.MISSING, null, name + " not found");
    }

    public static RemotionRuntimeToolStatus checkFailed(String name, String issue) {
        return new RemotionRuntimeToolStatus(name,
                RemotionRuntimeAvailabilityStatus.CHECK_FAILED, null, issue);
    }
}
