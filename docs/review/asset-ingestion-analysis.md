---
status: analysis
created: 2026-06-24
scope: platform-wide (future)
truth_level: research
owner: platform
---

# Asset Ingestion Analysis

> **Analysis Date:** 2026-06-24
> **Method:** Platform capability assessment + media pipeline research + ASWF integration mapping
> **Reference:** Adobe Experience Manager, Frame.io, Iconik, Immich, OpenImageIO, FFprobe

---

## 1. Current State

### What Exists

| Capability | Status | Location |
|-----------|--------|----------|
| Asset Identity (assetId, version, entityRef) | ✅ | `asset` table, `AssetRegistryService` |
| Asset Governance (7 fields) | ✅ | `asset` table (classification through ai_generated) |
| JSON-LD Export | ✅ | `AssetJsonLdExporter` |
| Asset Lineage (schema) | ✅ | `artifact_node` (workflow_id, source_asset_id, etc.) |
| Media Probe (basic) | ✅ | `media_asset_metadata` table — populated by custom code |
| Asset REST API | ✅ | GET/POST assets, versions, governance, JSON-LD |

### Missing Entirely

| Capability | Gap |
|-----------|-----|
| ASR Pipeline | No speech-to-text integration |
| OCR Pipeline | No optical character recognition |
| Vision Pipeline | No scene/object/face detection |
| Embedding Pipeline | No vector embedding generation |
| `asset_semantic_metadata` table | No table for AI enrichment results |
| Asset Search API | No search endpoint (identity/governance/transcript/embedding) |
| ElasticSearch integration | Not planned |
| Vector DB integration | Not planned |
| Marketplace | Blueprint only |
| OpenImageIO integration | Not implemented |
| Asset Review Workflow | Not yet applied to assets (review system exists for timelines) |

---

## 2. Missing Capabilities — Priority Matrix

| Capability | Difficulty | User Value | Phase |
|-----------|-----------|------------|-------|
| **Probe Pipeline** (FFprobe → media_asset_metadata) | Low | High | P1 |
| **ASR Pipeline** (Whisper → transcript) | Medium | High | P1 |
| **Asset Search (PostgreSQL)** | Low | High | P2 |
| **OCR Pipeline** (Tesseract → detected text) | Medium | Medium | P2 |
| **Vision Pipeline** (scene detection) | Medium | Medium | P2 |
| **Embedding Pipeline** (CLIP → vectors) | High | Medium | P3 |
| **ElasticSearch** (full-text search) | Medium | Medium | P3 |
| **Vector DB** (semantic search) | High | Medium | P3 |
| **Marketplace** | High | High | P4 |

---

## 3. Asset Lifecycle (End-to-End)

### Upload → Discovery

```
1. UPLOAD: User uploads file → stored in S3
2. REGISTER: AssetRegistryService → assetId, version, governance defaults
3. PROBE: FFprobe → duration, codec, resolution, fps, channels, sampleRate
4. ASR: Whisper → transcript + speaker diarization + word timings
5. OCR: Tesseract → detected text + bbox + time ranges
6. VISION: Scene detection → shots, scene labels, objects, faces
7. EMBED: CLIP → text embedding + image embedding → vector DB
8. INDEX: PostgreSQL (identity) + ElasticSearch (full-text) + Vector DB (semantic)
9. REVIEW (optional): TimelineReview → approve/reject enrichment quality
10. PUBLISH (optional): Marketplace listing → installable asset
11. SEARCH: User searches → hybrid results (keyword + semantic + visual)
```

### Timeline per Asset Type

| Asset Type | Pipeline Steps | Estimated Time |
|-----------|---------------|----------------|
| **Image** | Upload → Register → Probe → OCR (optional) → Index | < 5s |
| **Audio** | Upload → Register → Probe → ASR → Embed → Index | 1-2x real-time |
| **Video (short)** | Upload → Register → Probe → ASR + OCR + Vision + Embed → Index | 2-5x real-time |
| **Video (long)** | Upload → Register → Probe → ASR + OCR + Vision + Embed → Index | 5-10x real-time |

---

## 4. AI Enrichment — Provider Strategy

### Phase 1 Providers (self-hosted, no API costs)

| Pipeline | Provider | Why |
|----------|---------|-----|
| **Probe** | FFprobe | Built-in, 99% format coverage, zero cost |
| **ASR** | Whisper (open-source) | Self-hosted, 100+ languages, GPU-accelerated |
| **OCR** | Tesseract | Self-hosted, mature, 100+ languages |
| **Scene Detection** | FFmpeg scene detect | Built-in, zero cost |
| **Embedding (text)** | Sentence-BERT | Self-hosted, small model, fast inference |

### Phase 2 Providers (cloud, for accuracy/scale)

| Pipeline | Provider | Why Upgrade |
|----------|---------|-------------|
| **ASR** | Deepgram / AssemblyAI | Better speaker diarization, real-time, enterprise SLAs |
| **OCR** | Google Vision / Azure OCR | Handwriting, logos, better accuracy |
| **Vision (objects)** | Google Vision / AWS Rekognition | Logo detection, celebrity recognition |
| **Embedding** | OpenAI Embeddings / CLIP | Better multimodal understanding |

---

## 5. Search Architecture

### Why Three Layers

| Layer | Query Type | Example |
|-------|-----------|---------|
| **PostgreSQL** | Exact | "Find all video assets with classification=internal" |
| **ElasticSearch** | Full-text | "Find clips mentioning 'quarterly results'" |
| **Vector DB** | Semantic | "Find clips visually similar to this reference" |

