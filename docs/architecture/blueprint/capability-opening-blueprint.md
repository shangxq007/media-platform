---
status: blueprint
last_verified: 2026-06-22
scope: future
truth_level: target
owner: platform
---

# Capability Opening Blueprint

> **⚠️ BLUEPRINT ONLY** - This document describes the target architecture for progressive system capability opening. It is not implemented.

> **Reality Check (2026-06-22):** Contract skeletons exist in shared-kernel (SystemAction, ExtensionPoint, ExtensionProvider interfaces). Extension-module has 55 files with sandbox execution and CLI tools. Sandbox-runtime-module has 15 files with Groovy/JS/Python execution. Marketplace and plugin security sandbox remain unimplemented. See [blueprint-reality-mapping-report.md](../../review/blueprint-reality-mapping-report.md).

---

## 1. Purpose

Platform capabilities should be opened gradually through controlled extension points and system actions, not by immediately allowing arbitrary plugins or user code.

This blueprint defines a phased approach to:
- Expose internal system actions as first-class concepts
- Enable internal automation flows
- Support provider-based extension points
- Progress toward connector and plugin marketplaces
- Maintain security, isolation, and auditability at every level

**Key Principle:** Start with internal actions, end with reviewed marketplace. Never skip security levels.

---

## 2. Design Principles

### 2.1 Progressive Opening

| Principle | Description |
|-----------|-------------|
| Internal actions before external plugins | System actions must be defined and audited before exposing to external code |
| Fixed extension points before arbitrary runtime | Stable SPI contracts before allowing dynamic code |
| Connector marketplace before code plugin marketplace | HTTP/API connectors before sandboxed code execution |
| Tenant isolation from day one | All capability access scoped to tenant |
| Explicit permissions | Every action requires explicit permission grant |

### 2.2 Security First

| Principle | Description |
|-----------|-------------|
| Schema validation | All inputs/outputs validated against schemas |
| Audit log | Every capability invocation logged |
| Kill switch | Every provider/marketplace item can be disabled instantly |
| No direct database access for plugins | Plugins use API contracts only |
| No production secret exposure | Secrets referenced by ID, never raw values |
| No arbitrary code execution in early phases | Code plugins only in sandbox runtime (Level 5) |

### 2.3 Contract Stability

| Principle | Description |
|-----------|-------------|
| Versioned contracts | All SPI interfaces versioned semantically |
| Backward compatibility | New versions must not break existing consumers |
| Deprecation policy | Minimum 2 release cycles before removing deprecated contracts |

---

## 3. Capability Opening Levels

### Level 0 - Internal System Actions

**Status:** Planned / Partially implemented (as normal services, not formal registry)

**Description:** Internal platform actions that can be triggered by automation flows or internal services.

**Examples:**
| Action | Description | Current Status |
|--------|-------------|----------------|
| `create_render_job` | Create a new render job | ✅ Exists as service |
| `generate_proxy` | Generate proxy video | ✅ Exists as service |
| `generate_thumbnail` | Generate thumbnail image | ✅ Exists as service |
| `generate_hls_preview` | Generate HLS preview segments | ⚠️ Partial |
| `tag_asset` | Add metadata tags to asset | ✅ Exists as service |
| `create_review_link` | Create shareable review link | ✅ Exists as service |
| `send_notification` | Send notification to user | ✅ Exists as service |
| `send_webhook` | Send HTTP webhook | ⚠️ Partial |
| `export_artifact` | Export artifact to external storage | ✅ Exists as service |

**Non-Goals:**
- No formal SystemAction registry (unless verified in code)
- No external invocation
- No marketplace exposure

---

### Level 1 - Internal Automation Flows

**Status:** Planned

**Description:** Config-only workflows that chain system actions based on triggers.

**Examples:**
| Trigger | Flow | Description |
|---------|------|-------------|
| `asset.uploaded` | `generate_proxy → create_review_link` | Auto-generate proxy and review link on upload |
| `render.completed` | `notify_user → send_webhook` | Notify user and send webhook on render complete |
| `review.approved` | `export_artifact` | Auto-export on review approval |

**Constraints:**
- No user code execution
- No external marketplace
- Config-only workflows (YAML/DB configuration)
- Internal triggers only

**Non-Goals:**
- No arbitrary workflow definitions by tenants
- No external trigger sources
- No code-based workflow steps

---

