# New Frontend Architecture — media-platform

**Date:** 2026-07-05
**Status:** PLANNED
**Authority:** FRONTEND-ARCH.0

---

## Core Decision

The next-generation frontend is planned as a **contract-first rebuild**, not a migration from the existing UI. The implementation should start after the backend API, RenderJob contract, canonical timeline model, caption template schema, and font manifest contract are stable enough to serve as frontend source-of-truth.

## Prerequisites (Gate)

Before frontend implementation begins, these contracts must be stable:

| Contract | Status | Notes |
|----------|--------|-------|
| RenderJob API | STABLE | Preview render endpoint working |
| Preview render endpoint | STABLE | `/api/render/preview` |
| Canonical Timeline model | STABLE | TimelineSpec, tracks, clips |
| Caption template schema | STABLE | CaptionTemplateRenderRequest |
| Font manifest contract | PLANNED | Font subset, glyph coverage |
| Asset upload/reference | STABLE | Asset API |
| Job status/artifact response | STABLE | PreviewRenderJobResponse |
| Auth/tenant model | PLANNED | Current: permit-all |
| Error response format | STABLE | Structured error model |

## Architecture Goals

1. **Contract-first API integration** — Generated API client from OpenAPI/schema
2. **Timeline-first editor state** — Timeline is the source of truth
3. **RenderJob assembly** — Frontend assembles RenderJob from timeline
4. **Remotion preview bridge** — Remotion consumes render contracts, not UI CSS
5. **Caption style/template editing** — Generates CaptionStyleSchema
6. **Font manifest consistency** — Font picker + glyph warnings
7. **Asset management** — Media/font/template assets
8. **Render job dashboard** — Job status, artifacts, errors
9. **Provider/capability visibility** — What providers are available
10. **Preview deployment workflow** — Branch-based preview

## Frontend Stack Recommendation

| Layer | Technology | Notes |
|-------|-----------|-------|
| Framework | React 19 | Already adopted |
| Build | Vite 6 | Already adopted |
| Language | TypeScript 5.7 | Already adopted |
| Routing | TanStack Router | Already adopted |
| Data fetching | TanStack Query | Already adopted |
| State | Zustand | Already adopted |
| Validation | Zod | Already adopted |
| Styling | TBD | See styling-technology-decision.md |
| Preview | Remotion Player | Already adopted |

## Directory Structure (Proposed)

```
frontend-new/
  src/
    app/                     # routing / app shell / layout
    api/                     # generated API client / query layer
    editor/                  # timeline / canvas / caption editor
      timeline/
      canvas/
      captions/
      inspector/
      selection/
      commands/
    preview/                 # Remotion player bridge
    render-job/              # RenderJob contract assembly
    assets/                  # media / font / template assets
    design-system/           # tokens / primitives / theme
    ui/                      # generic UI primitives
    jobs/                    # render job dashboard
    settings/                # project/user/system settings
    admin/                   # provider/capability/internal tools
    state/                   # Zustand or other state coordination
    schemas/                 # Zod schemas / generated schemas
    testing/                 # test utilities
```

## Boundary Definitions

| Component | Ownership | Notes |
|-----------|----------|-------|
| `editor/` | Platform-owned | Custom components, not bound to component libraries |
| `preview/` | Remotion bridge | Consumes render-facing contracts |
| `design-system/` | Web UI tokens | StyleX/Astryx/Tailwind/CSS Modules |
| `render-job/` | Contract assembly | Frontend → backend RenderJob |
| `schemas/` | Contract validation | Zod schemas, shared with backend |

## Styling Three-Layer Model

### Layer 1: Web Product UI

Components: buttons, forms, panels, settings, jobs, admin, asset list, render job dashboard

Options: StyleX, Astryx, Tailwind, CSS Modules, Radix primitives

### Layer 2: Editor Interaction

Components: timeline, track, clip, caption segment, word-level selection, canvas overlay, keyframe curve, inspector

Recommendation: **Custom editor components**, platform-owned design tokens

### Layer 3: Video Render Styles

Components: caption font, size, color, stroke, shadow, position, safe area, animation, template, font fallback, glyph coverage

Must use: **Platform-owned schemas** (CaptionStyleSchema, TextStyleToken, FontManifest, RenderJob props)

**NOT** Web UI CSS (StyleX/Astryx/Tailwind are for Web UI only)

## Remotion Boundary

Remotion preview is a **render preview bridge**, not a regular UI component library.

- Remotion receives: RenderJob, PreviewProps, FontManifest, CaptionStyleSchema
- Visual consistency comes from **render contracts**, not Web UI CSS
- StyleX/Astryx/Tailwind style the editor UI; render output uses platform schemas

## What NOT To Do

- Do not treat this as a migration from the old frontend
- Do not introduce styling libraries into production without scoped POC
- Do not bind render output styles to Web UI CSS
- Do not use component libraries for editor core interactions
- Do not start implementation before contracts are stable

---

*Document created by FRONTEND-ARCH.0*
