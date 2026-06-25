---
status: blueprint
created: 2026-06-24
scope: platform-wide (future)
truth_level: target
owner: platform
---

# Asset Ecosystem Blueprint

> **Reality Check (2026-06-24):** Asset Registry Phase 1 is complete — identity, versioning, governance, XMP sidecar records, and JSON-LD exporter exist within `render-module`. Asset ingestion (upload, ASR, OCR, vision), search, and marketplace are not implemented. This blueprint describes the **target architecture** — the fifth platform pillar.

---

## 1. Executive Summary

The Asset Ecosystem is the **fifth platform pillar**, joining:
1. **Timeline IR** — Editing truth
2. **Timeline Git** — Version truth
3. **Asset Registry** — Identity truth
4. **Artifact DAG** — Execution truth
5. **Asset Ecosystem** — Discovery truth (search, marketplace, sharing)

### Why a Fifth Pillar

Today, users bring their own media and use built-in effects. Tomorrow, they will:
- **Search** for assets across projects, tenants, and the marketplace
- **Discover** templates, effects, styles, and AI models
- **Install** assets with one click (governance-checked, version-tracked)
- **Share** their own assets with the community
- **Monetize** assets through a two-sided marketplace

The Asset Registry provides the **identity layer** for all assets. The Asset Ecosystem provides the **discovery layer**.

---

## 2. Asset Types

| Type | Description | Registry Fields | Example |
|------|-------------|----------------|---------|
| `MEDIA` | Raw media files | storageKey, mediaType, checksum, durationMs, width, height | Stock footage clip, music track |
| `TIMELINE_TEMPLATE` | Reusable timeline structure | payload (Internal Timeline JSON), effectKeys | "YouTube Intro" template |
| `PLUGIN` | Render/effect extensions | extensionCode, runtime, version, trustLevel | Custom blur filter, OFX plugin |
| `WORKFLOW` | Temporal/LiteFlow execution flows | workflowDefinition, triggers | "Auto-transcribe-and-subtitle" flow |
| `EFFECT` | Effect presets and parameter bundles | effectKey, parameters, packId | "Cinematic color grade" pack |
| `STYLE` | Visual styling presets | fontFamily, colors, animation | "Corporate Blue" subtitle style |
| `MODEL` | AI model weights and configurations | modelName, version, framework, dataset | Whisper-v3, Stable Diffusion |

Every asset type inherits from the base Asset Registry identity model:
- `assetId` — unique, stable identity
- `assetVersion` — versioned releases
- `governance` — classification, license, rights holder, retention
- `lineage` — provenance (derived from, trained on, processed by)

---

## 3. Asset Lifecycle

```
Create / Ingest
    │
    ▼
Probe (ASR, OCR, Vision, Embedding)
    │
    ▼
Register (Asset Registry)
    │
    ▼
Publish (optional — to Marketplace)
    │
    ▼
Discover (Search)
    │
    ▼
Install (governance check, version resolution)
    │
    ▼
Use (in timeline, render, AI pipeline)
    │
    ▼
Archive (retention policy enforced)
```

### Ingestion Pipeline

```
User Uploads Media
    │
    ├── Video:    ASR (speech→text) + OCR (text in frames) + Vision (scene detection)
    ├── Audio:    ASR (speech→text) + Speaker Diarization
    ├── Image:    OCR + Vision (object/scene classification)
    └── Document: Text extraction + NER
    │
    ▼
Embedding Generation
    │  model-specific embeddings for semantic search
    ▼
Asset Registry Registration
    │  UUID, version, governance, storage mapping
    ▼
JSON-LD Indexing
    │  for Knowledge Graph projection (future)
    ▼
Searchable
```

---

## 4. Asset Search

### Search Dimensions

