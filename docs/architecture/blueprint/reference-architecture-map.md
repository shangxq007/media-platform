---
status: blueprint
last_verified: 2026-06-24
scope: platform-wide
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

## 14. XMP (Extensible Metadata Platform)

### Borrowed Ideas

| Concept | Description | Platform Layer |
|---------|-------------|----------------|
| Portable embedded/sidecar metadata | Self-describing metadata envelope that travels with media files | XMP sidecar schema classes (`render-module/.../domain/xmp/`) |
| Asset governance fields | Classification, license, rights holder, retention policy | Governance fields on `asset` table |
| AI generation metadata | Model name, prompt, seed, sampler, confidence, review status | AI metadata through OTIO `platform.*` references |
| Namespace extensibility | Custom URI-based namespaces for domain-specific metadata | `asset:`, `vfx:`, `ai:`, `lineage:`, `governance:`, `ml:` namespaces |
| JSON-LD projection | Linked Data export from XMP metadata | `JsonLdExportService` for asset knowledge graph seeding |

### Explicitly Not Borrowed

| Idea | Reason |
|------|--------|
| Complex VFX node graph execution | VFX node graphs exceed XMP payload capacity; use external URI references |
| Render DAG | Artifact Dependency Graph is the provider-neutral render DAG; XMP is metadata only |
| Workflow orchestration | XMP describes what was done, not how to execute it |
| Cache truth | Authoritative asset state lives in Asset Registry (database); XMP is a derived export |
| XMP as active runtime dependency | Phase 1 uses XMP schema classes only; no XMP SDK or file I/O engine |

**Status:** XMP is **not an active runtime** — represented through Java domain records and JSON-LD serialization in Phase 1. Full XMP SDK integration (Adobe XMP Toolkit or similar) is a future consideration.

---

## 15. OpenAssetIO

### Borrowed Ideas

| Concept | Description | Platform Layer |
|---------|-------------|----------------|
| Asset identity abstraction | Entity references that decouple asset identity from storage location | `entity_ref` column on `asset` table |
| Entity reference resolution | Resolve abstract entity reference to concrete storage URI | Future `OpenAssetioResolver` (Phase 2) |
| DAM/MAM integration patterns | Standardized asset resolution across different asset management systems | Future federation adapter |
| Manager/plugin architecture | Host application (manager) resolves assets via plugins | Future provider SPI |

### Explicitly Not Borrowed

| Idea | Reason |
|------|--------|
| Runtime SDK dependency | Phase 1 stores `entity_ref` strings only; no OpenAssetIO library integration |
| Replacing internal asset registry | The `asset` table is the authoritative asset identity; OpenAssetIO resolves references, not stores identity |
| OpenAssetIO as canonical model | Internal canonical models (Asset record) are the source of truth; OpenAssetIO is an integration layer |

**Status:** OpenAssetIO is **not implemented**. The `entity_ref` column is seeded in Phase 1 as a future integration point. Full OpenAssetIO SDK integration is a Phase 2 consideration.

---

## 16. OpenLineage

### Borrowed Ideas

| Concept | Description | Platform Layer |
|---------|-------------|----------------|
| Job/run/dataset lineage event model | Standardized event types for processing lineage | `asset_lineage` table structure (Phase 1 schema) |
| Processing audit trail | Record what tool, operator, workflow produced each asset | Structured lineage fields (`workflow_id`, `run_id`, `operator`, `tool`, `parameters_hash`) |
| ASR / render / VFX / AIGC processing audit | Trace AI generation and media processing steps | Lineage records linked to `render_job` and `prompt_execution_run` |
| Open standard for lineage | Interoperable lineage events across tools and platforms | Future OpenLineage event emission (Phase 2) |

### Explicitly Not Borrowed

| Idea | Reason |
|------|--------|
| OpenLineage SDK runtime | Phase 1 uses `asset_lineage` database records only; no OpenLineage library integration |
| OpenLineage as active event emitter | Lineage is stored at rest in Phase 1; event streaming is Phase 2 |
| Replacing internal lineage model | The `asset_lineage` table is the authoritative lineage store; OpenLineage events are derived exports |

**Status:** OpenLineage is **not implemented**. The `asset_lineage` table provides structured lineage storage in Phase 1. Full OpenLineage SDK integration (event emission to lineage backends like Marquez) is a Phase 2 consideration.

---

## 17. Knowledge Graph

### Borrowed Ideas

| Concept | Description | Platform Layer |
|---------|-------------|----------------|
| Cross-asset semantic relationships | Query assets by people, scenes, prompts, models, licenses | JSON-LD export foundation (Phase 1) |
| Search and reasoning | Semantic queries across asset metadata, AI provenance, and governance | Future graph database projection (Phase 2) |
| Linked Data principles | Assets as identifiable entities with typed relationships | JSON-LD `@id` and `@type` from asset + XMP metadata |
| Multi-source graph aggregation | Combine asset, lineage, AI, and governance data into unified graph | Future Knowledge Graph module |

### Explicitly Not Borrowed

| Idea | Reason |
|------|--------|
| Neo4j / RDF graph database deployment | Phase 1 uses JSON-LD file/API export only; no graph database runtime |
| Real-time graph queries | Graph queries are Phase 2+; Phase 1 provides static JSON-LD export |
| Replacing relational database with graph DB | The `asset` table (PostgreSQL) is the authoritative store; graph DB is a query projection |
| Graph as source of truth | Asset Registry (relational database) is the source of truth; Knowledge Graph is a derived view |

**Status:** Knowledge Graph is **not implemented**. JSON-LD export in Phase 1 lays the foundation for future graph database projection. Full Neo4j/RDF deployment is a Phase 2+ consideration.

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

---

## 18. Editorial Version Control

### Borrowed Ideas (Perforce Helix Core, Git, GitHub PRs, Avid Nexis, Premiere Team Projects)

| Reference | Borrow | Reject | Platform Layer |
|-----------|--------|--------|----------------|
| **Perforce Helix Core** | Binary version control, branching, changelists, atomic commits | File-locking model, centralized server (we use PostgreSQL + Git-like semantics) | TimelineGit: revision chain, merge |
| **Git** | Commit model, parent chain, three-way merge, branch, conflict markers | Line-based diff, staging area, file-based storage (we store timeline JSON snapshots) | TimelineGit: snapshot, patch, diff, merge |
| **GitHub Pull Requests** | PR workflow, review comments, approve/reject, CI checks | Code-review UX (we need timeline-specific review UI) | AI Proposal Review: approve/reject loop |
| **Avid Nexis** | Shared storage, bin locking, multi-user workspace | Hardware-locked, proprietary (we use platform-agnostic PostgreSQL + S3) | Asset Registry: shared identity across users |
| **Premiere Team Projects** | Real-time collaboration, visual timeline sharing, conflict markers | Adobe-only, subscription-locked, no version history API | TimelineGit: diff + merge + restore |

### How We Differ

| Traditional VCS | Timeline Git |
|-----------------|-------------|
| Diffs lines of text | Diffs timeline entities (clips, tracks, effects) |
| Merge needs human for any conflict | Semantic conflict detection (same clip modified = conflict; different clips = auto-merge) |
| Branch is a file system concept | Branch is a named revision pointer (lightweight) |
| Commit author is a user | Commit author can be user, AI agent, or system |
| No render awareness | Diff → incremental render impact analysis |

---

## 19. Asset Ecosystem

