---
status: blueprint
last_verified: 2026-06-18
scope: future
truth_level: target
owner: platform
---

# Reference Architecture Map

This document maps external reference projects that inform the platform's design. Each reference is a **design input**, not an implementation dependency.

## Purpose

The platform borrows proven concepts from industry tools while maintaining architectural independence. This map clarifies:

1. **What we borrow**: Specific concepts, patterns, or UX approaches
2. **What we explicitly do not borrow**: Runtime dependencies, product positioning, or architectural assumptions
3. **Where it maps**: Which platform layer implements or plans to implement the concept

This prevents accidental dependency creep and ensures the platform remains self-contained.

---

## Reference Categories

| Category | Description | References |
|----------|-------------|------------|
| User workflow builders | Visual automation tools for end users | n8n, Node-RED, Make, Zapier |
| Reliable execution engines | Durable workflow orchestration | Temporal |
| Internal rules / policy chains | Backend rule evaluation | LiteFlow |
| Data / artifact orchestration | Asset lineage and materialization | Dagster, Airflow, Prefect |
| Media production tracking | Film/video production management | ShotGrid / Autodesk Flow |
| Video ingest and distribution | Large-scale video workflows | Opencast |
| Render farms and media execution | Distributed rendering | OpenCue, Deadline, render farm tools |
| Timeline / node-based media tools | Compositing and editing | NLE tools, node compositors |

---

## 1. n8n / Node-RED / Make / Zapier

### Borrowed Ideas

| Concept | Description | Platform Layer |
|---------|-------------|----------------|
| Node-based workflow UX | Visual drag-and-drop flow builder | AutomationFlow UI |
| Trigger/action model | Event-driven workflow initiation | AutomationTrigger → SystemAction |
| Credential references | Secure credential handling without exposing secrets | CredentialRef |
| Execution history | Track what happened during flow execution | AutomationExecutionTrace |
| Templates | Pre-built workflow templates | AutomationFlow templates |
| Error handling | Per-node error policies (fail, skip, retry) | FlowNode.FlowNodeErrorPolicy |

### Explicitly Not Borrowed

| Idea | Reason |
|------|--------|
| Arbitrary script execution | Security risk in early phases; sandbox runtime not implemented |
| Generic automation positioning | Platform focuses on media workflows, not general-purpose automation |
| Unrestricted marketplace | Marketplace not implemented; curated extension model only |
| Direct access to internal services | Actions must go through SystemAction contract |

---

## 2. Temporal

### Borrowed Ideas

| Concept | Description | Platform Layer |
|---------|-------------|----------------|
| Reliable long-running execution | Durable workflows that survive failures | Future AutomationFlow runtime |
| Activity retry | Automatic retry with backoff | AutomationNodeExecutionAttempt |
| Durable history | Persistent execution state | AutomationExecutionTrace (future persistence) |
| Cancellation/recovery | Ability to cancel and recover workflows | Future AutomationFlow runtime |
| Human-waiting workflows | Approval steps that pause execution | AutomationFlow.NodeType.APPROVAL |

### Explicitly Not Borrowed

| Idea | Reason |
|------|--------|
| Exposing Temporal workflow API to users | Users compose AutomationFlows, not Temporal workflows |
| Temporal-first for all actions | Only long-running flows use Temporal; simple actions are synchronous |
| Temporal SDK as compile dependency | Integration is future phase; no current dependency |

**Status:** Temporal integration is **not implemented**. This is a future consideration only.

---

## 3. LiteFlow

### Borrowed Ideas

| Concept | Description | Platform Layer |
|---------|-------------|----------------|
| Internal policy chains | Sequential rule evaluation | Future policy engine |
| Provider selection chains | Choosing which provider handles a request | ExtensionProvider selection |
| Pre-submit validation chains | Multi-step validation before execution | AutomationFlowValidator + hooks |
| Quota/security/billing checks | Pre-execution policy enforcement | Future policy chains |

### Explicitly Not Borrowed

| Idea | Reason |
|------|--------|
| Exposing LiteFlow scripts to users | Users don't write rule scripts; they compose visual flows |
| LiteFlow as user-facing workflow builder | AutomationFlow is the user-facing abstraction |
| LiteFlow XML/JSON as storage format | Platform uses its own AutomationFlow model |

