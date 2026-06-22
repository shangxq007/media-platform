---
status: blueprint
last_verified: 2026-06-22
scope: future
truth_level: target
owner: platform
---

# Platform Composition Blueprint

> **Reality Check (2026-06-22):** Temporal integration is implemented in workflow-module (20 files). LiteFlow integration exists in render-module (7 files). Contract skeletons are implemented in shared-kernel. Extension-module has 55 files with sandbox execution and CLI tools. Marketplace and plugin security sandbox remain unimplemented.

This document explains how core platform abstractions relate to each other and what users can compose.

## Purpose

The platform provides a layered architecture where:
1. **Users** compose high-level workflows using approved building blocks
2. **Platform** manages low-level execution, security, and infrastructure
3. **Providers** implement specific capabilities behind extension points

This blueprint clarifies boundaries and prevents users from bypassing safety mechanisms.

---

## Core Layering

### User-Facing Abstractions

| Abstraction | Description | Status |
|-------------|-------------|--------|
| **AutomationFlow** | User-configurable business/media automation | ✅ Contracts exist |
| **SystemAction** | Callable platform operation (12 built-in actions) | ✅ Metadata catalog exists |
| **AutomationTrigger** | Event/schedule that starts a flow | ✅ Contracts exist |
| **FlowNode** | Single step in an automation flow | ✅ Contracts exist |
| **FlowEdge** | Connection between flow nodes | ✅ Contracts exist |

### Internal Abstractions

| Abstraction | Description | Status |
|-------------|-------------|--------|
| **RenderPlan** | Internal media rendering DAG | ✅ Implemented |
| **ExtensionPoint** | Provider-backed capability contract | ✅ Contracts exist |
| **ExtensionProvider** | Implementation of an extension point | ✅ Contracts exist |
| **DomainEvent** | Immutable fact about something that happened | ✅ Contracts exist |
| **HookPoint** | Lifecycle interception point | ✅ Contracts exist |
| **ArtifactRef** | Media artifact reference (never raw URI) | ✅ Implemented |
| **CredentialRef** | Secret reference (never raw secret) | ✅ Implemented |

### Future Runtime

| Abstraction | Description | Status |
|-------------|-------------|--------|
| **Temporal** | Reliable long-running execution backend | ❌ Not implemented |
| **LiteFlow** | Internal rule/policy chain engine | ❌ Not implemented |
| **Event Bus** | Publish/subscribe for domain events | ❌ Not implemented |
| **Hook Runtime** | Execute hook handlers | ❌ Not implemented |

---

## Relationship Diagram

```
┌─────────────────────────────────────────────────────────────────────┐
│                         USER LAYER                                  │
│  ┌─────────────────────────────────────────────────────────────┐   │
│  │ AutomationFlow                                               │   │
│  │   └─ FlowNode[] ──→ SystemAction / ExtensionPoint / Hook    │   │
│  │   └─ FlowEdge[] ──→ DAG connections                         │   │
│  │   └─ AutomationTrigger ──→ Event / Schedule / Webhook       │   │
│  └─────────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────┐
│                      VALIDATION LAYER                               │
│  ┌─────────────────────┐  ┌─────────────────────┐                  │
│  │ AutomationFlowValidator │  │ ValidatingSystemActionExecutor │          │
│  │  └─ cycle detection  │  │  └─ action exists?   │                  │
│  │  └─ registry refs    │  │  └─ dry-run support  │                  │
│  │  └─ disconnected     │  │  └─ NOT_IMPLEMENTED  │                  │
│  └─────────────────────┘  └─────────────────────┘                  │
└─────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────┐
│                       EXECUTION LAYER                               │
│  ┌─────────────────────┐  ┌─────────────────────┐                  │
│  │ AutomationFlowDryRun │  │ Future: Temporal     │                  │
│  │ Executor             │  │ Runtime              │                  │
│  │  └─ explain-plan     │  │  └─ durable exec     │                  │
│  └─────────────────────┘  └─────────────────────┘                  │
│  ┌─────────────────────┐  ┌─────────────────────┐                  │
│  │ AutomationExecution  │  │ Future: LiteFlow     │                  │
│  │ Trace                │  │ Policy Chains        │                  │
│  │  └─ node traces      │  │  └─ quota/billing    │                  │
│  │  └─ attempts         │  │  └─ security         │                  │
│  └─────────────────────┘  └─────────────────────┘                  │
└─────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────┐
│                      CAPABILITY LAYER                               │
│  ┌──────────────────┐  ┌──────────────────┐  ┌──────────────────┐  │
│  │ SystemActionRegistry │  │ ExtensionPointRegistry │  │ EventTypeRegistry  │  │
│  └──────────────────┘  └──────────────────┘  └──────────────────┘  │
│  ┌──────────────────┐  ┌──────────────────┐                        │
│  │ HookPointRegistry    │  │ ExtensionProviderRegistry│                        │
│  └──────────────────┘  └──────────────────┘                        │
└─────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────┐
│                     IMPLEMENTATION LAYER                            │
│  ┌──────────────────┐  ┌──────────────────┐  ┌──────────────────┐  │
│  │ RenderPlan       │  │ ToolRouter       │  │ ArtifactCache    │  │
│  └──────────────────┘  └──────────────────┘  └──────────────────┘  │
│  ┌──────────────────┐  ┌──────────────────┐  ┌──────────────────┐  │
│  │ Provider Selection│  │ Policy Chains    │  │ Quota/Billing    │  │
│  └──────────────────┘  └──────────────────┘  └──────────────────┘  │
└─────────────────────────────────────────────────────────────────────┘
```