| Dimension | Source | Example Query |
|-----------|--------|---------------|
| **Identity** | assetId, assetType, version, project, owner | "Show me all v3 assets in project proj_abc" |
| **Governance** | classification, license, rightsHolder, retention | "Find all internal-use assets with Creative Commons license" |
| **Content (probe)** | ASR transcript, OCR text, detected objects, scenes | "Find clips containing the phrase 'quarterly results'" |
| **Embedding** | Vision embedding, text embedding, cross-modal | "Find clips visually similar to this frame" |
| **Lineage** | derivedFrom, workflowId, operator | "Find all assets derived from asset_123" |
| **AI Metadata** | model, prompt, seed, confidence | "Find all AI-generated assets from Stable Diffusion" |

### Architecture

```
Asset Search API
    │
    ├── PostgreSQL (identity, governance, lineage — exact queries)
    ├── ElasticSearch (full-text: transcripts, OCR, names)
    └── Vector DB (embeddings: visual, semantic, cross-modal)
```

**Phase 1 (P2):** PostgreSQL-only search (identity + governance + lineage)
**Phase 2 (P3):** ElasticSearch for full-text (transcripts, OCR)
**Phase 3 (P4):** Vector DB for semantic + visual search

---

## 5. Marketplace

### Unified Marketplace Vision

Today we have separate concepts: template store, plugin store, effect store, model store. The unified vision:

```
Marketplace
  ├── Templates     (timeline templates — reusable project structures)
  ├── Effects       (effect packs + parameter presets)
  ├── Plugins       (render extensions + OFX plugins)
  ├── Styles        (subtitle styles, color grades, font packages)
  ├── Models        (AI models — ASR, vision, LLM, diffusion)
  ├── Workflows     (automation flows — Temporal/LiteFlow)
  └── Media         (stock footage, music, sound effects, images)
```

### Marketplace vs Asset Registry

| Layer | Responsibility | Example |
|-------|---------------|---------|
| **Marketplace** | Discovery, curation, reviews, ratings, pricing, installation | "Find trending YouTube intro templates under $10" |
| **Asset Registry** | Identity, versioning, governance, storage, lineage | "Asset template_456 v2.1 is licensed Creative Commons, owned by OrgX" |

The Marketplace is the **storefront**. The Asset Registry is the **warehouse**.

### Marketplace Asset Lifecycle

```
Publisher creates asset
    │
    ▼
Asset Registry registration (identity + governance)
    │
    ▼
Marketplace listing (title, description, screenshots, pricing)
    │
    ▼
Review / moderation (policy check)
    │
    ▼
Published (searchable, installable)
    │
    ▼
User discovers → installs (governance check → version resolution → asset copy to tenant)
    │
    ▼
User rates / reviews (feedback loop)
    │
    ▼
Publisher updates → new version → users notified
```

---

## 6. Relationship to Existing Systems

| System | Role in Asset Ecosystem |
|--------|------------------------|
| **Asset Registry** | Identity and governance for every marketplace asset |
| **Timeline Git** | Version history for templates and workflows |
| **OTIO Adapter** | Import/export of timeline templates |
| **Render Pipeline** | Rendering assets using installed effects and models |
| **Policy Governance** | Marketplace moderation, entitlement checks |
| **Entitlement** | Feature-gated access to premium assets |
| **Billing** | Payment processing for paid marketplace assets |

---

## 7. Deployment Roadmap

| Phase | Capability | Timeline |
|-------|-----------|----------|
| **P1 — Ingestion Blueprint** | Upload pipeline design, ASR/OCR/Vision provider interfaces, embedding strategy | Next |
| **P2 — Asset Search** | PostgreSQL identity/governance/lineage search API | After P1 |
| **P3 — Marketplace Foundation** | Asset listing, search, install API; unified asset type taxonomy | After P2 |
| **P4 — Marketplace Productization** | Reviews, ratings, pricing, publisher dashboard, entitlement integration | After P3 |
| **P5 — Advanced Search** | ElasticSearch full-text, Vector DB semantic search | After P4 |

---

## 8. Key Design Decisions

