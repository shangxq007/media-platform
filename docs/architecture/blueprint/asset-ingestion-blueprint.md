---
status: blueprint
created: 2026-06-24
scope: platform-wide (future)
truth_level: target
owner: platform
---

# Asset Ingestion Blueprint

> **Reality Check (2026-06-24):** Asset Registry Phase 1 is complete (identity, versioning, governance, JSON-LD). Timeline Git productization is complete (history, diff, merge, restore, review, comments, approval, merge guard). Asset ingestion, AI enrichment, search, and marketplace are not implemented. This blueprint defines the **asset lifecycle** — what happens from upload to marketplace.

---

## 1. Executive Summary

### The Question

When a user uploads a media file, what happens?

Today: It gets an `assetId`, a `storageKey`, and basic probe metadata. Nothing else.

Tomorrow: It flows through a pipeline — probe → AI enrichment (ASR, OCR, vision, embedding) → search index → review → optional marketplace publish.

### The Pipeline

```
Upload
    │
    ▼
Registration (assetId + version + governance)
    │
    ▼
Probe (FFprobe / OpenImageIO — codec, resolution, duration, color space)
    │
    ▼
AI Enrichment
    ├── ASR (speech → transcript + speaker diarization)
    ├── OCR (text in frames → detected text + time ranges)
    ├── Vision (scene detection, object recognition, face detection)
    └── Embedding (text + image → vector embeddings for semantic search)
    │
    ▼
Asset Registry (identity, version, governance, lineage, semantic metadata)
    │
    ▼
Search Index (full-text + vector — PostgreSQL / ElasticSearch / Vector DB)
    │
    ▼
Review (optional — reuse TimelineReview for asset approval)
    │
    ▼
Publish (optional — to marketplace)
    │
    ▼
Marketplace (discovery, curation, monetization)
```

---

## 2. Asset Lifecycle

### Step 1: Upload

**What happens:** User uploads a file (video, audio, image, or document) via REST API or web UI.

**Action:** File stored in object storage (S3/OSS/GCS). `storageKey` generated.

**Key decision:** File is NOT immediately registered as an Asset Registry entity. Registration happens after validation.

### Step 2: Registration

**What happens:** Asset Registry creates a canonical identity.

```java
AssetRegistryService.register(
    projectId, storageKey, mediaType, filename,
    sizeBytes, checksum, durationMs, width, height,
    assetVersion, ownerId
)
```

**Output:**
- `assetId` — globally unique, stable identity
- `assetVersion` — "v1" by default
- `entityRef` — `asset://{assetId}?v={version}`
- `governance` — defaults (no classification, no license)

**Principle:** Assets are always referenced by `assetId` + `assetVersion`, never by file path. File paths change. Identity is stable.

### Step 3: Probe

**What happens:** Media probe extracts technical metadata.

**Providers (future):**
- **FFprobe** — for video/audio codec, bitrate, framerate, resolution, duration
- **OpenImageIO** — for 50+ image formats, color space, bit depth, EXIF/XMP metadata
- **MediaInfo** — for container-level metadata (file format, track layout)

**Output:** `media_asset_metadata` table populated with probe fields.

**Schema:**
```
Video:    duration, fps, resolution, codec, bitrate, colorSpace, pixelFormat
Audio:    channels, sampleRate, bitrate, loudness, codec
Image:    width, height, format, colorSpace, bitDepth, dpi
```

### Step 4: AI Enrichment

**What happens:** AI pipelines extract semantic understanding from media.

**Pipelines (each is a future provider):**

| Pipeline | Input | Output | Provider |
|----------|-------|--------|----------|
| **ASR** | Audio stream | Transcript, speaker segments, word timings | Whisper, Deepgram, AssemblyAI |
| **OCR** | Video frames | Detected text, bounding boxes, time ranges | Tesseract, Google Vision, Azure OCR |
| **Vision — Scene Detection** | Video frames | Shot boundaries, scene labels | FFmpeg scene detect, ResNet, CLIP |
| **Vision — Object Recognition** | Video frames | Detected objects with confidence scores | YOLO, Detectron, Google Vision |
| **Vision — Face Detection** | Video frames | Face bounding boxes, optional identity | MTCNN, FaceNet, AWS Rekognition |
| **Vision — Logo Detection** | Video frames | Brand logos detected | Google Vision, AWS Rekognition |
| **Embedding** | Text (transcript) + Image (keyframes) | Vector embeddings for semantic search | CLIP, OpenAI Embeddings, Sentence-BERT |

**Principle:** AI results are Asset Metadata, not Timeline data. They enrich the asset, not the edit.

### Step 5: Asset Registry Update

