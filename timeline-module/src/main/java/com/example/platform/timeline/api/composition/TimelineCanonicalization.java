package com.example.platform.timeline.api.composition;

/** Published Timeline owner contract; implemented by the existing single authority. */
public interface TimelineCanonicalization {
CanonicalizeResult canonicalize(String timelineJson) throws java.io.IOException;

public record CanonicalizeResult(
            String timelineJson,
            String schemaVersion,
            String timelineId,
            int revision) {}
}
