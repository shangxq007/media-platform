# Dynamic Extension Platform Upgrade (Prompt 56)

## Overview

This document describes the upgrade to the dynamic extension platform, implementing multi-layer trust model, routing with canary release, resource limits, SPI context enhancement, structured results, rollback mechanism, comprehensive audit, and sandbox script execution.

## Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                    Extension Platform v2                         │
├─────────────┬──────────────┬──────────────┬────────────────────┤
│ Trust Model │  Routing &   │  Resource    │  SPI Context &     │
│             │  Canary      │  Limits      │  Structured Result │
├─────────────┼──────────────┼──────────────┼────────────────────┤
│ FULLY_TRUST │ ExtensionRou │ Concurrency  │ ExtensionContext   │
│ SEMI_TRUST  │ RoutingRule  │ Memory/CPU   │ ExtensionResult    │
│ UNTRUSTED   │ Traffic %    │ Queue + I/O  │ success/metrics    │
├─────────────┴──────────────┴──────────────┴────────────────────┤
│                    Rollback & Audit                              │
├─────────────────────────┬───────────────────────────────────────┤
│ RollbackPoint           │ ExtensionAuditService                 │
│ Version History         │ ExtensionAuditEvent                   │
│ Routing Rule Rollback   │ 20+ event types                       │
├─────────────────────────┴───────────────────────────────────────┤
│                    Sandbox Runtime                               │
├─────────────────────────────────────────────────────────────────┤
│ Groovy / JS / Python / Wasm                                     │
│ DefaultSandboxSecurityPolicy                                    │
│ Timeout + Output limits                                         │
└─────────────────────────────────────────────────────────────────┘
```

## 1. Multi-Layer Trust Model

Three trust levels control extension capabilities:

| Trust Level | Concurrency | Memory | CPU | Timeout | Sandbox | Use Case |
|---|---|---|---|---|---|---|
| `FULLY_TRUSTED` | 16 | 1024MB | 100% | 120s | Optional | PF4J plugins, internal extensions |
| `SEMI_TRUSTED` (default) | 4 | 256MB | 50% | 30s | Required | Third-party providers, scripts |
| `UNTRUSTED` | 1 | 64MB | 25% | 10s | Strict | External user scripts |

### Usage

```java
// Register a PF4J plugin as fully trusted
registryService.registerProviderExtension("my-plugin", plugin,
    ExtensionTrustLevel.FULLY_TRUSTED, "admin");

// Register a third-party script as semi-trusted
registryService.registerPromptExtension("custom-render", extension,
    ExtensionTrustLevel.SEMI_TRUSTED, "admin");

// Register an untrusted user script
registryService.registerWorkflowStepExtension("user-step", extension,
    ExtensionTrustLevel.UNTRUSTED, "admin");
```

## 2. Routing & Canary Release

The `ExtensionRouter` manages version routing rules stored in the database:

### Creating a Canary Rule

```java
// Route 10% of tenant-1 traffic to v2.0.0
router.createRule("canary-10%", "ext-1", "1.0.0", "2.0.0",
    "tenant-1", null, null, 100, 10, "admin");

// Route all "render" scene traffic to v2.0.0
router.createRule("render-v2", "ext-1", "1.0.0", "2.0.0",
    null, null, "render", 50, 100, "admin");
```

### Updating Traffic Percent

```java
// Increase canary to 50%
router.updateRule("route-id", 50);
```

### Rolling Back Routing Rules

```java
// Remove all routing rules for an extension
router.rollbackRules("ext-1", "admin");
```

### REST API

```
POST   /api/v1/extensions/{key}/routing-rules          - Create rule
GET    /api/v1/extensions/{key}/routing-rules           - List rules
```

## 3. Resource Limits

Per-extension resource control via `ExtensionResourceLimiter`:

| Limit | Default | Description |
|---|---|---|
| maxConcurrency | 4 | Max parallel executions |
| maxMemoryMb | 256 | Memory ceiling |
| maxCpuPercent | 50 | CPU share |
| maxQueueSize | 100 | Max queued requests |
| maxInputBytes | 10MB | Max input payload |
| maxOutputBytes | 4MB | Max output payload |
| timeoutMs | 30s | Execution timeout |

### REST API

```
GET /api/v1/extensions/{key}/resource-limits - View limits and usage
```

## 4. SPI Context & Structured Results

### ExtensionContext

```java
ExtensionContext context = ExtensionContext.builder()
    .extensionKey("my-ext")
    .extensionVersion("1.0.0")
    .tenantId("tenant-1")
    .userId("user-1")
    .traceId("trace-abc")
    .trustLevel(ExtensionTrustLevel.SEMI_TRUSTED)
    .config("apiEndpoint", "https://api.example.com")
    .attribute("priority", 10)
    .build();
```

### ExtensionResult

```java
// Success
ExtensionResult result = ExtensionResult.success(
    "{\"output\":\"data\"}",
    Map.of("durationMs", 150L));

