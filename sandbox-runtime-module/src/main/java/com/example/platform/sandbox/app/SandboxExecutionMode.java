package com.example.platform.sandbox.app;

/**
 * Execution mode for sandbox code execution.
 *
 * <p>Determines how user-submitted code is executed:
 * <ul>
 *   <li>{@link #DISABLED} — All code execution is rejected.</li>
 *   <li>{@link #IN_PROCESS} — Code runs in the main JVM via ScriptEngine.
 *       Only for dev/test. MUST NOT be used in production.</li>
 *   <li>{@link #EXTERNAL} — Code is sent to an external sandbox worker via HTTP.
 *       Production-safe when worker is properly isolated.</li>
 * </ul>
 */
public enum SandboxExecutionMode {
    DISABLED,
    IN_PROCESS,
    EXTERNAL
}