**Status:** LiteFlow integration is **not implemented**. This is a future consideration only.

---

## 4. Dagster

### Borrowed Ideas

| Concept | Description | Platform Layer |
|---------|-------------|----------------|
| Asset lineage | Track dependencies between artifacts | ArtifactRef relationships |
| Materialization | Knowing when an asset was last produced | ArtifactRef.metadata |
| Asset checks | Validate artifact quality | Future artifact validation |
| Freshness | Track how current an artifact is | Future artifact metadata |
| Artifact dependency thinking | DAG-based artifact relationships | RenderPlan internal DAG |

### Explicitly Not Borrowed

| Idea | Reason |
|------|--------|
| Python data platform runtime | Platform is Java/Spring Boot, not Python |
| Replacing RenderPlan with Dagster | RenderPlan is domain-specific; Dagster is general-purpose |
| Dagster's software-defined assets | Platform uses ArtifactRef model |

---

## 5. Airflow

### Borrowed Ideas

| Concept | Description | Platform Layer |
|---------|-------------|----------------|
| DAG dependency concepts | Directed acyclic graph for task ordering | AutomationFlow edges, RenderPlan |
| Operator/provider ecosystem | Pluggable execution units | ExtensionProvider model |
| Retries and backfill | Retry failed tasks, reprocess historical data | AutomationNodeExecutionAttempt |

### Explicitly Not Borrowed

| Idea | Reason |
|------|--------|
| Airflow as core runtime | Platform uses its own execution model |
| Scheduled ETL-first architecture | Platform is event/trigger-driven, not cron-first |
| Airflow DAG file format | Platform uses AutomationFlow model |

---

## 6. Prefect

### Borrowed Ideas

| Concept | Description | Platform Layer |
|---------|-------------|----------------|
| Flow/task developer experience | Clean API for defining workflows | AutomationFlow API |
| Execution history | Detailed execution tracking | AutomationExecutionTrace |
| Artifacts/logs | Output and logging references | ArtifactRef, logsRef |
| Lightweight automation feel | Simple trigger-action patterns | AutomationFlow UI |

### Explicitly Not Borrowed

| Idea | Reason |
|------|--------|
| Python-first runtime | Platform is Java/Spring Boot |
| Prefect Cloud dependency | Platform is self-hosted |
| Prefect's flow/task decorators | Platform uses declarative model |

---

## 7. ShotGrid / Autodesk Flow Production Tracking

### Borrowed Ideas

| Concept | Description | Platform Layer |
|---------|-------------|----------------|
| Project / asset / task model | Organizational hierarchy | Project/Asset/Task entities |
| Version tracking | Asset version management | ArtifactRef versioning |
| Review / approval model | Human review workflows | AutomationFlow.APPROVAL nodes |
| Production tracking | Status tracking across pipeline | Execution traces, status fields |
| Notes and review states | Collaborative review | Future review module |

### Explicitly Not Borrowed

| Idea | Reason |
|------|--------|
| Treating ShotGrid as render engine | ShotGrid is tracking, not execution |
| Replacing automation with ShotGrid | Platform has own AutomationFlow model |
| ShotGrid's API as primary interface | Platform has own API |

---

## 8. Opencast

### Borrowed Ideas

| Concept | Description | Platform Layer |
|---------|-------------|----------------|
| Video ingest workflow | Automated video processing pipelines | AutomationFlow for video |
| Transcoding / metadata / publishing | Multi-step media processing | SystemAction chain |
| Large-scale video management | Handling many concurrent videos | Future scaling patterns |

### Explicitly Not Borrowed

| Idea | Reason |
|------|--------|
| Education/lecture-capture-only assumptions | Platform supports general media workflows |
| Replacing creative review/render workflows | Platform has specialized render pipeline |

---

## 9. Render Farm Tools (OpenCue, Deadline)

### Borrowed Ideas

| Concept | Description | Platform Layer |
|---------|-------------|----------------|
| Queueing | Task queue management | Future render queue |
| Worker assignment | Distributing work to workers | Future render worker pool |
| Retry | Handling failed render tasks | AutomationNodeExecutionAttempt |
| Resource management | Tracking GPU/CPU resources | Future resource tracking |

