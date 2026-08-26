package com.example.platform.sandbox;

@FunctionalInterface
@org.springframework.modulith.NamedInterface("API")
public interface SandboxCancellation {
    boolean isCancellationRequested();
    static SandboxCancellation never() { return () -> false; }
}
