package com.example.platform.ai.api;

import com.example.platform.shared.web.ErrorCode;
import com.example.platform.shared.web.PlatformException;

/** Typed fail-closed outcome when no real chat provider can serve a capability. */
public final class AiProviderUnavailableException extends PlatformException {

    private static final ErrorCode AI_PROVIDER_UNAVAILABLE = new ErrorCode() {
        @Override public String code() { return "AI-503-002"; }
        @Override public String title() { return "AI provider unavailable"; }
        @Override public int status() { return 503; }
    };

    public AiProviderUnavailableException(String capability) {
        this(capability, null);
    }

    public AiProviderUnavailableException(String capability, Throwable cause) {
        super(AI_PROVIDER_UNAVAILABLE,
                "No real AI provider is available for capability: " + capability);
        if (cause != null) {
            initCause(cause);
        }
    }
}
