# Integration Lab Architecture

**Date:** 2026-07-12
**Status:** COMPLETE
**Authority:** INTEGRATION-LAB-ARCHITECTURE.0
**Branch:** main
**Base commit:** a22f9cd (PR #16 merge)

---

## Background

Preview baseline merged to main. R2 storage, signed access, SPA fallback, and full user flow verified. Future complexity expected. Need safe path for third-party technology experiments.

---

## Goals

- Allow early evaluation of third-party technologies
- Protect stable preview baseline
- Keep canonical models stable
- Isolate experimental risk
- Define provider lifecycle
- Enable future delivery flexibility

## Non-goals

- No runtime plugin system now
- No dynamic JAR loading
- No marketplace
- No production certification in this task

---

## Vocabulary

| Term | Meaning |
|------|---------|
| **Module** | Architecture boundary. Owns domain/application/service responsibility. Part of source tree. Versioned with platform. |
| **Provider** | Implementation adapter behind stable SPI. Selected by config/profile. Can be experimental or production candidate. |
| **Plugin** | Future packaging/deployment concept. NOT current runtime dynamic loading. |

**Current project uses Module + Provider pattern. Runtime plugin system is NOT introduced.**

---

## Integration Lab Layers

### Storage Integration Lab
- OpenDAL, MinIO, SeaweedFS, JuiceFS, OpenAssetIO

### Ingest / Metadata Integration Lab
- Tika, MediaInfo, ExifTool, FFprobe extensions

### Event / Outbox Integration Lab
- Camel, EventMesh, NATS, Kafka/Redpanda, OpenLineage

### Gateway / Delivery Integration Lab
- APISIX, Cloudflare routing, webhook gateway

### Frontend / Workflow Integration Lab
- React Flow/xyflow, workflow visualizers

---

## Provider Lifecycle

```
CANDIDATE
  → EVALUATION_READY
  → EVALUATING
  → POC
  → EXPERIMENTAL_PROVIDER
  → DISABLED_BY_DEFAULT
  → PREVIEW_CANDIDATE
  → PRODUCTION_CANDIDATE
  → PROMOTED

Alternative paths:
  → REJECTED
  → REMOVED
  → DEFERRED
```

---

## Experimental Provider Rules

| Rule | Description |
|------|-------------|
| Default-off | `enabled=false` by default |
| Profile/flag gated | Only under explicit profile: experimental, lab, poc |
| Startup-safe | No startup-time remote I/O, lazy init |
| No canonical mutation | Must not change Product, Artifact, StorageReference, etc. |
| No schema by default | No DB changes unless justified |
| Removable | Can be removed without breaking stable path |
| Observable | Clear logs with redaction |
| Safe failure | Cannot break stable preview baseline |

---

## Promotion Criteria

- Local tests pass
- Docker packaging verified
- Preview smoke passes
- Security/redaction checks pass
- Rollback plan exists
- No regression to stable path

## Rejection / Removal Criteria

- Native packaging impossible
- Startup instability
- Excessive operational complexity
- Missing required capability
- Unsafe security model
- Poor fit with canonical domain model

---

## Configuration Convention

```yaml
# Storage
storage:
  provider: s3
  experimental:
    opendal:
      enabled: false
      backend: fs
      mode: poc

# Ingest
ingest:
  metadata:
    provider: basic
  experimental:
    tika:
      enabled: false

# Outbox
outbox:
  relay:
    provider: spring-event
  experimental:
    camel:
      enabled: false

# Gateway
gateway:
  provider: none
  experimental:
    apisix:
      enabled: false
```

---

## Candidate Matrix

| Technology | Category | Provider Type | Status | Next Task |
|------------|----------|---------------|--------|-----------|
| Apache OpenDAL | Storage | Materializer | EVALUATION_READY | STORAGE-OPENDAL-EVALUATION.0 |
| Apache Tika | Ingest/Metadata | MetadataProvider | EVALUATION_READY_LATER | INGEST-TIKA-METADATA-EVALUATION.0 |
| Apache Camel | Event/Outbox | RelayProvider | ARCHITECTURE_READY | OUTBOX-CAMEL-RELAY-DESIGN.0 |
| Apache APISIX | Gateway | GatewayProvider | DEPLOYMENT_LAB_LATER | GATEWAY-APISIX-LAB.0 |
| Apache EventMesh | Event | EventProvider | CANDIDATE_LATER | EVENTMESH-EVALUATION.0 |
| MediaInfo | Ingest | MetadataProvider | CANDIDATE | — |
| ExifTool | Ingest | MetadataProvider | CANDIDATE | — |
| MinIO | Storage | StorageProvider | CANDIDATE | — |
| SeaweedFS | Storage | StorageProvider | CANDIDATE | — |
| JuiceFS | Storage | StorageProvider | CANDIDATE | — |
| OpenAssetIO | Storage | AssetProvider | DEFERRED | — |
| OTIO | Timeline | TimelineProvider | DEFERRED | — |
| OpenLineage | Event | LineageProvider | CANDIDATE | — |
| JobRunr | Workflow | JobProvider | CANDIDATE | — |
| LiteFlow | Workflow | FlowProvider | CANDIDATE | — |
| NATS/Redpanda/Kafka | Event | EventBusProvider | CANDIDATE | — |
| React Flow/xyflow | Frontend | Visualizer | CANDIDATE | — |

---

## Current Decisions

| Technology | Status | Notes |
|------------|--------|-------|
| OpenDAL | EVALUATION_READY | Production path DEFERRED. Current R2 path: KEEP_STABLE |
| Tika | EVALUATION_READY_LATER | Must not replace FFprobe |
| Camel | ARCHITECTURE_READY | Outbox relay provider. CODE_DEFERRED |
| APISIX | DEPLOYMENT_LAB_LATER | External gateway, not app dependency |
| EventMesh | CANDIDATE_LATER | After multi-service pressure appears |
| OpenCue | NOT_STARTED | Separate render execution backend |
| Artifact DAG | POSTPONED | Extension layer only |
| Merge | MERGE_EXPERIMENTAL | Not MVP |
| Branch/Patch/ANTLR/CRDT | NOT_INTRODUCED | — |

---

## Safety Gates

- No startup remote I/O
- No secret exposure
- No signed URL persistence
- No preview baseline regression
- No default profile activation
- Rollback/removal plan required

---

## Next Tasks

1. **STORAGE-OPENDAL-EVALUATION.0**
2. **STORAGE-OPENDAL-PROVIDER-POC.0**
3. **INGEST-TIKA-METADATA-EVALUATION.0**
4. **OUTBOX-CAMEL-RELAY-DESIGN.0**
5. **GATEWAY-APISIX-LAB.0**
6. **EVENTMESH-EVALUATION.0**

---

## Status

- INTEGRATION-LAB-ARCHITECTURE.0: COMPLETE
- Architecture doc: CREATED
- Provider lifecycle: DEFINED
- Candidate matrix: CREATED
- No code changes: DOCS_ONLY
