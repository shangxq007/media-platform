# Timeline Git Planning

**Date:** 2026-07-09
**Status:** COMPLETE
**Authority:** TIMELINE-GIT-PLANNING.0
**Implementation mode:** DOCS_ONLY

---

## Background

Timeline Render MVP is COMPLETE. Basic effects, subtitles, and security hardening are done. This document plans Git-like timeline versioning.

---

## Current Inventory

| Concept | File | Support | Persistence |
|---------|------|---------|-------------|
| TimelineSpec | render-module domain | EXISTS | NO (inline JSON) |
| TimelineRevision | render-module domain | EXISTS | YES |
| TimelineRevisionService | render-module app | EXISTS | YES |
| TimelineRevisionRepository | render-module infra | EXISTS | YES |
| parentRevisionId | TimelineRevision | PARTIAL | YES |
| timelineSpecJson | TimelineRevision | YES | YES |
| TimelineScriptParser | render-module domain | EXISTS | NO |
| Inline timeline JSON | submit prompt field | EXISTS | NO |
| Render by revision | — | NO | NO |
| Semantic diff | — | NO | NO |
| Patch model | — | NO | NO |

---

## Goals and Non-Goals

### Goals

- Version canonical TimelineSpec
- Immutable revision snapshots
- Revision history
- Semantic diff
- Patch operations
- Render selected revision

### Non-Goals

- Branches/merge (deferred)
- CRDT (deferred)
- Multiplayer (deferred)
- Artifact DAG (postponed)
- ANTLR (deferred)
- Generated FFmpeg/ASS as canonical source

---

## MVP Scope

### Included

- Immutable TimelineRevision snapshot
- timelineId, revisionId, parentRevisionId
- Commit message, author/source metadata
- Canonical timeline JSON/spec snapshot
- Revision history list
- Checkout/read selected revision
- Render selected revision
- Semantic diff between revisions
- Basic patch representation (design only)

### Excluded

- Branches
- Merge
- Conflict resolver UI
- CRDT
- Realtime collaboration
- Artifact DAG
- ANTLR
- Natural language patch DSL
- Binary media diff
- Provider-specific filtergraph diff
- Generated ASS diff

---

## TimelineRevision Model

### Fields

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| revisionId | string | YES | Unique revision ID |
| timelineId | string | YES | Parent timeline ID |
| tenantId | string | YES | Tenant scope |
| projectId | string | YES | Project scope |
| parentRevisionId | string | NO | Previous revision |
| rootRevisionId | string | NO | First revision in chain |
| revisionNumber | int | NO | Sequential per timeline |
| title | string | NO | Revision title |
| message | string | YES | Commit message |
| authorId | string | NO | Author identity |
| authorType | enum | YES | USER / AGENT / SYSTEM / IMPORT |
| source | enum | YES | API / DEV_CONSOLE / AGENT / IMPORT |
| timelineSpecJson | string | YES | Canonical timeline JSON |
| timelineSpecHash | string | YES | Content hash |
| schemaVersion | string | YES | Spec schema version |
| createdAt | timestamp | YES | Creation time |
| renderable | boolean | YES | Can be rendered |
| validationStatus | enum | YES | VALID / INVALID / WARNING |
| metadata | json | NO | Additional metadata |
| correlationId | string | NO | Request correlation |

### Rules

- Immutable after creation
- New edit creates new revision
- timelineSpecHash based on canonical normalized spec
- Generated FFmpeg/ASS not stored as source of truth
- parentRevisionId defines linear history in MVP
- Multiple parents deferred

---

## Canonical Timeline Normalization

### Rules

- Stable JSON field ordering
- Stable effect ordering
- Stable subtitle cue ordering
- Stable IDs preserved
- Omit transient fields
- Omit generated paths
- Omit runtime provider-specific data
- Include schemaVersion
- Include timeline structure, clips, effects, subtitles, styles
- Exclude render job ID
- Exclude artifact output ID
- Exclude generated ASS
- Exclude FFmpeg filtergraph

---

## Semantic Diff Model

### Categories

| Category | Examples |
|----------|---------|
| Timeline-level | output profile, duration, metadata |
| Track-level | added/removed/reordered, muted |
| Clip-level | added/removed/moved/trimmed, media changed |
| Effect-level | added/removed/enabled, param changed |
| Subtitle-level | cue added/removed/changed, style changed |
| Text overlay | text/timing/style changed |
| Asset reference | mediaInputId changed |

### Diff Item Shape

```json
{
  "changeId": "chg_001",
  "changeType": "clip.trimmed",
  "path": "/tracks/v1/clips/clip_001",
  "entityType": "clip",
  "entityId": "clip_001",
  "field": "sourceInMs",
  "before": 0,
  "after": 1000,
  "renderAffecting": true,
  "severity": "normal"
}
```

### Rules

- Diff operates on canonical TimelineSpec
- Semantic, not raw JSON line diff
- Generated FFmpeg/ASS not diff source
- Stable IDs required