**What happens:** Enriched metadata is stored alongside the asset identity.

**Update fields:**
- `ai_generated` — was this asset AI-generated?
- `contains_pii` — did OCR/ASR detect PII?
- `lineage` — what processing steps produced this asset?

**New table: `asset_semantic_metadata`** (future):
```
assetId, transcript, keywords, entities, topics, scenes,
detectedObjects, detectedPeople, detectedBrands, embeddingRef
```

### Step 6: Search Index

**What happens:** Enriched metadata is indexed for search.

**Three-layer search:**
1. **PostgreSQL** — identity, governance, lineage, probe metadata (exact queries)
2. **ElasticSearch** — full-text: transcripts, OCR text, entity names (full-text queries)
3. **Vector DB** — embeddings for semantic/visual similarity (vector queries)

### Step 7: Review (Optional)

**What happens:** Asset can enter a review workflow before being published to marketplace.

**Reuse existing `TimelineReview`:**
- Review asset quality (is the transcript accurate? are objects correctly detected?)
- Approve/reject/request changes
- Comment on specific entity (e.g., "transcript at 1:23 has a typo")

**Principle:** One review system for all platform content — timelines, assets, marketplace submissions.

### Step 8: Publish to Marketplace (Optional)

**What happens:** Approved assets can be published to the marketplace.

**Flow:**
1. User selects "Publish to Marketplace"
2. Marketplace listing created (title, description, screenshots, pricing)
3. Governance check (license valid? rights holder confirmed?)
4. Published — searchable, installable

---

## 3. Probe Architecture

### Provider Selection

| Format | Probe Provider | Why |
|--------|---------------|-----|
| Video (MP4, MOV, MKV) | FFprobe | Fast, built-in, covers 99% of video formats |
| Audio (MP3, WAV, FLAC) | FFprobe | Same as video — covers all audio |
| Image (JPEG, PNG, GIF) | OpenImageIO | 50+ formats, EXIF/XMP metadata, color space |
| Professional (DPX, EXR, TIFF) | OpenImageIO | Industry-standard for VFX/Cinema formats |
| Container metadata (MKV, AVI, MXF) | MediaInfo | Track layout, chapter info, subtitle tracks |

### Probe Metadata Schema

```json
{
  "mediaType": "VIDEO",
  "probe": {
    "duration": 120.5,
    "fps": 29.97,
    "resolution": { "width": 1920, "height": 1080 },
    "codec": "h264",
    "bitrate": 8000000,
    "colorSpace": "bt709",
    "pixelFormat": "yuv420p",
    "audio": {
      "codec": "aac",
      "channels": 2,
      "sampleRate": 48000,
      "bitrate": 192000
    }
  }
}
```

### Future: OpenImageIO Integration

When the platform needs 50+ image format support (DPX sequences, EXR files, TIFF stacks), OpenImageIO becomes the primary probe engine. Phase 1 uses FFprobe + Java ImageIO for JPEG/PNG.

---

## 4. AI Enrichment Pipeline

### ASR (Automatic Speech Recognition)

```
Audio stream → ASR Provider → Transcript + Speaker Diarization
```

**Output:**
```json
{
  "asr": {
    "provider": "whisper-large-v3",
    "language": "en",
    "transcript": "Welcome to the quarterly review. Today we'll cover...",
    "segments": [
      { "start": 0.0, "end": 3.5, "text": "Welcome to the quarterly review.", "speaker": "SPEAKER_1" },
      { "start": 3.5, "end": 9.2, "text": "Today we'll cover revenue...", "speaker": "SPEAKER_1" }
    ],
    "confidence": 0.94
  }
}
```

### OCR (Optical Character Recognition)

```
Video frames (sampled) → OCR Provider → Detected Text + Time Ranges
```

**Output:**
```json
{
  "ocr": {
    "provider": "tesseract-v5",
    "detections": [
      { "frame": 42, "time": 1.4, "text": "Q4 Revenue: $12.3M", "bbox": [100, 200, 300, 50], "confidence": 0.92 },
      { "frame": 90, "time": 3.0, "text": "Growth: 15% YoY", "bbox": [100, 300, 250, 50], "confidence": 0.88 }
    ]
  }
}
```

### Vision Pipeline

**Scene Detection:**
```
Video → FFmpeg scene detect → Shot boundaries → Keyframes → Scene labels
```

**Object Recognition:**
```
Keyframes → YOLO / Detectron → Objects with bboxes + confidence
```

**Face Detection:**
```
Keyframes → MTCNN / FaceNet → Face bboxes + identity (optional)
```

### Embedding Pipeline