### Explicitly Not Borrowed

| Idea | Reason |
|------|--------|
| Farm-only product model | Platform is broader than render farms |
| Exposing low-level worker controls | Users interact with SystemActions, not workers |
| Farm-specific UI | Platform has own UI model |

---

## 10. NLE / Node Compositor Tools

### Borrowed Ideas

| Concept | Description | Platform Layer |
|---------|-------------|----------------|
| Timeline/render graph concepts | DAG-based processing graphs | RenderPlan internal model |
| Preview/cache thinking | Cached intermediate results | ArtifactCache |
| Node graph inspiration | Visual node-based editing | AutomationFlow UI inspiration |

### Explicitly Not Borrowed

| Idea | Reason |
|------|--------|
| Exposing raw FFmpeg/MLT/Remotion nodes | Users use high-level SystemActions, not low-level tools |
| NLE timeline as primary UX | Platform has own workflow UX |
| Direct node compositor integration | Platform abstracts behind ExtensionProvider |

---

## Summary Table

| Reference | Borrowed Ideas | Explicit Non-Borrowed Ideas | Related Platform Layer |
|-----------|---------------|----------------------------|------------------------|
| n8n / Node-RED / Make / Zapier | Node UX, trigger/action, credentials, history, templates, error handling | Arbitrary scripts, generic positioning, unrestricted marketplace | AutomationFlow, CredentialRef, ExecutionTrace |
| Temporal | Durable execution, retry, history, cancellation, human-waiting | User-facing Temporal API, Temporal-first, SDK dependency | Future AutomationFlow runtime |
| LiteFlow | Policy chains, provider selection, validation chains, quota checks | User-facing scripts, LiteFlow as builder | Future policy engine |
| Dagster | Asset lineage, materialization, checks, freshness | Python runtime, replace RenderPlan | ArtifactRef, RenderPlan |
| Airflow | DAG concepts, operator ecosystem, retries | Airflow runtime, ETL-first | AutomationFlow, RenderPlan |
| Prefect | Flow/task DX, history, artifacts, lightweight feel | Python runtime, Cloud dependency | AutomationFlow |
| ShotGrid | Project/asset/task, version, review, tracking | ShotGrid as render engine, replace automation | Project entities, Review module |
| Opencast | Video ingest, transcoding, metadata, publishing | Education-only, replace creative workflows | AutomationFlow for video |
| Render farm tools | Queueing, worker assignment, retry, resources | Farm-only model, low-level controls | Future render queue |
| NLE / node compositors | Timeline graphs, preview/cache, node graph | Raw FFmpeg/MLT nodes, NLE timeline UX | RenderPlan, ArtifactCache |

---

## 11. Timeline Versioning References

### OpenTimelineIO

| Borrow | Description | Platform Layer |
|--------|-------------|----------------|
| Timeline interchange | Universal JSON format for timeline data | OTIO Exchange Layer (L1) |
| Editorial semantics | Clips, tracks, transitions, markers as first-class concepts | Canonical Timeline IR (L2) |
| Adapter architecture | Pluggable import/export for AAF, EDL, FCP XML, CMX 3600 | `timeline/standards/` adapters |
| Custom metadata schema | Platform-specific annotations in OTIO metadata | `TimelinePlatformMetadata` |

| Explicitly Not Borrowed | Reason |
|------------------------|--------|
| Render execution | OTIO is an interchange format, not an execution engine |
| Workflow orchestration | OTIO does not define execution semantics |

### vedit (Git-like Video Editing)

| Borrow | Description | Platform Layer |
|--------|-------------|----------------|
| Timeline snapshots | Commit-based timeline state capture | `TimelineRevisionService`, `timeline_revision` table |
| Timeline branch | Parallel editing paths for the same project | (Planned) Timeline branch model |
| Timeline diff | Structural diff between timeline snapshots | `TimelineSemanticDiffService`, `TimelineRevisionDiffService` |
| Timeline merge | Three-way merge for concurrent edits | `TimelineConflictDialog` (frontend) |
| AI agent workflow | LLM proposes edits as patches, human reviews | `AiTimelineEditService`, `AiTimelineProposalService` |

