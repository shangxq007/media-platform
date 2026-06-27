---
status: blueprint
created: 2026-06-27
scope: platform-wide
truth_level: target
owner: platform
---

# Public Capability Composition

## 1. User-Defined Capabilities

Users may register as Public Capabilities: own LLM, REST Service, AI Model, OCR, Vision, Render, Storage, Search, Recommendation.

Treated as Public Capabilities — NEVER as internal components. Use the same Capability Resolution flow.

## 2. Script Node

Script receives Products, produces Products. Supports: JavaScript, Python, WASM, Container. Never accesses Platform internals. Always sandboxed.

## 3. Extended Public Capability Descriptor

| Field | Description |
|-------|-------------|
| capabilityId | Unique identifier |
| inputSchema | JSON Schema for input validation |
| outputSchema | JSON Schema for output validation |
| executionMode | SYNC, ASYNC, STREAMING |
| supportedProductTypes | Input/output Product types |
| visibility | PUBLIC, PRIVATE, BETA |
| trustLevel | FULLY_TRUSTED, SEMI_TRUSTED, UNTRUSTED |
| idempotency | IDEMPOTENT, NOT_IDEMPOTENT |
| cancellation | SUPPORTED, NOT_SUPPORTED |
| rateLimits | Max requests/time |
| securityProfile | Required permissions |
| slaProfile | STANDARD, PREMIUM |
| version | Semantic version |
| meterDeclaration | Declared meters |

## 4. Composition Example

```
Input: Raw Media Product
    ↓ [ASR Capability] → Transcript Product
    ↓ [Summary Script] → Summary Product
    ↓ [Clip Selection] → Clip List Product
    ↓ [Timeline Capability] → Timeline Product
    ↓ [Render Capability] → Preview Product
    ↓ [Publish Capability] → Published Product
```

Each arrow = Product dependency. Platform resolves Producers, Backends, and Environments automatically.