---

## What Users Can Compose

### Allowed Composition

Users can compose automation flows using these building blocks:

| Building Block | Example | Description |
|----------------|---------|-------------|
| **Triggers** | "When media uploaded", "Every Monday 9am" | Start a flow |
| **Conditions** | "If duration > 60min", "If format = MP4" | Branch logic |
| **Approved SystemActions** | `render.create_job`, `media.generate_proxy` | Platform operations |
| **Approved Connectors** | Webhook, Notification | External integrations |
| **Approvals** | "Wait for manager approval" | Human-in-the-loop |
| **Notifications** | "Send email when done" | User notifications |
| **Webhooks** | "POST to external URL" | External callbacks |

### Example User Flow

```
Trigger: When media uploaded (event: media.uploaded)
  │
  ├─ Condition: If format = video/*
  │    │
  │    ├─ Action: media.generate_proxy (dry-run supported)
  │    ├─ Action: media.generate_thumbnail (dry-run supported)
  │    └─ Action: notification.send ("Proxy ready")
  │
  └─ Condition: If format = image/*
       │
       └─ Action: artifact.tag ("needs-review")
```

---

## What Users Cannot Directly Compose

Users **cannot** bypass the SystemAction/ExtensionPoint layer to access:

| Restricted Resource | Reason |
|--------------------|--------|
| Raw repositories | Security: direct DB access |
| Database access | Security: SQL injection risk |
| Raw object storage URIs | Security: credential exposure |
| Arbitrary code | Security: sandbox not implemented |
| Low-level FFmpeg nodes | Safety: users use high-level actions |
| Ungated hooks | Safety: hooks require registration |
| Production secrets | Security: CredentialRef abstraction |
| Internal service URLs | Security: service mesh abstraction |

### Why These Restrictions Exist

1. **Security**: Direct access bypasses audit, quota, and authorization
2. **Safety**: Low-level access can corrupt data or leak credentials
3. **Composability**: High-level actions are testable and predictable
4. **Upgradability**: Platform can change internals without breaking user flows

---

## What Remains Internal

These are **never** exposed to ordinary users:

| Internal Component | Description |
|-------------------|-------------|
| **RenderPlan low-level DAG** | Internal rendering orchestration |
| **ToolRouter** | Routes requests to correct provider |
| **ArtifactCache internals** | Caching strategy and eviction |
| **Policy chains** | Quota, security, billing enforcement |
| **Provider selection** | Which provider handles a request |
| **Secrets** | Raw credentials (only CredentialRef exposed) |
| **Quota/billing enforcement** | Rate limiting, cost tracking |

### Internal-Only Access