### Level 2 - ExtensionPoint / Provider SPI

**Status:** Planned

**Description:** Stable contracts (SPI) that allow provider implementations to be plugged in.

**Examples:**
| Extension Point | Description | Provider Types |
|-----------------|-------------|----------------|
| `ai.transcribe` | Transcribe audio to text | Built-in, HTTP connector, BYOK |
| `ai.translate` | Translate text | Built-in, HTTP connector, BYOK |
| `ai.scene_summary` | Generate scene summary | Built-in, HTTP connector, BYOK |
| `ai.timeline_draft` | Draft timeline from prompt | Built-in, HTTP connector, BYOK |
| `media.generate_thumbnail` | Generate thumbnail | Built-in, external service |
| `render.generate_preview` | Generate preview | Built-in, external service |
| `notification.send` | Send notification | Built-in, webhook, email, SMS |
| `storage.export` | Export to storage | Built-in, S3, R2, custom |

**Provider Types:**
- Built-in provider (platform code)
- HTTP connector (external API)
- BYOK AI provider (user-provided API key)
- External service connector

**Non-Goals:**
- No arbitrary code execution
- No marketplace (Level 3+)
- No plugin sandboxing (Level 5)

---

### Level 3 - Connector Marketplace

**Status:** Deferred

**Description:** Reviewed connectors that can be installed by tenants.

**Characteristics:**
- Reviewed connectors (code reviewed by platform team)
- Manifests with permissions declaration
- Tenant installation with explicit consent
- Credential requirements (API keys, OAuth)
- Version compatibility matrix
- Enable/disable per tenant
- Full audit trail

**Examples:**
| Connector | Type | Description |
|-----------|------|-------------|
| OpenAI-compatible | AI provider | Any OpenAI-compatible API endpoint |
| Custom HTTP AI | AI provider | Custom AI service endpoint |
| Webhook | Notification | Custom HTTP webhook endpoint |
| Storage export | Storage | Custom S3-compatible storage |
| Notification | Notification | Custom notification service |

**Non-Goals:**
- No arbitrary code execution
- No unreviewed connectors
- No sandbox runtime (Level 5)

---

### Level 4 - Reviewed Plugin Marketplace

**Status:** Deferred

**Description:** Signed, reviewed plugin packages with scoped permissions.

**Characteristics:**
- Plugin manifest with declared capabilities
- Review status (pending, approved, rejected)
- Compatibility matrix (platform version, dependencies)
- Signed packages (cryptographic signature)
- Scoped permissions (explicit capability grants)
- Install/uninstall lifecycle
- Kill switch per plugin
- No arbitrary unreviewed code

**Non-Goals:**
- No unreviewed plugins
- No arbitrary code execution without sandbox
- No direct database access

---

### Level 5 - Sandbox / Container Runtime

**Status:** Deferred / Future

**Description:** Long-term sandboxed execution environment for untrusted code.

**Characteristics:**
- Sandbox functions (Wasm, container)
- Container plugins (Docker, Firecracker)
- Quotas (CPU, memory, storage, network)
- Network egress policy (allowlist)
- Timeout enforcement
- Logging and observability
- Secret isolation (vault integration)
- Security review required

**Non-Goals:**
- No arbitrary unsandboxed code
- No production secret exposure
- No unlimited resource access

---

## 4. Core Concepts

### 4.1 SystemAction

A named, auditable unit of work that the platform can execute.

```typescript
interface SystemAction {
  name: string;                    // e.g., "create_render_job"
  version: string;                 // semver
  inputSchema: JSONSchema;         // validated input
  outputSchema: JSONSchema;        // validated output
  permissions: string[];           // required permissions
  idempotent: boolean;             // safe to retry
  timeout: Duration;               // max execution time
}
```

### 4.2 ExtensionPoint

A stable contract that defines how providers can extend platform capabilities.

```typescript
interface ExtensionPoint {
  name: string;                    // e.g., "ai.transcribe"
  version: string;                 // semver
  inputSchema: JSONSchema;
  outputSchema: JSONSchema;
  providerTypes: ProviderType[];   // allowed provider types
  requiredPermissions: string[];
}
```

### 4.3 ExtensionProvider

An implementation of an ExtensionPoint.