// Failure
ExtensionResult result = ExtensionResult.failure(
    "ERR-001", "Processing failed");
```

### V2 SPI Interfaces

```java
// Provider with context and structured result
public interface ProviderExtensionSPIV2 extends ProviderExtensionSPI {
    ExtensionTrustLevel trustLevel();
    ExtensionResult execute(ExtensionContext context, String inputJson);
    ExtensionResourceLimits resourceLimits();
}
```

## 5. Rollback Mechanism

### Extension Version Rollback

```
POST /api/v1/extensions/{key}/rollback
{
  "targetVersion": "1.0.0",
  "rolledBackBy": "admin"
}
```

### Creating Rollback Points

```
POST /api/v1/extensions/{key}/rollback-point?createdBy=admin
```

### Routing Rule Rollback

```java
router.rollbackRules("ext-1", "admin");
```

## 6. Comprehensive Audit

All operations are recorded via `ExtensionAuditService`:

| Event Type | Trigger |
|---|---|
| EXTENSION_REGISTERED | New extension registered |
| EXTENSION_UNLOADED | Extension removed |
| EXTENSION_UPGRADE | Version upgrade |
| EXTENSION_ROLLED_BACK | Version rollback |
| EXTENSION_EXECUTION_STARTED | Execution begins |
| EXTENSION_EXECUTION_COMPLETED | Execution succeeds |
| EXTENSION_EXECUTION_TIMEOUT | Execution times out |
| EXTENSION_EXECUTION_FAILED | Execution fails |
| ROUTING_RULE_CREATED | New routing rule |
| ROUTING_RULE_UPDATED | Rule traffic changed |
| ROUTING_RULE_DELETED | Rule removed |
| ROUTING_RULE_ROLLED_BACK | All rules cleared |
| RESOURCE_LIMIT_EXCEEDED | Quota breached |
| SECURITY_VIOLATION | Blocked operation |
| REVIEW_REQUIRED | Pending approval |
| ROLLBACK_POINT_CREATED | Snapshot taken |

### REST API

```
GET /api/v1/extensions/{key}/audit-events   - Events for extension
GET /api/v1/extensions/audit-events/recent  - Recent events (global)
```

## 7. Sandbox Runtime

### Supported Languages

| Language | Engine | Status |
|---|---|---|
| Groovy | javax.script (Groovy) | Available if Groovy on classpath |
| JavaScript | Nashorn / GraalJS | Available if engine on classpath |
| Python | GraalVM Python / Jython | Available if engine on classpath |
| Wasm | Wasmtime / Wasmer | Planned |

### Security Policy

The `DefaultSandboxSecurityPolicy` blocks:
- `Runtime.getRuntime`, `ProcessBuilder`
- `java.io.File`, `java.nio.file`
- `Socket`, `ServerSocket`, `URL`
- `ClassLoader`, `reflect`, `Unsafe`
- `System.setProperty`, `System.getenv`

### REST API

```
GET  /api/v1/sandbox/runtime/overview  - Module status
POST /api/v1/sandbox/execute          - Execute script
```

## 8. Examples

### 8.1 Third-Party Provider Hot-Load

See `ThirdPartyRenderProviderExtension.java`:
- Implements `ProviderExtensionSPIV2`
- `SEMI_TRUSTED` trust level
- Custom resource limits (2 concurrency, 512MB memory)
- Structured result with metrics

### 8.2 Prompt Extension / Custom Render Script

See `CustomPromptRenderExtension.java`:
- Implements `PromptExtensionSPIV2`
- Template variable substitution with `{{var}}` syntax
- Reports unresolved variables

### 8.3 Workflow Dynamic Extension

See `QualityCheckWorkflowStepExtension.java`:
- Implements `WorkflowStepExtensionSPIV2`
- POST_PROCESS step type
- Quality score reporting

### 8.4 Scheduler Dynamic Trigger

See `DynamicSchedulerTriggerExtension.java`:
- Implements `ProviderExtensionSPIV2`
- `FULLY_TRUSTED` (internal job)
- Supports dry-run mode
- Extended timeout (300s)

## Database Schema (V13)

New tables:
- `extension_routing_rule` - Canary routing rules
- `extension_resource_limit` - Per-extension resource quotas
- `extension_rollback_point` - Rollback snapshots
- `extension_audit_event` - Detailed audit trail
- `sandbox_execution_job` - Sandbox execution tracking

Modified tables:
- `extension_definition` - Added trust_level, sandboxed, resource limits, review_status
- `extension_invocation` - Added trace_id, trust_level, resource usage metrics

## Testing

Run tests:

```bash
cd media-platform
./gradlew :extension-module:test
./gradlew :sandbox-runtime-module:test
```

## Migration from v1

Existing extensions continue to work:
- V1 SPI interfaces are preserved (backward compatible)
- V2 interfaces extend V1 with default methods
- Default trust level is `SEMI_TRUSTED`
- Default resource limits match previous behavior