| Decision | Rationale |
|----------|-----------|
| Marketplace assets ARE Asset Registry entities | Avoids duplicating identity/governance; every marketplace item inherits versioning and lineage |
| Search is layered (relational → full-text → vector) | Start simple, add complexity only when needed. PostgreSQL can handle identity/governance search at launch. |
| Marketplace is discovery, not identity | Asset Registry is authoritative for identity. Marketplace lists what's available; Registry stores what's installed. |
| Unified asset type taxonomy | One `assetType` field across media, templates, effects, plugins, models, styles, workflows. Avoids separate stores with incompatible models. |
| Governance-first marketplace | Every asset has classification, license, rights holder BEFORE it can be listed. No ungovernable assets. |

---

## 9. Related Documents

| Document | Relationship |
|----------|-------------|
| [OTIO Render Platform Blueprint](otio-render-platform-blueprint.md) | Parent blueprint — Asset Ecosystem is the fifth pillar |
| [Timeline Git Blueprint](timeline-git-blueprint.md) | Version history for templates and workflows |
| [OTIO + XMP + Asset Registry Phase 1 Implementation](../../review/otio-xmp-asset-registry-phase1-implementation.md) | Asset Registry foundation (Phase 1 complete) |
| [Reference Architecture Map](reference-architecture-map.md) | External references (Unity Asset Store, Unreal Marketplace, GitHub Marketplace, Figma Community) |
| [Architecture Re-Prioritization Sprint](../../review/architecture-reprioritization-sprint.md) | Strategic decisions placing Asset Ecosystem at P1 |
| [ASWF Ecosystem Analysis](../../review/aswf-ecosystem-analysis.md) | ASWF project integration roadmap |
| [Asset Ingestion Blueprint](asset-ingestion-blueprint.md) | Ingestion lifecycle — upload → probe → AI → search → publish |
| [Asset Ingestion Analysis](../../review/asset-ingestion-analysis.md) | Detailed capabilities analysis |

---

## 12. Asset Ingestion Integration

### How Ingestion Feeds the Ecosystem

The Asset Ingestion Blueprint defines what happens from upload to marketplace. This section maps that lifecycle to the Asset Ecosystem pillars:

```
Ingestion Pipeline          →  Ecosystem Layer
─────────────────────────────────────────────────────────
Upload + Register           →  Asset Registry (identity, version, governance)
Probe (FFprobe, OIIO)       →  Asset Metadata (technical: codec, resolution, color space)
AI Enrichment (ASR, OCR)    →  Asset Understanding (transcript, keywords, entities)
AI Enrichment (Vision)      →  Asset Understanding (scenes, objects, faces, brands)
AI Enrichment (Embedding)   →  Asset Search (semantic + visual vectors)
Indexing                    →  Asset Search (PostgreSQL + ElasticSearch + Vector DB)
Review                      →  Asset Review (reuses TimelineReview workflow)
Publish                     →  Marketplace (unified listing for all asset types)
```

### One Identity, Many Views

Every asset goes through the same lifecycle. The Asset Registry provides the single source of identity. Different layers add different views:

```
Asset Registry (identity) ──────────────────────────────
    │  assetId, version, entityRef, governance
    │
    ├── Probe Metadata (technical)
    ├── Semantic Metadata (AI enrichment)
    ├── Lineage Metadata (processing provenance)
    ├── Search Index (discovery)
    ├── Review Threads (quality assurance)
    └── Marketplace Listing (monetization)
```

### Review Reuse

Asset review reuses the same `TimelineReview` → `ReviewThread` → `TimelineComment` → `ReviewDecision` workflow built for timeline changes. No separate "Asset Review" system needed. The `entityRef` field distinguishes content types:
- `CLIP:hero_shot` — timeline review
- `ASSET:asset_789` — asset review
- `TEMPLATE:tmpl_456` — marketplace template review

---

## 10. ASWF Asset Standards

### How ASWF Standards Enable the Asset Ecosystem

