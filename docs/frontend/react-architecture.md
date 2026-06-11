# React Architecture

## Overview

This document describes the React architecture for the video editor frontend.

## Why React

### 1. Remotion is React

Remotion is a React-based video composition framework. Every Remotion Composition is a React component. Every Remotion Player is a React component. Using React as the UI framework means:

- **No bridge needed**: Remotion Player runs directly in the React tree
- **Shared component model**: Caption templates, font effects, and visual effects are React components used in both preview and render
- **Shared types**: TypeScript types are shared between frontend, Remotion compositions, and backend

### 2. No Vue Compatibility

This is a new project. There is no existing Vue code to maintain. The previous Vue 3 frontend (App.vue, router, stores, 30+ admin pages) is not carried forward. No Vue/React bridge is needed.

### 3. Ecosystem

The React ecosystem provides excellent tools for video editing UIs:
- **dnd-kit**: Drag and drop for timeline
- **TanStack Virtual**: Virtual scrolling for long timelines
- **Zustand**: Simple, scalable state management
- **TanStack Query**: Server state management with caching
- **Zod**: Schema validation shared with backend
- **Radix UI / shadcn/ui**: Accessible component primitives

## Architecture Layers

```
┌─────────────────────────────────────────────────┐
│                  React App                       │
│  ┌─────────────────────────────────────────────┐ │
│  │              Editor Module                   │ │
│  │  ┌─────────┐ ┌─────────┐ ┌─────────┐      │ │
│  │  │Timeline │ │ Canvas  │ │Inspector│      │ │
│  │  └─────────┘ └─────────┘ └─────────┘      │ │
│  │  ┌─────────┐ ┌─────────┐ ┌─────────┐      │ │
│  │  │Captions │ │Templates│ │Playback │      │ │
│  │  └─────────┘ └─────────┘ └─────────┘      │ │
│  └─────────────────────────────────────────────┘ │
│  ┌─────────────────────────────────────────────┐ │
│  │           Remotion Module                    │ │
│  │  ┌─────────────────────────────────────┐    │ │
│  │  │        Remotion Player              │    │ │
│  │  │  ┌───────────────────────────────┐  │    │ │
│  │  │  │     Remotion Composition      │  │    │ │
│  │  │  │  ┌─────────┐ ┌─────────────┐ │  │    │ │
│  │  │  │  │Captions │ │   Effects   │ │  │    │ │
│  │  │  │  └─────────┘ └─────────────┘ │  │    │ │
│  │  │  └───────────────────────────────┘  │    │ │
│  │  └─────────────────────────────────────┘    │ │
│  └─────────────────────────────────────────────┘ │
│  ┌─────────────────────────────────────────────┐ │
│  │          RenderJob Module                    │ │
│  │  ┌─────────┐ ┌──────────┐ ┌────────────┐  │ │
│  │  │ Schema  │ │ Builders │ │ Validators │  │ │
│  │  └─────────┘ └──────────┘ └────────────┘  │ │
│  └─────────────────────────────────────────────┘ │
│  ┌─────────────────────────────────────────────┐ │
│  │            API Module                        │ │
│  │  ┌─────────┐ ┌──────────┐ ┌────────────┐  │ │
│  │  │ Render  │ │ Materials│ │ Projects   │  │ │
│  │  └─────────┘ └──────────┘ └────────────┘  │ │
│  └─────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────┘
```

## Module Responsibilities

### Editor Module

Responsible for the video editing user interface:

- **Timeline**: Multi-track timeline with drag-and-drop (dnd-kit)
- **Canvas**: Preview area (wraps Remotion Player)
- **Captions**: Caption editor with text, timing, style
- **Templates**: Template selector and preview
- **Inspector**: Properties panel for selected elements
- **Playback**: Play/pause/seek controls

### Remotion Module

Responsible for video composition and preview:

- **Compositions**: Remotion Composition definitions
- **Caption Components**: Caption template React components
- **Effects Components**: Visual effects React components
- **Font Loaders**: Font loading components
- **Player Wrapper**: Remotion Player configuration

### RenderJob Module

Responsible for RenderJob schema and builders:

- **Schema**: Zod schemas for RenderJob, RenderPlan, RenderStep
- **Builders**: Functions to build RenderJob from Editor State
- **Validators**: Validation helpers for RenderJob

### API Module

Responsible for backend communication:

- **Render API**: Submit render jobs, poll status, fetch results
- **Materials API**: Upload, browse, manage media assets
- **Projects API**: Create, update, delete projects

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

## Component Hierarchy

```
App
├── QueryClientProvider
├── ThemeProvider
├── AppLayout
│   ├── AppHeader
│   ├── AppSidebar
│   └── AppContent
│       └── Routes
│           ├── EditorPage
│           │   ├── EditorLayout
│           │   │   ├── TimelinePanel
│           │   │   │   ├── TimelineTrack
│           │   │   │   │   ├── VideoClip
│           │   │   │   │   ├── AudioClip
│           │   │   │   │   └── CaptionClip
│           │   │   │   └── TimelineRuler
│           │   │   ├── CanvasPanel
│           │   │   │   └── RemotionPlayerWrapper
│           │   │   │       └── RemotionComposition
│           │   │   ├── InspectorPanel
│           │   │   │   ├── CaptionInspector
│           │   │   │   ├── TemplateInspector
│           │   │   │   └── EffectInspector
│           │   │   └── PlaybackControls
│           │   └── CaptionEditor
│           │       ├── CaptionList
│           │       ├── CaptionTimeline
│           │       └── CaptionStylePanel
│           ├── ProjectLibraryPage
│           └── SettingsPage
```

## State Management

### Editor State (Zustand)

```typescript
interface EditorState {
  // Timeline
  timeline: TimelineState;
  // Captions
  captions: CaptionState;
  // Templates
  templates: TemplateState;
  // Selection
  selection: SelectionState;
  // Playback
  playback: PlaybackState;
  // UI
  ui: UIState;
}

interface TimelineState {
  tracks: TimelineTrack[];
  duration: number;
  zoom: number;
  scrollPosition: number;
}

interface CaptionState {
  captions: Caption[];
  selectedCaptionId: string | null;
}

interface TemplateState {
  selectedTemplateId: string | null;
  templateParams: Record<string, unknown>;
}
```

### Server State (TanStack Query)

```typescript
// Render jobs
useQuery({ queryKey: ['renderJob', id], queryFn: fetchRenderJob });
useMutation({ mutationFn: submitRenderJob });

// Projects
useQuery({ queryKey: ['projects'], queryFn: fetchProjects });

// Materials
useQuery({ queryKey: ['materials'], queryFn: fetchMaterials });
```

## Routing

Using TanStack Router:

```typescript
const routeConfig = new RouteConfig()
  .addRootRoute({
    component: AppLayout,
  })
  .addRoute({
    path: '/editor/$projectId',
    component: EditorPage,
  })
  .addRoute({
    path: '/projects',
    component: ProjectLibraryPage,
  });
```

## Styling

Using Tailwind CSS with a design token system:

```css
@layer base {
  :root {
    --background: 0 0% 100%;
    --foreground: 222.2 84% 4.9%;
    --primary: 222.2 47.4% 11.2%;
    --secondary: 210 40% 96.1%;
    --muted: 210 40% 96.1%;
    --accent: 210 40% 96.1%;
    --destructive: 0 84.2% 60.2%;
  }
}
```

## Accessibility

- All interactive components use Radix UI primitives (accessible by default)
- Keyboard shortcuts for common operations
- ARIA labels for screen readers
- Focus management for modal dialogs
