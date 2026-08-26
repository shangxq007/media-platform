package com.example.platform.sandbox;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Local implementation of the canonical bounded process-execution port. */
@org.springframework.modulith.NamedInterface("API")
public final class LocalSandboxProcessExecutionAdapter implements SandboxProcessExecutionPort {
    @Override
    public SandboxExecutionResult execute(
            List<String> command,
            Path workspace,
            Path workingDirectory,
            Set<Path> readOnlyInputs,
            Map<String, String> exactEnvironment,
            Duration timeout,
            long captureBytes,
            SandboxCancellation cancellation) throws IOException {
        return LocalSandboxProcess.execute(
                command,
                workspace,
                workingDirectory,
                readOnlyInputs,
                exactEnvironment,
                timeout,
                captureBytes,
                cancellation);
    }
}