### Layer 1: PostgreSQL — Now

**Searchable fields (already indexed):**
- `asset_type` (VIDEO, AUDIO, IMAGE)
- `classification` (public, internal, confidential, restricted)
- `license`
- `ai_generated`
- `contains_pii`
- `project_id`
- `asset_version`

**Query examples:**
```sql
SELECT * FROM asset WHERE project_id = 'proj_123' AND ai_generated = true;
SELECT * FROM asset WHERE classification = 'internal' AND license = 'enterprise-owned';
```

### Layer 2: ElasticSearch — Future (2027)

**Indexed content:**
- Transcripts (ASR output)
- OCR text
- Entity names (people, orgs, locations)
- Keywords and topics
- Scene descriptions

### Layer 3: Vector DB — Future (2027+)

**Indexed vectors:**
- Text embeddings (from transcripts)
- Image embeddings (from keyframes)
- Multimodal embeddings (combined)

---

## 6. Marketplace Flow

### Publisher Journey

```
1. Upload asset → Registered in Asset Registry
2. Run enrichment pipeline → ASR, OCR, vision, embedding
3. Review enrichment quality → Approve/request changes
4. "Publish to Marketplace" → Create listing
5. Governance check → License valid? Rights holder confirmed?
6. Published → Searchable, installable
7. Updates → New version → Review → Published
```

### Consumer Journey

```
1. Search marketplace → "YouTube intro template"
2. Preview → Timeline preview with sample media
3. Install → Asset copied to user's tenant
4. Governance check → Entitlement verified
5. Use → Template opens in editor
6. Rate/Review → Feedback to publisher
```

---

## 7. ASWF Mapping — Detailed

| ASWF Standard | Ingestion Role | Integration Phase | Difficulty |
|--------------|---------------|-------------------|------------|
| **OpenImageIO** | Probe — 50+ image formats, EXIF/XMP, color space | P2 | Low (CLI or JNI binding) |
| **OpenColorIO** | Color metadata — identify color spaces (Rec.709, ACES, sRGB) | P2 | Medium (config management) |
| **OpenEXR** | Asset type — HDR intermediate in marketplace | P2 | Low (format support in FFmpeg) |
| **OpenFX** | Effect marketplace — plugin standard | P3 | High (requires compositing host) |
| **MaterialX** | Style asset — material description format | P3 | Medium (reference only) |
| **OpenAssetIO** | DAM integration — entity reference resolution | Deferred | Medium (needs DAM/MAM) |
| **OpenCue** | Render farm — NOT asset layer | P3 | High (replaces worker registry) |

---

## 8. Asset Registry Evolution Roadmap

| Phase | New Capabilities | Timeline |
|-------|-----------------|----------|
| **Phase 1** (complete) | assetId, version, governance, JSON-LD, lineage (artifact_node) | 2026-06 |
| **Phase 2** | `asset_semantic_metadata` table, probe pipeline, ASR pipeline | 2026 Q3-Q4 |
| **Phase 3** | Asset Search API (PostgreSQL), ElasticSearch integration | 2027 Q1-Q2 |
| **Phase 4** | Vector DB integration, Marketplace foundation | 2027 Q3-Q4 |
| **Phase 5** | OpenAssetIO integration, cross-cloud federation | 2028+ |

---

## 9. Reference Analysis

### Industry Tools — What They Do Well

| Tool | Strength | Our Application |
|------|----------|-----------------|
| **Adobe Experience Manager (AEM)** | Enterprise DAM — metadata extraction, asset workflow, review/approval | Model our asset review workflow |
| **Frame.io** | Asset version comparison, review, timestamp comments | Model our review UI |
| **Iconik** | Hybrid cloud/on-premise media management, AI enrichment, smart collections | Model our enrichment pipeline + search |
| **Immich** | Self-hosted photo/video manager, face detection, object recognition, search | Model our AI enrichment pipeline (self-hosted first) |
| **Eagle** | Desktop asset manager — tagging, categorization, visual browsing | Model our asset taxonomy + visual search |
| **OpenImageIO** | Industry-standard image I/O — 50+ formats, metadata, color space | Model our probe layer |

### What We Do Differently

| Theme | Industry | Our Platform |
|-------|----------|-------------|
| **Identity** | Files identified by path/name | Assets identified by stable UUID + version |
| **Governance** | Directory-level permissions | Per-asset classification, license, PII, AI flag |
| **Version Control** | Time-based snapshots | Git-like revision chain with semantic diff |
| **Review** | Visual comparison only | Entity-anchored comments + structured approval |
| **Lineage** | None or manual | Automated lineage from processing pipeline |
| **Marketplace** | Separate stores per asset type | Unified marketplace with shared identity model |

---

## 10. Related Documents

| Document | Relationship |
|----------|-------------|
| [Asset Ingestion Blueprint](../architecture/blueprint/asset-ingestion-blueprint.md) | Main blueprint |
| [Asset Ecosystem Blueprint](../architecture/blueprint/asset-ecosystem-blueprint.md) | Marketplace and search vision |
| [Timeline Git Productization Blueprint](../architecture/blueprint/timeline-git-productization-blueprint.md) | Review workflow — reused for assets |
| [Reference Architecture Map](../architecture/blueprint/reference-architecture-map.md) | ASWF integration, external references |
