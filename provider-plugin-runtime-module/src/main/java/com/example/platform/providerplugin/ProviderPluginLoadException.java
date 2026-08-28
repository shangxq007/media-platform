package com.example.platform.providerplugin;

/** Fail-closed typed provider plugin discovery or catalog error. */
public final class ProviderPluginLoadException extends IllegalStateException {

    public ProviderPluginLoadException(String code, String detail) {
        super(code + ": " + detail);
    }

    public ProviderPluginLoadException(String code, String detail, Throwable cause) {
        super(code + ": " + detail, cause);
    }
}