```typescript
interface ExtensionProvider {
  id: string;                      // unique provider ID
  name: string;                    // display name
  extensionPoint: string;          // which ExtensionPoint
  type: ProviderType;              // built-in, http, byok, connector
  config: ProviderConfig;          // provider-specific config
  credentials?: CredentialRef;     // secret reference
  enabled: boolean;
  tenantScoped: boolean;
}
```

### 4.4 AutomationFlow

A config-defined workflow that chains system actions.

```typescript
interface AutomationFlow {
  id: string;
  name: string;
  trigger: AutomationTrigger;
  steps: AutomationStep[];
  enabled: boolean;
  tenantScoped: boolean;
}

interface AutomationTrigger {
  type: 'event' | 'schedule' | 'manual';
  config: TriggerConfig;
}

interface AutomationStep {
  action: string;                  // SystemAction name
  inputMapping: Record<string, string>;
  onError: 'fail' | 'skip' | 'retry';
  retryPolicy?: RetryPolicy;
}
```

### 4.5 ConnectorProvider

A marketplace-installable provider for external services.

```typescript
interface ConnectorProvider {
  id: string;
  name: string;
  version: string;
  manifest: ConnectorManifest;
  reviewStatus: 'pending' | 'approved' | 'rejected';
  signature: string;               // cryptographic signature
  permissions: Permission[];
  credentialRequirements: CredentialRequirement[];
}
```

### 4.6 PluginManifest

Declares a plugin's capabilities and requirements.

```typescript
interface PluginManifest {
  id: string;
  name: string;
  version: string;
  author: string;
  description: string;
  capabilities: PluginCapability[];
  permissions: Permission[];
  platformVersion: string;         // compatibility
  dependencies: PluginDependency[];
  reviewStatus: 'pending' | 'approved' | 'rejected';
  signature: string;
}
```

### 4.7 TenantProviderInstallation

Tracks which providers/plugins a tenant has installed.

```typescript
interface TenantProviderInstallation {
  tenantId: string;
  providerId: string;
  installedAt: DateTime;
  enabled: boolean;
  config: Record<string, unknown>;
  credentials: CredentialRef;
}
```

### 4.8 CredentialRef

A reference to a secret, never the raw secret value.

```typescript
interface CredentialRef {
  type: 'vault' | 'env' | 'db';
  path: string;                    // secret path or key
  version?: string;                // secret version
}
```

### 4.9 ArtifactRef

A reference to a platform artifact.

```typescript
interface ArtifactRef {
  id: string;
  type: 'render' | 'asset' | 'export';
  storageUri: string;
  tenantId: string;
}
```

---

## 5. Security Model

### 5.1 Tenant Isolation

| Aspect | Enforcement |
|--------|-------------|
| Data access | All queries scoped by tenant_id |
| Provider access | Providers installed per tenant |
| Action execution | Actions executed in tenant context |
| Artifact access | Artifacts scoped by tenant |

### 5.2 Permission Model

| Permission | Scope | Description |
|------------|-------|-------------|
| `system.action.execute` | System | Execute system actions |
| `automation.flow.manage` | Tenant | Create/edit/delete automation flows |
| `provider.install` | Tenant | Install connector providers |
| `plugin.install` | Tenant | Install marketplace plugins |
| `extensionpoint.register` | Platform | Register new extension points |

### 5.3 Input Validation

| Validation | Description |
|------------|-------------|
| Schema validation | All inputs validated against JSON Schema |
| Size limits | Max payload size enforced |
| Type checking | Strict type validation |
| Sanitization | HTML/SQL injection prevention |

### 5.4 Output Validation

| Validation | Description |
|------------|-------------|
| Schema validation | All outputs validated against JSON Schema |
| Size limits | Max response size enforced |
| Timeout | Execution timeout enforced |

### 5.5 Network Security

| Protection | Description |
|------------|-------------|
| SSRF protection | HTTP connectors blocked from localhost/private CIDR |
| DNS pinning | Per-request DNS resolution |
| Rate limits | Per-tenant, per-provider rate limits |
| Timeout | Connection and read timeouts |

### 5.6 Secret Management

| Rule | Description |
|------|-------------|
| Reference only | Secrets referenced by CredentialRef, never raw values |
| Vault integration | Secrets stored in HashiCorp Vault |
| Rotation | Secrets can be rotated without code changes |
| Audit | Secret access logged |

### 5.7 Audit Trail

