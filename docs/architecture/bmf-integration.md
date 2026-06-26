---
status: architecture-spike
created: 2026-06-25
scope: platform-wide
truth_level: target
owner: platform
---

# BMF Integration Architecture

## 1. BMF Capability Analysis

| Capability | Category | Platform Equivalent |
|-----------|----------|-------------------|
| Media graph (DAG) composition | Media Pipeline | Artifact DAG |
| Graph execution engine | Execution Runtime | ExecutionBackend |
| Operator scheduling | Coordination | PlatformTask |
| GPU acceleration (CUDA) | Hardware | (below ExecutionBackend) |
| AI inference (TensorRT, ONNX) | AI Processing | Overlaps ProviderRuntime |
| Media I/O (decode, encode) | Media Pipeline | Artifact DAG + FFmpeg |
| Transcoding, filtering | Media Pipeline | Render Providers |

BMF is BOTH an execution engine AND a media processing framework.

## 2. Architecture Options

### Option A: BMF as ExecutionBackend
PlatformTask → TaskHandler → ProviderExtension → ExecutionBackend (BMF) → BMF Graph

Pros: Matches existing ExecutionBackend SPI. OpenCue replaces later.
Cons: Loses graph composition. Operators forced into generic args.

### Option B: BMF as Provider Plugin
PlatformTask → TaskHandler → ExtensionRegistryService → BmfProviderExtension → LocalProcess → BMF CLI

Pros: Full BMF capabilities as provider. Matches AI provider pattern.
Cons: Always runs as subprocess. No GPU advantage.

### Option C: Hybrid (RECOMMENDED)
PlatformTask → TaskHandler → ExtensionRegistryService → BmfProviderExtension (capability reg)
→ BmfExecutionBackend (graph execution) → BMF Graph → Operators → GPU/FFmpeg

Pros: BMF is ExecutionBackend for graphs. Media capabilities registered as provider.
OpenCue replaces BmfExecutionBackend without touching providers.
Cons: Two components (Extension + Backend).

## 3. Responsibility Matrix

| Layer | BMF Role |
|-------|----------|
| Coordination Runtime | Unchanged |
| TaskHandler | Routes to BMF provider |
| ExtensionRegistryService | Registers BmfProviderExtension |
| BmfExecutionBackend | Executes BMF graphs |
| BMF Operators | Internal to BMF (NOT platform concepts) |
| GPU/FFmpeg | Below ExecutionBackend |

## 4. Execution Model

One PlatformTask → one BMF graph execution.
Graph composition happens inside BMF, not at platform level.
Task payload carries graph specification or template reference.

## 5. Operator Model

BMF operators are INTERNAL to BMF. Platform does NOT know individual operators.
Platform knows: BMF capability (TRANSCODE, FILTER, COMPOSITE).
BMF knows: which operators fulfill the capability.

## 6. ExecutionBackend Impact

BmfExecutionBackend implements ExecutionBackend. Capable of: PROBE, ASR, OCR, VISION, EMBEDDING + new MEDIA capabilities.
ExecutionRequest carries graph spec as payload. ExecutionResult carries output files + metadata.

## 7. Provider Interaction

AI Providers compose BMF graphs:
- Vision: BMF decode → YOLO inference → objects
- OCR: BMF frame extraction → Tesseract → text
- Embedding: BMF decode → CLIP → embedding vector
- Whisper: BMF audio extraction → Whisper → transcript

All go through BmfExecutionBackend, not LocalProcess.

## 8. OpenCue Compatibility

OpenCue replaces BmfExecutionBackend: same SPI, different backend.
Providers unchanged. TaskHandler unchanged. Coordination unchanged.

## 9. Final Decision

RECOMMENDED: Option C — Hybrid (BmfExecutionBackend + BmfProviderExtension).
BMF as execution location (ExecutionBackend) for graph-based media processing.
BMF as provider plugin (ProviderExtension) for media capability catalog.

## 10. Migration Roadmap

S-001: Architecture spike (this document)
Sprint 046: BmfExecutionBackend
Sprint 047: BmfProviderExtension
Sprint 048: OpenCueExecutionBackend
A2: Module governance
