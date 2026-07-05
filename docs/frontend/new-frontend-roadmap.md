# New Frontend Roadmap — media-platform

**Date:** 2026-07-05
**Status:** PLANNED
**Authority:** FRONTEND-ARCH.0

---

## Phase 0 — Contract Stabilization Gate

**Goal:** Wait for backend contracts to stabilize before frontend implementation.

**Inputs:**
- OpenAPI / API docs
- RenderJob schema
- Timeline schema
- Caption template schema
- Font manifest schema
- Asset contract
- Preview render endpoint
- Job status endpoint
- Artifact response format
- Error response format

**Deliverables:**
- Frontend contract readiness checklist
- API client generation plan
- Schema validation plan

**Completion Criteria:**
- Key contracts documented
- Backend has smoke tests
- Preview endpoint working
- Job status/artifact flow working

**Status:** GATE (backend contracts mostly stable)

---

## Phase 1 — Frontend Architecture POC

**Goal:** Validate new frontend technology baseline.

**Scope:**
- React 19 app shell
- Routing (TanStack Router)
- Query layer (TanStack Query)
- Auth placeholder
- API client mock
- StyleX / Astryx / Tailwind / CSS Modules technology POC
- Design token demo
- Jobs/settings/admin page demo

**NOT included:**
- Complete timeline editor
- Complete caption editor
- Complete Remotion production integration

**Completion Criteria:**
- Styling approach selected
- Build pipeline confirmed
- Bundle baseline confirmed
- Agent maintainability confirmed

**Depends on:** Phase 0

---

## Phase 2 — RenderJob Preview Flow

**Goal:** Create minimal RenderJob from frontend and call preview endpoint.

**Scope:**
- Asset selection
- Minimal timeline form
- RenderJob assembly
- Preview request
- Job status polling
- Artifact result display
- Basic error display

**Completion Criteria:**
- Can initiate preview render from UI
- Can see job status
- Can see artifact response

**Depends on:** Phase 1

---

## Phase 3 — Timeline Editor Core

**Goal:** Build core timeline editor.

**Scope:**
- Timeline state
- Tracks/clips
- Selection model
- Trim/split/move
- Undo/redo command model
- Basic inspector
- Keyboard shortcuts

**Completion Criteria:**
- Timeline state convertible to RenderJob
- Basic editing operations stable
- Core interaction components platform-owned

**Depends on:** Phase 2

---

## Phase 4 — Caption / Font / Template Editor

**Goal:** Build caption template and font consistency editing.

**Scope:**
- Caption segments
- Word-level editing
- Caption style inspector
- Font picker
- Font manifest integration
- Missing glyph warnings
- Caption template preview
- Style schema validation

**Completion Criteria:**
- Frontend style editing generates backend-acceptable CaptionStyleSchema
- Remotion preview and render contract use same schema

**Depends on:** Phase 3

---

## Phase 5 — Remotion Preview Integration

**Goal:** Integrate Remotion Player as preview bridge.

**Scope:**
- PreviewProps
- FontManifest
- CaptionStyleSchema
- Timeline model
- Remotion Player
- Preview sync
- Safe area
- Frame/time mapping

**Completion Criteria:**
- Remotion preview aligned with backend preview render contract
- Video output styles not dependent on Web UI CSS

**Depends on:** Phase 4

---

## Phase 6 — Jobs / Provider / Admin UI

**Goal:** Build management UI.

**Scope:**
- Render job dashboard
- Provider capability matrix
- Worker status
- Artifact list
- Project settings
- System settings
- Internal tools

**Can expand usage of:**
- Astryx
- StyleX
- Radix primitives

**Completion Criteria:**
- Can observe render jobs
- Can view provider capabilities
- Can track artifacts

**Depends on:** Phase 2

---

## Phase 7 — Production Hardening

**Goal:** Frontend production readiness.

**Scope:**
- Auth / tenant
- Rate limit UX
- Error boundary
- Logging
- Telemetry
- Accessibility
- i18n
- Performance
- Bundle optimization
- Visual regression
- E2E tests

**Depends on:** Phase 6

---

## Agent Task Sequence

| Task | Depends On | Scope |
|------|-----------|-------|
| FRONTEND-CONTRACT-GATE.0 | None | Audit backend API readiness |
| FRONTEND-STYLE-POC.0 | CONTRACT-GATE.0 | Evaluate styling technologies |
| FRONTEND-SHELL.0 | STYLE-POC.0 | React app shell and routing |
| FRONTEND-API.0 | SHELL.0 | Generate API client and query layer |
| FRONTEND-RENDERJOB.0 | API.0 | Minimal RenderJob creation flow |
| FRONTEND-PREVIEW.0 | RENDERJOB.0 | Connect preview render endpoint |
| FRONTEND-TIMELINE.0 | PREVIEW.0 | Minimal timeline state and UI |
| FRONTEND-CAPTION.0 | TIMELINE.0 | Caption style editor |
| FRONTEND-FONT.0 | CAPTION.0 | Font manifest integration |
| FRONTEND-REMOTION.0 | FONT.0 | Remotion Player bridge |
| FRONTEND-JOBS.0 | PREVIEW.0 | Render job dashboard |
| FRONTEND-PROVIDER.0 | JOBS.0 | Provider capability dashboard |
| FRONTEND-HARDEN.0 | All | Accessibility, telemetry, tests |

---

## Timeline Estimate

| Phase | Duration | Dependencies |
|-------|----------|-------------|
| 0 | 0 weeks | Backend contracts |
| 1 | 2-3 weeks | Phase 0 |
| 2 | 2-3 weeks | Phase 1 |
| 3 | 4-6 weeks | Phase 2 |
| 4 | 3-4 weeks | Phase 3 |
| 5 | 2-3 weeks | Phase 4 |
| 6 | 3-4 weeks | Phase 2 |
| 7 | 2-3 weeks | Phase 6 |

**Total estimated:** 18-26 weeks (4.5-6.5 months)

---

*Document created by FRONTEND-ARCH.0*
