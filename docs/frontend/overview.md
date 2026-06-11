# Frontend Architecture Overview

> **Module:** `frontend/`
> **Last Updated:** 2026-06-11
> **Status:** React-first

## Technology Stack

| Component | Role |
|-----------|------|
| React 19 | UI framework |
| TypeScript | Type safety |
| Vite | Build tool |
| Vitest | Test framework |
| TanStack Router | Client-side routing |
| Zustand | State management |
| TanStack Query | Server state management |
| Zod | Schema validation |
| Tailwind CSS | Styling |
| Radix UI / shadcn/ui | Component primitives |
| dnd-kit | Drag and drop |
| react-hook-form + zod | Form handling |
| TanStack Virtual | Virtual scrolling |
| Remotion | Video composition & preview |

## Why React

### 1. Remotion is React-native

RemotionRenderProvider, Remotion Player, Remotion Composition, subtitle templates, font effects, and consistent front/back-end preview are all built on React. Using React as the frontend framework means:

- Remotion Player runs directly in the browser as a React component
- Remotion Composition is authored in React
- Subtitle templates are React components
- Font effects are React components
- Preview and render share the same React component tree

### 2. No Vue/React bridge needed

This is a new project. There is no existing Vue code to maintain compatibility with. Using React throughout the stack ensures:

- Shared types between frontend and Remotion compositions
- Shared validation schemas (Zod) across frontend, backend, and Remotion
- Consistent component model for preview and render
- Single mental model for developers

### 3. Ecosystem

The React ecosystem provides excellent tools for video editing UIs:
- **dnd-kit**: Drag and drop for timeline
- **TanStack Virtual**: Virtual scrolling for long timelines
- **Zustand**: Simple, scalable state management
- **TanStack Query**: Server state management with caching
- **Zod**: Schema validation shared with backend
- **Radix UI / shadcn/ui**: Accessible component primitives

## Architecture Principles

1. **React-first**: All UI is React. No Vue, no Vue/React bridge.
2. **Remotion-native**: Video preview and composition use Remotion directly.
3. **Schema-first**: RenderJob Schema is the contract between frontend, backend, and Remotion.
4. **State isolation**: Editor State does not leak into Remotion Composition. Editor State is converted to standard RenderJob/PreviewProps before passing to Remotion.
5. **Provider-agnostic UI**: Providers (FFmpeg, MLT, GPAC, Libass, Blender, BMF) are not directly exposed in the UI. They appear only as capabilities or export options determined by the backend RenderOrchestrator.
6. **Font asset management**: Fonts are managed through FontManifest/FontAsset. No system font dependency.
7. **Consistent rendering**: Front-end preview and back-end render use the same Composition + same inputProps + same font assets.

## Directory Structure

```
frontend/
  src/
    main.tsx                    # React entry point
    app/
      RootLayout.tsx            # Root layout
      routeTree.tsx             # Route definitions
      providers/                # Context providers (QueryClient, Theme)
      layout/                   # App layout components

    editor/
      EditorPage.tsx            # Main editor page
      components/               # Shared editor UI components
      timeline/                 # Timeline component (dnd-kit based)
      captions/                 # Caption editor
      templates/                # Template selector
      inspector/                # Properties inspector panel
      playback/                 # Playback controls
      state/                    # Zustand stores

    remotion/
      player/                   # Remotion Player wrapper
      compositions/             # Remotion Composition definitions
      captions/                 # Caption template components
      fonts/                    # Font loader components
      templates/                # Reusable template components

    render-job/
      schema/                   # Zod schemas for RenderJob
      builders/                 # RenderJob builder functions
      validators/               # Validation helpers

    api/
      render/                   # Render API client

    shared/
      types/                    # Shared TypeScript types
      utils/                    # Utility functions

    styles/
      index.css                 # Tailwind CSS + custom styles
```

## Application Flow

```
User Action
    │
    ▼
Editor State (Zustand)
    │
    ▼
Editor State → RenderJob (Builder)
    │
    ├──────────────────────────┐
    ▼                          ▼
Remotion Player         Backend API
(Preview)               (Render)
    │                          │
    ▼                          ▼
PreviewProps            RenderResult
(from RenderJob)        (from API)
```

## State Management

### Editor State (Zustand)

- **Timeline state** — Tracks, clips, effects, transitions
- **Caption state** — Caption text, timing, style, template
- **Template state** — Selected template, parameters
- **Selection state** — Selected element, multi-select
- **Playback state** — Play/pause/seek, current time
- **UI state** — Panel visibility, zoom, scroll position

### Server State (TanStack Query)

- **Render jobs** — Job status, progress, results
- **Projects** — Project CRUD
- **Materials** — Asset upload, browse, manage
- **Font manifest** — Font asset management

## Data Flow

```
User Action
    │
    ▼
Editor State (Zustand)
    │
    ▼
Editor State → RenderJob (Builder)
    │
    ├──────────────────────────┐
    ▼                          ▼
Remotion Player         Backend API
(Preview)               (Render)
    │                          │
    ▼                          ▼
PreviewProps            RenderResult
(from RenderJob)        (from API)
```

## Key Design Decisions

1. **No Vue**: This is a React-first project. No Vue code exists.
2. **No provider selection in UI**: Backend RenderPlanner decides providers.
3. **Font consistency**: Same FontManifest/subsetUrl for preview and render.
4. **Async rendering**: Long-running jobs use async polling/webhook.
5. **Artifact tracing**: All results tracked via RenderArtifact/RenderExecutionTrace.

## Related Documents

- [React Architecture](./react-architecture.md)
- [Editor State Management](./editor-state.md)
- [Remotion Integration](./remotion-integration.md)
- [RenderJob Contract](./renderjob-contract.md)
- [Timeline Model](./timeline-model.md)
- [Caption Template System](./caption-template-system.md)
- [Font Asset Management](./font-asset-management.md)
