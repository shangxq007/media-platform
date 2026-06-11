# RenderJob Contract

## Overview

RenderJob Schema is the shared contract between frontend, backend, and Remotion Provider. It defines the structure of a render job, plan, and step.

## Schema Definition (Zod)

```typescript
import { z } from 'zod';

// Canvas specification
export const CanvasSchema = z.object({
  width: z.number().int().positive(),
  height: z.number().int().positive(),
  fps: z.number().int().positive(),
});

// Output specification
export const OutputSchema = z.object({
  format: z.enum(['mp4', 'webm', 'mov', 'm3u8', 'mpd']),
  codec: z.string(),
  audioCodec: z.string(),
  resolution: z.string(),
  fps: z.number(),
  bitrate: z.number().optional(),
});

// Timeline clip
export const TimelineClipSchema = z.object({
  id: z.string(),
  assetId: z.string(),
  startTime: z.number(),
  duration: z.number(),
  sourceInPoint: z.number(),
  sourceOutPoint: z.number(),
  effects: z.array(z.object({
    id: z.string(),
    effectId: z.string(),
    params: z.record(z.unknown()),
    startTime: z.number(),
    duration: z.number(),
  })),
});

// Timeline track
export const TimelineTrackSchema = z.object({
  id: z.string(),
  type: z.enum(['video', 'audio', 'subtitle']),
  clips: z.array(TimelineClipSchema),
  muted: z.boolean(),
  locked: z.boolean(),
});

// Timeline
export const TimelineSchema = z.object({
  tracks: z.array(TimelineTrackSchema),
});

// Caption item
export const CaptionItemSchema = z.object({
  id: z.string(),
  text: z.string(),
  startTime: z.number(),
  endTime: z.number(),
  style: z.object({
    fontFamily: z.string(),
    fontSize: z.number(),
    fontColor: z.string(),
    backgroundColor: z.string(),
    position: z.object({
      x: z.number(),
      y: z.number(),
    }),
    alignment: z.enum(['left', 'center', 'right']),
    bold: z.boolean(),
    italic: z.boolean(),
  }),
  templateId: z.string().optional(),
});

// Captions
export const CaptionsSchema = z.object({
  items: z.array(CaptionItemSchema),
});

// Template
export const TemplateSchema = z.object({
  id: z.string(),
  params: z.record(z.unknown()),
  version: z.string(),
});

// Constraints
export const ConstraintsSchema = z.object({
  maxWidth: z.number(),
  maxHeight: z.number(),
  maxFrameRate: z.number(),
  maxDurationSec: z.number(),
  requiredFormat: z.string().optional(),
  requiredCodec: z.string().optional(),
});

// RenderJob
export const RenderJobSchema = z.object({
  id: z.string().uuid(),
  jobType: z.enum([
    'video_export',
    'captioned_video_export',
    'hls_package_export',
    'dash_package_export',
    'timeline_export',
    'blender_intro_export',
    'bmf_spike_test',
    'thumbnail_extract',
    'audio_extract',
  ]),
  mode: z.enum(['production', 'experiment', 'manual']),
  canvas: CanvasSchema,
  assets: z.array(z.string()),
  timeline: TimelineSchema,
  captions: CaptionsSchema,
  template: TemplateSchema.nullable(),
  output: OutputSchema,
  requiredCapabilities: z.array(z.string()),
  constraints: ConstraintsSchema,
  allowDegrade: z.boolean(),
  preferredProviders: z.array(z.string()),
  blockedProviders: z.array(z.string()),
  featureFlags: z.record(z.boolean()).optional(),
});

// RenderStep
export const RenderStepSchema = z.object({
  id: z.string(),
  providerType: z.enum([
    'MediaProcessing',
    'CompositionRender',
    'TimelineRender',
    'Overlay',
    'Packaging',
    'Preprocess',
    'MediaPipeline',
    'ThreeDRender',
    'CloudRender',
    'Utility',
  ]),
  providerName: z.string(),
  requiredCapabilities: z.array(z.string()),
  inputUri: z.string(),
  outputUri: z.string(),
  dependsOn: z.array(z.string()),
  allowFallback: z.boolean(),
  fallbackProviders: z.array(z.string()),
});

// RenderPlan
export const RenderPlanSchema = z.object({
  jobId: z.string().uuid(),
  steps: z.array(RenderStepSchema),
  selectedProviders: z.array(z.string()),
  requiredCapabilities: z.array(z.string()),
  fallbackPlan: z.lazy(() => RenderPlanSchema).nullable(),
  ruleVersion: z.string(),
  estimatedCost: z.number(),
  estimatedDurationMs: z.number(),
});

// PreviewProps (for Remotion Player)
export const PreviewPropsSchema = z.object({
  compositionId: z.string(),
  canvas: CanvasSchema,
  clips: z.array(z.object({
    id: z.string(),
    src: z.string(),
    startTime: z.number(),
    duration: z.number(),
    startFrame: z.number(),
    durationFrames: z.number(),
    sourceInPoint: z.number(),
    sourceOutPoint: z.number(),
  })),
  captions: z.object({
    items: z.array(CaptionItemSchema),
    fontAssets: z.array(z.object({
      family: z.string(),
      weight: z.number(),
      style: z.string(),
      url: z.string(),
      format: z.enum(['woff2', 'ttf']),
      version: z.string(),
    })),
  }),
  template: TemplateSchema.nullable(),
  currentTime: z.number(),
  isPlaying: z.boolean(),
});

// Type exports
export type Canvas = z.infer<typeof CanvasSchema>;
export type Output = z.infer<typeof OutputSchema>;
export type TimelineClip = z.infer<typeof TimelineClipSchema>;
export type TimelineTrack = z.infer<typeof TimelineTrackSchema>;
export type Timeline = z.infer<typeof TimelineSchema>;
export type CaptionItem = z.infer<typeof CaptionItemSchema>;
export type Captions = z.infer<typeof CaptionsSchema>;
export type Template = z.infer<typeof TemplateSchema>;
export type Constraints = z.infer<typeof ConstraintsSchema>;
export type RenderJob = z.infer<typeof RenderJobSchema>;
export type RenderStep = z.infer<typeof RenderStepSchema>;
export type RenderPlan = z.infer<typeof RenderPlanSchema>;
export type PreviewProps = z.infer<typeof PreviewPropsSchema>;
```

