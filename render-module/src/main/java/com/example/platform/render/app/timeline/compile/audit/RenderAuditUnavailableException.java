package com.example.platform.render.app.timeline.compile.audit;

import com.example.platform.shared.web.ErrorCode;
import com.example.platform.shared.web.PlatformException;

/** Fail-closed outcome until durable render-audit authority is composed. */
public final class RenderAuditUnavailableException extends PlatformException {

    private static final ErrorCode RENDER_AUDIT_UNAVAILABLE = new ErrorCode() {
        @Override public String code() { return "RENDER-503-AUDIT"; }
        @Override public String title() { return "Render audit unavailable"; }
        @Override public int status() { return 503; }
    };

    public RenderAuditUnavailableException() {
        super(RENDER_AUDIT_UNAVAILABLE,
                "Durable render audit authority is not configured");
    }
}