The Academy Software Foundation defines interchange standards that are directly relevant to asset types in the marketplace:

| ASWF Standard | Asset Type | Marketplace Role |
|--------------|-----------|-----------------|
| **OpenEXR** | `MEDIA` (HDR intermediate), Render Output | Standard format for VFX render outputs. EXR assets are multi-layer (beauty, matte, depth, normal) — richer than MP4. |
| **MaterialX** | `STYLE` (Material, Look) | Standard format for material/look descriptions. A MaterialX `.mtlx` file is a portable Style asset. |
| **OpenFX** | `EFFECT`, `PLUGIN` | Standard plugin interface. An OpenFX plugin is a portable Effect/Plugin asset. |
| **OpenAssetIO** | All types (identity resolution) | Standard for resolving `entity_ref` across DAM/MAM systems. Every marketplace asset has an `entity_ref`. |

### OpenEXR as a Marketplace Asset Type

```
Asset Type: MEDIA (HDR Intermediate)
Format: OpenEXR (.exr)
Channels: RGBA + Z-depth + Motion Vectors + Object IDs
Use case: VFX compositing template — user buys "Explosion VFX Pack" containing 10 EXR sequences
Asset Registry: assetType=MEDIA, mediaType=EXR_SEQUENCE, governance: classification=restricted, license=commercial
```

### MaterialX as a Style Asset

```
Asset Type: STYLE
Format: MaterialX (.mtlx)
Content: Shader graph describing a cinematic look (color grading, grain, glow)
Use case: User buys "Cinematic Look Pack" — applies MaterialX look to render output
Asset Registry: assetType=STYLE, format=MATERIALX, governance: license=CC-BY 4.0
```

### OpenFX as an Effect/Plugin Asset

```
Asset Type: PLUGIN or EFFECT
Format: OpenFX plugin binary + parameter schema
Use case: User buys "Glow Pro" OpenFX plugin — installed via ExtensionProvider
Asset Registry: assetType=PLUGIN, effectFamily=ofx:glow, governance: license=commercial
```

### OpenAssetIO as the Resolution Backbone

Every marketplace asset has an `entity_ref` column seeded in Phase 1:

```
entity_ref = "openassetio://asset/marketplace/template_456?v=v2.1"
```

When a user installs a marketplace asset, the Asset Registry resolves this reference:
1. Check entitlements (does user have rights?)
2. Resolve version (is v2.1 the latest?)
3. Resolve storage (where is the template file?)
4. Copy to user's tenant (tenant-specific storage key)
5. Update governance (usage rights consumed)

### Design Principle

**ASWF standards define the interchange layer for the marketplace.** Users bring assets in standard formats (EXR, MaterialX, OpenFX). The Asset Registry manages identity, version, governance, and storage. The marketplace handles discovery, curation, and monetization.

---

## 11. Review Workflow Reuse for Marketplace Assets

### Why Marketplace Assets Need Review

When users submit assets to the marketplace (templates, effects, plugins, styles), those submissions must go through the same review workflow as timeline edits:

```
Publisher submits asset to marketplace
    │
    ▼
Review created (TimelineReview)
    │  Reviewer checks: quality, governance, compatibility
    ▼
Review approved → Asset published
    │
    └── Review rejected / changes requested → Publisher revises
```

### Shared Review Model

| Review Concept | Timeline Use | Marketplace Use |
|---------------|-------------|-----------------|
| `TimelineReview` | Review a timeline revision | Review a marketplace asset submission |
| `TimelineComment` | Comment on a clip/effect in a diff | Comment on an asset's parameters/code/quality |
| `TimelineApproval` | Approve a timeline change | Approve marketplace listing |
| `ReviewStatus` (OPEN→APPROVED→MERGED) | Review → approve → merge | Review → approve → publish |

### Template Review

