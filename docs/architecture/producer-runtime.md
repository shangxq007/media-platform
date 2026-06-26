---
status: blueprint
created: 2026-06-25
scope: platform-wide
truth_level: target
owner: platform
---

# Producer Runtime & Build Graph Blueprint

> **Predecessors:** [Product Runtime](product-runtime.md), [Execution Planner](execution-planner.md), [Storage Runtime](storage-runtime.md)
> **Linked ADR:** [ADR-012](adr/ADR-012-producer-runtime.md)

## 1. Core Architecture Decision

Every processing component is a Producer. Producer consumes Products, produces Products. Execution Planner plans Producer Graph, not Task Graph.

## 2. Producer Definition

```
Producer {
    producerId: String
    producerType: AI | CREATIVE | MUTATION | MEDIA | PROJECTION | PACKAGING | INFRASTRUCTURE
    displayName: String
    version: String
    capabilities: List<String>
    supportedInputTypes: List<String>
    supportedOutputTypes: List<String>
    executionMode: SYNCHRONOUS | ASYCHRONOUS | BATCH | STREAMING
    backendRequirements: List<String>
    priority: int (0-100, default 50)
}
```

Producer NEVER owns: storage, timeline, execution plan, product registry.

## 3. Producer Categories

| Category | Examples | Input | Output |
|----------|---------|-------|--------|
| AI Producer | Whisper, OCR, Vision, Embedding | Raw Media | Transcript, OCR, Vision Metadata, Embedding |
| Creative Producer | Creative Planner | Transcript, Vision, Embedding | TimelineEditPlan |
| Mutation Producer | TimelineMutationService | TimelineEditPlan | TimelineRevision |
| Media Producer | BMF, FFmpeg, Remotion, MLT | Media Product | Transcode, Filter, Render |
| Projection Producer | Search Reindexer, Marketplace Builder | Transcript, Render | Search Projection, Marketplace Listing |
| Packaging Producer | Marketplace Package Builder | Preview, Thumbnail | Marketplace Package |
| Infrastructure Producer | ExecutionBackend | Execute | ExecutionResult |

## 4. Producer Contract

```
Input Products → Producer → Output Products

Producer MUST declare:
  - required inputs (productTypes)
  - optional inputs
  - output products (productTypes)
  - supported capabilities
  - execution hints (synchronous, batch, streaming)
```

## 5. Producer Graph

```
Raw Media
  → Whisper (AI Producer) → Transcript
    → Embedding (AI Producer) → Embedding Reference
  → Creative Planner (Creative Producer) → TimelineEditPlan
    → TimelineMutation (Mutation Producer) → TimelineRevision
      → BMF Render (Media Producer) → Final Render
        → Marketplace Builder (Packaging Producer) → Marketplace Package
```

Execution Planner plans Producer Graph.

## 6. Relationship to Product Runtime

Product Runtime owns Products. Producer Runtime owns Producers. Producer Graph connects Products via Producer edges.

## 7. Relationship to Execution Planner

Execution Planner resolves: Product requirements → Producer candidates → Provider → ExecutionBackend → ExecutionPlan.

Planner does NOT know implementation details.

## 8. Relationship to Provider Runtime

Provider Runtime is one Producer implementation strategy. WhisperProviderExtension implements AI Producer. BMF Provider implements Media Producer.

## 9. Relationship to Storage Runtime

Producer never accesses storage directly. Producer requests Product materialization. Storage Runtime decides cache / stream / download.

## 10. Architecture Rules

1. Everything transforming Products is a Producer
2. Producer never owns Product lifecycle
3. Producer never owns Storage
4. Execution Planner plans Producers
5. Storage Runtime materializes Products
6. Product Runtime tracks lineage
7. ExecutionBackend executes Producers