## Shared Types

These types are shared between frontend and backend. They should be published as a shared package:

```
packages/
  render-job-types/
    src/
      schema.ts          # Zod schemas
      types.ts           # TypeScript types
      builders.ts        # Builder functions
      validators.ts      # Validation helpers
      index.ts           # Public API
```

## Example RenderJobs

### Captioned Video Export

```json
{
  "id": "job-001",
  "jobType": "captioned_video_export",
  "mode": "production",
  "canvas": { "width": 1920, "height": 1080, "fps": 30 },
  "assets": ["asset-001", "asset-002"],
  "timeline": {
    "tracks": [
      {
        "id": "track-1",
        "type": "video",
        "clips": [
          {
            "id": "clip-1",
            "assetId": "asset-001",
            "startTime": 0,
            "duration": 10,
            "sourceInPoint": 0,
            "sourceOutPoint": 10,
            "effects": []
          }
        ],
        "muted": false,
        "locked": false
      }
    ]
  },
  "captions": {
    "items": [
      {
        "id": "caption-1",
        "text": "Hello World",
        "startTime": 1,
        "endTime": 5,
        "style": {
          "fontFamily": "NotoSansCJK",
          "fontSize": 24,
          "fontColor": "#FFFFFF",
          "backgroundColor": "#000000",
          "position": { "x": 50, "y": 80 },
          "alignment": "center",
          "bold": false,
          "italic": false
        },
        "templateId": "subtitle-template-001"
      }
    ]
  },
  "template": null,
  "output": {
    "format": "mp4",
    "codec": "h264",
    "audioCodec": "aac",
    "resolution": "1920x1080",
    "fps": 30
  },
  "requiredCapabilities": ["timeline_render", "caption_effects", "output_normalize"],
  "constraints": {
    "maxWidth": 3840,
    "maxHeight": 2160,
    "maxFrameRate": 60,
    "maxDurationSec": 3600
  },
  "allowDegrade": true,
  "preferredProviders": [],
  "blockedProviders": []
}
```

### HLS Package Export

```json
{
  "id": "job-002",
  "jobType": "hls_package_export",
  "mode": "production",
  "canvas": { "width": 1920, "height": 1080, "fps": 30 },
  "assets": ["asset-001"],
  "timeline": { "tracks": [...] },
  "captions": { "items": [] },
  "template": null,
  "output": {
    "format": "m3u8",
    "codec": "h264",
    "audioCodec": "aac",
    "resolution": "1920x1080",
    "fps": 30
  },
  "requiredCapabilities": ["output_normalize", "package_hls"],
  "constraints": { "maxWidth": 3840, "maxHeight": 2160, "maxFrameRate": 60, "maxDurationSec": 3600 },
  "allowDegrade": true,
  "preferredProviders": ["gpac"],
  "blockedProviders": []
}
```

### Timeline Export (MLT)

```json
{
  "id": "job-003",
  "jobType": "timeline_export",
  "mode": "production",
  "canvas": { "width": 1920, "height": 1080, "fps": 30 },
  "assets": ["asset-001", "asset-002"],
  "timeline": { "tracks": [...] },
  "captions": { "items": [] },
  "template": null,
  "output": {
    "format": "mp4",
    "codec": "h264",
    "audioCodec": "aac",
    "resolution": "1920x1080",
    "fps": 30
  },
  "requiredCapabilities": ["timeline_render", "output_normalize"],
  "constraints": { "maxWidth": 3840, "maxHeight": 2160, "maxFrameRate": 60, "maxDurationSec": 3600 },
  "allowDegrade": true,
  "preferredProviders": ["mlt"],
  "blockedProviders": []
}
```

### 3D Intro + Caption Export

