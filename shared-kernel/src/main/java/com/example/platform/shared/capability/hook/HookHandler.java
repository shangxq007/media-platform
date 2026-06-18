package com.example.platform.shared.capability.hook;

import java.util.Set;

/**
 * Interface for hook handlers.
 *
 * <p>HookHandler is the contract for implementing hook logic.
 * Handlers declare which hook points they support and can be
 * invoked through the HookInvocation.</p>
 *
 * <p><strong>Contract only:</strong> This defines the handler interface.
 * Hook runtime is not implemented.</p>
 */
public interface HookHandler {

    /**
     * Unique handler identifier.
     */
    String handlerId();

    /**
     * Hook points supported by this handler.
     */
    Set<String> supportedHookPoints();

    /**
     * Handler capabilities.
     */
    HookHandlerCapabilities capabilities();

    /**
     * Handle a hook invocation.
     *
     * @param invocation the hook invocation
     * @return the hook result
     */
    HookResult handle(HookInvocation invocation);
}