| Event | Logged |
|-------|--------|
| Action execution | Yes |
| Provider installation | Yes |
| Plugin installation | Yes |
| Credential access | Yes |
| Configuration changes | Yes |
| Errors | Yes |

### 5.8 Kill Switch

| Scope | Description |
|-------|-------------|
| Global | Disable all external providers |
| Provider type | Disable all providers of a type |
| Individual provider | Disable specific provider |
| Tenant | Disable provider for specific tenant |

---

## 6. Current Status

### What Is Implemented

| Component | Status | Evidence |
|-----------|--------|----------|
| Internal render services | ✅ Exists | `RenderJobService`, `RenderOrchestratorService` |
| Internal artifact services | ✅ Exists | `ArtifactService`, `StorageService` |
| Internal notification services | ✅ Exists | `NotificationService` |
| Internal review services | ✅ Exists | `ReviewService`, `ReviewLinkService` |
| Extension module (PF4J) | ✅ Exists | `extension-module` with `ToolRegistry` |
| Sandbox runtime stub | ⚠️ Stub | `sandbox-runtime-module` exists but not implemented |

### What Is NOT Implemented

| Component | Status | Notes |
|-----------|--------|-------|
| Formal SystemAction registry | ❌ Not implemented | Actions exist as services, not formal registry |
| Automation flow engine | ❌ Not implemented | No workflow execution engine |
| ExtensionPoint SPI | ❌ Not implemented | No formal SPI contracts |
| Connector marketplace | ❌ Not implemented | No marketplace infrastructure |
| Plugin marketplace | ❌ Not implemented | No marketplace infrastructure |
| Sandbox runtime | ❌ Not implemented | Stub only |
| BYOK/custom AI provider | ❌ Roadmap | Not in platform-app runtime |
| Plugin security sandbox | ❌ Not implemented | No Wasm/container isolation |

### What Is Blueprint Only

| Component | Level | Notes |
|-----------|-------|-------|
| Capability opening model | All | This document is blueprint only |
| SystemAction formalization | Level 0 | Services exist, registry does not |
| Automation flows | Level 1 | Config-only workflows not implemented |
| ExtensionPoint SPI | Level 2 | Stable contracts not defined |
| Connector marketplace | Level 3 | Deferred |
| Plugin marketplace | Level 4 | Deferred |
| Sandbox runtime | Level 5 | Deferred / Future |

---

## 7. Non-Goals

### Early Phases (Level 0-2)

- ❌ Implement arbitrary code plugins
- ❌ Expose direct database access to plugins
- ❌ Expose production secrets to plugins
- ❌ Allow ungated marketplace installation
- ❌ Allow plugins to bypass tenant permissions
- ❌ Couple workflows directly to repositories
- ❌ Enable unreviewed connectors
- ❌ Enable unreviewed plugins

### Later Phases (Level 3-5)

- ❌ Allow unreviewed marketplace items
- ❌ Allow unsandboxed code execution
- ❌ Expose production secrets
- ❌ Allow unlimited resource access
- ❌ Skip security review

---

## 8. Relationship to Existing Docs

| Document | Relationship |
|----------|--------------|
| [module-blueprint-automation-plugin.md](module-blueprint-automation-plugin.md) | Automation is the orchestration layer for SystemActions |
| [module-blueprint-ai-provider.md](module-blueprint-ai-provider.md) | AI providers are ExtensionProviders (Level 2/3) |
| [automation-plugin-platform-roadmap.md](../../roadmap/automation-plugin-platform-roadmap.md) | Plugin marketplace roadmap (Level 4) |
| [ai-provider-ecosystem-roadmap.md](../../roadmap/ai-provider-ecosystem-roadmap.md) | AI provider ecosystem roadmap (Level 2/3) |
| [current-module-status.md](../current/current-module-status.md) | Current implementation status |
| [current-known-gaps.md](../current/current-known-gaps.md) | Known gaps in implementation |

---

## 9. Contract Skeleton Status

> **Contract skeleton exists in shared-kernel.** Runtime is not implemented.

### Implemented Contracts

The following contracts are defined in `shared-kernel/src/main/java/com/example/platform/shared/capability/`:

