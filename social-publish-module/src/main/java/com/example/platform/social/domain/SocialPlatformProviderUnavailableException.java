package com.example.platform.social.domain;

import com.example.platform.shared.web.ErrorCode;
import com.example.platform.shared.web.PlatformException;

/** Typed HTTP-501 outcome when no real social provider authority is configured. */
public final class SocialPlatformProviderUnavailableException extends PlatformException {

    private static final ErrorCode PROVIDER_UNAVAILABLE = new ErrorCode() {
        @Override public String code() { return "SOCIAL-501-001"; }
        @Override public String title() { return "Social platform provider unavailable"; }
        @Override public int status() { return 501; }
    };

    private final String platform;

    public SocialPlatformProviderUnavailableException(String platform) {
        super(PROVIDER_UNAVAILABLE,
                "No real social platform provider is configured for: " + platform);
        this.platform = platform;
    }

    public String platform() {
        return platform;
    }
}
