> [!NOTE]
> **Status:** Design/contract candidate.
> Frontend implementation is paused until backend contracts stabilize.
> This document describes design intent, not implemented capability.

# Editor Workbench UI

> **Module:** `frontend/`
> **Last Updated:** 2026-05-18

## Overview

The video editor is a Vue 3 single-page application providing timeline-based video editing with clip management, effects, subtitles, and export capabilities.

## Layout

```
┌─────────────────────────────────────────────────────────────┐
│                        Top Bar                               │
│  [Logo] [Project Name] [Save] [Export] [User Menu]          │
├──────────┬──────────────────────────────────┬───────────────┤
│          │                                  │               │
│  Clip    │         Preview                  │  Properties   │
│  Library │         Panel                    │  Panel        │
│          │                                  │               │
│  [Search]│    ┌──────────────────────┐      │  [Clip Info]  │
│  [Upload]│    │                      │      │  [Effects]    │
│          │    │    Video Preview     │      │  [Subtitles]  │
│  ┌────┐  │    │                      │      │               │
│  │Clip│  │    └──────────────────────┘      │               │
│  └────┘  │                                  │               │
│  ┌────┐  │                                  │               │
│  │Clip│  │                                  │               │
│  └────┘  │                                  │               │
│          │                                  │               │
├──────────┴──────────────────────────────────┴───────────────┤
│                      Timeline                                │
│  ┌─────────────────────────────────────────────────────────┐ │
│  │  Track 1: [====Clip A====][==Clip B==]                  │ │
│  │  Track 2: [======Clip C======]                          │ │
│  │  Subtitles: [__Subtitle 1__][__Subtitle 2__]            │ │
│  └─────────────────────────────────────────────────────────┘ │
│  [Play] [Pause] [<<] [>>]          Timeline: 00:00 / 01:30  │
└─────────────────────────────────────────────────────────────┘
```

## Component Hierarchy

```
EditorPage
├── TopBar
│   ├── ProjectTitle
│   ├── SaveButton
│   └── ExportButton
├── ClipLibrary
│   ├── SearchBar
│   ├── UploadDropzone
│   ├── ClipList
│   └── ClipItem
├── PreviewPanel
│   ├── VideoPlayer
│   └── PlayheadControl
├── PropertiesPanel
│   ├── ClipProperties
│   ├── EffectsPanel
│   └── SubtitlesPanel
├── TimelineEditor
│   ├── Track
│   ├── Clip
│   ├── SubtitleTrack
│   └── Playhead
├── ExportPanel
│   ├── PresetSelector
│   ├── BudgetEstimate
│   └── SubmitButton
├── RenderJobStatus
├── ArtifactResult
├── ArtifactPreviewModal
└── ErrorState
```

## Key Components

| Component | Purpose | Test Coverage |
|-----------|---------|---------------|
| `EditorPage` | Main editor layout | Via child components |
| `ClipLibrary` | Clip management | Via store integration |
| `TimelineEditor` | Timeline rendering | 16 tests |
| `PropertiesPanel` | Property editing | Via store integration |
| `ExportPanel` | Export with presets | 8 tests |
| `SubtitlesPanel` | Subtitle management | 6 tests |
| `EffectsPanel` | Effect application | 8 tests |
| `RenderJobStatus` | Job status display | 13 tests |
| `ArtifactResult` | Completed artifact | 16 tests |
| `ArtifactPreviewModal` | Media preview | 12 tests |
| `ErrorState` | Error display | 10 tests |

## Composables

| Composable | Purpose | Test Coverage |
|------------|---------|---------------|
| `usePlayback` | Playback control | 13 tests |
| `useSaveProject` | Project saving | 7 tests |
| `useExportValidation` | Export validation | 4 tests |
| `useRenderJob` | Render job management | 8 tests |
| `useArtifact` | Artifact management | 6 tests |
| `useI18nError` | Error code i18n | ✅ |

## State Management

Pinia stores manage:
- **Project store** — Current project, timeline, clips, selected items
- **User store** — Authentication, preferences, capabilities
- **UI store** — Panel visibility, modal states
- **Render store** — Job status, artifacts, progress