```
Transcript → Text Embedding Model → Vector (768d)
Keyframes → Image Embedding Model → Vector (512d)
Combined → Multimodal Embedding → Vector (1024d)
```

**Usage:** Semantic search ("find clips like this one"), recommendation, duplicate detection.

---

## 5. Asset Understanding Model

### AssetSemanticMetadata (Future Table)

```
AssetSemanticMetadata {
    assetId:            String
    transcript:         String          // full ASR transcript
    keywords:           List<String>    // extracted keywords (via NLP)
    entities:           List<Entity>    // people, orgs, locations (via NER)
    topics:             List<String>    // "quarterly review", "revenue growth"
    scenes:             List<Scene>     // shot boundaries + labels
    detectedObjects:    List<Object>    // "laptop", "whiteboard", "person" + bbox
    detectedPeople:     List<Person>    // face embeddings + optional identity
    detectedBrands:     List<Brand>     // "Apple", "Tesla" + bbox
    embeddingRef:       String          // reference to vector embedding
    enrichmentStatus:   EnrichmentStatus // PENDING, IN_PROGRESS, COMPLETE, FAILED
}
```

### Search Dependencies

| Search Dimension | Depends On |
|-----------------|------------|
| Keyword search | transcript, keywords, entities, topics |
| Semantic search | embeddings (text + image) |
| Visual similarity | image embeddings |
| Governance search | classification, license, rightsHolder |
| Lineage search | derivedFrom, workflowId |

---

## 6. Search Architecture

### Three-Layer Search

```
Layer 1: PostgreSQL (Relational)
    └── Identity + Governance + Lineage
        └── "Find all internal-use video assets in project proj_123"
        └── "Find all AI-generated assets with Creative Commons license"

Layer 2: ElasticSearch (Full-Text)
    └── Transcripts + OCR + Entity names
        └── "Find clips containing the phrase 'quarterly results'"
        └── "Find assets mentioning 'Tesla' in OCR"

Layer 3: Vector DB (Semantic)
    └── Text embeddings + Image embeddings
        └── "Find clips visually similar to this reference frame"
        └── "Find clips discussing 'revenue growth' (semantic, not keyword)"
```

### Hybrid Search

```
User searches: "OpenAI interview about GPT-5"

1. Keyword match in transcript → 3 results
2. NER match on "OpenAI" entity → 2 results
3. Semantic match on "interview" + "GPT-5" → 2 results
4. Object detection match on "OpenAI logo" → 1 result
5. Merge + rank → 5 unique results sorted by relevance
```

---

## 7. Review Workflow Integration

### One Review System

The platform has ONE review system (`TimelineReview`, `ReviewThread`, `TimelineComment`, `ReviewDecision`). It serves:

| Content Type | Review Context |
|-------------|---------------|
| **Timeline Revision** | Review a timeline change before merging |
| **Asset** | Review an uploaded asset before publishing |
| **AI Enrichment** | Review ASR transcript / OCR results before accepting |
| **Marketplace Submission** | Review a marketplace asset before listing |

### Asset Review Flow

```
Asset uploaded + enriched
    │
    ▼
TimelineReview created (entityRef = ASSET:asset_789)
    │
    ▼
Reviewer checks:
    ├── Probe metadata correct?
    ├── Transcript accurate?
    ├── Object detection complete?
    └── Governance compliant?
    │
    ├── APPROVE → Asset published to project library
    └── REQUEST_CHANGES → Enrichment re-run or manual correction
```

---

## 8. Marketplace Publishing

### Publish Flow

```
User selects "Publish to Marketplace"
    │
    ▼
Marketplace listing created (title, description, screenshots, pricing tier)
    │
    ▼
Governance check:
    ├── License valid? (must have redistribution rights)
    ├── Rights holder confirmed?
    ├── Classification appropriate for marketplace?
    └── No PII in public assets?
    │
    ▼
Review (TimelineReview created)
    │
    ├── APPROVE → Asset published → searchable + installable
    └── REQUEST_CHANGES / REJECT → Back to publisher
```

### Asset Types in Marketplace

| Type | Description | Example |
|------|-------------|---------|
| `MEDIA` | Raw media files | Stock footage, music tracks, sound effects |
| `TIMELINE_TEMPLATE` | Reusable timeline structure | "YouTube Intro", "Podcast Episode" |
| `EFFECT` | Effect presets + parameters | "Cinematic Color Grade", "Glow Pro" |
| `PLUGIN` | Render/extension plugins | OpenFX plugin, FFmpeg filter wrapper |
| `STYLE` | Visual styling presets | "Corporate Blue" subtitle style, MaterialX look |
| `WORKFLOW` | Automation flows | "Auto-transcribe-and-subtitle", "Batch export" |
| `AI_MODEL` | AI model weights | Whisper-v3 fine-tune, Custom YOLO detector |

