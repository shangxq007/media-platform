package com.example.platform.sandbox;

import java.io.IOException;

/** Process-launch mechanics only; no RuntimeAdapter, Artifact, completion, or fencing authority. */
@org.springframework.modulith.NamedInterface("API")
public interface BoundedProcessLauncher {
    SandboxExecutionResult launch(
            EffectiveSandboxExecutionSpecification specification, SandboxCancellation cancellation)
            throws IOException;
}
