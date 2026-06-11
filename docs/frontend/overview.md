# Frontend Architecture Overview

> **Module:** `frontend/`
> **Last Updated:** 2026-06-11
> **Status:** React-first migration

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

## Why React-first

### 1. Remotion is React-native

RemotionRenderProvider, Remotion Player, Remotion Composition, subtitle templates, font effects, and consistent front/back-end preview are all built on React. Using React as the frontend framework means:

- Remotion Player runs directly in the browser as a React component
- Remotion Composition is authored in React
- Subtitle templates are React components
- Font effects are React components
- Preview and render share the same React component tree

### 2. No Vue/React bridge needed

The previous Vue 3 frontend was a separate application. This is a new project. There is no existing Vue code to maintain compatibility with. Introducing a Vue/React bridge would add unnecessary complexity, increase bundle size, and create a maintenance burden.

### 3. Ecosystem alignment

The render pipeline (Remotion, subtitle templates, font management) is React-based. Using React throughout the stack ensures:

- Shared types between frontend and Remotion compositions
- Shared validation schemas (Zod) across frontend, backend, and Remotion
- Consistent component model for preview and render
- Single mental model for developers

### 4. No Vue App Shell

The previous Vue App Shell (App.vue, router, stores) is not carried forward. The new frontend is a clean React project with no Vue dependencies.

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
    app/
      routes/              # TanStack Router routes
      providers/           # Context providers (QueryClient, Theme, etc.)
      layout/              # App layout components

    editor/
      components/          # Shared editor UI components
      timeline/            # Timeline component (dnd-kit based)
      canvas/              # Canvas / preview area
      captions/            # Caption editor
      templates/           # Template selector
      inspector/           # Properties inspector panel
      playback/            # Playback controls
      state/               # Zustand stores
      commands/            # Editor command pattern
      shortcuts/           # Keyboard shortcuts

    remotion/
      compositions/        # Remotion Composition definitions
      captions/            # Caption template components
      effects/             # Visual effects components
      fonts/               # Font loader components
      templates/           # Reusable template components
      player/              # Remotion Player wrapper

    render-job/
      schema/              # Zod schemas for RenderJob
      builders/            # RenderJob builder functions
      serializers/         # Serialization utilities
      validators/          # Validation helpers

    assets/
      upload/              # Asset upload flow
      library/             # Asset library browser
      metadata/            # Asset metadata editor

    api/
      render/              # Render API client
      materials/           # Materials API client
      projects/            # Projects API client

    shared/
      types/               # Shared TypeScript types
      utils/               # Utility functions
      constants/           # Constants
```

## Key Design Decisions

### Editor State vs RenderJob

Editor State (Zustand store) contains:
- Timeline tracks, clips, effects
- Caption text, timing, style
- Selected template, font, effects
- UI state (selection, zoom, scroll position)

RenderJob (standard schema) contains:
- Normalized timeline data
- Caption data with timing
- Font asset references
- Template version, effect version
- Output specification

**Editor State is converted to RenderJob before passing to Remotion or submitting to the backend.**

### PreviewProps vs RenderJob

PreviewProps is the input to Remotion Composition:
- Derived from RenderJob
- Includes resolved font assets
- Includes resolved template data
- Includes caption timing data

**PreviewProps is a subset of RenderJob, optimized for Remotion rendering.**

### Provider-agnostic UI

The frontend does not know or care which provider handles a render job. The backend RenderOrchestrator determines the provider. The frontend only:
- Submits a standardized RenderJob
- Receives a RenderPlan with selected providers
- Displays the result

## Related Documents

- [React Architecture](./react-architecture.md)
- [Editor State Management](./editor-state.md)
- [Remotion Integration](./remotion-integration.md)
- [RenderJob Contract](./renderjob-contract.md)
- [Timeline Model](./timeline-model.md)
- [Caption Template System](./caption-template-system.md)
- [Font Asset Management](./font-asset-management.md)
