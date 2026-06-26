---
status: blueprint
created: 2026-06-26
scope: platform-wide
truth_level: target
owner: platform
---

# Execution Environment Architecture

> **Linked ADR:** [ADR-014](adr/ADR-014-execution-environment.md)
> **Parent:** [Platform Kernel Baseline](platform-kernel.md)

## 1. Execution Backend vs Execution Environment

| Execution Backend | Execution Environment |
|-------------------|----------------------|
| **What** media processing capability | **Where/How** execution occurs |
| BMF, FFmpeg, Remotion, MLT, Blender | Local, OpenCue, Kubernetes, Ray, Lambda |
| Graph execution, operator scheduling | Worker selection, container, node, queue, retry |
| Owned by BackendCompiler + ExecutionSpec | Owned by Environment layer |
| Platform Stable API v1.0 | Architecture blueprint (not yet implemented) |

**They are orthogonal.** BMF may execute on Local or OpenCue. FFmpeg may execute on Local or Kubernetes. No one-to-one relationship.

## 2. Execution Model

```
ExecutionPlan
    ↓
BackendCompiler → BackendExecutionSpec
    ↓
Execution Environment (selects worker, schedules, isolates)
    ↓
Execution Backend (executes media processing)
    ↓
ExecutionResult
```

## 3. Environment Types

| Environment | Scheduler | Isolation | Phase |
|------------|-----------|-----------|-------|
| **Local Process** | None (direct) | OS process | ✅ V1 |
| **OpenCue** | OpenCue Job/Frame | OpenCue RQD | Phase 1 |
| **Kubernetes** | K8s Job | Pod/Container | Phase 2 |
| **Ray** | Ray Task | Ray Actor | Phase 3 |
| **Cloud Render** | Cloud-native | VM/Container | Phase 3 |
| **Serverless** | Lambda/Function | Sandbox | Phase 4 |

## 4. Worker Model (Future)

```yaml
Worker:
  workerId
  capabilities: [FFmpeg, BMF, Remotion]
  resources: {cpu: 8, memory: 32GB, gpu: true}
  environment: LOCAL | KUBERNETES | OPENCUE
  status: ONLINE | BUSY | OFFLINE
  heartbeat: timestamp
  registration: timestamp
```

Workers advertise capabilities. Environment selects matching workers.

## 5. Capability Matching

```
ExecutionBackend capability (FFmpeg, BMF, Remotion)
    ×
ExecutionSpec requirements (GPU, memory, timeout)
    ×
Worker advertisements (capabilities, resources)
    ↓
Environment selects worker
```

## 6. Backend × Environment Matrix

| Backend | Local | OpenCue | Kubernetes | Cloud |
|---------|-------|---------|-----------|-------|
| **BMF** | ✅ V1 | Phase 1 | Phase 2 | Phase 3 |
| **FFmpeg** | ✅ V1 | Phase 1 | Phase 2 | Phase 3 |
| **Remotion** | Phase 2 | Phase 3 | Phase 3 | Phase 4 |
| **MLT** | Phase 2 | Phase 3 | Phase 3 | Phase 4 |
| **Blender** | Phase 3 | Phase 3 | Phase 3 | Phase 4 |

## 7. Relationship to Kernel

Execution Environment is a NEW layer in the platform, sitting between Compilation and Execution:

```
Platform Kernel v1.0 Layers (updated):
  Planning Layer:      Execution Planner, Capability Resolution, Capability Catalog
  Capability Layer:    Producer Runtime, Producer SPI
  Compilation Layer:   Backend Compiler Runtime, BackendCompiler SPI, BackendExecutionSpec
  Environment Layer:   Execution Environment (NEW — selects worker, schedules, isolates)
  Execution Layer:     Execution Pipeline, Execution Backend, ExecutionBackend SPI
  Runtime Layer:       Product Runtime, Product Graph, Storage Runtime
  Storage Layer:       StorageReference, StorageRuntimeService
```

## 8. OpenCue Positioning

OpenCue is an **Execution Environment**, not an Execution Backend. OpenCue provides scheduling, job management, frame dispatch — it does NOT provide media processing (BMF/FFmpeg does that). OpenCue integrates as an Environment, wrapping the Backend.

## 9. Future Evolution

| Phase | Environment | Backend |
|-------|-----------|---------|
| V1 (current) | Local Process only | BMF, FFmpeg |
| Phase 1 | OpenCue | BMF, FFmpeg |
| Phase 2 | Kubernetes | BMF, FFmpeg, Remotion, MLT |
| Phase 3 | Ray, Cloud Render | Blender, Cloud AI |
| Phase 4 | Serverless | All backends |
