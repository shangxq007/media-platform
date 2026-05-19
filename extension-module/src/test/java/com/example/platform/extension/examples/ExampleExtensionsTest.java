package com.example.platform.extension.examples;

import com.example.platform.extension.domain.*;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ExampleExtensionsTest {

    @Test
    void thirdPartyProviderShouldReturnStructuredResult() throws ExtensionExecutionException {
        ThirdPartyRenderProviderExtension ext = new ThirdPartyRenderProviderExtension();

        assertEquals("provider.third_party.render", ext.providerKey());
        assertEquals("RENDER", ext.providerType());
        assertEquals("1.0.0", ext.version());
        assertEquals(ExtensionTrustLevel.SEMI_TRUSTED, ext.trustLevel());
        assertTrue(ext.isAvailable());

        ExtensionContext ctx = ExtensionContext.builder()
                .extensionKey(ext.providerKey())
                .tenantId("tenant-1")
                .userId("user-1")
                .build();

        ExtensionResult result = ext.execute(ctx, "{\"sourceUri\":\"s3://bucket/video.mp4\"}");
        assertTrue(result.success());
        assertNotNull(result.outputJson());
        assertTrue(result.outputJson().contains("outputUri"));
        assertNotNull(result.metrics());
    }

    @Test
    void customPromptRenderShouldRenderTemplate() throws ExtensionExecutionException {
        CustomPromptRenderExtension ext = new CustomPromptRenderExtension();

        assertEquals("prompt.extension.custom_render", ext.extensionKey());
        assertEquals("RENDER_SCRIPT", ext.extensionType());
        assertEquals(ExtensionTrustLevel.SEMI_TRUSTED, ext.trustLevel());

        ExtensionContext ctx = ExtensionContext.builder()
                .extensionKey(ext.extensionKey())
                .tenantId("tenant-1")
                .build();

        ExtensionResult result = ext.execute(ctx, "Hello {{name}}, welcome to {{place}}!",
                "{\"name\":\"Alice\",\"place\":\"Wonderland\"}");
        assertTrue(result.success());
        assertTrue(result.outputJson().contains("rendered"));
        assertTrue(result.metrics().containsKey("inputLength"));
    }

    @Test
    void customPromptRenderShouldDetectUnresolved() throws ExtensionExecutionException {
        CustomPromptRenderExtension ext = new CustomPromptRenderExtension();

        ExtensionContext ctx = ExtensionContext.builder()
                .extensionKey(ext.extensionKey())
                .build();

        ExtensionResult result = ext.execute(ctx, "Hello {{name}}!", "{}");
        assertTrue(result.success());
        assertTrue(result.outputJson().contains("unresolvedVariables"));
    }

    @Test
    void qualityCheckWorkflowShouldPass() throws ExtensionExecutionException {
        QualityCheckWorkflowStepExtension ext = new QualityCheckWorkflowStepExtension();

        assertEquals("workflow.step.quality_check", ext.stepKey());
        assertEquals("POST_PROCESS", ext.stepType());
        assertEquals(ExtensionTrustLevel.SEMI_TRUSTED, ext.trustLevel());

        ExtensionContext ctx = ExtensionContext.builder()
                .extensionKey(ext.stepKey())
                .tenantId("tenant-1")
                .build();

        ExtensionResult result = ext.execute(ctx, "{\"outputUri\":\"s3://bucket/output.mp4\"}");
        assertTrue(result.success());
        assertTrue(result.outputJson().contains("passed"));
        assertTrue(result.metrics().containsKey("qualityScore"));
    }

    @Test
    void dynamicSchedulerJobShouldComplete() throws ExtensionExecutionException {
        DynamicSchedulerTriggerExtension ext = new DynamicSchedulerTriggerExtension();

        assertEquals("scheduler.job.dynamic_cleanup", ext.providerKey());
        assertEquals("SCHEDULER_JOB", ext.providerType());
        assertEquals(ExtensionTrustLevel.FULLY_TRUSTED, ext.trustLevel());

        ExtensionContext ctx = ExtensionContext.builder()
                .extensionKey(ext.providerKey())
                .tenantId("tenant-1")
                .build();

        ExtensionResult result = ext.execute(ctx, "{\"jobType\":\"cleanup\",\"retentionDays\":30}");
        assertTrue(result.success());
        assertTrue(result.outputJson().contains("completed"));
        assertTrue(result.metrics().containsKey("recordsProcessed"));
    }

    @Test
    void dynamicSchedulerDryRunShouldWork() throws ExtensionExecutionException {
        DynamicSchedulerTriggerExtension ext = new DynamicSchedulerTriggerExtension();

        ExtensionContext ctx = ExtensionContext.builder()
                .extensionKey(ext.providerKey())
                .build();

        ExtensionResult result = ext.execute(ctx, "{\"jobType\":\"cleanup\",\"dryRun\":true}");
        assertTrue(result.success());
        assertTrue(result.outputJson().contains("dry_run"));
    }

    @Test
    void thirdPartyProviderShouldReturnCustomResourceLimits() {
        ThirdPartyRenderProviderExtension ext = new ThirdPartyRenderProviderExtension();
        ExtensionResourceLimits limits = ext.resourceLimits();

        assertEquals(2, limits.maxConcurrency());
        assertEquals(512, limits.maxMemoryMb());
        assertEquals(60_000, limits.timeoutMs());
    }

    @Test
    void allExamplesShouldHaveValidSchemas() {
        ThirdPartyRenderProviderExtension provider = new ThirdPartyRenderProviderExtension();
        assertNotNull(provider.inputSchema());
        assertNotNull(provider.outputSchema());
        assertTrue(provider.inputSchema().contains("sourceUri"));

        CustomPromptRenderExtension prompt = new CustomPromptRenderExtension();
        assertNotNull(prompt.validate("{}"));

        QualityCheckWorkflowStepExtension workflow = new QualityCheckWorkflowStepExtension();
        assertNotNull(workflow.inputSchema());
        assertNotNull(workflow.outputSchema());
        assertTrue(workflow.inputSchema().contains("outputUri"));
    }
}
