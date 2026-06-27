---
status: blueprint
created: 2026-06-27
scope: platform-wide
truth_level: target
owner: platform
---

# Public Capability Architecture

## 1. Public Capability Model

External consumers interact with **Capabilities**, not internal components. A Capability is a public-facing abstraction over internal Producers, Backends, Environments, and Compilers.

## 2. Public Capability Descriptor (Conceptual)

| Field | Description |
|-------|-------------|
| capabilityId | Unique capability identifier |
| displayName | Human-readable name (e.g., "Transcribe Audio") |
| inputProductTypes | Accepted input types (AUDIO, VIDEO) |
| outputProductTypes | Produced output types (TRANSCRIPT) |
| supportedFormats | Input/output format support |
| executionMode | SYNCHRONOUS, ASYNCHRONOUS |
| permissions | Required access permissions |
| meterDeclaration | Declared meters (never prices) |
| version | Capability version |
| visibility | PUBLIC, PRIVATE, BETA |
| slaProfile | STANDARD, PREMIUM |
| idempotency | IDEMPOTENT, NOT_IDEMPOTENT |
| cancellation | SUPPORTED, NOT_SUPPORTED |
| webhookEvents | Event types emitted |
| stability | STABLE, BETA, DEPRECATED |

## 3. Capability Resolution Flow

```
External Request (capabilityId + input products)
    ↓
Public Capability (descriptor match)
    ↓
Capability Resolution (productType → capability → producer)
    ↓
Producer (internal implementation)
    ↓
Backend (execution location)
    ↓
Environment (execution context)
    ↓
Storage (input materialization, output storage)
    ↓
Product (output registered, dependency linked)
```

Callers never choose implementation directly. Platform owns implementation selection. Callers may express preferences only.

## 4. Capability Categories

| Category | Examples |
|----------|---------|
| Understanding | ASR, OCR, Vision, Embedding |
| Generation | Rendering, Timeline, Packaging |
| Storage | Upload, Download, Archive |
| Cloud | S3, GCS, CDN |
| Planning | Execution planning |
| Governance | Access control, metering |
| Marketplace | Listing, install |

Capabilities remain independent. Categories are organizational — not runtime.

## 5. Public vs Internal Boundary

| Public API | Internal SPI |
|-----------|-------------|
| Capability | Producer |
| Job | ExecutionJob |
| Product | Product (domain model shared) |
| Timeline | TimelineRevision |
| Metering | MeterEvent |

Internal SPIs (Producer, BackendCompiler, ExecutionEnvironment, StorageProvider) are never exposed directly to external consumers.

## 6. Related Documents

| Document | Purpose |
|----------|---------|
| [ADR-020](adr/ADR-020-public-capability-architecture.md) | Formal ADR |
| [Platform Positioning](platform-positioning.md) | Platform vision |
| [Platform Constitution](platform-constitution-v1.md) | Frozen architecture |