| Contract | Type | Status |
|----------|------|--------|
| `SystemAction` | Interface | ✅ Defined |
| `ExtensionPoint` | Interface | ✅ Defined |
| `ExtensionProvider` | Interface | ✅ Defined |
| `ProviderCapabilities` | Record | ✅ Defined |
| `AutomationFlow` | Record | ✅ Defined |
| `AutomationTrigger` | Record | ✅ Defined |
| `AutomationExecution` | Record | ✅ Defined |
| `CredentialRef` | Record | ✅ Defined |
| `ArtifactRef` | Record | ✅ Defined |
| `InvocationContext` | Record | ✅ Defined |
| `InvocationResult` | Record | ✅ Defined |
| `CapabilityStability` | Enum | ✅ Defined |
| `InvocationStatus` | Enum | ✅ Defined |
| `ProviderRuntimeType` | Enum | ✅ Defined |
| `FlowStatus` | Enum | ✅ Defined |
| `CapabilityErrorCode` | Enum | ✅ Defined |

### What Is NOT Implemented

| Component | Status | Notes |
|-----------|--------|-------|
| Runtime execution engine | ❌ Not implemented | Contracts only |
| Provider registry | ⚠️ Skeleton only | Registry exists, runtime execution not implemented |
| Automation flow engine | ❌ Not implemented | Contracts only |
| Marketplace infrastructure | ❌ Not implemented | Contracts only |
| Sandbox runtime | ❌ Not implemented | Contracts only |

### Registry Skeleton Status

The following registries are defined in `shared-kernel/src/main/java/com/example/platform/shared/capability/registry/`:

| Registry | Interface | Implementation | Status |
|----------|-----------|----------------|--------|
| `SystemActionRegistry` | ✅ Defined | `InMemorySystemActionRegistry` | ✅ Implemented |
| `ExtensionPointRegistry` | ✅ Defined | `InMemoryExtensionPointRegistry` | ✅ Implemented |
| `ExtensionProviderRegistry` | ✅ Defined | `InMemoryExtensionProviderRegistry` | ✅ Implemented |
| `CapabilityRegistryException` | ✅ Defined | — | ✅ Implemented |

### Registry Capabilities

| Capability | Status | Notes |
|------------|--------|-------|
| Registration | ✅ Implemented | Actions, extension points, providers |
| Lookup by key | ✅ Implemented | Find by action key, extension point key/version, provider id |
| List all | ✅ Implemented | Immutable list views |
| Duplicate detection | ✅ Implemented | Rejects duplicate keys |
| Validation | ✅ Implemented | Rejects blank keys |
| Provider lookup by extension point | ✅ Implemented | `findSupporting()` |
| Runtime execution | ❌ Not implemented | Registry only, no execution |
| Provider invocation | ❌ Not implemented | Registry only, no invocation |

### Contract Design Principles

1. **Stable vocabulary first** - Contracts define shared vocabulary
2. **No runtime execution yet** - Contracts only, no implementation
3. **Tenant-aware by design** - All contracts include tenant context
4. **Secret references only** - CredentialRef never exposes raw secrets
5. **Artifact references** - ArtifactRef uses logical URIs
6. **Idempotency key included** - InvocationContext includes idempotency key
7. **Timeout/deadline included** - InvocationContext includes deadline
8. **Audit fields included** - InvocationContext includes audit context

---

## References

- [Automation Plugin Blueprint](module-blueprint-automation-plugin.md)
- [AI Provider Blueprint](module-blueprint-ai-provider.md)
- [Automation Plugin Roadmap](../../roadmap/automation-plugin-platform-roadmap.md)
- [AI Provider Roadmap](../../roadmap/ai-provider-ecosystem-roadmap.md)
- [Current Module Status](../current/current-module-status.md)
- [Current Known Gaps](../current/current-known-gaps.md)
- [Contract Source](../../../shared-kernel/src/main/java/com/example/platform/shared/capability/)

---

## 10. Events, Hooks, Actions, and ExtensionPoints

> **Important distinction:** These are four different concepts with different purposes.

| Concept | Purpose | Example | Status |
|---------|---------|---------|--------|
| DomainEvent | Immutable fact that already happened | `asset.uploaded`, `render.completed` | ✅ Contract defined |
| HookPoint | Controlled lifecycle interception point | `render.before_create`, `asset.after_upload` | ✅ Contract defined |
| SystemAction | Callable platform operation | `render.create_job`, `media.generate_thumbnail` | ✅ Contract defined |
| ExtensionPoint | Provider-backed capability contract | `ai.transcribe`, `media.generate_thumbnail` | ✅ Contract defined |

