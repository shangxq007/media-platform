---
status: roadmap
created: 2026-06-26
scope: platform-wide
owner: platform
---

# Platform Roadmap

## Architecture Releases (Complete)

| Release | Scope | Status |
|---------|-------|--------|
| A1 | Runtime & Registry Governance | ✅ Frozen |
| A1.1 | Extension Decision Guide | ✅ Frozen |
| A2 | Execution Planner Blueprint | ✅ Frozen |
| A3 | Creative Planner Blueprint | ✅ Frozen |
| A4 | Artifact Runtime Blueprint | ✅ Frozen |
| A4.1 | Product Runtime Refinement | ✅ Frozen |
| A5 | Storage Runtime Blueprint | ✅ Frozen |
| A5.1 | Producer Runtime Blueprint | ✅ Frozen |
| **B1** | **Platform Kernel Baseline 1.0** | **✅ Frozen** |

## Foundation Releases (Complete)

| Foundation | Scope | Status |
|-----------|-------|--------|
| F1 | Product Runtime Foundation | ✅ |
| F2 | Product Graph Foundation | ✅ |
| F3 | Storage Runtime Foundation | ✅ |
| F4 | Producer Runtime Foundation | ✅ |
| F5 | Producer Runtime Integration | ✅ |
| F6 | Execution Planner Foundation | ✅ |
| F7 | Planner Product Graph Integration | ✅ |
| F8 | Backend Selection Foundation | ✅ |
| F9 | Backend Execution Model | ✅ |
| F10 | Backend Compiler Runtime | ✅ |
| F11 | Execution Pipeline Foundation | ✅ |
| F12 | Local Compiler Integration | ✅ |
| F13 | BMF Backend Compiler | ✅ |
| F14 | Execution Spec Specialization | ✅ |
| F15 | Capability Resolution | ✅ |
| C1 | Capability Catalog | ✅ |

## Architecture Decisions (Complete)

| ADR | Scope | Status |
|-----|-------|--------|
| ADR-001–004 | Runtime, Registry, Provider, Execution | ✅ |
| ADR-005 | Extension Decision Guide | ✅ |
| ADR-006 | BMF Integration | ✅ |
| ADR-007 | Execution Planner | ✅ |
| ADR-008 | Creative Planner | ✅ |
| ADR-009 | Artifact Runtime | ✅ |
| ADR-010 | Product Runtime + PostgreSQL | ✅ |
| ADR-011 | Storage Runtime | ✅ |
| ADR-012 | Producer Runtime | ✅ |
| ADR-013 | Platform Kernel Baseline | ✅ |
| **ADR-014** | **Execution Environment (Backend ≠ Environment)** | **✅** |

## Capability Extensions (Future — Not Kernel Work)

### Execution Environments (NEW — separate from Backends)

> Per ADR-014: Backend = WHAT media processing. Environment = WHERE/HOW execution occurs.

| Environment | Description | Phase |
|------------|-------------|-------|
| Local Process | Direct subprocess (V1) | ✅ |
| OpenCue | Render farm scheduling, job/frame dispatch | Phase 1 |
| Kubernetes | K8s Job, Pod isolation | Phase 2 |
| Ray | Ray Task, distributed workers | Phase 3 |
| Cloud Render | Cloud-native VM/container | Phase 3 |

## Capability Extensions (Future — Not Kernel Work)

### Render Backends (WHAT — media processing)
| Capability | Dependencies | Phase |
|-----------|-------------|-------|
| BMF Graph Optimizer | BmfExecutionBackend | Phase 2 |
| Remotion Compiler | BackendCompiler SPI | Phase 2 |
| MLT Compiler | BackendCompiler SPI | Phase 2 |
| Blender Compiler | BackendCompiler SPI | Phase 3 |

### Storage Providers
| Capability | Dependencies | Phase |
|-----------|-------------|-------|
| S3 Storage Provider | StorageReference (on-disk only in V1) | Phase 2 |
| MinIO Storage Provider | StorageReference | Phase 2 |
| OSS Storage Provider | StorageReference | Phase 3 |
| GCS Storage Provider | StorageReference | Phase 3 |

### AI Providers
| Capability | Dependencies | Phase |
|-----------|-------------|-------|
| GPU Whisper Provider | Producer SPI | Phase 2 |
| Deepgram ASR Provider | Producer SPI | Phase 2 |
| Cloud Vision Provider | Producer SPI | Phase 3 |

### Marketplace
| Capability | Dependencies | Phase |
|-----------|-------------|-------|
| Plugin Marketplace | ExtensionRuntime + PF4J | Phase 3 |
| Template Marketplace | ProductRuntime + Timeline Git | Phase 3 |

### Planner Optimization
| Capability | Dependencies | Phase |
|-----------|-------------|-------|
| DAG-aware Planning | ProductGraph + Planner | Phase 3 |
| Cache-aware Planning | ProductGraph + StorageRuntime | Phase 3 |
| Cost-based Backend Selection | CapabilityCatalog | Phase 4 |

### Distributed Execution Environments
| Capability | Dependencies | Phase |
|-----------|-------------|-------|
| OpenCue Environment (scheduling, dispatch) | ExecutionBackend (BMF/FFmpeg) | Phase 1 |
| Kubernetes Environment | ExecutionBackend SPI | Phase 2 |
| Ray Distributed Workers | ExecutionBackend SPI | Phase 3 |
| Worker Capability Registry | Execution Environment | Phase 3 |