---

## Patch Operation Model

### Operation Families

| Family | Operations |
|--------|-----------|
| Timeline | setOutputProfile, updateMetadata |
| Track | add/remove/reorder/update |
| Clip | add/remove/move/trim/replaceMedia/update |
| Effect | add/remove/setEnabled/setParam/reorder |
| Subtitle | addTrack/removeTrack/addCue/removeCue/updateCue/setStyle |

### Patch Item Shape

```json
{
  "opId": "op_001",
  "op": "setEffectParam",
  "target": {
    "entityType": "effect",
    "entityId": "eff_001",
    "path": "/tracks/v1/clips/clip_001/effects/eff_001"
  },
  "param": "brightness",
  "value": 0.1,
  "baseValue": 0.0
}
```

### Rules

- Semantic and ID-based
- Validate after applying
- No raw FFmpeg/ASS
- Creates new revision

---

## Future Conflict Model

### Conflict Scopes

| Scope | Example |
|-------|---------|
| Same clip timing | Both edit sourceInMs |
| Same effect param | Both edit brightness |
| Same cue text | Both edit subtitle text |
| Delete vs modify | One deletes, other edits |
| Move vs trim | Both change clip |

### Conflict Item Shape

```json
{
  "conflictId": "conf_001",
  "scope": "effect.param",
  "entityType": "effect",
  "entityId": "eff_001",
  "field": "brightness",
  "base": 0.0,
  "ours": 0.1,
  "theirs": -0.1,
  "resolutionRequired": true
}
```

---

## Render Selected Revision Flow

```
1. Select timelineRevisionId
2. Load immutable TimelineRevision
3. Validate timelineSpec
4. Resolve media inputs/assets
5. Submit render job (references timelineRevisionId)
6. FFmpeg provider renders
7. Artifact metadata records timelineRevisionId
8. Artifact content access works
```

### Provenance

- timelineRevisionId
- timelineId
- source media/input IDs
- renderJobId
- provider
- output artifact ID

---

## Migration from Inline Timeline JSON

| Stage | Description |
|-------|-------------|
| 1 | Continue inline JSON for preview/dev |
| 2 | Add TimelineRevision creation API |
| 3 | Add revision history API |
| 4 | Add semantic diff API |
| 5 | Add patch application API |
| 6 | Add Timeline Patch DSL / ANTLR (optional) |

---

## Future API Design

> Design only, not implemented.

| Endpoint | Method | Description |
|----------|--------|-------------|
| `.../timelines/{id}/revisions` | POST | Create revision |
| `.../timelines/{id}/revisions` | GET | List revisions |
| `.../timelines/{id}/revisions/{rid}` | GET | Get revision |
| `.../timelines/{id}/revisions/{from}/diff/{to}` | GET | Diff revisions |
| `.../timelines/{id}/patch` | POST | Apply patch |
| `.../timeline-revisions/{rid}/render` | POST | Render revision |

---

## Timeline Patch DSL / ANTLR Positioning

- ANTLR not needed for MVP
- First implement semantic patch as JSON/domain operations
- Timeline Patch DSL added later as human/agent input layer
- ANTLR parser compiles text DSL into semantic patch operations
- Timeline domain must not depend on ANTLR

---

## Validation and Invariants

### Revision Validation

- timelineId present
- revisionId unique
- parentRevisionId exists if provided
- timelineSpec schemaVersion supported
- Clip/track/effect/cue IDs unique
- Media references valid or marked unresolved
- No raw FFmpeg/ASS

### Patch Validation

- Target entity exists
- Resulting timeline validates
- Patch does not mutate existing revision

---

## Roadmap

| Phase | Task | Description |
|-------|------|-------------|
| 1 | TIMELINE-GIT-PLANNING.0 | ✅ This document |
| 2 | TIMELINE-REVISION-MODEL.0 | Align persistent model |
| 3 | TIMELINE-REVISION-API.0 | Create/list/get APIs |
| 4 | TIMELINE-RENDER-BY-REVISION.0 | Render selected revision |
| 5 | TIMELINE-DIFF-MVP.0 | Semantic diff |
| 6 | TIMELINE-PATCH-MVP.0 | Semantic patch |
| 7 | TIMELINE-GIT-MVP.0 | End-to-end |
| 8 | TIMELINE-PATCH-DSL-SPIKE.0 | Text patch DSL |
| 9 | ANTLR-TIMELINE-PATCH-PARSER.0 | ANTLR parser |

### Deferred

- Branch/merge
- Conflict resolver
- CRDT
- Multiplayer
- Artifact DAG
- OpenCue integration

---

## Open Questions

1. Should TimelineRevision reuse existing tables or add new ones?
2. How should timelineSpecHash be normalized?
3. Should revisionNumber be sequential per timeline?
4. What is the first externally visible API?
5. Should diff be generated on demand or stored?
6. Should patch use JSON Patch internally or semantic operations only?
7. When should branch/merge be introduced?
8. How should author/source metadata represent agents?
