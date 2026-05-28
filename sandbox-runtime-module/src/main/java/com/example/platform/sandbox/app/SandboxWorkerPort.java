package com.example.platform.sandbox.app;

/**
 * Port interface for executing code in an external sandbox worker.
 *
 * <p>Implementations communicate with an isolated sandbox service
 * (e.g., a containerized worker) via HTTP. The worker handles the actual
 * ScriptEngine / WASM / container-based execution.
 *
 * <p>The port does NOT execute code in the main JVM.
 */
public interface SandboxWorkerPort {

    /**
     * Execute code in the external sandbox worker.
     *
     * @param request the execution request
     * @return the execution result
     */
    SandboxWorkerResult execute(SandboxWorkerRequest request);
}