**All types inherit from base `AssetRegistry` identity + governance.**

---

## 9. ASWF Integration Mapping

| ASWF Standard | Role in Ingestion | Phase |
|--------------|-------------------|-------|
| **OpenImageIO** | Probe layer — 50+ image formats, EXIF/XMP, color space | P2 |
| **OpenColorIO** | Color metadata — color space identification, ACES support | P2 |
| **OpenEXR** | Asset type — HDR intermediate format in marketplace | P2 |
| **OpenFX** | Effect marketplace — plugin standard for effects | P3 |
| **MaterialX** | Style asset — material/look description format | P3 |
| **OpenAssetIO** | Enterprise DAM integration — entity reference resolution | Deferred |
| **OpenCue** | Execution layer — NOT asset layer (render farm scheduling) | P3 |

---

## 10. Asset Registry Evolution

### Phase 1 — Complete (2026-06)

- `assetId`, `assetVersion`, `entity_ref`, `governance` (7 fields)
- JSON-LD export (`AssetJsonLdExporter`)
- Lineage fields on `artifact_node`
- REST API: version, governance, JSON-LD

### Phase 2 — Ingestion Implementation (2026 Q3-Q4)

- `asset_semantic_metadata` table (transcript, keywords, entities, topics, scenes, objects, people, brands, embedding)
- Probe pipeline (FFprobe + OpenImageIO for formats)
- ASR pipeline (Whisper provider integration)
- OCR pipeline (Tesseract provider integration)
- Vision pipeline (scene detection + object recognition)

### Phase 3 — Search + Discovery (2027)

- Asset Search API (PostgreSQL identity/governance/lineage queries)
- ElasticSearch full-text search (transcripts, OCR, entities)
- Vector DB semantic search (embeddings)
- Asset recommendation ("you might also like")

### Phase 4 — Marketplace (2027+)

- Unified marketplace asset taxonomy (7 types)
- Marketplace listing/publishing/install API
- Two-sided marketplace (creators + producers)
- Reviews, ratings, curation

---

## 11. Search & Recommendation Vision

### Content Discovery

```
User action → Platform response
────────────────────────────────────
"Show me my drone shots"                                   → Keyword search in scene labels
"Find clips like this one" (reference image)              → Visual similarity via embeddings
"Show AI-generated assets with CC license"                → Governance search
"What footage do I have about product launches?"          → Semantic search across transcripts + OCR
"Recommend B-roll for my podcast intro"                   → Recommendation engine
```

### Community Discovery

```
"Trending YouTube intro templates this week"
"Most installed color grade effects"
"Featured creator: Alice Chen's template pack"
```

### Marketplace Discovery

```
"Free video templates under 30 seconds"
"Professional podcast templates ($10-50)"
"ASR models fine-tuned for medical terminology"
```

---

## 12. Roadmap

| Priority | Capability | Phase | Timeline |
|----------|-----------|-------|----------|
| **P0** | Asset Ingestion Blueprint (this document) | Design | 2026-06 |
| **P1** | Probe Pipeline (FFprobe + media_asset_metadata population) | Implementation | 2026 Q3 |
| **P1** | ASR Pipeline (Whisper provider integration) | Implementation | 2026 Q3 |
| **P2** | OCR + Vision Pipeline | Implementation | 2026 Q4 |
| **P2** | Asset Search API (PostgreSQL layer) | Implementation | 2026 Q4 |
| **P3** | Embedding Pipeline + Vector DB | Implementation | 2027 Q1 |
| **P3** | ElasticSearch full-text | Implementation | 2027 Q2 |
| **P4** | Marketplace Foundation | Implementation | 2027 Q3 |
| **Deferred** | Branch, Rebase, OpenCue, OpenAssetIO, Knowledge Graph | Future | 2028+ |

---

## 13. Related Documents

| Document | Relationship |
|----------|-------------|
| [Asset Ecosystem Blueprint](asset-ecosystem-blueprint.md) | Parent blueprint — marketplace, search, asset types |
| [OTIO Render Platform Blueprint](otio-render-platform-blueprint.md) | Platform-level architecture |
| [Reference Architecture Map](reference-architecture-map.md) | ASWF integration mapping, external references |
| [Asset Ingestion Analysis](../../review/asset-ingestion-analysis.md) | Detailed analysis report |
| [Timeline Git Productization Blueprint](timeline-git-productization-blueprint.md) | Review workflow — reused for asset review |
