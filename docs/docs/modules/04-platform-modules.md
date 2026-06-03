# Platform Services Modules

> **Last Updated:** 2026-05-18

## prompt-module

**Status:** ✅ Implemented

Prompt template management with safety governance.

| Feature | Status | Notes |
|---------|--------|-------|
| Template CRUD | ✅ | Create, read, update, delete |
| Versioning | ✅ | Template version history |
| Variable substitution | ✅ | `{{variable}}` syntax |
| Template rendering | ✅ | Render with context |
| Safety governance | ✅ | Content safety checks |
| In-memory storage | 🔧 | ConcurrentHashMap (not persisted) |

**Dependencies:** `shared-kernel`

**REST API:** `/api/v1/prompts/*`

## extension-module

**Status:** ✅ Implemented

Dynamic extension platform with PF4J plugin system.

| Feature | Status | Notes |
|---------|--------|-------|
| Plugin lifecycle | ✅ | Load, start, stop, unload |
| Tool registry | ✅ | Executable allowlist |
| CLI tool execution | ✅ | Configuration-driven |
| Trust levels | ✅ | FULLY_TRUSTED, SEMI_TRUSTED, UNTRUSTED |
| Canary routing | ✅ | Percentage-based traffic splitting |
| Resource limits | ✅ | Concurrency, memory, CPU, I/O |
| Rollback | ✅ | Version rollback, routing rollback |
| Audit | ✅ | 15+ event types |
| Apache Commons Exec | ⚠️ | Still present for CLI tools |

**Dependencies:** `shared-kernel`

**REST API:** `/api/v1/extensions/*`

## sandbox-runtime-module

**Status:** ✅ Implemented (Placeholder)

Sandbox execution environment for untrusted scripts.

| Feature | Status | Notes |
|---------|--------|-------|
| Groovy support | ✅ | If Groovy on classpath |
| JavaScript support | ✅ | If Nashorn/GraalJS on classpath |
| Wasm support | 📋 Future | Wasmtime/Wasmer planned |
| Security policy | ✅ | Blocks Runtime, File, Socket, etc. |
| Default disabled | ✅ | Must be explicitly enabled |

**Dependencies:** None

**REST API:** `/api/v1/sandbox/*`

## federation-query-module

**Status:** ✅ Implemented

GraphQL query aggregation layer and NLQ assistant.

| Feature | Status | Notes |
|---------|--------|-------|
| GraphQL schema | ✅ | 12+ query types |
| DataLoader batching | ✅ | N+1 query prevention |
| REST fallback | ✅ | REST controllers as fallback |
| Query limits | ✅ | Depth, complexity, page size |
| Audit interception | ✅ | All queries audited |
| Data redaction | ✅ | PII field redaction |
| NLQ assistant | ✅ | Natural language → SQL |
| SQL safety validation | ✅ | 10 safety rules |
| Scope isolation | ✅ | Tenant/workspace/user scoping |
| Chart suggestions | ✅ | Auto chart type recommendation |

**Dependencies:** `shared-kernel`

**REST API:** `/api/v1/analytics/nlq/*`, `/api/v1/analytics/reports/*`
**GraphQL:** `/graphql`

## cloud-resource-module

**Status:** ✅ Implemented

Cloud resource provider catalog.

| Feature | Status | Notes |
|---------|--------|-------|
| Resource definitions | ✅ | CloudResourceDefinition CRUD |
| Provider catalog | ✅ | Multi-cloud resource types |

**Dependencies:** `shared-kernel`
