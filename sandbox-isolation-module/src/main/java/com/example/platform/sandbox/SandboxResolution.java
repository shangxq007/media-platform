package com.example.platform.sandbox;

@org.springframework.modulith.NamedInterface("API")
public sealed interface SandboxResolution permits SandboxResolution.Resolved, SandboxResolution.Rejected {
    @org.springframework.modulith.NamedInterface("API")
    record Resolved(EffectiveSandboxExecutionSpecification specification) implements SandboxResolution {}

    @org.springframework.modulith.NamedInterface("API")
    record Rejected(SandboxFailure failure) implements SandboxResolution {}
}
