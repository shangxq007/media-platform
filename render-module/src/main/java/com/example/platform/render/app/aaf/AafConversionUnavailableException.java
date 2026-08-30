package com.example.platform.render.app.aaf;

import com.example.platform.shared.web.ErrorCode;
import com.example.platform.shared.web.PlatformException;

/** Typed fail-closed outcome when no real AAF conversion command is configured. */
public final class AafConversionUnavailableException extends PlatformException {

    private static final ErrorCode AAF_CONVERSION_UNAVAILABLE = new ErrorCode() {
        @Override public String code() { return "RENDER-503-AAF"; }
        @Override public String title() { return "AAF conversion unavailable"; }
        @Override public int status() { return 503; }
    };

    public AafConversionUnavailableException() {
        super(AAF_CONVERSION_UNAVAILABLE,
                "AAF conversion requires an enabled, explicitly configured converter command");
    }
}