### Borrowed Ideas (Unity Asset Store, Unreal Marketplace, GitHub Marketplace, Figma Community)

| Reference | Borrow | Reject | Platform Layer |
|-----------|--------|--------|----------------|
| **Unity Asset Store** | Categorized assets (models, scripts, shaders), versioned packages, reviews/ratings | Game-specific taxonomy (we need media-specific: templates, effects, styles, models) | Asset Ecosystem: marketplace |
| **Unreal Marketplace** | Plugin ecosystem, entitlement-based access, free/paid tiers | Engine-locked (we are platform-agnostic) | Asset Registry: identity + governance + entitlement |
| **GitHub Marketplace** | App integration model, listing with metadata, install/uninstall lifecycle | GitHub-specific OAuth (we use platform auth) | Extension module: plugin listing |
| **Figma Community** | Template sharing, remixing, version history, community curation | Design-specific data model (we have timeline templates, effect packs, styles) | Asset Ecosystem: template sharing |

### Asset Types (Vision)

| Type | Description | Example |
|------|-------------|---------|
| `MEDIA` | Raw media files (video, audio, image) | Stock footage, music tracks |
| `TIMELINE_TEMPLATE` | Reusable timeline structure | "YouTube Intro" template |
| `PLUGIN` | Render/effect extensions | Custom blur filter |
| `WORKFLOW` | Temporal/LiteFlow execution flows | "Auto-transcribe-and-subtitle" workflow |
| `EFFECT` | Effect presets and parameter bundles | "Cinematic color grade" effect pack |
| `STYLE` | Visual styling presets | "Corporate Blue" subtitle style |
| `MODEL` | AI model weights and configurations | Whisper-v3 ASR model, Stable Diffusion checkpoint |

### Relationship to Asset Registry

Every marketplace asset is ultimately an Asset Registry entity:
- `assetId` — unique identity across federation
- `assetVersion` — versioned releases (v1.0, v1.1, v2.0)
- `governance` — license, rights holder, classification
- `lineage` — derived from what? trained on what dataset?
- `entityRef` — OpenAssetIO reference for resolution

The marketplace is the **discovery layer**. The Asset Registry is the **identity layer**. They are orthogonal.

### Marketplace Vision (Unified)

Today we have separate concepts: template store, plugin store, effect store, model store. The unified vision:

```
Marketplace
  ├── Templates     (timeline templates)
  ├── Effects       (effect packs + presets)
  ├── Plugins       (render extensions + OFX)
  ├── Styles        (subtitle styles, color grades)
  ├── Models        (AI models — ASR, vision, LLM)
  ├── Workflows     (automation flows)
  └── Media         (stock footage, music, sound effects)
```

All are Asset Registry entities with governance metadata. All are searchable via Asset Search.

### Relationship to Product Strategy

The Asset Ecosystem is the **fifth platform pillar** (alongside Timeline IR, Timeline Git, Asset Registry, Artifact DAG). It represents the platform's long-term value:
- **Today:** Users import their own media and edit with built-in effects
- **Tomorrow:** Users discover, install, and share assets across projects and tenants
- **Future:** A two-sided marketplace connects asset creators with video producers

---

## 20. Frame.io — Creative Collaboration Platform

### Overview

Frame.io is the dominant creative collaboration platform for video production. It provides asset review, version comparison, timestamp comments, approval workflows, and stakeholder sharing — all centered around the video asset, not the timeline. Acquired by Adobe in 2021.

### Core Capabilities

| Capability | Description | Timeline Git Mapping |
|-----------|-------------|---------------------|
| **Asset Review** | Share a render for review; stakeholders comment on specific frames | Future: Review snapshots against render outputs |
| **Version Review** | Side-by-side comparison of render versions with visual diff | Timeline Revision Review (compare any two revisions) |
| **Timestamp Comments** | Comments anchored to specific timecodes in video | Future: Timeline annotations on clips/markers |
| **Approval Workflow** | Structured "needs review → in review → approved" lifecycle | AI Proposal Review (adopt/reject) — could extend to human approval |
| **Stakeholder Collaboration** | Share links; external reviewers don't need accounts | Future: Guest review links for sharing timeline states |
| **Share Links** | Share a specific version with password protection, expiry | Future: Shareable revision snapshots |

### What We Should Learn

| Frame.io Strength | Our Application |
|-------------------|-----------------|
| **Review Status Model** (pending → in review → needs changes → approved) | Apply to timeline revisions and asset versions |
| **Comment Anchoring** (comments pinned to frame/timecode) | Apply to timeline markers and clip-level annotations |
| **Version Comparison UX** (side-by-side player, toggle visibility) | Apply to timeline diff visualization |
| **Stakeholder Workflow** (editor → reviewer → approver) | Extend AI proposal review to human review chains |
| **Visual Diff** (overlay/shift between versions) | Combine semantic diff with frame-level visual diff |

### What We Should NOT Copy

| Frame.io Feature | Why Not |
|-----------------|---------|
| **Video hosting / CDN** | We use platform-agnostic S3/OSS/GCS; don't build a CDN |
| **Transcoding pipeline** | Already have provider-agnostic render pipeline |
| **Proprietary player** | Use Remotion for preview; don't build a video player |
| **Cloud-only SaaS model** | Platform must support self-hosted deployment |
| **Review-centric product positioning** | Frame.io is review-first; we are editing-first with review as feature |

### Direct Mapping

```
Frame.io                        →  Timeline Git + Asset Registry
────────────────────────────────────────────────────────────────
Frame.io Asset Version          →  Asset Registry assetVersion
Frame.io Version Comparison     →  Timeline Revision Compare (GET /compare)
Frame.io Timestamp Comment      →  Future: Timeline Marker Annotation
Frame.io Approval Workflow      →  AI Proposal Review + Future: Human Review Pipelines
Frame.io Share Link             →  Future: Shareable Revision Snapshot URLs
Frame.io Review Status          →  Asset Governance reviewStatus + classification
```

### Assessment

**Frame.io is the closest product to what we're building — but in reverse.** Frame.io built review-first and is adding editing. We built editing-first and are adding review. Both converge on the same capability set from opposite directions.

**Our advantage:** Timeline Git (version control of the source timeline, not just rendered outputs) is something Frame.io cannot match without rebuilding their core model. Frame.io versions renders; we version the editing source.

**Our gap:** Frame.io's review UX (visual diff player, timestamp comments, approval dashboard) is production-grade. We have none of this.

---

## 21. Perforce Helix Core — Deep Reassessment

### Original Assessment (Sprint 001)

Helix Core provides binary version control, branching, and changelists for large media files.

### Deep Reassessment

| Helix Core Concept | Our Implementation | Status |
|-------------------|-------------------|--------|
| **Changelist** (group of file changes) | Timeline Revision (snapshot + patch ops) | ✅ Implemented |
| **Revision** (version of a file) | Revision chain (parent → child with content hash) | ✅ Implemented |
| **Branch** (divergent file tree) | Not implemented — revisions are linear | ❌ Missing |
| **Merge** (combine branches) | `TimelineMergeService.threeWayMerge()` — engine complete | ⚠️ No API |
| **Review** (code review workflow) | AI Proposal Review (adopt/reject) — engine complete | ⚠️ Needs human review extension |
| **Shelve** (stash changes without commit) | Not implemented | ❌ Missing |
| **Stream** (composed workspace view) | Not implemented | ❌ Missing |
| **Protections** (per-branch ACL) | Asset governance (classification, license, security level) | ⚠️ No API |

