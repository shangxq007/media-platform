package com.example.platform.federation.app;

import com.example.platform.shared.web.ErrorCode;
import com.example.platform.shared.web.PlatformException;

/** Typed unavailable outcome until a real federated query executor is composed. */
public final class FederationQueryUnavailableException extends PlatformException {
    private static final ErrorCode UNAVAILABLE = new ErrorCode() {
        @Override public String code() { return "FEDERATION-503-001"; }
        @Override public String title() { return "Federated query unavailable"; }
        @Override public int status() { return 503; }
    };

    public FederationQueryUnavailableException() {
        super(UNAVAILABLE, "No real federated query executor is configured");
    }
}
