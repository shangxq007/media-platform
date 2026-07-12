package com.example.platform.ingest.preflight.policy;

import java.util.regex.Pattern;

public record PreflightPolicyRuleId(String value) {
    private static final Pattern VALID_ID = Pattern.compile("^[a-z0-9]+(-[a-z0-9]+)*$");

    public static final PreflightPolicyRuleId CONTENT_TYPE_MISMATCH = new PreflightPolicyRuleId("content-type-mismatch");
    public static final PreflightPolicyRuleId UNSUPPORTED_CONTENT_TYPE = new PreflightPolicyRuleId("unsupported-content-type");
    public static final PreflightPolicyRuleId UNSUPPORTED_VIDEO_CODEC = new PreflightPolicyRuleId("unsupported-video-codec");
    public static final PreflightPolicyRuleId UNSUPPORTED_AUDIO_CODEC = new PreflightPolicyRuleId("unsupported-audio-codec");
    public static final PreflightPolicyRuleId DURATION_TOO_LONG = new PreflightPolicyRuleId("duration-too-long");
    public static final PreflightPolicyRuleId RESOLUTION_TOO_LARGE = new PreflightPolicyRuleId("resolution-too-large");
    public static final PreflightPolicyRuleId NO_VIDEO_STREAM = new PreflightPolicyRuleId("no-video-stream");
    public static final PreflightPolicyRuleId NO_AUDIO_STREAM = new PreflightPolicyRuleId("no-audio-stream");
    public static final PreflightPolicyRuleId MEDIA_PROBE_FAILED = new PreflightPolicyRuleId("media-probe-failed");
    public static final PreflightPolicyRuleId DETECTOR_TIMEOUT = new PreflightPolicyRuleId("detector-timeout");

    public PreflightPolicyRuleId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Rule ID must not be blank");
        }
        if (!VALID_ID.matcher(value).matches()) {
            throw new IllegalArgumentException("Rule ID must be lowercase kebab-case: " + value);
        }
    }
}
