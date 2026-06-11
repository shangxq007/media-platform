# Frontend Minimum Integration Demo

> **Status:** Documentation-only (no runnable code yet)

## Goal

Demonstrate the minimum React → Backend → RenderJob → RenderExecutionTrace → Final Artifact flow.

## Demo Scope

- **No** full video editor
- **No** asset management
- **No** provider selection UI
- **Only** captioned_video_export demo

## Data Flow

```
React Editor Demo
    │
    ├─ 1. Create minimal timeline (1 video track, 1 subtitle track)
    ├─ 2. Add 1 caption: "Hello World" @ 0s-3s
    ├─ 3. Select 1 font: NotoSansCJK (from FontManifest)
    ├─ 4. Select 1 template: BasicSubtitle
    │
    ▼
Generate OTIO + metadata.bluepulse
    │
    ▼
POST /api/v1/render/jobs
    │
    ▼
Backend: OTIOTimelineCompiler → RenderJob
    │
    ▼
Backend: RenderPlanner → RenderPlan
    │
    ▼
Backend: RenderOrchestrator.execute()
    ├─ RenderJobFontPreflight
    ├─ Remotion step → INTERMEDIATE artifact
    ├─ FFmpeg normalize → FINAL_OUTPUT artifact
    └─ RenderExecutionTrace
    │
    ▼
GET /api/v1/render/jobs/{jobId}
    │
    ▼
React: Display RenderExecutionTrace + Final Artifact URL
```

## API Contract

### Submit Render Job

```typescript
// POST /api/v1/render/jobs
interface SubmitRenderJobRequest {
  otio: string;              // OTIO JSON
  metadata: {
    bluepulse: {
      schemaVersion: string;
      projectId: string;
      timelineId: string;
      captions: CaptionRef[];
      fonts: FontRef[];
      templates: TemplateRef[];
      effects: EffectRef[];
      renderHints: RenderHints;
    };
  };
  mode: 'production' | 'experiment' | 'manual';
}

interface CaptionRef {
  id: string;
  assetRef: string;
  startTime: number;
  endTime: number;
  styleRef?: string;
  templateRef?: string;
}

interface FontRef {
  refId: string;
  assetId: string;
  fontFamily: string;
  fontWeight: string;
  fontStyle: string;
  subsetRef?: string;
}

interface TemplateRef {
  refId: string;
  templateId: string;
  templateVersion: string;
  params: Record<string, unknown>;
}

interface EffectRef {
  refId: string;
  effectId: string;
  effectVersion: string;
  startTime: number;
  duration: number;
  params: Record<string, unknown>;
}

interface RenderHints {
  outputFormat: string;
  outputWidth: number;
  outputHeight: number;
  outputFps: number;
  preferredNormalizeProvider?: string;
  requiredCapabilities?: string[];
}
```

### Render Job Response

```typescript
// Response 202
interface RenderJobResponse {
  jobId: string;
  status: 'queued' | 'processing' | 'completed' | 'failed';
  renderPlan?: RenderPlan;
  estimatedCost: number;
  estimatedDurationMs: number;
}
```

### Query Render Job Status

```typescript
// GET /api/v1/render/jobs/{jobId}
interface RenderJobStatusResponse {
  jobId: string;
  status: string;
  progress: number;
  currentStep?: string;
  renderPlan?: RenderPlan;
  trace?: RenderExecutionTrace;
  result?: RenderResult;
  error?: RenderError;
}
```

## React Component Hierarchy (Demo)

```
App
├── QueryClientProvider
├── ThemeProvider
└── DemoPage
    ├── TimelineEditor
    │   ├── TrackList
    │   │   ├── VideoTrack
    │   │   └── SubtitleTrack
    │   └── Playhead
    ├── CaptionEditor
    │   ├── CaptionList
    │   └── CaptionStylePanel
    ├── FontSelector
    │   └── FontManifestList
    ├── TemplateSelector
    │   └── TemplateList
    ├── SubmitButton
    └── RenderStatusPanel
        ├── StepProgress
        ├── ArtifactList
        └── FinalOutputPlayer
```

## Key Design Decisions

1. **Frontend does NOT select providers** - Backend RenderPlanner decides
2. **Frontend does NOT access raw fonts** - Uses FontManifestResolver
3. **Frontend submits standard OTIO** - No provider-specific data
4. **Frontend reads RenderExecutionTrace** - For progress and debug
5. **Same Composition/FontManifest** - Frontend preview and backend render share identical inputs

## Related Documents

- [Frontend Overview](../frontend/overview.md)
- [RenderJob Contract](../frontend/renderjob-contract.md)
- [Remotion Integration](../frontend/remotion-integration.md)
- [Font Asset Management](../frontend/font-asset-management.md)
