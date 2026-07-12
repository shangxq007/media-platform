# Open-source Capability Extension Blueprint

**Date:** 2026-07-08
**Status:** REFERENCE
**Authority:** OPEN-SOURCE-CAPABILITY-REFERENCE.0

---

## Core Principle

> **开源项目负责能力实现；平台自己负责对象模型、生命周期、权限、追踪和产品语义。**
>
> Open-source projects implement capabilities.
> The platform owns contracts, lifecycle, provenance, and product semantics.

---

## Platform-Owned Core Models

These models are **platform-owned** and must NOT be replaced by external tools:

| Model | Responsibility |
|-------|---------------|
| **Product** | User-facing product definition and lifecycle |
| **TimelineRevision** | Timeline versioning and edit history |
| **RenderJob** | Render execution lifecycle and status |
| **Artifact** | Output artifact metadata and provenance |
| **Provider** | Capability provider abstraction |
| **Worker** | Execution worker abstraction |
| **Provenance** | Origin, lineage, and audit trail |
| **Tenant / Project** | Multi-tenant ownership and scoping |
| **API Contracts** | Public API surface and versioning |
| **Lifecycle States** | Job/product/artifact state machines |
| **Authorization** | Permission and access control semantics |

---

## Open-source Entry Boundaries

External tools must enter through **Provider / Adapter / SPI boundaries**:

| Tool | Entry Point | What It Implements |
|------|-------------|-------------------|
| **OpenDAL** | `StorageProvider` | Storage access abstraction |
| **OpenCue** | `WorkerBackend` | Distributed render scheduling |
| **FFmpeg** | `RenderProvider` | Media processing baseline |
| **OpenTimelineIO** | `TimelineAdapter` | Timeline import/export |
| **React Flow** | Frontend layer | Visualization only |
| **LangChain4j** | `AiProvider` | AI capability adapter |
| **Apache Camel** | `ConnectorProvider` | Ingest/publish connectors |
| **Apache Tika** | `DocumentExtractor` | Document ingest |

---

## Fourteen Capability Domains

### 1. Workflow / Node Canvas

| Candidate | Role | Status |
|-----------|------|--------|
| React Flow / xyflow | Frontend visual graph | Candidate |
| Rete.js | Node editor | Candidate |
| LiteGraph.js | Graph library | Candidate |
| Node-RED | Flow reference | Reference only |

**Not allowed to replace:** RenderJob lifecycle, TimelineRevision, Product model.

### 2. Queue / Background Jobs

| Candidate | Role | Status |
|-----------|------|--------|
| JobRunr | Lightweight job queue | Candidate |
| Quartz | Scheduler | Candidate |
| Spring Batch | Batch processing | Candidate |
| Temporal | Workflow orchestration | Deferred |
| Argo Workflows | K8s workflows | Deferred |
| OpenCue | Render worker backend | Future extension |

**Current decision:** Candidate, not adopted. OpenCue remains NOT STARTED.

### 3. Connector Ecosystem

| Candidate | Role | Status |
|-----------|------|--------|
| Apache Camel | Connector SPI | Candidate |
| Spring Integration | Messaging/connectors | Candidate |
| Airbyte | Data integration | Reference only |
| Meltano | ELT pipeline | Reference only |

**Not allowed to replace:** Product / Artifact ownership.

### 4. AI / Agent Provider Layer

| Candidate | Role | Status |
|-----------|------|--------|
| LangChain4j | Java AI framework | Candidate |
| Spring AI | Spring AI integration | Candidate |
| LiteLLM | Model routing gateway | Candidate |
| Dify | AI platform reference | Reference only |

**Not allowed to replace:** Timeline core, render pipeline, Product semantics.

### 5. Document and Multimodal Ingest

| Candidate | Role | Status |
|-----------|------|--------|
| Apache Tika | Document extraction | Candidate / POC recommended |
| Unstructured | Document parsing | Candidate |
| Pandoc | Document conversion | Candidate |
| LibreOffice headless | Office conversion | Candidate |
| Whisper / faster-whisper | Transcription | Candidate |

### 6. Sandbox / Custom Code Execution

| Candidate | Role | Status |
|-----------|------|--------|
| Docker sandbox | Container isolation | Deferred |
| gVisor | Sandboxed containers | Deferred |
| Firecracker | MicroVM | Deferred |
| WASM / Wasmtime | WebAssembly runtime | Deferred |

**Decision:** Deferred. High risk.

### 7. Auth / Authorization / Audit

| Candidate | Role | Status |
|-----------|------|--------|
| Spring Security | Auth baseline | Existing baseline |
| Keycloak | IAM | Candidate |
| OpenFGA | Fine-grained auth | Candidate |
| Casbin | Policy engine | Candidate |
| OPA | Policy agent | Candidate |

### 8. Observability / RenderOps / LLMOps

| Candidate | Role | Status |
|-----------|------|--------|
| OpenTelemetry | Observability framework | P0 Candidate |
| Prometheus | Metrics | Candidate |
| Grafana | Dashboards | Candidate |
| Loki | Logs | Candidate |
| Sentry | Error tracking | Candidate |
| Langfuse | LLMOps | Deferred |

