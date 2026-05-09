package com.example.platform.sandbox.domain;

/**
 * SPI interface for sandbox code execution.
 *
 * <p>Implementations may use Wasm runtimes (e.g., Wasmtime, Wasmer),
 * container-based isolation, or other sandboxing mechanisms.</p>
 *
 * <p><strong>Future work:</strong> Wasm implementation is planned but not yet available.
 * This interface is intentionally minimal to allow pluggable backends.</p>
 */
public interface SandboxExecutor {

    /**
     * Executes code in an isolated sandbox.
     *
     * @param code     the source code to execute
     * @param language the language/runtime identifier (e.g., "wasm", "python", "javascript")
     * @param timeout  maximum execution time in milliseconds
     * @return the execution result containing output and exit information
     */
    SandboxResult execute(String code, String language, long timeout);

    /**
     * Result of a sandboxed execution.
     */
    record SandboxResult(int exitCode, String output, String error) {}
}
