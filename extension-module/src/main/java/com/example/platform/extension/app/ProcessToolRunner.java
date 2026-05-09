package com.example.platform.extension.app;

import com.example.platform.extension.domain.ToolExecutionRequest;
import com.example.platform.extension.domain.ToolExecutionResult;
import com.example.platform.extension.domain.ToolSandboxPolicy;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Port interface for executing media tools as external processes.
 *
 * <p>Implementations must:</p>
 * <ul>
 *   <li>Use {@link java.util.List String} arguments, never shell concatenation</li>
 *   <li>Enforce executable allowlist from {@link ToolRegistry}</li>
 *   <li>Support timeout via {@link ToolSandboxPolicy#getTimeoutMillis()}</li>
 *   <li>Capture stdout/stderr with size limits</li>
 *   <li>Protect against path traversal</li>
 * </ul>
 *
 * <p>Business modules must depend on this interface, not on any specific
 * process execution implementation.</p>
 *
 * @see ToolRegistry
 * @see ToolExecutionRequest
 * @see ToolExecutionResult
 */
public interface ProcessToolRunner {

    /**
     * Executes a media tool according to the given request.
     *
     * @param request the tool execution request
     * @return the execution result
     * @throws IllegalArgumentException if the tool is not registered or the request is invalid
     * @throws IllegalStateException    if the process fails to start
     */
    ToolExecutionResult execute(ToolExecutionRequest request);

    /**
     * Executes a media tool with an explicit sandbox policy override.
     *
     * @param request the tool execution request
     * @param policy  the sandbox policy to apply
     * @return the execution result
     */
    ToolExecutionResult execute(ToolExecutionRequest request, ToolSandboxPolicy policy);
}
