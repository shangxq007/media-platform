package com.example.platform.sandbox;

/** Typed resolver boundary failure; messages must never contain resolved secret material. */
@org.springframework.modulith.NamedInterface("API")
public final class SandboxSecretResolutionException extends Exception {
    public SandboxSecretResolutionException(String message) {
        super(message);
    }

    public SandboxSecretResolutionException(String message, Throwable cause) {
        super(message, cause);
    }
}