```json
{
  "id": "job-004",
  "jobType": "blender_intro_export",
  "mode": "production",
  "canvas": { "width": 1920, "height": 1080, "fps": 30 },
  "assets": ["asset-001"],
  "timeline": { "tracks": [...] },
  "captions": { "items": [...] },
  "template": {
    "id": "intro-template-001",
    "params": { "logoUrl": "s3://assets/logo.png", "duration": 5 },
    "version": "1.0.0"
  },
  "output": {
    "format": "mp4",
    "codec": "h264",
    "audioCodec": "aac",
    "resolution": "1920x1080",
    "fps": 30
  },
  "requiredCapabilities": ["3d_render", "caption_effects", "template_render", "output_normalize"],
  "constraints": { "maxWidth": 3840, "maxHeight": 2160, "maxFrameRate": 60, "maxDurationSec": 3600 },
  "allowDegrade": true,
  "preferredProviders": ["blender", "remotion"],
  "blockedProviders": []
}
```

### BMF Spike Test

```json
{
  "id": "job-bmf-001",
  "jobType": "bmf_spike_test",
  "mode": "manual",
  "canvas": { "width": 1920, "height": 1080, "fps": 30 },
  "assets": ["asset-001"],
  "timeline": { "tracks": [...] },
  "captions": { "items": [] },
  "template": null,
  "output": {
    "format": "mp4",
    "codec": "h264",
    "audioCodec": "aac",
    "resolution": "1920x1080",
    "fps": 30
  },
  "requiredCapabilities": ["media_pipeline", "output_normalize"],
  "constraints": { "maxWidth": 3840, "maxHeight": 2160, "maxFrameRate": 60, "maxDurationSec": 3600 },
  "allowDegrade": false,
  "preferredProviders": ["bmf"],
  "blockedProviders": []
}
```

## Builder Functions

### RenderJob Builder

```typescript
class RenderJobBuilder {
  private job: Partial<RenderJob> = {};

  setCanvas(width: number, height: number, fps: number): this {
    this.job.canvas = { width, height, fps };
    return this;
  }

  setOutput(format: Output['format'], codec: string, audioCodec: string): this {
    this.job.output = { format, codec, audioCodec, resolution: '', fps: 30 };
    return this;
  }

  setTimeline(tracks: TimelineTrack[]): this {
    this.job.timeline = { tracks };
    return this;
  }

  setCaptions(items: CaptionItem[]): this {
    this.job.captions = { items };
    return this;
  }

  setTemplate(id: string, params: Record<string, unknown>, version: string): this {
    this.job.template = { id, params, version };
    return this;
  }

  setRequiredCapabilities(capabilities: string[]): this {
    this.job.requiredCapabilities = capabilities;
    return this;
  }

  setMode(mode: RenderJob['mode']): this {
    this.job.mode = mode;
    return this;
  }

  setAllowDegrade(allow: boolean): this {
    this.job.allowDegrade = allow;
    return this;
  }

  build(): RenderJob {
    return RenderJobSchema.parse({
      id: crypto.randomUUID(),
      jobType: 'video_export',
      ...this.job,
    });
  }
}

// Usage
const job = new RenderJobBuilder()
  .setCanvas(1920, 1080, 30)
  .setOutput('mp4', 'h264', 'aac')
  .setTimeline(tracks)
  .setCaptions(captions)
  .setRequiredCapabilities(['timeline_render', 'caption_effects', 'output_normalize'])
  .setMode('production')
  .setAllowDegrade(true)
  .build();
```

### Validation

```typescript
import { z } from 'zod';

function validateRenderJob(data: unknown): RenderJob {
  return RenderJobSchema.parse(data);
}

function validateRenderPlan(data: unknown): RenderPlan {
  return RenderPlanSchema.parse(data);
}

function validatePreviewProps(data: unknown): PreviewProps {
  return PreviewPropsSchema.parse(data);
}

function isValidRenderJob(data: unknown): boolean {
  return RenderJobSchema.safeParse(data).success;
}
```

## API Contract

### Submit Render Job

```
POST /api/v1/render/jobs
Content-Type: application/json

{
  "job": { ... RenderJob ... }
}

Response 202:
{
  "jobId": "uuid",
  "status": "queued",
  "renderPlan": { ... RenderPlan ... },
  "estimatedCost": 0.05,
  "estimatedDurationMs": 30000
}
```

### Get Render Job Status

```
GET /api/v1/render/jobs/{jobId}

Response 200:
{
  "jobId": "uuid",
  "status": "queued" | "processing" | "completed" | "failed",
  "progress": 0.75,
  "currentStep": "caption_effects",
  "renderPlan": { ... RenderPlan ... },
  "result": {
    "artifactId": "art-001",
    "storageUri": "s3://output/video.mp4",
    "duration": 30,
    "format": "mp4",
    "resolution": "1920x1080"
  },
  "error": null | { "code": "RENDER-500-001", "message": "..." }
}
```

### Preview Render Job

```
POST /api/v1/render/jobs/preview
Content-Type: application/json

{
  "job": { ... RenderJob ... }
}

Response 200:
{
  "previewProps": { ... PreviewProps ... },
  "compositionId": "main-editor-composition",
  "fontAssets": [ ... ]
}
```
