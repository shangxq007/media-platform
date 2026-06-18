---
status: blueprint
last_verified: 2026-06-18
scope: future
truth_level: target
owner: platform
---

# Module Blueprint: AI Provider Ecosystem

> **⚠️ BLUEPRINT ONLY** - Spring AI is isolated and not active in runtime. This describes the target design for future AI integration.

## 1. Purpose

The AI Provider Ecosystem enables integration with various AI/LLM providers for features like prompt execution, content generation, and intelligent automation.

## 2. Responsibilities

- AI provider abstraction and routing
- Prompt template management
- Prompt execution and monitoring
- Cost tracking and quota management
- Provider health monitoring

## 3. Non-Responsibilities

- Core rendering (separate module)
- User interface (frontend)
- Billing calculations (billing-module)

## 4. Public Ports / APIs

### Prompt API
- Prompt template CRUD
- Prompt execution
- Prompt history

### Provider API
- Provider registration
- Provider health checks
- Provider configuration

### Monitoring API
- Execution metrics
- Cost reports
- Usage analytics

## 5. Domain Model

### PromptTemplate
- id, name, version
- template_body, variables
- category, tags

### PromptExecution
- id, template_id
- provider, model
- input, output, tokens, cost

### AIProvider
- code, type, endpoint
- capabilities, limits
- health_status

## 6. Events Published

- `PromptExecuted` - When prompt runs
- `ProviderHealthChanged` - When provider status changes
- `QuotaExceeded` - When quota limit reached

## 7. Events Consumed

- `UserRequest` - For prompt execution
- `SystemTrigger` - For automated prompts

## 8. Dependencies Allowed

- `shared-kernel` - For common types
- `billing-module` - For cost tracking
- `identity-access-module` - For tenant context

## 9. Dependencies Forbidden

- Direct provider API calls from other modules
- Direct prompt execution (must use service)
- Direct cost calculations

## 10. Extension Points

- `AIProvider` interface - For new AI providers
- `PromptProcessor` interface - For prompt transformations
- `OutputValidator` interface - For output validation

## 11. Security / Tenant Rules

- Prompts are tenant-scoped
- Provider access controlled by entitlements
- Sensitive data redaction in logs
- Rate limiting per tenant

## 12. Persistence Ownership

- `prompt_template` table
- `prompt_template_version` table
- `prompt_execution_run` table
- `prompt_evaluation_result` table

## 13. Observability

- Metrics: execution count, token usage, cost
- Traces: prompt lifecycle, provider calls
- Logs: execution details, errors

## 14. Current Status

**Status: Isolated / Not Active**

### Current State
- Spring AI adapter exists but is **isolated**
- **Not active in runtime path**
- No OpenAI key required
- Stub provider active for development

### Why Isolated
- Security concerns with active AI in preview
- Cost control requirements
- Need for proper provider abstraction

## 15. Gap to Blueprint

| Blueprint Feature | Current Status | Gap |
|-------------------|----------------|-----|
| Active AI providers | Isolated stub | Critical |
| Multi-provider routing | Not implemented | Critical |
| Cost tracking | Not implemented | High |
| Prompt marketplace | Not implemented | High |
| BYOK support | Not implemented | Medium |

---

## 16. Relationship to Capability Opening Blueprint

See [Capability Opening Blueprint](capability-opening-blueprint.md) for the phased approach to system capability opening.

### AI Providers as ExtensionProviders

AI providers are a class of ExtensionProvider in the capability opening model:

| Capability Level | AI Provider Role | Description |
|------------------|------------------|-------------|
| Level 2 | ExtensionPoint / Provider SPI | AI capabilities (transcribe, translate, etc.) exposed as ExtensionPoints |
| Level 3 | Connector Marketplace | BYOK/custom HTTP AI providers as marketplace connectors |
| Level 4 | Plugin Marketplace | AI plugins with custom logic |
| Level 5 | Sandbox Runtime | Sandboxed AI functions |

### Key Clarifications

1. **AI providers are ExtensionProviders** - They implement ExtensionPoints like `ai.transcribe`, `ai.translate`, etc.
2. **BYOK/custom HTTP provider belongs to Level 2/3** - Built-in providers at Level 2, custom HTTP connectors at Level 3
3. **Spring AI remains isolated optional adapter** - Not active in platform-app runtime, remains in `spring-ai-adapter` module
4. **AI provider ecosystem must not require Spring AI in platform-app** - Platform can use AI providers via ExtensionPoint SPI without Spring AI dependency

### BYOK Support Path

| Phase | BYOK Capability | Status |
|-------|-----------------|--------|
| Level 2 | Built-in providers with API key config | Planned |
| Level 3 | Custom HTTP AI endpoint connector | Planned |
| Level 3 | OpenAI-compatible connector | Planned |
| Level 4 | Marketplace AI plugins | Deferred |

### Non-Goals

- ❌ AI providers should not require Spring AI in platform-app runtime
- ❌ AI providers should not expose production secrets
- ❌ AI providers should not bypass tenant isolation
- ❌ AI providers should not allow arbitrary code execution (Level 5 only)