### 9. Search / Index / Metadata

| Candidate | Role | Status |
|-----------|------|--------|
| PostgreSQL full-text | Search baseline | P1 recommended |
| pgvector | Vector search | P1 recommended |
| Meilisearch | Search engine | P2 candidate |
| Typesense | Search engine | P2 candidate |
| Qdrant / Milvus | Vector DB | P3 candidate |

### 10. API Gateway / Webhook / External Events

| Candidate | Role | Status |
|-----------|------|--------|
| CloudEvents | Event contract | P1 recommended |
| AsyncAPI | Async API spec | P1 recommended |
| Svix | Webhook delivery | P2 candidate |
| Hookdeck | Event gateway | P2 candidate |

### 11. Frontend Engineering and Visualization

| Candidate | Role | Status |
|-----------|------|--------|
| TanStack Router/Query/Table | Frontend framework | Existing baseline |
| Zod | Schema validation | Existing baseline |
| React Flow / xyflow | Graph visualization | P2 candidate |
| Monaco Editor | Code editor | P2 candidate |
| Video.js / Shaka Player | Video playback | Candidate |
| WaveSurfer.js | Waveform visualization | P2 candidate |
| Remotion Player | Video preview | Candidate |

### 12. Config / Feature Flags / Provider Gates

| Candidate | Role | Status |
|-----------|------|--------|
| Spring Config | Configuration | Existing baseline |
| OpenFeature | Feature flags | P2 candidate |
| Unleash | Feature management | P3 candidate |

### 13. Database / Migration / Audit / Testing

| Candidate | Role | Status |
|-----------|------|--------|
| Flyway | Migration baseline | Existing baseline |
| Testcontainers | Integration tests | P0 recommended |
| jOOQ | SQL builder | Existing baseline |
| Hibernate Envers | Audit | Deferred |
| Debezium | CDC | Deferred |

### 14. Media Capability Provider Ecosystem

| Candidate | Role | Status |
|-----------|------|--------|
| FFmpeg / ffprobe | Media processing | Existing baseline |
| MediaInfo | Media analysis | P0 candidate |
| libass | Subtitle rendering | P1 candidate |
| OpenTimelineIO | Timeline adapter | P2 candidate |
| OpenCue | Worker backend | Future extension |
| OpenDAL | Storage abstraction | P2 candidate |
| Bento4 / GPAC | Packaging | P2 candidate |
| GStreamer | Media framework | P3 deferred |
| MLT | Media framework | P3 deferred |
| Remotion | Video rendering | P2 candidate |
| Natron | Compositing | P3 deferred |
| VapourSynth | Video processing | P3 deferred |

---

## Priority Classification

| Priority | Tools |
|----------|-------|
| **P0** | Testcontainers, OpenTelemetry, ffprobe/MediaInfo, artifact/content safety |
| **P1** | OpenDAL POC, Apache Tika POC, WebhookDeliveryProvider, PostgreSQL full-text/pgvector |
| **P2** | React Flow, JobRunr, Apache Camel, LangChain4j, OpenTimelineIO, Bento4/GPAC |
| **P3** | Temporal, OpenAssetIO, GStreamer, MLT, sandboxed custom code |

---

## Reference Platform Patterns

| Project | Pattern to Learn | What to Borrow | What NOT to Copy |
|---------|-----------------|----------------|------------------|
| **n8n** | Workflow/node ecosystem | Connector marketplace, custom nodes, run history | Don't replace RenderJob lifecycle |
| **Dify** | Provider abstraction | AI workflow, model management, observability | Don't make AI workflow the core |
| **Node-RED** | Node-based flow | Visual debugging, event flow | Don't use as render orchestration |
| **Airbyte** | Connector catalog | Source/dest abstraction, sync observability | Don't make ingest purely ETL |
| **Temporal** | Long-running workflows | Retry/compensation patterns | Don't introduce before OpenCue proves need |
| **Grafana** | Dashboard/plugin | Observability UX | Don't replace operational domain model |
| **GitLab** | Project/job/artifact traceability | Pipeline history, audit | Don't force Timeline Git too early |

---

## Current Decision Status

| Tool | Status | Notes |
|------|--------|-------|
| FFmpeg | Existing baseline | Temporary in platform-api |
| OpenCue | Future extension | NOT STARTED |
| OpenDAL | Candidate | POC recommended |
| React Flow | Candidate | Frontend visualization only |
| JobRunr | Candidate | Lightweight queue |
| Apache Camel | Candidate | Connector SPI |
| LangChain4j | Candidate | AI provider adapter |
| Apache Tika | Candidate | POC recommended |
| Temporal | Deferred | Not needed yet |
| Artifact DAG | Postponed | Until real reuse need |

---

## See Also

- [Capability Opening Blueprint](capability-opening-blueprint.md)
- [Module Blueprint: Render](module-blueprint-render.md)
- [Current System State](../current/current-system-state.md)
