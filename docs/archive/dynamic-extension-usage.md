# Dynamic Extension & Plugin Hot-Load

> **Purpose:** Guide for dynamically loading/unloading plugins and scripts with sandbox, audit, and rollback.  
> **Last Updated:** 2026-05-14

---

## Overview

The media-platform supports dynamic extension loading at runtime without redeployment. Extensions run in a sandboxed environment with full audit logging and rollback capabilities.

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                    Extension Sources                         │
│  PF4J Plugins │ Groovy Scripts │ JS Scripts │ Python Scripts │
└────────────────────────┬────────────────────────────────────┘
                         │
                         ▼
┌─────────────────────────────────────────────────────────────┐
│              ExtensionRegistryService                         │
│  - Register/Unload Provider, Prompt, Workflow extensions    │
│  - Version management + rollback                             │
│  - Health checking                                           │
└────────────────────────┬────────────────────────────────────┘
                         │
                         ▼
┌─────────────────────────────────────────────────────────────┐
│              SandboxExecutionService                          │
│  - Timeout enforcement (30s default, 120s max)               │
│  - Output size limits (4MB)                                  │
│  - Isolated thread execution                                 │
│  - No network/filesystem access by default                   │
└────────────────────────┬────────────────────────────────────┘
                         │
                         ▼
┌─────────────────────────────────────────────────────────────┐
│              Audit + Alerting                                 │
│  - All operations logged to audit trail                      │
│  - Critical issues trigger Sentry alerts                     │
│  - OpenReplay session correlation                            │
└─────────────────────────────────────────────────────────────┘
```

## SPI Interfaces

### ProviderExtensionSPI

For third-party render providers or AI providers:

```java
public interface ProviderExtensionSPI {
    String providerKey();          // Unique identifier
    String providerType();         // RENDER, AI, NOTIFICATION, STORAGE
    String version();              // Semantic version
    String inputSchema();          // JSON Schema for input
    String outputSchema();         // JSON Schema for output
    String execute(String inputJson) throws ExtensionExecutionException;
    boolean isAvailable();
    void onUnload();
    default void onRollback(String targetVersion) {}
}
```

### PromptExtensionSPI

For custom prompt templates, rendering scripts, and post-processing:

```java
public interface PromptExtensionSPI {
    String extensionKey();
    String extensionType();        // TEMPLATE, RENDER_SCRIPT, POST_PROCESSOR, VALIDATOR
    String version();
    String execute(String templateBody, String variables, String contextJson);
    String validate(String inputJson);
    void onUnload();
}
```

### WorkflowStepExtensionSPI

For custom workflow steps in render pipelines:

```java
public interface WorkflowStepExtensionSPI {
    String stepKey();
    String stepType();             // PRE_PROCESS, POST_PROCESS, VALIDATION, CUSTOM
    String version();
    String inputSchema();
    String outputSchema();
    String executeStep(String stepInput, String workflowContext);
    void onUnload();
}
```

## Registration Examples

### Register a Third-Party AI Provider

```java
@Service
public class ThirdPartyAiProviderRegistration {

    @Autowired
    private ExtensionRegistryService registry;

    @PostConstruct
    public void register() {
        registry.registerProviderExtension("ai.openai", new ProviderExtensionSPI() {
            public String providerKey() { return "ai.openai"; }
            public String providerType() { return "AI"; }
            public String version() { return "1.0.0"; }
            public String inputSchema() { return "{\"type\":\"object\",\"properties\":{\"prompt\":{\"type\":\"string\"}}}"; }
            public String outputSchema() { return "{\"type\":\"object\",\"properties\":{\"text\":{\"type\":\"string\"}}}"; }
            public String execute(String inputJson) { /* call OpenAI API */ return "{}"; }
            public boolean isAvailable() { return true; }
            public void onUnload() { /* cleanup */ }
        }, "system");
    }
}
```

### Register a Custom Prompt Template

```java
registry.registerPromptExtension("prompt.custom_greeting", new PromptExtensionSPI() {
    public String extensionKey() { return "prompt.custom_greeting"; }
    public String extensionType() { return "TEMPLATE"; }
    public String version() { return "1.0.0"; }
    public String execute(String body, String vars, String ctx) {
        // Custom template rendering logic
        return body.replace("{{greeting}}", "Hello");
    }
    public String validate(String input) { return "{\"valid\":true}"; }
    public void onUnload() {}
}, "admin");
```

### Register a Workflow Step Extension

```java
registry.registerWorkflowStepExtension("workflow.quality_check", new WorkflowStepExtensionSPI() {
    public String stepKey() { return "workflow.quality_check"; }
    public String stepType() { return "POST_PROCESS"; }
    public String version() { return "1.0.0"; }
    public String inputSchema() { return "{}"; }
    public String outputSchema() { return "{}"; }
    public String executeStep(String input, String ctx) {
        // Custom quality check logic
        return "{\"qualityScore\": 95}";
    }
    public void onUnload() {}
}, "admin");
```

## Scheduler Dynamic Trigger

```java
// Register a dynamic scheduled job
ScheduledJobDefinition jobDef = new ScheduledJobDefinition(
    "job.cleanup_stale_data",
    "Cleanup Stale Data",
    "0 0 2 * * ?",  // Daily at 2 AM
    true,
    3
);

scheduleRegistryService.registerJob("cleanup_stale", jobDef);

// Manual trigger
// POST /api/v1/internal/scheduler/run/cleanup_stale
```

## Rollback Examples

### Unload an Extension

```java
registry.unloadExtension("ai.openai", "admin");
```

### Rollback to Previous Version

```java
// Rollback to version 1.0.0
registry.rollbackExtension("ai.openai", "1.0.0", "admin");
```

### Revert Prompt Template

```java
// Using PromptTemplateService
promptTemplateService.rollbackToVersion("template-id", "1.0.0");
```

### Rerun Failed RenderJob

```java
// Reset failed job to QUEUED for retry
renderJobService.retry("job-123", "tenant-1");
```

## Security Limits

| Resource | Default Limit | Max |
|----------|--------------|-----|
| Execution timeout | 30 seconds | 120 seconds |
| Output size | 4 MB | 4 MB |
| Network access | Disabled | Configurable |
| Filesystem access | Working directory only | Configurable |
| Thread pool | Cached (unlimited) | Bounded by JVM |

## Audit Events

| Event | Category | Details |
|-------|----------|---------|
| `EXTENSION_REGISTERED` | EXTENSION | key, version, type, action |
| `EXTENSION_UNLOADED` | EXTENSION | key, version |
| `EXTENSION_ROLLED_BACK` | EXTENSION | key, fromVersion, toVersion |
| `EXTENSION_EXECUTION_STARTED` | EXTENSION | key, tenantId, inputSize |
| `EXTENSION_EXECUTION_COMPLETED` | EXTENSION | key, tenantId, durationMs, outputSize |
| `EXTENSION_EXECUTION_TIMEOUT` | EXTENSION | key, tenantId, timeoutMs |
