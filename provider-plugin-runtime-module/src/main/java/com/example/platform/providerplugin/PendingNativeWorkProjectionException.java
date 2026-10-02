package com.example.platform.providerplugin;

/** Fail-closed typed error: pending native work cannot be projected for this request. */
public final class PendingNativeWorkProjectionException extends IllegalStateException {

    public PendingNativeWorkProjectionException(String code, String detail) {
        super(code + ": " + detail);
    }
}
