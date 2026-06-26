---
status: blueprint
created: 2026-06-25
scope: platform-wide
truth_level: target
owner: platform
---

# Creative Planner & Timeline Authoring Blueprint

## 1. Core Architecture Decision

AI must NOT directly generate final video output.

AI must generate: `TimelineEditPlan`

```
TimelineEditPlan → TimelineMutationService → TimelineRevision → Review/Merge → Execution Planner → Artifact DAG → Render/Export
```

## 2. Creative Planner Role

| Owns | Does NOT Own |
|------|-------------|
| Analyze semantic metadata | Render video |
| Select meaningful segments | Call FFmpeg / BMF / OpenCue |
| Suggest clips / captions / effects | Modify Timeline directly |
| Generate TimelineEditPlan | Persist final artifacts |
| Provide confidence + explanation | Bypass review |

## 3. TimelineEditPlan Model

```
TimelineEditPlan {
    planId: String
    projectId: String
    timelineId: String
    goal: String
    sourceAssetIds: List<String>
    operations: List<EditOperation>
    confidence: double
    explanation: String
    createdBy: String (AI agent id)
    createdAt: Instant
}
```

### EditOperation Types

| Type | Description |
|------|-------------|
| ADD_CLIP | Insert a clip from source asset |
| TRIM_CLIP | Adjust in-point / out-point |
| REMOVE_CLIP | Remove a clip |
| ADD_CAPTION | Insert caption overlay |
| ADD_EFFECT | Apply effect to clip/track |
| ADD_TRANSITION | Insert transition between clips |
| ADD_MARKER | Place a marker |
| SET_AUDIO_LEVEL | Adjust audio gain |
| REORDER_CLIP | Move clip in timeline |

### EditOperation Fields

```
EditOperation {
    opType: EditOperationType
    target: EntityRef (clip/track/marker identifier)
    sourceRef: SemanticAnchor (optional)
    timeRange: TimeRange (startMs, endMs)
    parameters: Map<String, Object>
    reason: String
    confidence: double
}
```

## 4. Semantic Anchors

How Creative Planner references semantic metadata:

| Anchor Type | Source | Example |
|------------|--------|---------|
| TRANSCRIPT_SEGMENT | AssetSemanticMetadata.transcripts | "clip at 12s where speaker says 'Q4 revenue'" |
| DETECTED_OBJECT | AssetSemanticMetadata.objects | "frame with laptop at 45s" |
| DETECTED_SCENE | AssetSemanticMetadata.scenes | "office scene from 0s-30s" |
| OCR_TEXT | AssetSemanticMetadata.detectedTexts | "slide showing 'Growth: 15%'" |
| BRAND_MENTION | AssetSemanticMetadata.brands | "Apple logo at 60s" |
| EMBEDDING_MATCH | AssetSemanticMetadata.embeddings | "visually similar to reference frame" |

## 5. Timeline Mutation Service

```
TimelineMutationService:
  - Validate TimelineEditPlan (structural integrity)
  - Apply operations to current Timeline
  - Produce new TimelineRevision
  - Emit TimelineRevisionCreatedEvent (via outbox)
  - Preserve diff / merge / restore compatibility
```

## 6. Authoring Pipeline

```
Asset / Raw Media → AI Enrichment → AssetSemanticMetadata
    → Creative Planner → TimelineEditPlan
    → TimelineMutationService → TimelineRevision
    → Review Workflow → Execution Planner → Render/Export
```

## 7. Review Integration

AI-generated TimelineRevision must:
- Carry `explanation` + `confidence` in revision metadata
- Reference semantic anchors (transcript segment, object label, scene)
- Enter existing review flow (OPEN → APPROVED → MERGED)
- Support comment threads anchored to AI-suggested clips

Reviewers see: WHY a clip was suggested, WHAT metadata triggered it, with WHAT confidence.

## 8. Execution Planner Boundary

| Creative Planner | Execution Planner |
|-----------------|-------------------|
| Content decisions | Artifact/execution decisions |
| Output: TimelineEditPlan | Input: TimelineRevision |
| Uses semantic metadata | Uses artifact DAG |
| AI/LLM-driven | Algorithm-driven |

No overlap.

## 9. V1 Scope

V1 supports:
- Highlight clip selection from transcript
- Simple caption insertion
- Marker insertion
- Basic effect suggestion
- New timeline revision creation

V1 does NOT support:
- Fully automatic final video generation
- Complex multi-agent editing
- Frame-level editing
- Generative video
- Real-time collaboration

## 10. Future Evolution

- V2: Multi-agent editing (ASR agent + Vision agent + Pacing agent)
- V3: Segment cache reuse (don't re-render unchanged clips)
- V4: Real-time authoring suggestions during manual editing
