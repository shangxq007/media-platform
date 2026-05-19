package com.example.platform.extension.domain;

/**
 * SPI for Prompt template extensions.
 * Allows custom prompt templates, rendering scripts, and post-processing.
 */
public interface PromptExtensionSPI {

    /**
     * Unique identifier for this prompt extension.
     */
    String extensionKey();

    /**
     * Extension type (TEMPLATE, RENDER_SCRIPT, POST_PROCESSOR, VALIDATOR).
     */
    String extensionType();

    /**
     * Semantic version.
     */
    String version();

    /**
     * Execute the prompt extension.
     * @param templateBody the prompt template body
     * @param variables JSON string of variables
     * @param contextJson JSON string of execution context (tenantId, userId, etc.)
     * @return JSON string containing the result (rendered prompt, validation result, etc.)
     * @throws ExtensionExecutionException on failure
     */
    String execute(String templateBody, String variables, String contextJson) throws ExtensionExecutionException;

    /**
     * Validate input before execution.
     * @return JSON string with {valid: bool, errors: [...]}
     */
    String validate(String inputJson);

    /**
     * Called when the extension is being unloaded.
     */
    void onUnload();

    /**
     * Called before rollback.
     */
    default void onRollback(String targetVersion) {}
}
