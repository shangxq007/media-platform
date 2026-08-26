package com.example.platform.sandbox;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Technology-neutral process-execution port owned by the canonical sandbox boundary.
 *
 * <p>Callers supply only an already-authorized command and bounded execution inputs. Domain-specific
 * request, registry, and result types remain in their owning modules.
 */
@FunctionalInterface
@org.springframework.modulith.NamedInterface("API")
public interface SandboxProcessExecutionPort {
    SandboxExecutionResult execute(
            List<String> command,
            Path workspace,
            Path workingDirectory,
            Set<Path> readOnlyInputs,
            Map<String, String> exactEnvironment,
            Duration timeout,
            long captureBytes,
            SandboxCancellation cancellation) throws IOException;
}