```
User submits "YouTube Intro v2" template
    │
    ▼
TimelineReview created
    │  entityRef = ASSET:template_456
    │  revisionId = template_456 v2 snapshot
    ▼
Reviewer inspects:
    ├── Timeline structure (clips, tracks, effects)
    ├── Effect compatibility (all effect keys valid)
    ├── Governance (license, classification, AI-generated flag)
    └── Quality (does it render correctly?)
    │
    ├── APPROVE → Template published to marketplace
    └── CHANGES_REQUESTED → Publisher revises → new version submitted
```

### Design Principle

**One review system for all platform content.** Timeline changes, marketplace submissions, AI proposals — all flow through the same review/comment/approval pipeline. This avoids building separate review systems for each content type.

---

## 13. Coordination & Workflow Layer

### How Asset Ecosystem Uses Coordination

The Platform Coordination Blueprint defines a generic job/task model for orchestrating multi-step asset workflows:

```
Asset Enrichment Job (fan-out):
  platform_job → platform_task[probe, asr, ocr, vision, embedding]
  Barrier: all tasks complete → publish AssetEnriched event

Asset Publish Post-Processing Job (fan-out):
  platform_job → platform_task[search_reindex, marketplace_listing, analytics]
  Barrier: all tasks complete → publish AssetPostProcessed event
```

### Design Principle

**Coordination is generic. Domain logic is in task handlers.** The `platform_job` and `platform_task` tables are domain-agnostic. Asset-specific behavior (what probe/asr/ocr/vision means) lives in task handler implementations — not in the coordination layer.

### Recovery

- **Task lease** prevents double-dispatch if a handler crashes
- **Bitmask-based barrier** provides fast completion checks (1 integer compare)
- **Exponential backoff** on task retry (0s → 5s → 30s → 5min)
- **LISTEN/NOTIFY** wakes dispatchers in near-real-time (complementing 3s polling)

---

## 14. Asset Pipeline Coordination

### Current (Sequential Service Calls)

```
AssetEnrichmentService.enrich():
  runProvider(PROBE)
  runProvider(ASR)
  // Future: OCR, Vision, Embedding
  // Sequential — no parallelism, no per-task retry, no lease
```

### Target (Platform Job + Task)

```
AssetUploaded event → CoordinationConsumer:
  creates platform_job (type: ASSET_ENRICHMENT, requiredMask: PROBE|ASR|OCR|VISION|EMBEDDING)
  creates platform_task[PROBE], [ASR], [OCR], [VISION], [EMBEDDING]

TaskDispatcher (parallel):
  PROBE task    → FfprobeMetadataProvider     → platform_task completed
  ASR task      → WhisperProvider             → platform_task completed
  OCR task      → TesseractProvider           → platform_task completed
  VISION task   → VisionProvider              → platform_task completed
  EMBEDDING task → EmbeddingProvider          → platform_task completed

Barrier:
  completedMask == requiredMask → Job COMPLETED
  → AssetEnrichedEvent published (via outbox)
  → SearchIndexConsumer reindexes asset
  → Notification notifies owner
```

### Benefits Over Sequential

| Aspect | Sequential (Current) | Coordinated (Target) |
|--------|---------------------|---------------------|
| **Parallelism** | None (serial) | All 5 tasks run in parallel |
| **Retry** | Whole pipeline fails | Per-task retry with exponential backoff |
| **Partial completion** | Not possible | Non-critical tasks can fail; job continues |
| **Observability** | Logs only | platform_job/task rows — queryable, auditable |
| **Recovery** | Manual restart | Automatic lease sweep + re-dispatch |

### Full Post-Enrichment Flow

```
AssetUploaded
    ↓
AssetEnrichmentJob (PROBE | ASR | OCR | VISION | EMBEDDING)
    ↓ Barrier
AssetEnrichedEvent
    ↓
SearchReindexTask → Asset Search Index updated
    ↓
(Optional) MarketplacePublishFlow
    ↓
AssetFullyProcessedEvent → Notification, Audit
```