| Explicitly Not Borrowed | Reason |
|------------------------|--------|
| Provider architecture | vedit is about editing, not rendering |
| Execution engine | vedit does not produce media output |

### Vit (Git-backed Timeline Metadata)

| Borrow | Description | Platform Layer |
|--------|-------------|----------------|
| Git-backed timeline metadata | Timeline state stored as versioned metadata | `TimelineRevisionRepository` |
| Collaboration workflow | Multi-user editing with conflict detection | `TimelineEditorSyncService` |
| Timeline serialization | Efficient binary/JSON timeline representation | `InternalTimelineJson`, `InternalTimelineWriter` |

| Explicitly Not Borrowed | Reason |
|------------------------|--------|
| Render orchestration | Vit focuses on metadata, not media production |

---

## 12. Execution References

### BMF (BabitMF — Media Framework)

| Borrow | Description | Platform Layer |
|--------|-------------|----------------|
| Media graph runtime | DAG of processing nodes with typed connections | Artifact Dependency Graph → BMF graph compilation |
| AI inference pipeline | GPU-accelerated inference nodes in media graph | BMF execution backend (planned) |
| GPU execution | CUDA/OpenCL-accelerated media processing | BMF provider (M5 spike) |
| Hybrid media processing | Mix CPU and GPU nodes in same graph | BMF graph JSON schema |

| Explicitly Not Borrowed | Reason |
|------------------------|--------|
| Project model | BMF does not define project/timeline structure |
| Timeline model | BMF operates on media graphs, not editorial timelines |

### Temporal

| Borrow | Description | Platform Layer |
|--------|-------------|----------------|
| Workflow orchestration | Durable workflow execution with activities | `workflow-module` (20 files) |
| Retry and recovery | Automatic retry with exponential backoff | Temporal activity retry policy |
| Long-running execution | Workflows that span hours/days | Render pipeline workflows |
| Human-in-the-loop | Workflows that wait for external signals | Workflow signal handling |

| Explicitly Not Borrowed | Reason |
|------------------------|--------|
| Timeline representation | Temporal does not model editorial timelines |
| Artifact lineage | Temporal tracks workflow state, not artifact dependencies |

---

## 13. What We Intentionally Reject

| Rejected Pattern | Why |
|-----------------|-----|
| **Timeline = Execution Graph** | The timeline describes editorial intent; the execution graph describes how to produce media. Conflating them couples editing decisions to rendering infrastructure. |
| **Provider-specific Timeline** | The timeline must be engine-neutral. Switching from FFmpeg to BMF must not require timeline changes. |
| **LLM-generated execution commands** | LLMs generate edit intent (timeline patches), not FFmpeg commands or BMF graphs. The planner compiles intent into execution. |
| **Artifact DAG as editing source of truth** | The Artifact DAG describes what to produce, not what the user intended. The Timeline IR is the editing source of truth. |
| **Provider-owned project models** | Each provider (FFmpeg, BMF, Remotion) has its own internal model. The platform's Timeline IR is the canonical model; provider models are implementation details. |

---

## Key Principles

1. **Inspiration, not dependency**: Reference projects inform design; they don't dictate implementation
2. **Abstraction over exposure**: Users interact with high-level contracts (SystemAction, AutomationFlow), not low-level tools
3. **Progressive disclosure**: Simple things should be simple; complex things should be possible
4. **Security by default**: No arbitrary code execution in early phases
5. **Platform-specific models**: The platform has its own models (AutomationFlow, RenderPlan, ArtifactRef) inspired by but not copied from references

---

## Current Status

| Capability | Status |
|------------|--------|
| Capability contracts | ✅ Implemented |
| Registries | ✅ Implemented |
| Flow validation | ✅ Implemented |
| Action metadata catalog | ✅ Implemented |
| Dry-run executor | ✅ Implemented |
| Execution trace model | ✅ Implemented |
| Real runtime | ❌ Not implemented |
| Execution persistence | ❌ Not implemented |
| Event bus | ❌ Not implemented |
| Hook runtime | ❌ Not implemented |
| Marketplace | ❌ Not implemented |
| Sandbox runtime | ❌ Not implemented |
| Temporal integration | ❌ Not implemented |
| LiteFlow integration | ❌ Not implemented |
