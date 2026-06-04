# Frontend Editor Workbench UI

> **Purpose:** Document the editor layout structure, panel system, component hierarchy, and state management.  
> **Scope:** Vue 3.5 + TypeScript + Vite 6 frontend at `media-platform/frontend/src/`  
> **Last Updated:** 2026-05-16 (Prompt 62)

---

## Editor Layout Structure

The video editor uses a **three-panel layout** with a header bar:

```
┌─────────────────────────────────────────────────────────────┐
│ Header (EditorShell)                                        │
│ [←] Project Name    [CPU] [Worker] [Connected] [Save] [Export]│
├──────────┬──────────────────────────────┬───────────────────┤
│          │                              │                   │
│  Left    │        Center                │      Right        │
│  Panel   │        Panel                 │      Panel        │
│          │                              │                   │
│  Clip    │    Timeline Editor           │  [Effects] [Export]│
│  Library │                              │  [Subtitles]      │
│          │    ┌────────────────────┐    │                   │
│  [All]   │    │ Video 1 Track      │    │  Properties       │
│  [Video] │    │ [Clip1] [Clip2]    │    │  Panel            │
│  [Audio] │    ├────────────────────┤    │                   │
│  [Image] │    │ Audio 1 Track      │    │  Clip Properties  │
│          │    │ [AudioClip]        │    │  or Project Info  │
│  Upload  │    └────────────────────┘    │                   │
│  Zone    │                              │                   │
│          │    Playhead ──────►          │                   │
│          │                              │                   │
├──────────┴──────────────────────────────┴───────────────────┤
│ Bottom Panel (Playback Controls)                            │
│ [↩️] [↪️] [Play] [⏹] 0:00 / 1:30    [Zoom -] [1.0x] [Zoom +]│
└─────────────────────────────────────────────────────────────┘
```

### Panel Dimensions

| Panel | Position | Resizable | Default Width |
|-------|----------|-----------|---------------|
| Left (Clip Library) | Left | ✅ (collapsible) | ~250px |
| Center (Timeline) | Center | Flexible | Remaining space |
| Right (Properties/Tabs) | Right | ✅ (collapsible) | ~300px |
| Bottom (Playback) | Bottom | ❌ | ~60px |
| Header | Top | ❌ | ~48px |

---

## Panel System

### Left Panel — Clip Library

The left panel contains the **Clip Library** (`ClipLibrary.vue`) with:

1. **Search bar** — Filter clips by name
2. **Type filter** — Toggle between All, Video, Audio, Image, Subtitle, Text
3. **Clip list** — Draggable clip items with metadata
4. **Empty state** — `EmptyProjectGuide` when no clips exist
5. **Upload zone** — `MediaUploadDropzone` at the bottom
6. **Upload progress** — `UploadProgressList` for active uploads

### Center Panel — Timeline Editor

The center panel contains the **Timeline Editor** (`TimelineEditor.vue`) with:

1. **Track headers** — Track names and mute/lock controls
2. **Track lanes** — Visual representation of clips over time
3. **Playhead** — Draggable current-time indicator
4. **Time ruler** — Time markers at current zoom level
5. **Clip selection** — Click to select, drag to move

### Right Panel — Tabbed Panel

The right panel contains three tabs:

| Tab | Component | Icon | Purpose |
|-----|-----------|------|---------|
| Effects | `EffectsPanel.vue` | ✨ | Apply effects and transitions |
| Export | `ExportPanel.vue` | 📤 | Configure and submit render jobs |
| Subtitles | `SubtitlesPanel.vue` | 📝 | Manage subtitle tracks |

Below the tabs, the **Properties Panel** (`PropertiesPanel.vue`) shows:
- Clip properties when a clip is selected
- Project properties when no clip is selected

### Header — Editor Shell

The **Editor Shell** (`EditorShell.vue`) provides:

1. **Navigation** — Back to home link
2. **Project name** — Display only (editable via Properties)
3. **Save status** — "Unsaved changes" or "All changes saved"
4. **Status indicators** — CPU/GPU, Worker, Connected
5. **Action buttons** — Undo, Redo, Save, Export

---

## Component Hierarchy