### Event Contracts

Events are facts that already happened. They are immutable and should not block the original operation.

| Contract | Type | Status |
|----------|------|--------|
| `DomainEvent` | Record | ✅ Defined |
| `EventEnvelope` | Record | ✅ Defined |
| `EventSubscription` | Record | ✅ Defined |

**Event rules:**
- Events describe facts that already happened
- Events must not block the original operation
- Events are intended for automation triggers, webhook delivery, notification
- Event bus/runtime is not implemented

### Hook Contracts

Hooks are lifecycle interception points. They can allow or deny operations.

| Contract | Type | Status |
|----------|------|--------|
| `HookPoint` | Record | ✅ Defined |
| `HookHandler` | Interface | ✅ Defined |
| `HookInvocation` | Record | ✅ Defined |
| `HookResult` | Record | ✅ Defined |
| `HookPhase` | Enum | ✅ Defined |
| `HookDecision` | Enum | ✅ Defined |
| `HookFailurePolicy` | Enum | ✅ Defined |
| `HookHandlerCapabilities` | Record | ✅ Defined |

**Hook rules:**
- Hooks are lifecycle interception points
- Before hooks may allow or deny
- After hooks should not mutate core state in early phases
- External hook handlers are not implemented
- Hook runtime is not implemented

### Event Type Registry

Event types can be registered and discovered.

| Contract | Type | Status |
|----------|------|--------|
| `EventTypeDescriptor` | Record | ✅ Defined |
| `EventTypeRegistry` | Interface | ✅ Defined |
| `InMemoryEventTypeRegistry` | Class | ✅ Implemented |

**Registry capabilities:**
- Register event type descriptors
- Find by eventType and eventVersion
- List all event types
- Reject duplicate eventType/version
- Reject blank eventType/version
- Expose immutable list

**Location:** `shared-kernel/src/main/java/com/example/platform/shared/capability/registry/`

### Hook Point Registry

Hook points can be registered and discovered.

| Contract | Type | Status |
|----------|------|--------|
| `HookPointRegistry` | Interface | ✅ Defined |
| `InMemoryHookPointRegistry` | Class | ✅ Implemented |

**Registry capabilities:**
- Register hook points
- Find by hook key and phase
- List all hook points
- Reject duplicate hook key/phase
- Reject blank hook key
- Expose immutable list
- Preserve HookFailurePolicy

**Location:** `shared-kernel/src/main/java/com/example/platform/shared/capability/registry/`

### Flow Validation Skeleton

Automation flow definitions can be validated against registered capabilities.

| Contract | Type | Status |
|----------|------|--------|
| `AutomationFlowValidator` | Class | ✅ Implemented |
| `AutomationFlowValidationResult` | Record | ✅ Implemented |
| `AutomationFlowValidationIssue` | Record | ✅ Implemented |
| `AutomationFlowValidationSeverity` | Enum | ✅ Implemented |
| `AutomationFlowValidationCode` | Enum | ✅ Implemented |

**Validation rules:**
- Flow ID and tenant ID must exist
- Trigger must exist
- At least one node must exist
- Edge endpoints must reference known nodes
- Action nodes must reference registered SystemAction
- Extension point nodes must reference registered ExtensionPoint
- Hook nodes must reference registered HookPoint
- Event triggers must reference registered EventType
- Cycle detection for DAG validation
- Disconnected node warning

**Location:** `shared-kernel/src/main/java/com/example/platform/shared/capability/validation/`

### Built-in SystemAction Metadata Catalog

Built-in system actions provide metadata for first-party platform actions.

| Contract | Type | Status |
|----------|------|--------|
| `MetadataSystemAction` | Record | ✅ Implemented |
| `BuiltInSystemActions` | Class | ✅ Implemented |
| `SystemActionCategory` | Enum | ✅ Implemented |

**Built-in actions (12 total):**

| Category | Actions |
|----------|--------|
| RENDER | `render.create_job`, `render.generate_hls_preview` |
| MEDIA | `media.generate_proxy`, `media.generate_thumbnail`, `media.transcribe`, `media.extract_audio` |
| ARTIFACT | `artifact.export`, `artifact.tag` |
| REVIEW | `review.create_link`, `review.request_approval` |
| NOTIFICATION | `notification.send` |
| WEBHOOK | `webhook.send` |

