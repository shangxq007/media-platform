package com.example.platform.ai.api;

import com.example.platform.ai.domain.ChatResult;

/**
 * Port interface for AI gateway operations.
 * Exposed as part of the ai-module API surface for other modules to consume.
 */
public interface AiGatewayPort {

    /**
     * Send a chat request to the AI provider routed by capability.
     *
     * @param capability the capability key used for provider routing
     * @param prompt     the prompt to send
     * @return the chat result from the selected provider
     */
    ChatResult chat(String capability, String prompt);
}