```
App.vue
└── RouterView
    └── EditorPage.vue
        ├── EditorShell (header)
        │   ├── Project name
        │   ├── Save status
        │   ├── Status indicators
        │   └── Action buttons
        │
        ├── Left Panel
        │   └── ClipLibrary.vue
        │       ├── SearchBar
        │       ├── TypeFilter
        │       ├── ClipList
        │       │   └── ClipItem (×N)
        │       ├── EmptyProjectGuide.vue
        │       ├── MediaUploadDropzone.vue
        │       └── UploadProgressList.vue
        │
        ├── Center Panel
        │   └── TimelineEditor.vue
        │       ├── TimeRuler
        │       ├── TrackHeader (×N)
        │       ├── TrackLane (×N)
        │       │   └── ClipItem (×M)
        │       └── Playhead
        │
        ├── Right Panel
        │   ├── TabBar
        │   │   ├── EffectsPanel.vue
        │   │   │   ├── CategoryTabs
        │   │   │   ├── TierSelector
        │   │   │   ├── EffectList
        │   │   │   └── EffectParameterEditor.vue
        │   │   ├── ExportPanel.vue
        │   │   │   ├── TimelineSummary
        │   │   │   ├── BudgetStatus
        │   │   │   ├── WorkerSelection
        │   │   │   ├── PresetSelection
        │   │   │   ├── CostEstimate
        │   │   │   ├── FormatSelection
        │   │   │   ├── SubtitleMode
        │   │   │   ├── RenderJobStatus.vue
        │   │   │   ├── ArtifactResult.vue
        │   │   │   ├── ArtifactPreviewModal.vue
        │   │   │   └── ExportButton
        │   │   └── SubtitlesPanel.vue
        │   │       ├── UploadSection
        │   │       ├── LanguageSelector
        │   │       ├── BurnInSelector
        │   │       └── SubtitleCueList.vue
        │   │           └── SubtitleCueEditor.vue
        │   └── PropertiesPanel.vue
        │       ├── ClipProperties
        │       └── ProjectProperties
        │
        └── Bottom Panel (Playback Controls)
            ├── StepBackward
            ├── StepForward
            ├── PlayPause
            ├── Stop
            ├── TimeDisplay
            ├── ZoomOut
            ├── ZoomLevel
            └── ZoomIn
```

---

## State Management

### Stores (Pinia)

| Store | File | Purpose | Key State |
|-------|------|---------|-----------|
| `useTimelineStore` | `stores/timeline.ts` | Timeline data | `tracks`, `clips`, `duration`, `currentTime`, `zoom`, `playing`, `selectedClipId` |
| `useProjectStore` | `stores/project.ts` | Project metadata | `currentProject`, `currentTenant`, `saving`, `renderJobs`, `error` |
| `useSubtitleStore` | `stores/subtitle.ts` | Subtitle data | `tracks`, `fonts`, `activeTrackId`, `loading`, `error` |
| `useHistoryStore` | `stores/history.ts` | Undo/redo | `undoStack`, `redoStack` |
| `useEffectPackStore` | `stores/effectPack.ts` | Effect packs | `packs`, `activePackId`, `availableEffects` |

### Composables

| Composable | File | Purpose | Returns |
|------------|------|---------|---------|
| `usePlayback` | `composables/usePlayback.ts` | Playback control | `isPlaying`, `currentTime`, `togglePlayback()`, `stepForward()`, `stepBackward()`, `seek()` |
| `useSaveProject` | `composables/useSaveProject.ts` | Project saving | `isSaving`, `isDirty`, `saveError`, `lastSavedAt`, `saveProject()`, `markDirty()`, `clearDirty()` |
| `useExportValidation` | `composables/useExportValidation.ts` | Export validation | `validationResult`, `isValidating`, `validationError`, `validateExport()`, `clearValidation()` |
| `useRenderJob` | `composables/useRenderJob.ts` | Render job management | `jobId`, `status`, `progress`, `error`, `submitRenderJob()`, `cancelJob()`, `retryJob()` |
| `useArtifact` | `composables/useArtifact.ts` | Artifact management | `artifact`, `isDownloading`, `downloadError`, `previewOpen`, `downloadArtifact()`, `openPreview()`, `closePreview()` |
| `useI18nError` | `utils/i18n.ts` | Error message i18n | `t(errorCode)`, `setLocale()`, `currentLocale` |

### Store Relationships

```
useTimelineStore ←──→ useHistoryStore (undo/redo saves timeline state)
     │
     ├──→ useProjectStore (timeline belongs to project)
     │
     ├──→ useSubtitleStore (subtitle tracks reference timeline)
     │
     └──→ useEffectPackStore (effects applied to timeline clips)
```

---

## State Flow

### Playback Flow

```
User clicks Play
    │
    ▼
usePlayback.togglePlayback()
    │
    ▼
isPlaying.value = true
    │
    ▼
setInterval (30ms)
    │
    ▼
currentTime.value += 0.03
    │
    ▼
TimelineEditor updates playhead position
    │
    ▼
PropertiesPanel updates time display
```

### Save Flow

```
User clicks Save / Ctrl+S
    │
    ▼
useSaveProject.saveProject()
    │
    ▼
isSaving.value = true
    │
    ▼
ProjectAPI.create(projectData)
    │
    ▼
On success:
  isSaving.value = false
  isDirty.value = false
  lastSavedAt.value = new Date()
    │
    ▼
EditorShell updates save status display
```

### Export Flow

```
User clicks Export Video
    │
    ▼
useExportValidation.validateExport()
    │
    ▼
On blocked: show error + recommended preset
On allowed:
    │
    ▼
useRenderJob.submitRenderJob()
    │
    ▼
RenderAPI.createJob(params)
    │
    ▼
Start polling (3s interval)
    │
    ▼
RenderJobStatus displays progress
    │
    ▼
On completed:
  ArtifactResult displays output
  ArtifactPreviewModal available
```

---

## i18n System

