---
status: blueprint
created: 2026-06-26
scope: platform-wide
truth_level: target
owner: platform
---

# Unified Component Descriptor Architecture

> **Linked ADR:** [ADR-019](adr/ADR-019-unified-component-descriptor.md)
> **Related:** [Platform Governance](platform-governance.md), [Capability Catalog](capability-catalog.md)

## 1. Core Principle

Every extensible component is self-describing. Metadata belongs to the component. Platform aggregates metadata, never owns it. Platform behavior should prefer metadata over hardcoded branching.

## 2. Unified Component Descriptor Model

Every extensible component declares:

| Dimension | Description | Example |
|-----------|-------------|---------|
| **Identity** | componentId, name, version, vendor, description | `whisper-asr`, `Whisper ASR`, v1.0 |
| **Capability** | Supported capabilities | ASR, TRANSCRIBE, LANGUAGE_DETECTION |
| **Resource** | CPU, memory, GPU, disk requirements | cpu: 1, memoryMb: 2048, gpu: 0 |
| **Metering** | Declared measurable units (never prices) | audio-minutes, render-seconds, gpu-seconds |
| **Security** | trust level, permissions, sandbox | FULLY_TRUSTED, LOCAL_ONLY |
| **Configuration** | Supported options, defaults, validation | model: base, language: en |
| **Observability** | Metrics, tracing, logging tags | provider=whisper, model=${model} |
| **Health** | Health endpoint, readiness, diagnostics | /health, /ready |

## 3. Component Specializations

| Component Type | Descriptor Type | Special Fields |
|---------------|-----------------|----------------|
| Producer | ProducerDescriptor | supportedOutputTypes, requiredCapabilities |
| Backend | BackendDescriptor | backendType, supportedCapabilities |
| Execution Environment | EnvironmentDescriptor | environmentType, scheduling hints |
| Storage Provider | StorageDescriptor | providerType, access strategies |
| Backend Compiler | CompilerDescriptor | supportedBackendTypes, outputSpec |

All are specializations of the same conceptual model. Identity + Capability + Resource + Metering are common across all.

## 4. Metadata Flow

```
Plugin → Self Descriptor → Platform Aggregation → Effective Metadata → Governance → Execution
```

No centralized registration. Discovery via existing Spring auto-discovery. Platform never owns component metadata.

## 5. Relationship to Governance

Governance consumes descriptors (read-only):
- Metering → reads `MeterDescriptor` (declared units)
- Access Control → reads Resource + Security (trust, permissions)
- Policy → reads Capabilities (feature availability)
- Observability → reads ObservabilityDescriptor (metrics, tags)

Governance never modifies descriptors. Governance never owns component metadata.

## 6. Relationship to Capability Catalog

Capability Catalog focuses on capability resolution (which producer handles ASR?). Component Descriptor focuses on component metadata (what resources does Whisper need?). Capability is one dimension of a Component Descriptor. Catalog is not replaced — it is complemented.

## 7. Architecture Principles

1. Every extensible component is self-describing
2. Metadata belongs to the component, not the platform
3. Platform aggregates metadata, never owns it
4. Governance consumes metadata (read-only)
5. Metadata is declarative — no execution logic
6. Commercial behavior is never declared by plugins
7. Component identity is immutable
8. Platform behavior should prefer metadata over hardcoded branching
