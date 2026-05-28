package com.example.platform.sandbox.app;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * No-op implementation of {@link SandboxWorkerPort}.
 *
 * <p>Used when execution-mode=external but no worker URL is configured.
 * Returns a clear error without executing any code.
 */
@Component
public class NoopSandboxWorkerAdapter implements SandboxWorkerPort {

    private static final Logger log = LoggerFactory.getLogger(NoopSandboxWorkerAdapter.class);

    @Override
    public SandboxWorkerResult execute(SandboxWorkerRequest request) {
        log.warn("Sandbox worker not configured. Cannot execute code. " +
                 "Set sandbox.worker.base-url to enable external execution.");
        return SandboxWorkerResult.workerUnavailable(
                "External sandbox worker is not configured. " +
                "Set sandbox.worker.base-url to enable code execution.");
    }
}
