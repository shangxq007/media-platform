package com.example.platform.extension.domain;

/**
 * SPI for Workflow step extensions.
 * Allows custom workflow steps to be injected into render pipelines.
 */
public interface WorkflowStepExtensionSPI {

    /**
     * Unique identifier for this workflow step.
     */
    String stepKey();

    /**
     * Step type (PRE_PROCESS, POST_PROCESS, VALIDATION, CUSTOM).
     */
    String stepType();

    /**
     * Semantic version.
     */
    String version();

    /**
     * Input schema (JSON Schema).
     */
    String inputSchema();

    /**
     * Output schema (JSON Schema).
     */
    String outputSchema();

    /**
     * Execute the workflow step.
     * @param stepInput JSON string containing step input
     * @param workflowContext JSON string containing workflow context (jobId, tenantId, etc.)
     * @return JSON string containing step output
     * @throws ExtensionExecutionException on failure
     */
    String executeStep(String stepInput, String workflowContext) throws ExtensionExecutionException;

    /**
     * Called when the extension is being unloaded.
     */
    void onUnload();

    /**
     * Called before rollback.
     */
    default void onRollback(String targetVersion) {}
}