**Important notes:**
- All actions are metadata-only (no execution logic)
- Actions can be registered into SystemActionRegistry
- NOTIFICATION/WEBHOOK flow node types map to SystemAction metadata
- HOOK node type is contract-level and not exposed to ordinary users

**Location:** `shared-kernel/src/main/java/com/example/platform/shared/capability/action/`

### System Action Execution Skeleton

Execution abstraction for system actions.

| Contract | Type | Status |
|----------|------|--------|
| `SystemActionExecutor` | Interface | ✅ Implemented |
| `SystemActionExecutionContext` | Record | ✅ Implemented |
| `SystemActionExecutionRequest` | Record | ✅ Implemented |
| `SystemActionExecutionResult` | Record | ✅ Implemented |
| `SystemActionExecutionStatus` | Enum | ✅ Implemented |
| `ValidatingSystemActionExecutor` | Class | ✅ Implemented |

**Execution capabilities:**
- Validate action exists in registry
- Validate request shape (basic)
- Support dry-run mode
- Return NOT_IMPLEMENTED for real execution
- No side effects

**Important stability note:**
STABLE means the metadata key/contract is stable. It does NOT mean runtime execution is implemented.

**Location:** `shared-kernel/src/main/java/com/example/platform/shared/capability/execution/`

### AutomationFlow Dry-Run Executor

Dry-run executor for automation flows.

| Contract | Type | Status |
|----------|------|--------|
| `AutomationFlowDryRunExecutor` | Class | ✅ Implemented |
| `AutomationFlowDryRunRequest` | Record | ✅ Implemented |
| `AutomationFlowDryRunResult` | Record | ✅ Implemented |
| `AutomationNodeDryRunResult` | Record | ✅ Implemented |
| `AutomationFlowDryRunStatus` | Enum | ✅ Implemented |
| `AutomationNodeDryRunStatus` | Enum | ✅ Implemented |

**Dry-run capabilities:**
- Validate flow first
- Process ACTION nodes through ValidatingSystemActionExecutor with dryRun=true
- Mark EXTENSION_POINT/HOOK nodes as NOT_IMPLEMENTED
- Map NOTIFICATION/WEBHOOK nodes to SystemAction metadata if registered
- Mark CONDITION/APPROVAL/DELAY nodes as SKIPPED
- Produce deterministic node result order
- No side effects

**Important notes:**
- Dry-run is validation/explain-plan only
- Real runtime still not implemented
- No events/hooks/providers are invoked
- No webhooks/notifications/render jobs are created
- Temporal/LiteFlow integration not implemented

**Location:** `shared-kernel/src/main/java/com/example/platform/shared/capability/flow/`

### Execution Trace Model

Execution trace model for automation flows.

| Contract | Type | Status |
|----------|------|--------|
| `AutomationExecutionTrace` | Record | ✅ Implemented |
| `AutomationNodeExecutionTrace` | Record | ✅ Implemented |
| `AutomationNodeExecutionAttempt` | Record | ✅ Implemented |
| `AutomationExecutionTraceStatus` | Enum | ✅ Implemented |
| `AutomationNodeExecutionTraceStatus` | Enum | ✅ Implemented |
| `AutomationDryRunTraceMapper` | Class | ✅ Implemented |

**Trace capabilities:**
- Represent dry-run results as explain-plan traces
- Preserve node order
- Preserve validation issues
- Support retry metadata (attempts)
- Include correlation/causation/idempotency ids
- Calculate execution duration

**Explain Plan concept:**
- Dry-run result / execution trace can be used by future UI to show what a flow would do before execution
- This does not execute real actions
- Supports progressive capability opening model

**Important notes:**
- Persistence still not implemented
- Real runtime still not implemented
- Temporal/LiteFlow integration not implemented

**Location:** `shared-kernel/src/main/java/com/example/platform/shared/capability/trace/`

### What Is NOT Implemented

| Component | Status | Notes |
|-----------|--------|-------|
| Event bus | ❌ Not implemented | Registry exists, event bus does not |
| Event runtime | ❌ Not implemented | Registry exists, runtime does not |
| Hook runtime | ❌ Not implemented | Registry exists, hook runtime does not |
| External hook handlers | ❌ Not implemented | Registry exists, external handlers do not |
| Event-backed automation | ❌ Not implemented | Registry exists, automation does not |
| Event publishing | ❌ Not implemented | Registry exists, publishing does not |
