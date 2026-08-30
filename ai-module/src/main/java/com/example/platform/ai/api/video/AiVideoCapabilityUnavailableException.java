package com.example.platform.ai.api.video;

import com.example.platform.shared.web.ErrorCode;
import com.example.platform.shared.web.PlatformException;

/** Typed fail-closed outcome for unconfigured AI video capabilities. */
public final class AiVideoCapabilityUnavailableException extends PlatformException {
    private static final ErrorCode UNAVAILABLE = new ErrorCode() {
        @Override public String code() { return "AI-503-003"; }
        @Override public String title() { return "AI video capability unavailable"; }
        @Override public int status() { return 503; }
    };

    public AiVideoCapabilityUnavailableException(String capability) {
        super(UNAVAILABLE, "No real provider is configured for capability: " + capability);
    }
}
