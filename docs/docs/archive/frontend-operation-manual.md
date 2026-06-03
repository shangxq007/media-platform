# Frontend Video Editor — Operation Manual

> **Last updated**: 2026-05-11
> **Project**: `frontend/` — Vue 3 + TypeScript + Vite

## Quick Start

```bash
cd frontend
npm install
npm run dev      # Start dev server on port 3000
npm run build    # Production build
npm run test     # Run unit tests
```

## Architecture

```
src/
├── api/            # Axios API clients (ProjectAPI, RenderAPI, AnalyticsAPI)
├── components/
│   ├── timeline/   # TimelineEditor.vue — multi-track timeline
│   ├── clip-library/ # ClipLibrary.vue — asset management
│   ├── export/     # ExportPanel.vue — render job submission
│   ├── effects/    # EffectsPanel.vue — transitions and filters
│   └── common/     # OTIOPanel.vue, ProjectPanel.vue, FormInput.vue
├── stores/         # Pinia stores (timeline, project, history)
├── pages/          # EditorPage.vue — main editor layout
├── types/          # TypeScript type definitions
└── utils/          # OTIO utilities
```

## Timeline Editor

### Tracks
- **Video tracks**: Blue clips, top section
- **Audio tracks**: Teal clips, middle section
- **Text tracks**: Red clips, bottom section

### Clip Operations
| Action | How |
|--------|-----|
| Add clip | Double-click in Clip Library |
| Move clip | Drag on timeline |
| Resize clip | Drag clip edges |
| Delete clip | Click ✕ in Clip Library |
| Lock track | Click L button on track header |
| Mute track | Click M button on track header |

### Transport Controls
| Control | Description |
|---------|-------------|
| ▶ Play / ⏸ Pause | Toggle playback |
| Timeline slider | Scrub to position |
| − / + | Zoom in/out |
| ↶ / ↷ | Undo/Redo |

### Undo/Redo
- **Stack size**: 50 states maximum
- **Saved on**: Clip mouse-down and mouse-up
- **Buttons**: ↶ (undo) and ↷ (redo) in transport bar

## OTIO Import/Export

### Export Timeline
1. Click "📤 Export JSON" in the OTIO panel
2. Downloads `timeline-{timestamp}.json`

### Import Timeline
1. Click "📥 Import JSON"
2. Select a previously exported timeline JSON file
3. Timeline is rebuilt from the OTIO data

### OTIO Format
```json
{
  "name": "media-platform-timeline",
  "tracks": [
    {
      "name": "Video 1",
      "children": [
        {
          "name": "clip_id",
          "source_range": {
            "start_time": 0,
            "duration": 5
          },
          "transforms": []
        }
      ]
    }
  ]
}
```

## Export / Render Panel

### Presets
| Setting | Options |
|---------|---------|
| Format | MP4 (H.264), OGG (Theora), WebM (VP9) |
| Resolution | 720p, 1080p, 4K |
| Profile | Default 1080p, Social 720p, Mobile 480p |
| Frame Rate | 24 fps (Cinema), 30 fps (Standard), 60 fps (HFR) |
| Encoder | H.264, VP9, AAC |
| Audio | All Tracks, No Audio |

### Render Job Flow
1. Select export settings
2. Click "Export Video"
3. Job is submitted to backend RenderPipeline
4. Status polls every 3 seconds
5. Completed jobs show artifact download link

## Effects / Filters

### Categories
| Category | Filters |
|----------|---------|
| Transitions | Fade In, Fade Out, Crossfade |
| Video | Grayscale, Sepia, Blur, Brightness, Contrast |
| Audio | Volume |
| Text | Subtitle |

### Applying Filters
1. Select filter category tab
2. Click filter to select
3. Configure parameters (duration, text, etc.)
4. Click "Apply to Selected Clip"

## Project Management

### Creating a Project
1. Click "+ New" in Project Panel
2. Enter project name and description
3. Click "Create Project"

### Saving Timeline
- Click "💾 Save Timeline" to persist to localStorage
- Timeline is keyed by project ID

### Exporting Timeline
- Click "📥 Export JSON" to download timeline as file

## Keyboard Shortcuts

| Key | Action |
|-----|--------|
| Ctrl+Z | Undo (via history store) |
| Ctrl+Y | Redo (via history store) |

## Testing

```bash
# Run all tests
npm run test

# Run with watch mode
npm run test:watch
```

### Test Coverage
- **TimelineStore**: 12 tests — add/remove/move/resize clips, track locking, time/zoom bounds, serialization
- **HistoryStore**: 7 tests — undo/redo, empty stack edge cases, stack clearing
- **OTIO Utils**: 3 tests — export, empty tracks, import

## Docker Integration

```bash
# Start all services (backend + frontend + db)
docker compose up --build

# Frontend available at http://localhost:3000
# Backend API at http://localhost:8080
# API proxy configured in vite.config.ts
```

## API Integration

### Backend Endpoints Used
| Endpoint | Method | Purpose |
|----------|--------|---------|
| `/api/v1/projects` | GET/POST | List/create projects |
| `/api/v1/render/jobs` | POST | Submit render job |
| `/api/v1/render/jobs/{id}` | GET | Poll job status |
| `/api/v1/analytics/events` | POST | Track user behavior |
| `/api/v1/analytics/profiles/{userId}` | GET | Get user profile |
