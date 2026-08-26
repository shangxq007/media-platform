package com.example.platform.sandbox;

import java.io.IOException;
import java.util.Objects;

@org.springframework.modulith.NamedInterface("API")
public final class SandboxProcessExecutionException extends IOException {
    private final SandboxFailure failure;
    public SandboxProcessExecutionException(SandboxFailure failure) {
        super(Objects.requireNonNull(failure, "failure").message());
        this.failure = failure;
    }
    public SandboxFailure failure() { return failure; }
}
