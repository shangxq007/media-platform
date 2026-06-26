---
status: accepted
created: 2026-06-25
scope: platform-wide
owner: platform
---

# ADR-008: Creative Planner & Timeline Authoring

## Context

The platform has AI enrichment (ASR, OCR, Vision, Embedding) and Timeline Git (revision, diff, merge, review). Missing is the layer that transforms AI understanding into editing decisions.

## Decision

Introduce a Creative Planner layer between AI enrichment and Timeline mutation.

AI generates `TimelineEditPlan` (not final video, not direct Timeline mutation). `TimelineMutationService` validates and applies the plan, creating a `TimelineRevision` that enters the standard review → merge → render pipeline.

## Consequences

- AI never directly mutates Timeline — always goes through TimelineMutationService
- AI-generated revisions are reviewable (explanation + confidence + semantic anchors)
- Existing review/merge/render pipeline unchanged
- Creative Planner is separate from Execution Planner (content vs execution)

## Rejected Alternatives

1. AI directly generates final video: bypasses Timeline Git, no version control, no review
2. AI directly mutates Timeline: no audit trail, no rollback, no structured review
3. Execution Planner handles creative decisions: conflates content with execution
4. Render provider decides editing structure: tight coupling, no reuse across backends

## Migration

Phase 1: TimelineEditPlan domain model (Sprint 047)
Phase 2: TimelineMutationService (Sprint 048)
Phase 3: Semantic highlight planner (Sprint 049)