### Key Differences

| Helix Core | Timeline Git |
|-----------|-------------|
| Versions binary files in a depot | Versions timeline JSON snapshots in PostgreSQL |
| File-level diff (binary-aware) | Entity-level diff (clips, tracks, effects — 25 change types) |
| Centralized server (Perforce server) | Platform-native (PostgreSQL + S3) |
| Stream-based branching | Future: Named revision pointer branching |
| Per-file changelists | Per-timeline revisions (whole timeline snapshot) |
| No render awareness | Diff → incremental render impact |
| No AI proposal workflow | Structured AI proposal → approve/reject loop |

### Assessment

**Timeline Git has achieved ~60% of Helix Core's version control model.** We match or exceed: revision tracking, diff granularity (entity-level vs file-level), merge engine. We lack: branching, shelving, and per-branch protections. These are deferred to P4-P5.

**Timeline Git surpasses Helix Core in:** semantic diff (25 types), AI proposal review, incremental render impact, asset governance integration. Helix Core has no concept of "this clip is AI-generated" or "this asset has license restrictions."

---

## 22. Avid Nexis — Deep Reassessment

### Original Assessment

Avid Nexis provides shared storage, bin locking, and multi-user editorial collaboration for Avid Media Composer.

### Deep Reassessment

| Nexis Concept | Our Application | Status |
|--------------|-----------------|--------|
| **Shared Media Storage** | Asset Registry (assetId + storageUri) — identity decoupled from location | ✅ Schema complete |
| **Bin Locking** (exclusive edit on a bin) | Not implemented — we use merge-based collaboration, not locks | ❌ Not planned |
| **Multi-user Workspace** | Timeline Git: branches can represent editor workspaces | ❌ Branch not implemented |
| **Media Indexing** | Asset ingstion blueprint (ASR, OCR, vision, embedding) | ⚠️ Blueprint only |
| **Project Sharing** | Project-scoped revision chains (revisions scoped to project_id) | ✅ Implemented |
| **Avid-specific bin format** | Internal Timeline Schema 1.0 (platform-native, not NLE-specific) | ✅ Implemented |

### Assessment

**We are intentionally NOT building a shared-storage, lock-based collaboration model.** Nexis (and Avid) use file-system-level locks — one editor locks a bin, others are blocked. This is a 1990s model.

**Our model is merge-based:** Two editors work independently, then merge their revision chains. This is Git's model, applied to timelines. It scales better (no lock contention) but requires a working merge engine and conflict resolution — both of which we have at engine level.

**Gap:** The merge engine has no API yet. Until users can actually merge, the collaboration model is theoretical.

---

## 23. Frame.io vs GitHub — Two Collaboration Paradigms

### The Two Models

| Dimension | GitHub (Code Collaboration) | Frame.io (Creative Collaboration) |
|-----------|---------------------------|----------------------------------|
| **What is versioned** | Lines of text in files | Rendered video outputs |
| **What is reviewed** | Code changes (pull requests) | Video frames (timestamp comments) |
| **Who reviews** | Other developers | Stakeholders, clients, creative directors |
| **Review mechanism** | Inline comments on diff | Timestamp-anchored comments on video |
| **Approval** | PR approval → merge | Version approval → publish |
| **Collaboration model** | Branch → PR → review → merge | Share → comment → revise → approve |
| **Conflict resolution** | Merge conflicts in text | Manual re-edit (Frame.io has no merge) |

### Why Both Matter

```
Our Platform = GitHub's Model + Frame.io's UX + OTIO's Interchange

GitHub's model (branch, diff, merge, review)  ← Implemented at engine level
Frame.io's UX (visual diff, comments, approval) ← Blueprint / future
OTIO's interchange (import/export, metadata)   ← Implemented
```

### The Convergence Play

GitHub owns code collaboration. Frame.io owns creative collaboration. No platform owns **both** for video production. Our product strategy is to be the platform that combines:

1. **Editing** (Timeline IR + OTIO)
2. **Version control** (Timeline Git — GitHub model)
3. **Review** (Future — Frame.io model)
4. **Rendering** (Artifact DAG + multi-provider)
5. **Discovery** (Asset Ecosystem — marketplace)

This combination does not exist today.

---

## 24. Editorial Collaboration Platforms — New Strategic Category

### Category Definition

Platforms that enable multiple stakeholders to review, comment, and approve video content. Distinct from NLEs (which are editing tools) and DAM/MAMs (which are asset management systems).

### Key References

| Platform | Strength | Weakness | Our Position |
|----------|---------|----------|-------------|
| **Frame.io** | Video review UX, timestamp comments, approval workflow | No version control of source timelines, no editing, Adobe-only ecosystem | We have version control; need review UX |
| **Avid Nexis** | Shared storage, bin locking, Media Composer integration | Hardware-locked, proprietary, no cloud-native model, lock-based (not merge-based) | We have merge-based; don't need locks |
| **Premiere Team Projects** | Real-time collaboration, integration with Premiere | Adobe-only, subscription-locked, no version history API | We have version history API; no real-time |
| **LucidLink** | Cloud-native shared storage, stream-only access | Storage-only — no version control, no review, no editing | We have all three layers |

### Assessment

