---
status: reserved
created: 2026-06-28
scope: render-module
truth_level: design
owner: platform
---

# Provider Execution Document Model Reservation

## Purpose

Reserve the internal model layer for provider-specific execution documents without implementing provider binding or execution.

## What ProviderExecutionDocument Is

`ProviderExecutionDocument` is an **internal-only** model representing provider-specific execution instructions.

It sits between the compile pipeline and actual execution:

```text
TimelineRevision
→ NormalizedTimeline
→ ArtifactDependencyGraph
→ LogicalCapabilityGraph
→ [future: ProviderBindingPlan]
→ ProviderExecutionDocument  ← this reservation
→ [future: RenderExecutionPlan]
→ [future: actual execution]
```

## What ProviderExecutionDocument Is NOT

- It is **not** canonical Timeline data
- It is **not** ArtifactDependencyGraph
- It is **not** LogicalCapabilityGraph
- It is **not** a public API model
- It is **not** persisted in the database
- It must **not** be exposed in public APIs

## Provider-Specific Execution Document Examples

Each provider would produce its own execution document format:

| Provider | Execution Document | Format |
|----------|-------------------|--------|
| FFmpeg | FFmpegCommandPlan | List of CLI args, filter graph |
| MLT | MltProjectDocument | .mlt XML |
| Remotion | RemotionInputPropsDocument | React component props JSON |
| Blender | BlenderSceneSpec | Controlled .blend or Python script |
| Natron | NatronProjectSpec | .ntp or controlled Python |
| GPAC | PackagingPlan | MP4Box args |
| GStreamer | GStreamerPipelineSpec | Pipeline description |
| OpenFX | EffectDescriptor / ParameterSchema | Effect metadata only (not executable) |

## Reserved Model Classes

The following internal model classes are reserved for future implementation:

```java
// Internal marker interface for provider-specific execution documents
public interface ProviderExecutionDocument {
    String providerCode();
    String documentType();
    Map<String, Object> metadata();
}

// Types of execution documents
public enum ProviderExecutionDocumentType {
    CLI_COMMAND,
    XML_PROJECT,
    JSON_PROPS,
    BINARY_FILE,
    PIPELINE_SPEC,
    EFFECT_DESCRIPTOR
}

// Metadata common to all execution documents
public record ProviderExecutionDocumentMetadata(
        String providerCode,
        String providerVersion,
        String capabilityGraphId,
        String artifactGraphId,
        Instant createdAt,
        Map<String, String> labels
) {}
```

## Design Principles

1. **Provider-specific**: Each provider defines its own document format
2. **Internal-only**: Never exposed in public APIs
3. **Immutable**: Once created, not modified
4. **Traceable**: Carries metadata linking back to capability and artifact graphs
5. **Validated**: Provider validates document before execution

## Relationship to Compile Pipeline

```text
LogicalCapabilityGraph
  → ProviderBindingPlan (future: selects provider per node)
    → ProviderExecutionDocumentGenerator (future: creates provider-specific docs)
      → ProviderExecutionDocument (this reservation)
        → RenderExecutionPlan (future: orchestrates execution)
```

## Non-Goals

- No provider binding implementation in this task
- No command generation implementation
- No public API exposure
- No local path/materialized path exposure outside internal execution context
- No database persistence of execution documents

## Related Documents

- `docs/review/timeline-compile-contract-v0.md` — Compile contract v0
- `docs/review/multi-provider-poc-integration-report.md` — Provider integration pattern
- `docs/review/openfx-capability-model-reservation.md` — OpenFX as capability model
- `docs/architecture/blueprint/otio-render-platform-blueprint.md` — Full blueprint