Platform administrators and internal services may access these through:
- Admin APIs (with authentication)
- Internal service-to-service calls (with mTLS)
- Direct database access (with audit logging)

---

## Current Status

### Implemented Contracts

| Contract | Status | Location |
|----------|--------|----------|
| SystemAction | ✅ Implemented | `shared-kernel/.../capability/SystemAction.java` |
| ExtensionPoint | ✅ Implemented | `shared-kernel/.../capability/ExtensionPoint.java` |
| ExtensionProvider | ✅ Implemented | `shared-kernel/.../capability/ExtensionProvider.java` |
| DomainEvent | ✅ Implemented | `shared-kernel/.../capability/DomainEvent.java` |
| HookPoint | ✅ Implemented | `shared-kernel/.../capability/HookPoint.java` |
| AutomationFlow | ✅ Implemented | `shared-kernel/.../capability/AutomationFlow.java` |
| AutomationTrigger | ✅ Implemented | `shared-kernel/.../capability/AutomationTrigger.java` |

### Implemented Registries

| Registry | Status | Location |
|----------|--------|----------|
| SystemActionRegistry | ✅ Implemented | `shared-kernel/.../capability/registry/SystemActionRegistry.java` |
| ExtensionPointRegistry | ✅ Implemented | `shared-kernel/.../capability/registry/ExtensionPointRegistry.java` |
| ExtensionProviderRegistry | ✅ Implemented | `shared-kernel/.../capability/registry/ExtensionProviderRegistry.java` |
| EventTypeRegistry | ✅ Implemented | `shared-kernel/.../capability/registry/EventTypeRegistry.java` |
| HookPointRegistry | ✅ Implemented | `shared-kernel/.../capability/registry/HookPointRegistry.java` |

### Implemented Validation & Execution

| Capability | Status | Location |
|------------|--------|----------|
| AutomationFlowValidator | ✅ Implemented | `shared-kernel/.../capability/validation/AutomationFlowValidator.java` |
| ValidatingSystemActionExecutor | ✅ Implemented | `shared-kernel/.../capability/execution/ValidatingSystemActionExecutor.java` |
| AutomationFlowDryRunExecutor | ✅ Implemented | `shared-kernel/.../capability/flow/AutomationFlowDryRunExecutor.java` |
| Execution trace model | ✅ Implemented | `shared-kernel/.../capability/trace/` |
| Built-in SystemActions | ✅ Implemented | `shared-kernel/.../capability/action/BuiltInSystemActions.java` |

### Not Implemented

| Capability | Status | Reason |
|------------|--------|--------|
| Real runtime execution | ❌ Not implemented | Skeleton only |
| Execution persistence | ❌ Not implemented | No Flyway migration |
| Event bus | ❌ Not implemented | Contracts only |
| Hook runtime | ❌ Not implemented | Contracts only |
| Marketplace | ❌ Not implemented | Future consideration |
| Sandbox runtime | ❌ Not implemented | Security concerns |
| Temporal integration | ❌ Not implemented | Future consideration |
| LiteFlow integration | ❌ Not implemented | Future consideration |

---

## Non-Goals

The platform explicitly does **not** aim to:

1. **Expose raw media DAG to ordinary users**
   - Users compose with SystemActions, not FFmpeg commands
   - RenderPlan is internal orchestration

2. **Expose arbitrary plugin code in early phases**
   - Sandbox runtime not implemented
   - Only curated SystemActions allowed

3. **Turn platform into generic n8n clone**
   - Focus on media workflows
   - Not general-purpose automation

4. **Make all actions Temporal-first**
   - Simple actions are synchronous
   - Only long-running flows use Temporal (future)

5. **Expose LiteFlow to users**
   - LiteFlow is for internal policy chains
   - Users compose with AutomationFlow UI

---

## Design Principles

1. **Progressive disclosure**: Simple things simple, complex things possible
2. **Security by default**: No arbitrary code execution
3. **Abstraction over exposure**: High-level contracts, not low-level tools
4. **Composability**: Building blocks that work together
5. **Testability**: Dry-run and explain-plan before execution
6. **Auditability**: Execution traces for every flow run