**Frame.io is the benchmark for review UX.** We should copy their review model (timestamp comments, approval workflow, version comparison UX) but layer it on top of our own Timeline Git (which they don't have).

---

## 25. Creative Marketplaces — New Strategic Category

### Category Definition

Platforms that enable creators to discover, acquire, and integrate third-party assets into their projects.

### Benchmarking for Asset Types

| Asset Type | Best Reference | Why | Adoption Priority |
|-----------|---------------|-----|-------------------|
| `TIMELINE_TEMPLATE` | **Figma Community** | Figma's template sharing (remix, version history, community curation) is the closest analog to timeline templates. Figma templates are design files — ours are video project templates. | **P3 — Highest priority. Most differentiated asset type.** |
| `EFFECT` | **Unity Asset Store** | Unity's effect/plugin packaging (versioned, dependency-aware, parameterized) is the most mature model for visual effects. | P3 — Depends on template marketplace infrastructure |
| `PLUGIN` | **GitHub Marketplace** | GitHub's app/action listing (metadata, install, uninstall) is the standard for developer-oriented plugins. | P3 — Similar to effects but for render/extension plugins |
| `STYLE` | **Figma Community** | Figma's style system (color, typography, effects) shared as community assets. | P3 — Lower priority; bundled with templates |
| `MODEL` | **HuggingFace Hub** | HuggingFace's model hub (versioned, tagged, documented, downloadable) is the closest analog. | P4 — AI models complex; defer until marketplace mature |
| `MEDIA` | **Storyblocks / Artlist** | Stock media marketplaces (search, preview, license, download). | P4 — Commodity; differentiate via governance |
| `WORKFLOW` | **GitHub Actions Marketplace** | Action workflows with triggers, inputs, and outputs. | P4 — Requires Temporal/LiteFlow runtime first |

### Why Figma Community is the #1 Reference

| Figma Community Feature | Our Application |
|------------------------|-----------------|
| **Template remixing** — anyone can create a copy and modify | Fork a timeline template → edit in your own project |
| **Version history** — every template change is tracked | Timeline Git revisions on the template |
| **Community profiles** — publisher pages with their assets | Creator profile → published templates, effects, styles |
| **Categories + tags** — browse by type, use case, industry | "YouTube Intro", "Podcast", "Event Recap" categories |
| **Free + paid tiers** — free community templates, paid professional | Free tier for community; paid for professional creators |
| **In-app installation** — install directly into your workspace | Install template → creates new project with template timeline |

---

## 26. Asset Ecosystem Benchmarking — Deep Dive

### Unity Asset Store

| Feature | Status | Apply To |
|---------|--------|----------|
| Package versioning (semver) | ✅ Implemented (assetVersion) | All asset types |
| Dependency management | Not needed (timeline templates are self-contained) | Effects/plugins may need deps |
| Ratings + reviews | Future | Marketplace listing |
| Free + paid tiers | Future | Marketplace monetization |
| Asset validation (must compile) | No compile step needed (templates are JSON) | Validate templates parse + contain valid effect keys |

### Unreal Marketplace

| Feature | Status | Apply To |
|---------|--------|----------|
| Plugin ecosystem (C++/Blueprints) | Not applicable (we use extension/provider model) | Plugins map to ExtensionProvider |
| Entitlement-based access | Future (entitlement-module) | Gated access to premium assets |
| Engine version compatibility | Map to platform version compatibility | Template schema version compatibility |

### Figma Community

| Feature | Status | Apply To |
|---------|--------|----------|
| Template sharing | Blueprint | TIMELINE_TEMPLATE |
| Remix workflow | Blueprint | Fork → edit → publish |
| Version history per template | ✅ (Timeline Git) | Every template has revision history |
| Community curation (trending, featured) | Future | Marketplace curation |

### Recommendation

**Figma Community is the primary reference for Timeline Templates** (highest priority asset type).
**Unity Asset Store is the primary reference for Effects and Plugins** (packaging + versioning + dependency management).
**HuggingFace Hub is the primary reference for AI Models** (deferred).

---

## 27. Platform Positioning Update

### What Are We Closest To?

The platform does not fit a single existing category. It is a **composite platform**:

```
Platform DNA = 
  30% Timeline Git (version control — GitHub for video)
+ 25% OTIO Exchange (interchange — OpenTimelineIO++ )
+ 20% Render Pipeline (execution — render farm for web/coders)
+ 15% Asset Ecosystem (discovery — Figma Community for video)
+ 10% Review Workflow (collaboration — Frame.io for timeline-aware review)
```

### Category Comparison

| Category | We Have | We Don't Have |
|----------|---------|---------------|
| **Render Farm** | Multi-provider render, incremental render, worker farm | GPU scheduling, farm-scale queue management, cost optimization |
| **DAM/MAM** | Asset identity, versioning, governance, JSON-LD, lineage | DAM-level search (ElasticSearch), MAM-level catalog browsing, asset relationships |
| **Frame.io** | Version comparison (API), proposal review (adopt/reject), restore | Visual diff player, timestamp comments, approval dashboard, share links |
| **GitHub** | Revision chain, semantic diff, merge engine, conflict detection | Branch UX, PR review workflow, blame, code search |
| **Helix Core** | Changelists (revisions), merge, diff (entity-level), content hash | Branch, shelve, per-branch ACLs, stream composition |
| **NLE** | Timeline IR, OTIO import/export, effects, markers, tracks | Video player, real-time preview, waveform editor, color grading UI |

### Emerging Positioning

The platform is uniquely positioned as a **GitHub for Video** — combining Git-like version control with Frame.io-like review, OTIO-compatible interchange, and an asset marketplace. No single competitor occupies this space.

---

## 28. Strategic Gap Analysis

### Goal: Open Media Collaboration Platform

**Gap Priority (must-have → nice-to-have):**

| Rank | Gap | Why Priority | Blocked By |
|------|-----|-------------|------------|
| **1** | **Merge REST API** | Engine complete, no endpoint. Users cannot merge branches. Prevents collaboration. | Nothing — 1-2 days of API work |
| **2** | **Review Workflow** | Frame.io-like review is the biggest missing UX. No timestamp comments, no approval dashboard, no share links. | Merge API + diff visualization |
| **3** | **Asset Search API** | Users cannot search assets by type, governance, or lineage. Asset Registry is a data layer only. | Nothing — search over existing columns |
| **4** | **Comment System** | Frame.io anchor point: clip-level and marker-level annotations with thread support. No structured review feedback. | Review workflow |
| **5** | **Marketplace Foundation** | Template/effect/plugin listing + install. Users cannot discover community assets. | Asset Registry + search |
| **6** | **Branch UX** | Named branches enable parallel workflows. Engine supports merge without branches. | Merge API |

### Reasoning

Merge API is #1 because it's a **3-day fix that unlocks collaboration**. Build it now. Review workflow is #2 because it's the **biggest user-visible gap** — Frame.io's entire value proposition. Asset Search is #3 because it's the **foundation for marketplace** (can't discover what you can't search). Branch UX is #6 because **merge works without named branches** — branch is a UX layer on top of existing revision chains.

---

## 29. Updated Reference Categories

| Category | Description | References |
|----------|-------------|------------|
| User workflow builders | Visual automation tools for end users | n8n, Node-RED, Make, Zapier |
| Reliable execution engines | Durable workflow orchestration | Temporal |
| Internal rules / policy chains | Backend rule evaluation | LiteFlow |
| Data / artifact orchestration | Asset lineage and materialization | Dagster, Airflow, Prefect |
| Media production tracking | Film/video production management | ShotGrid / Autodesk Flow |
| Video ingest and distribution | Large-scale video workflows | Opencast |
| Render farms and media execution | Distributed rendering | OpenCue, Deadline |
| Timeline / node-based compositing | Compositing and editing | vedit, Vit, OpenTimelineIO |
| **Editorial collaboration platforms** | Video review, comment, approval | **Frame.io**, Avid Nexis, Premiere Team Projects |
| **Creative marketplaces** | Asset discovery, sharing, monetization | **Unity Asset Store**, Unreal Marketplace, GitHub Marketplace, **Figma Community** |
| **Version control systems** | Branch, merge, diff, history | **Git**, **Perforce Helix Core**, GitHub |

---

## 30. Related Documents

| Document | Relationship |
|----------|-------------|
| [OTIO Render Platform Blueprint](otio-render-platform-blueprint.md) | Parent blueprint |
| [Timeline Git Blueprint](timeline-git-blueprint.md) | Version control architecture |
| [Asset Ecosystem Blueprint](asset-ecosystem-blueprint.md) | Marketplace and search vision |
| [Frame.io Reference Analysis](../../review/frameio-reference-analysis.md) | Detailed Frame.io analysis |
| [Timeline Git Product Readiness](../../review/timeline-git-product-readiness.md) | Product assessment |
| [Architecture Re-Prioritization Sprint](../../review/architecture-reprioritization-sprint.md) | Strategic decisions (2026-06-24) |
| [ASWF Ecosystem Analysis](../../review/aswf-ecosystem-analysis.md) | ASWF project integration assessment |

---

## 31. ASWF Ecosystem — Overview

### Academy Software Foundation

The Academy Software Foundation (ASWF) hosts open-source projects that form the backbone of professional visual effects and animation pipelines. These projects define industry standards for color, image formats, material description, plugin interfaces, render farm scheduling, and asset resolution.

### Category Mapping to Platform

| ASWF Project | Category | Platform Layer | Phase |
|-------------|----------|---------------|-------|
| **OpenTimelineIO** | Timeline Interchange | OTIO Adapter (L1) | ✅ Integrated |
| **OpenCue** | Render Farm | Execution Graph (future backend) | P3 |
| **OpenColorIO** | Color Management | Render Pipeline (cross-provider consistency) | P2 |
| **OpenImageIO** | Image I/O + Metadata | Asset Ingestion (probe, thumbnail, proxy) | P2 |
| **OpenEXR** | HDR Image Format | Artifact Type (intermediate render output) | P2 |
| **MaterialX** | Material + Look Description | Asset Ecosystem (style marketplace) | P3 |
| **OpenFX** | Plugin Standard | Extension Module (effect plugin standard) | P3 |
| **Rez** | Runtime Packaging | Worker Environment (render worker deps) | P3 |
| **OpenAssetIO** | Asset Resolution | Asset Registry (entity reference resolution) | Deferred |

---

## 32. OpenCue — Render Farm & Job Scheduling

### Purpose

OpenCue is an open-source render manager for visual effects and animation. It provides:
- **Job Scheduling** — submit render jobs to a pool of machines
- **Layer Scheduling** — jobs decomposed into layers (frames, segments)
- **Frame Scheduling** — individual frames dispatched to available hosts
- **Host Management** — track machine health, capacity, and job allocation
- **Dependency Management** — jobs can depend on other jobs completing

Used in production by Sony Pictures Imageworks, Google Cloud, and others.

### Platform Mapping

```
Platform Layer              →  OpenCue Equivalent
─────────────────────────────────────────────────────
Artifact DAG (nodes + deps) →  OpenCue Job (layers + frames)  *(indefinitely deferred, P2A.2; current OpenCue work does not depend on Artifact DAG)
Execution Graph (bindings)  →  OpenCue Job Dispatch
Render Job (logical)        →  OpenCue Job
Artifact Node (per-segment) →  OpenCue Layer
Frame Rendering (per-frame) →  OpenCue Frame
Worker Registry             →  OpenCue Host / RQD
Worker Heartbeat            →  OpenCue Host Report
```

### Adoption Strategy

| Capability | Status | Approach |
|-----------|--------|----------|
| **Artifact DAG → Job mapping** | ✅ Implemented (artifact_graph + unified_request_graph) | Map to OpenCue job submission API |
| **Worker registry** | ✅ Implemented (RenderFarm Worker Registry) | Replace with OpenCue host management |
| **Job scheduling** | ✅ Basic (RenderRequest → Bind → Execute) | Replace with OpenCue scheduler (more sophisticated: priority, preemption, frame range) |
| **Host management** | ⚠️ Basic (heartbeat-based health check) | OpenCue provides professional host lifecycle management |
| **Frame-level dispatch** | ❌ Not implemented (segment-level only) | OpenCue provides per-frame scheduling for image sequence renders |
| **Dependency chains** | ✅ Implemented (ArtifactNode parent relationship) | Map to OpenCue job dependencies |

### Recommendation

**Reference Only for Phase 1-3. Future adapter spike for Phase 4+.**

**Why not now:** Our worker registry and execution graph handle current render workloads. OpenCue's value (frame-level scheduling, host pools, priority queues) applies at production farm scale (50+ machines, 1000+ concurrent jobs). We are not at that scale yet.

**Why later:** When the platform needs multi-machine render farm capabilities (beyond single-worker FFmpeg/Remotion rendering), OpenCue is the standard integration target.

---

## 33. OpenColorIO — Color Management

### Purpose

OpenColorIO (OCIO) is the industry-standard color management framework. It provides:
- **Color space conversion** — transform between any supported color spaces
- **Display transforms** — render to specific display devices (sRGB, P3, Rec.709, HDR)
- **ACES** — Academy Color Encoding System workflow support
- **LUT management** — lookup table files for color transformations
- **Cross-application consistency** — same config across Nuke, Blender, Maya, Houdini

Used in every major VFX and animation studio. Configuration-driven (OCIO config file defines color spaces, roles, and transforms).

### Platform Mapping

```
Platform Layer                  →  OpenColorIO Integration Point
────────────────────────────────────────────────────────────────
Render Provider (FFmpeg)        →  Apply OCIO color transform on render output
Render Provider (Remotion)      →  Apply OCIO display transform for preview
Effect: Color Grade             →  Store OCIO config reference in effect metadata
Asset Governance: colorPipeline →  "ACES", "Rec.709", "Custom OCIO" enum values
Preview Pipeline                →  Apply OCIO display transform for consistent preview
```

### Priority Assessment

**P2 — High strategic value, implemented as render provider capability.**

| Reason | Priority |
|--------|----------|
| Color consistency across providers (FFmpeg, Remotion, BMF) | P2 |
| Professional color pipeline support (ACES, HDR) | P2 |
| Preview fidelity (what you see = what you render) | P2 |
| LUT-based color grading integration | P2 |
| Standards compliance (SMPTE, ACES) | P3 |

**Not P0 because:** Most initial use cases (social media, YouTube, internal video) don't require ACES or HDR. Color management becomes critical when serving professional VFX/cinema clients.

---

## 34. OpenImageIO — Image I/O & Metadata

### Purpose

OpenImageIO (OIIO) is the industry-standard image reading and writing library. It provides:
- **Format-agnostic image I/O** — read/write JPEG, PNG, TIFF, EXR, DPX, and 50+ formats
- **Metadata extraction** — EXIF, XMP, IPTC, custom metadata from image files
- **Thumbnail generation** — efficient thumbnail and proxy creation
- **Image sequence handling** — frame-numbered sequences as logical assets
- **Color space awareness** — read color space metadata from image files

### Platform Mapping

```
Platform Layer                  →  OpenImageIO Integration Point
──────────────────────────────────────────────────────────────────
Asset Ingestion Pipeline        →  Probe media metadata (codec, fps, color space, duration)
Asset Search (metadata)         →  Index extracted metadata for search queries
Thumbnail / Proxy Generation    →  Generate thumbnails for asset browsing
Image Sequence Support          →  Handle frame sequences as single logical assets
Asset Registry: probe metadata  →  Enrich Asset metadata with OIIO-extracted fields
```

### Priority Assessment

**P2 — High practical value for asset ingestion pipeline.**

| Use Case | Priority |
|----------|----------|
| Format-agnostic ingestion (50+ image formats) | P2 |
| Metadata extraction for search (EXIF, XMP, geo tags) | P2 |
| Thumbnail / proxy generation pipeline | P2 |
| Image sequence handling (frame-numbered assets) | P3 |

---

## 35. OpenEXR — HDR Intermediate Format

### Purpose

OpenEXR is the industry-standard high dynamic range (HDR) image file format. It provides:
- **HDR support** — 16-bit float, 32-bit float per channel
- **Multi-layer** — multiple image layers in one file (beauty, matte, depth, normal)
- **Multi-view** — stereo/multi-camera support
- **Arbitrary channels** — beyond RGBA (e.g., Z-depth, motion vectors, object IDs)
- **Lossless compression** — multiple compression schemes for different use cases

### Platform Mapping

```
Platform Layer                  →  OpenEXR Integration Point
────────────────────────────────────────────────────────────────
Artifact Type (intermediate)    →  Render intermediate outputs as EXR (multi-layer)
Asset Type (VFX render output)  →  VFX render outputs with channels (beauty, matte, depth)
Render Output (final)           →  EXR for HDR delivery (cinema, VFX)
Marketplace: VFX Assets         →  EXR-based VFX templates and assets
```

### Priority Assessment

**P2 — Format support for professional VFX workflows.**

| Use Case | Priority |
|----------|----------|
| Intermediate render output format (multi-layer for compositing) | P2 |
| HDR delivery format (cinema, streaming) | P2 |
| VFX asset exchange format | P3 |
| Marketplace VFX templates | P3 |

**Not P0 because:** MP4/H.264 is sufficient for initial use cases. EXR becomes necessary when the platform serves VFX/compositing pipelines.

---

## 36. MaterialX — Material & Look Description

### Purpose

MaterialX is an open standard for transfer of rich material and look-development content between applications. It provides:
- **Material description** — shader graphs with nodes and connections
- **Look description** — material assignments + overrides for a scene
- **Application-agnostic** — same material definition in Maya, Blender, Houdini, Unreal
- **Shader generation** — compile MaterialX graphs to target shader languages (GLSL, OSL, MDL)
- **Library of standard nodes** — standardized surface shader nodes

### Platform Mapping

```
Platform Layer                  →  MaterialX Integration Point
────────────────────────────────────────────────────────────────
Style Asset                      →  MaterialX Look file as Style asset
Effect: Color Grade / Look      →  MaterialX material assignment
Marketplace: Style Marketplace  →  MaterialX-based look assets (cinematic looks)
Template: Scene Look            →  MaterialX look applied to timeline template
```

### Long-term Potential

**Reference for Phase 1-3. Future integration for Phase 4+ (Style Marketplace).**

| Stage | Role |
|-------|------|
| **Reference** | Understand MaterialX node graph and standard surface model as patterns for our Effect/ Style models |
| **Future Integration** | Support MaterialX `.mtlx` files as Style assets in the marketplace |
| **Core Platform** | No — MaterialX is a VFX-specific standard. Our platform's Style model is broader (subtitle styles, UI overlays, not just 3D materials). |

**Verdict:** MaterialX is valuable as a **reference pattern** for how to describe materials/looks in an application-agnostic way. It could become an **import/export format** for Style assets in the marketplace, similar to how OTIO serves as the interchange format for timelines.

---

## 37. OpenFX — Plugin Standard

### Purpose

OpenFX is an open standard for visual effects plug-ins. It provides:
- **Plugin API** — standardized interface for image processing effects
- **Host-side API** — how host applications discover, load, and control plugins
- **Parameter model** — standard parameter types (float, int, color, position, string)
- **GPU acceleration** — OpenCL and CUDA support within plugins
- **Cross-host compatibility** — same plugin works in Nuke, Natron, Resolve, Vegas

Used by Natron (open-source compositor), DaVinci Resolve, and Vegas Pro.

### Platform Mapping

```
Platform Layer                  →  OpenFX Integration Point
────────────────────────────────────────────────────────────────
Effect Asset                     →  OpenFX plugin as Effect asset
Extension Module: Effect Plugin  →  OpenFX plugin loaded via ExtensionProvider
Plugin Marketplace                →  OpenFX-compatible plugins listed in marketplace
Provider Capability              →  "supports OFX" capability flag
Effect: Custom Effect            →  User brings OFX plugin → registered as effect
```

### Recommendation

**Reference for Phase 1-3. Adapter spike for Phase 4+.**

**Value proposition:** OpenFX would allow users to bring their own VFX plugins (from Natron, Resolve) into the platform. This is the "plugin ecosystem" for visual effects — similar to how OTIO is the interchange format for timelines.

**Why not now:**
1. Our effect model is provider-agnostic (FFmpeg filters, Remotion effects, caption providers). OpenFX is a specific plugin standard.
2. Effect marketplace doesn't exist yet (P3). OpenFX integration would be a marketplace feature.
3. Requires a compositing host to load OpenFX plugins — we would need Natron or a custom OFX host.

**Why later:** OpenFX support makes the effect marketplace immediately valuable — users can upload existing OFX plugins and have them work in the platform. This is a Phase 4+ capability.

---

## 38. Rez — Runtime Environment & Dependency Management

### Purpose

Rez is a cross-platform package manager and runtime environment for VFX and animation pipelines. It provides:
- **Package resolution** — resolve compatible versions of software packages
- **Dependency isolation** — create isolated runtime environments for each job
- **Version management** — multiple versions of the same package coexist
- **Shell integration** — spawn shells with configured environments
- **Release management** — package build, test, and release workflow

Used in production at Method Studios, Animal Logic, and other studios.

### Platform Mapping

```
Platform Layer                  →  Rez Integration Point
────────────────────────────────────────────────────────────────
Render Worker Environment       →  Rez resolves and provisions worker runtime
Plugin Runtime                   →  Rez ensures plugin dependencies are available
Provider Runtime                 →  Rez configures FFmpeg/BMF/Remotion versions
Extension Module: Versioning     →  Rez-style package resolution for extensions
```

### Adoption Strategy

**Reference for Phase 1-3. Optional for Phase 4+.**

| Stage | Rationale |
|-------|-----------|
| **Reference** | Understand package resolution and dependency isolation patterns |
| **Optional** | Docker containers already provide environment isolation for render workers |
| **Future Standard** | Only if the platform needs bare-metal worker provisioning (most deployments use containers) |

**Verdict:** Rez's package resolution pattern is valuable as a **design reference** for extension/plugin version management. However, container-based deployment (Docker/Kubernetes) solves the same problem for most use cases. Rez is more relevant for bare-metal VFX render farms.

---

## 39. OpenAssetIO — Reassessment with Asset Registry Context

### Current State (Updated)

Since the original OpenAssetIO assessment in §15, we have implemented:

| Implementation | Status |
|---------------|--------|
| `asset` table with `entity_ref` column | ✅ V1 schema |
| `AssetIdentity` record (assetId, assetVersion, entityRef, xmpUri) | ✅ Domain model |
| `AssetRegistryService` (register, resolve, buildOtioClipMetadataRef) | ✅ Service |
| `AssetJsonLdExporter` (JSON-LD projection) | ✅ Exporter |

### Future Mapping

```
Current State                           →  Future State with OpenAssetIO
────────────────────────────────────────────────────────────────────────
entity_ref = "asset://asset_123?v=v7"   →  OpenAssetIO Entity Reference
AssetRegistryService.resolve(assetId)   →  OpenAssetIO Manager.resolve(entityRef)
AssetRegistryService: storageUri        →  OpenAssetIO Plugin: S3/OSS/GCS resolver
AssetRegistry: asset_version column     →  OpenAssetIO: version-aware resolution
AssetRegistry: governance fields        →  OpenAssetIO: metadata attached to resolved entity
```

### Priority Reassessment

**Deferred (2027+).** Rationale unchanged from the original assessment:

1. **OpenAssetIO adds no user-visible value on its own.** It's an integration layer that enables DAM/MAM interoperability. No user asks for "OpenAssetIO support" — they ask for "Can I use my existing asset library?"
2. **Our asset registry already resolves identity → storage.** OpenAssetIO would standardize this resolution, but doesn't add new capability.
3. **Deploying OpenAssetIO requires a DAM/MAM to connect to.** We don't have one yet (deferred). OpenAssetIO without a connected asset management system is a pointless abstraction.

**When to revisit:** When the platform needs to integrate with external DAM/MAM systems (e.g., customers want to use their existing ShotGrid/Flow Production Tracking asset library). This is a 2027+ requirement.

---

## 40. Updated Summary Table

### ASWF Projects — Status Matrix

| Project | Platform Layer | Reference Value | Integration Difficulty | Strategic Value | Phase | Recommendation |
|---------|---------------|----------------|----------------------|----------------|-------|----------------|
| **OpenTimelineIO** | OTIO Adapter | 10/10 | 3/10 | 10/10 | ✅ | Core dependency |
| **OpenCue** | Execution Graph | 8/10 | 7/10 | 6/10 | P3 | Reference → Future Adapter |
| **OpenColorIO** | Render Pipeline | 9/10 | 5/10 | 8/10 | P2 | Future adapter spike |
| **OpenImageIO** | Asset Ingestion | 8/10 | 4/10 | 7/10 | P2 | Future adapter spike |
| **OpenEXR** | Artifact Format | 7/10 | 3/10 | 6/10 | P2 | Format support |
| **MaterialX** | Style Marketplace | 6/10 | 6/10 | 5/10 | P3 | Reference only |
| **OpenFX** | Effect Marketplace | 7/10 | 8/10 | 7/10 | P3 | Reference → Future adapter |
| **Rez** | Worker Environment | 5/10 | 7/10 | 4/10 | P3 | Reference only |
| **OpenAssetIO** | Asset Registry | 8/10 | 6/10 | 5/10 | Deferred | Reference only |

### Key

| Scale | Meaning |
|-------|---------|
| 1-3 | Low |
| 4-6 | Medium |
| 7-8 | High |
| 9-10 | Critical |

---

## 41. Asset Ingestion References

### Industry Tools — What We Learn

| Reference | Borrow | Apply To |
|-----------|--------|----------|
| **Adobe Experience Manager (AEM)** | Enterprise DAM — metadata extraction, asset workflow, review/approval, taxonomy | Asset review workflow, governance pipeline |
| **Frame.io** | Asset version comparison, timestamp comments, stakeholder review | Asset review UX (entity-anchored, not timecode-only) |
| **Iconik** | Hybrid cloud/on-premise media management, AI enrichment (transcription, object recognition), smart collections, REST API-first | AI enrichment pipeline design, hybrid deployment model |
| **Immich** | Self-hosted photo/video manager, face detection, object recognition, semantic search, duplicate detection | AI enrichment pipeline (self-hosted first), vector search |
| **Eagle** | Desktop asset manager — hierarchical tagging, smart folders, visual browsing, color-based search | Asset taxonomy, visual search UX, collection management |
| **OpenImageIO** | Industry-standard image I/O — 50+ formats, EXIF/XMP/color space metadata, thumbnail generation, image sequence handling | Probe layer — format-agnostic media ingestion |

### Explicitly Not Borrowed

| Reference | Why |
|-----------|-----|
| **AEM's monolithic DAM** | We build modular platform; AEM is a monolithic enterprise suite |
| **Frame.io's video hosting/CDN** | We use platform-agnostic S3/OSS/GCS; don't build a CDN |
| **Iconik's SaaS-only model** | Platform must support self-hosted deployment |
| **Immich's photo-first design** | We support all media types equally |
| **Eagle's desktop-only model** | We are cloud-native with desktop/web clients |

### Mapping to Platform Layers

```
Reference                        →  Platform Layer
─────────────────────────────────────────────────────────
AEM Review/Approval              →  TimelineReview (reuse for assets)
Frame.io Version Compare         →  Asset version diff
Iconik AI Enrichment             →  ASR + OCR + Vision pipelines
Iconik Smart Collections         →  Asset Search (hybrid)
Immich Face/Object Detection     →  Vision pipeline
Immich Semantic Search           →  Embedding + Vector DB
Eagle Tagging/Taxonomy           →  Asset tags + categories
OpenImageIO Probe                →  Probe layer (FFprobe + OIIO)
```

---

## 42. Updated Strategic References

| Document | Relationship |
|----------|-------------|
| [Asset Ingestion Blueprint](asset-ingestion-blueprint.md) | Full ingestion lifecycle design |
| [Asset Ingestion Analysis](../../review/asset-ingestion-analysis.md) | Capability gap analysis |
| [Asset Ecosystem Blueprint](asset-ecosystem-blueprint.md) | Marketplace, search, asset types |
| [ASWF Ecosystem Analysis](../../review/aswf-ecosystem-analysis.md) | ASWF integration roadmap |
| [Domain Event & Outbox Blueprint](domain-event-outbox-blueprint.md) | Event-driven architecture |
| [Platform Coordination Blueprint](platform-coordination-blueprint.md) | Coordination layer architecture |
| [Domain Event & Outbox Audit](../../review/domain-event-outbox-audit.md) | Existing event infrastructure audit |
| [Platform Coordination Analysis](../../review/platform-coordination-analysis.md) | Coordination gap analysis |

---

## 44. Coordination & Workflow References

### Industry Patterns — What We Learn

| Reference | Pattern | Apply To |
|-----------|---------|----------|
| **Temporal** | DAG workflow orchestration, retry policies, Saga compensation, human-in-the-loop | Future complex workflows (2027+) |
| **Netflix Conductor** | JSON-based workflow DSL, task routing, fork/join, decision tasks | Fork/join pattern for fan-out/fan-in |
| **Cadence (Uber)** | Multi-tenant workflow engine, activity heartbeating, local activities | Activity heartbeat pattern for task leasing |
| **AWS Step Functions** | State machine as JSON, parallel branches, choice states, error handling with retry/catch | State machine pattern for job status transitions |
| **Airflow** | DAG-based task scheduling, sensors (wait for external condition), backfill | Sensor pattern for barrier/aggregation |
| **Dagster** | Software-defined assets, I/O managers, partitioned assets, backfill | Asset-centric workflow model |
| **PostgreSQL LISTEN/NOTIFY** | In-database pub/sub for low-latency wake-up | Wake-up signal for task dispatchers (not reliable delivery) |
| **PGMQ** | PostgreSQL-native message queue — FIFO, visibility timeout, archival | Assessed — task table is better for coordination needs |

### What We Borrow vs. Reject

| Reference | Borrow | Reject |
|-----------|--------|--------|
| **Temporal** | DAG model, retry policies, saga pattern | Why: overkill for current coordination needs. platform_job + platform_task + outbox is sufficient. |
| **Netflix Conductor** | Fork/join pattern, task routing | JSON DSL — we use table-driven models |
| **AWS Step Functions** | State machine for job status transitions | Cloud-locked — we are platform-agnostic |
| **Airflow** | Barrier/sensor pattern | DAG scheduling — we use event-driven, not schedule-driven |
| **PostgreSQL LISTEN/NOTIFY** | Wake-up pattern ✅ | Reliability — we use outbox for delivery guarantee |
| **PGMQ** | Queue semantics | Why: task table with bitmask solves coordination better |

### Architecture Decision: PostgreSQL-Native Coordination

| Question | Answer | Why |
|----------|--------|-----|
| Do we need Temporal now? | No | Coordination needs are simple: fan-out/fan-in + retry. platform_job/task handles this. |
| Do we need Kafka? | No | Internal consumers (< 10) use Spring events. External consumers (future webhooks) can use outbox. |
| Do we need PGMQ? | No | Task table with bitmask barrier is purpose-built for coordination. PGMQ is a message queue — different problem. |
| Do we need LISTEN/NOTIFY? | Yes — as optimization | Wakes dispatchers in near-real-time. Complements 3s polling, doesn't replace it. |
| When do we need Temporal? | 2027+ | Complex DAG workflows, human-in-the-loop, Saga compensation, multi-service orchestration with days-long waits. |

---

## 43. Domain Event Architecture References

### Industry Patterns — What We Learn

| Reference | Pattern | Apply To |
|-----------|---------|----------|
| **Stripe** | Outbox pattern for payment → invoice → notification. Exactly-once via idempotency keys. Event routing via webhooks. | Our outbox (already built). Extend to all domains. |
| **GitHub** | Webhook events for every action (PR opened, merged, comment added). Reliable delivery with retry + signature verification. | Event catalog — one event per user action |
| **Linear** | Every mutation produces an event → audit trail + notification + webhook. Real-time sync via WebSocket. | Model our audit trail |
| **Shopify** | Webhook subscriptions for store events. Customer-configurable event routing. | Future webhook subscription API |
| **Frame.io** | Review status change events → notifications + webhooks. Real-time presence. | Review domain events |
| **Figma** | Document change events → outbox → async consumers + WebSocket sync. | Asset domain events |
| **Kafka** | Partitioned event log. At-least-once delivery. Consumer group offset tracking. | Future external event bus (Phase 5) |
| **RabbitMQ** | Exchange + queue routing. Dead-letter exchanges. Message TTL. | Lighter alternative to Kafka for simpler deployments |

### Outbox Pattern — Why We Use It

```
Service writes to database + outbox in same transaction
    → OutboxDispatcher polls outbox_events
        → Publishes to application event bus
            → Consumers react (audit, notification, search, marketplace)
```

| Benefit | Without Outbox | With Outbox |
|---------|---------------|-------------|
| **Atomicity** | Event may be lost if transaction fails after publish | Event is written in same DB transaction |
| **Reliability** | If publisher crashes, event is lost | OutboxDispatcher retries with exponential backoff |
| **Decoupling** | Services call each other directly | Services consume events; no direct dependencies |
| **Idempotency** | Duplicate events on retry | `idempotency_key` prevents duplicate processing |
| **Auditability** | No record of what happened | Every event in `outbox_events` is an audit trail |

### Why NOT Event Sourcing

We use the outbox pattern, not full event sourcing:

| Choice | Rationale |
|--------|-----------|
| **Current state in database** | Asset table is authoritative. Rebuilding from events would be slower and more complex. |
| **Outbox for async consumers** | Audit, notification, search, marketplace need to know "what happened" — they don't need to rebuild state. |
| **No event store** | We don't need event replay for business logic. Temporal handles workflow orchestration. |

### Mapping to Platform Layers

```
Reference                        →  Platform Layer
─────────────────────────────────────────────────────────
Stripe Outbox                     →  OutboxEventService (already built)
GitHub Event Catalog             →  Timeline + Review + Asset events
Linear Audit Trail               →  AuditEventHandler
Shopify Webhook Subscriptions    →  Future webhook subscription API
Frame.io Review Events           →  Review domain events
Figma Document Events            →  Asset domain events
Kafka/RabbitMQ                   →  Future external event bus (Phase 5)

---

## 45. PostgreSQL-backed Coordination References

### Technology Comparison

| Technology | Best For | Why Not Now | Future Trigger |
|-----------|----------|-------------|----------------|
| **Transactional Outbox** (outbox_events) | Reliable event delivery. At-least-once. Idempotent. Crash-safe. | Already adopted. ✅ | Stays forever. Add Kafka adapter later. |
| **platform_job / platform_task** | Fan-out/fan-in coordination. Barrier/aggregation. Task retry with lease. | Already designed. Next sprint. | Replace with Temporal only if workflows become complex DAGs. |
| **PostgreSQL LISTEN/NOTIFY** | Low-latency wake-up for dispatchers. Near-real-time (< 100ms). | Not yet used. Optimization, not reliability. | Implement after platform_job/task. |
| **Bitmask Barrier** | Fast completion check (1 int compare). Derives from task state. | Part of platform_job design. | Replace with explicit barrier table only if > 32 tasks needed. |
| **PGMQ** (PostgreSQL Message Queue) | Message queuing in PostgreSQL. FIFO, visibility timeout, archival. | Rejected. Task table solves coordination better. PGMQ is a queue — coordination is more than queuing. | Never. Task table is purpose-built for coordination. |
| **LiteFlow** | Local business rule/policy chains. Route decisions based on conditions. | Rejected for coordination. LiteFlow is a rule engine, not a coordination engine. May integrate later for business rules. | If policy chains become complex (e.g., "if asset is AI-generated AND contains PII, route to legal review"). |
| **Temporal** | Complex DAG workflows. Retry policies. Saga compensation. Human-in-the-loop with arbitrary waits. | Deferred. platform_job/task handles current needs. Temporal adds operational complexity (Temporal server, workers, namespaces). | When workflows span multiple services with days-long waits OR need Saga pattern. |
| **Netflix Conductor** | JSON-based workflow DSL. Fork/join, decision tasks. | Deferred. Similar complexity to Temporal. Less ecosystem maturity. | Unlikely — Temporal is stronger candidate. |
| **AWS Step Functions** | State machine as JSON. AWS-native. | Deferred. Platform is cloud-agnostic. Vendor lock-in. | Only if deployed on AWS exclusively. |
| **Kafka / RabbitMQ** | External event bus. Cross-service pub/sub. 100+ consumers. | Deferred. < 10 internal consumers use Spring events. Outbox handles reliable delivery. | When external consumers need events OR webhook delivery at scale. |

### Decision Matrix

| Criterion | Outbox Only | Outbox + Job/Task + NOTIFY | PGMQ | LiteFlow | Temporal | Kafka |
|-----------|------------|---------------------------|------|---------|----------|-------|
| **Reliability** | ✅ | ✅ | ✅ | ⚠️ Rule-dependent | ✅ | ✅ |
| **Observability** | ✅ Query table | ✅ Query tables | ⚠️ Queue ops only | ⚠️ Logs only | ✅ Temporal UI | ✅ Kafka UI |
| **Re-drive/Retry** | ⚠️ Manual | ✅ Task lease + backoff | ✅ Visibility timeout | ❌ No built-in | ✅ Retry policies | ✅ Consumer offset |
| **Fan-out/Fan-in** | ❌ No | ✅ Bitmask barrier | ❌ No | ❌ No | ✅ Child workflows | ❌ No (needs app logic) |
| **Operational Complexity** | Low | Low | Medium (extension) | Medium | High (separate server) | High (cluster) |
| **Future Migration** | Stay | Migrate to Temporal when needed | Rip out | Rip out | Already there | Already there |

### Final Decision

**PostgreSQL-backed Lightweight Coordination:**

```
outbox_events (domain events — immutable facts)
platform_job (coordination — fan-out/fan-in)
platform_task (task state — lease, retry, dead-letter)
LISTEN/NOTIFY (wake-up — optimization, not reliability)
Spring consumers (audit, notification, search, marketplace)
```

This architecture is chosen because:
1. **All state in PostgreSQL ACID** — no external queue, no external workflow engine. Crash-safe by definition.
2. **Operational complexity stays low** — no Temporal server, no Kafka cluster, no PGMQ extension. Just PostgreSQL.
3. **Purpose-built for coordination** — bitmask barrier, task lease, exponential backoff are built into the model, not layered on top of a queue.
4. **Future migration path is clear** — When coordination needs exceed this model (complex DAGs, Saga patterns, days-long waits), Temporal is the upgrade target. Outbox stays. Kafka adapter connects to outbox for external consumers.
