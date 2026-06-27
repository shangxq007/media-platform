---
status: implementation-report
created: 2026-06-26
scope: render-module
truth_level: current
owner: platform
---

# Capability X2 — Metering & Attribution Foundation

## Implemented

### Domain Models
| Component | Purpose |
|-----------|---------|
| `MeterDescriptor` | Component declares measurable units: capability, category (4 types), meters[], units[] |
| `MeterEvent` | Immutable fact: meterName, quantity, unit, occurredAt, attribution, attributes{} |
| `MeterAttribution` | Multi-dimensional: tenantId, projectId, assetId, productId, producerId, backendId, environmentId |

### Service
| Component | Key Methods |
|-----------|-----------|
| `MeteringService` | `record(event)`, `recordAll(events)`, `recent(limit)`, `count()` |

## Meter Categories

| Category | Examples | Units |
|----------|---------|-------|
| INSTANT_USAGE | ASR, OCR, AI inference, Render request | minutes, seconds, requests |
| RESOURCE_OCCUPANCY | Storage, Database, Cache, GPU reservation | GB-hours |
| TRANSFER | Upload, Download, CDN, Object Storage | GB |
| RESERVED_CAPACITY | Worker, GPU, OpenCue, Node | instance-hours |

## Descriptor Examples

```java
MeterDescriptor.asr()  // INSTANT_USAGE: AUDIO_MINUTES, PROCESSING_DURATION, REQUEST_COUNT
MeterDescriptor.storage() // RESOURCE_OCCUPANCY: STORAGE_OCCUPANCY (GB-hours)
```

## Integration Points

| Caller | Integration |
|--------|-----------|
| Producer | `meteringService.record(MeterEvent.of("AUDIO_MINUTES", 5.0, "minutes", attribution))` |
| Backend | `meteringService.record(MeterEvent.of("GPU_SECONDS", 120, "seconds", attribution))` |
| Environment | `meteringService.record(MeterEvent.of("RENDER_SECONDS", 300, "seconds", attribution))` |
| Storage | `meteringService.record(MeterEvent.of("STORAGE_OCCUPANCY", 1024, "GB-hours", attribution))` |

## Governance Relationship

```
Metering → records facts
    ↓
Cost Attribution → reads metering data
    ↓
Pricing → versioned pricing models
    ↓
Billing → combines all (future)
```

Metering never depends on Pricing. Metering never depends on Billing.

## Architecture Validation

| Test | Result |
|------|--------|
| Single metering entry? | ✅ `MeteringService.record()` |
| Records facts only? | ✅ No pricing, no billing, no quotas |
| Multi-dimensional attribution? | ✅ 7 dimensions composable |
| Descriptor-driven? | ✅ Producers declare meters via `MeterDescriptor` |
| Kernel unchanged? | ✅ All 10 invariants satisfied |

## Remaining Work

| Item | Phase |
|------|-------|
| OpenMeter/Lago integration | Phase 3 |
| Persistent event storage | Phase 3 |
| Aggregated query API | Phase 4 |
| Provider-reported usage ingestion | Phase 4 |