Error codes are translated using the `useI18nError()` composable:

### Supported Locales

| Locale | Code | Coverage |
|--------|------|----------|
| English | `en` | All 47 error codes |
| Chinese | `zh` | All 47 error codes |

### Error Code Format

```
{CATEGORY}-{HTTP_STATUS}-{SEQUENCE}
```

Examples:
- `RENDER-500-001` — Render execution failed
- `SUBTITLE-400-001` — Subtitle parsing failed
- `COMMON-404-001` — Resource not found

---

## Routing

The editor is accessible via:
- `/` — Default editor page (new project)
- `/project/:id` — Load existing project

Routes are defined in `router/index.ts` using Vue Router 4.

---

## Test Coverage

| Component | Test File | Tests |
|-----------|-----------|-------|
| `EmptyProjectGuide` | Via `ClipLibrary` integration | Renders empty state, upload button, demo button |
| `ClipLibrary` | Via `TimelineEditor` + store tests | Clip display, insert, delete, filter |
| `TimelineEditor` | `TimelineEditor.spec.ts` | 16 tests (tracks, clips, playback, zoom, demo) |
| `PropertiesPanel` | Via store integration tests | Selected clip display, project properties |
| `MediaUploadDropzone` | Via `ClipLibrary` integration | Drag/drop, file selection |
| `UploadProgressList` | Via `ClipLibrary` integration | Progress display, cancel |
| `ExportPanel` | `ExportPanel.spec.ts` | 7 tests (GraphQL data, validation, workers) |
| `RenderJobStatus` | `RenderJobStatus.spec.ts` | 13 tests (states, actions, errors) |
| `ArtifactResult` | `ArtifactResult.spec.ts` | 16 tests (display, actions, formatting) |
| `ArtifactPreviewModal` | `ArtifactPreviewModal.spec.ts` | 12 tests (media types, open/close) |
| `SubtitlesPanel` | `SubtitlesPanel.spec.ts` | 6 tests (upload, empty state, language) |
| `EffectsPanel` | `EffectsPanel.spec.ts` | 8 tests (categories, tier, effects list) |
| `ErrorState` | `ErrorState.spec.ts` | 10 tests (rendering, events, admin debug) |
| `usePlayback` | `usePlayback.spec.ts` | 13 tests (playback, stepping, seeking) |
| `useSaveProject` | `useSaveProject.spec.ts` | 7 tests (state, save, status text) |
| `useExportValidation` | `useExportValidation.spec.ts` | 4 tests (validation, debounce, clear) |
| `useRenderJob` | `useRenderJob.spec.ts` | 8 tests (submit, cancel, retry, progress) |
| `useArtifact` | `useArtifact.spec.ts` | 6 tests (set, preview, download) |

---

## Build Configuration

### Vite Config (`vite.config.ts`)

- **Vue plugin** with custom element handling for `<buttons>`
- **Path alias**: `@` → `./src`
- **Dev server**: Port 3000 with proxy to backend (port 8080)

### Vitest Config

- **Environment**: jsdom
- **Globals**: Enabled (`describe`, `it`, `expect` available globally)
- **Setup file**: `test-setup.ts` (patches `querySelectorAll` for `buttons` selector)
- **Root**: `./` (frontend directory)

---

## Component Reference

| Component | File | Emits | Props |
|-----------|------|-------|-------|
| `EditorShell` | `components/editor/EditorShell.vue` | `undo`, `redo`, `save`, `export` | `gpuAvailable`, `workerConnected`, `saveStatus` |
| `EmptyProjectGuide` | `components/editor/EmptyProjectGuide.vue` | `upload`, `tryDemo`, `importSubtitle` | — |
| `PropertiesPanel` | `components/editor/PropertiesPanel.vue` | — | — |
| `ClipLibrary` | `components/clip-library/ClipLibrary.vue` | `tryDemo`, `importSubtitle` | — |
| `MediaUploadDropzone` | `components/upload/MediaUploadDropzone.vue` | `files-selected` | — |
| `UploadProgressList` | `components/upload/UploadProgressList.vue` | `cancel` | `items` |
| `ExportPanel` | `components/export/ExportPanel.vue` | — | — |
| `RenderJobStatus` | `components/export/RenderJobStatus.vue` | `retry`, `cancel`, `copy-diagnostic` | `jobId`, `status`, `progress`, `error`, `errorCode`, `diagnosticInfo` |
| `ArtifactResult` | `components/export/ArtifactResult.vue` | `preview`, `download`, `copy-id`, `open-catalog`, `view-logs` | `artifact` |
| `ArtifactPreviewModal` | `components/export/ArtifactPreviewModal.vue` | `close` | `open`, `artifact` |
| `SubtitlesPanel` | `components/subtitles/SubtitlesPanel.vue` | — | — |
| `EffectsPanel` | `components/effects/EffectsPanel.vue` | — | — |
| `ErrorState` | `components/ui/ErrorState.vue` | `retry`, `dismiss` | `title`, `description`, `errorCode`, `diagnosticId`, `showRetry`, `showDismiss`, `showAdminDebug` |
