# Editor State Management

## Overview

Editor State is managed using Zustand. It represents the current state of the video editor, including timeline, captions, templates, selection, playback, and UI.

## Design Principles

1. **Editor State is not RenderJob**: Editor State contains UI-specific state (zoom, scroll position, selection) that should not be sent to the backend.
2. **Conversion boundary**: Editor State is converted to RenderJob before passing to Remotion or submitting to the backend.
3. **Remotion receives PreviewProps**: Remotion Composition receives a subset of RenderJob called PreviewProps, optimized for rendering.
4. **Single source of truth**: Each piece of state lives in one place.

## Store Structure

```typescript
import { create } from 'zustand';

interface EditorStore {
  // Timeline
  timeline: TimelineState;
  setTimeline: (timeline: TimelineState) => void;
  addTrack: (track: TimelineTrack) => void;
  removeTrack: (trackId: string) => void;
  addClip: (trackId: string, clip: TimelineClip) => void;
  removeClip: (trackId: string, clipId: string) => void;
  moveClip: (trackId: string, clipId: string, newPosition: number) => void;

  // Captions
  captions: CaptionState;
  setCaptions: (captions: Caption[]) => void;
  addCaption: (caption: Caption) => void;
  updateCaption: (captionId: string, updates: Partial<Caption>) => void;
  removeCaption: (captionId: string) => void;

  // Templates
  templates: TemplateState;
  selectTemplate: (templateId: string) => void;
  setTemplateParams: (params: Record<string, unknown>) => void;

  // Selection
  selection: SelectionState;
  selectElement: (elementId: string | null) => void;
  selectTrack: (trackId: string | null) => void;

  // Playback
  playback: PlaybackState;
  play: () => void;
  pause: () => void;
  seek: (time: number) => void;

  // UI
  ui: UIState;
  setZoom: (zoom: number) => void;
  setScrollPosition: (position: number) => void;
  togglePanel: (panel: string) => void;

  // Actions
  buildRenderJob: () => RenderJob;
  buildPreviewProps: () => PreviewProps;
  reset: () => void;
}

export const useEditorStore = create<EditorStore>((set, get) => ({
  // ... implementation
}));
```

## Timeline State

```typescript
interface TimelineState {
  tracks: TimelineTrack[];
  duration: number;       // Total duration in seconds
  fps: number;
  resolution: { width: number; height: number };
  zoom: number;           // Zoom level (pixels per second)
  scrollPosition: number; // Horizontal scroll position
}

interface TimelineTrack {
  id: string;
  name: string;
  type: 'video' | 'audio' | 'subtitle';
  clips: TimelineClip[];
  muted: boolean;
  locked: boolean;
  visible: boolean;
}

interface TimelineClip {
  id: string;
  assetId: string;
  trackId: string;
  startTime: number;      // Start time on timeline (seconds)
  duration: number;       // Duration on timeline (seconds)
  sourceInPoint: number;  // Source in-point (seconds)
  sourceOutPoint: number; // Source out-point (seconds)
  effects: AppliedEffect[];
}

interface AppliedEffect {
  id: string;
  effectId: string;
  params: Record<string, unknown>;
  startTime: number;
  duration: number;
}
```

## Caption State

```typescript
interface CaptionState {
  captions: Caption[];
  selectedCaptionId: string | null;
}

interface Caption {
  id: string;
  text: string;
  startTime: number;      // seconds
  endTime: number;        // seconds
  style: CaptionStyle;
  templateId?: string;    // Optional template reference
}

interface CaptionStyle {
  fontFamily: string;
  fontSize: number;
  fontColor: string;
  backgroundColor: string;
  position: CaptionPosition;
  alignment: 'left' | 'center' | 'right';
  bold: boolean;
  italic: boolean;
}

interface CaptionPosition {
  x: number;  // percentage 0-100
  y: number;  // percentage 0-100
}
```

## Template State

```typescript
interface TemplateState {
  selectedTemplateId: string | null;
  templateParams: Record<string, unknown>;
  availableTemplates: TemplateDefinition[];
}

interface TemplateDefinition {
  id: string;
  name: string;
  description: string;
  thumbnailUrl: string;
  version: string;
  params: TemplateParam[];
  compatibleProviders: string[];  // e.g., ['remotion']
}

interface TemplateParam {
  name: string;
  type: 'string' | 'number' | 'boolean' | 'color';
  defaultValue: unknown;
  label: string;
}
```

## Selection State

```typescript
interface SelectionState {
  elementId: string | null;
  elementType: 'clip' | 'caption' | 'effect' | 'track' | null;
  trackId: string | null;
  multiSelect: string[];
}
```

## Playback State

```typescript
interface PlaybackState {
  isPlaying: boolean;
  currentTime: number;  // seconds
  playbackRate: number;
  loop: boolean;
  inPoint: number | null;
  outPoint: number | null;
}
```

## UI State

```typescript
interface UIState {
  zoom: number;
  scrollPosition: number;
  panels: {
    inspector: boolean;
    captions: boolean;
    templates: boolean;
    effects: boolean;
  };
  theme: 'light' | 'dark' | 'system';
  sidebarCollapsed: boolean;
}
```

## Editor State → RenderJob Conversion

```typescript
function buildRenderJob(store: EditorStore): RenderJob {
  const { timeline, captions, templates, playback } = store;

  return {
    id: generateId(),
    jobType: 'video_export',
    mode: 'production',
    canvas: {
      width: timeline.resolution.width,
      height: timeline.resolution.height,
      fps: timeline.fps,
    },
    assets: timeline.tracks
      .flatMap(track => track.clips)
      .map(clip => clip.assetId),
    timeline: {
      tracks: timeline.tracks.map(track => ({
        id: track.id,
        type: track.type,
        clips: track.clips.map(clip => ({
          id: clip.id,
          assetId: clip.assetId,
          startTime: clip.startTime,
          duration: clip.duration,
          sourceInPoint: clip.sourceInPoint,
          sourceOutPoint: clip.sourceOutPoint,
          effects: clip.effects,
        })),
      })),
    },
    captions: {
      items: captions.captions.map(caption => ({
        id: caption.id,
        text: caption.text,
        startTime: caption.startTime,
        endTime: caption.endTime,
        style: caption.style,
        templateId: caption.templateId,
      })),
    },
    template: templates.selectedTemplateId ? {
      id: templates.selectedTemplateId,
      params: templates.templateParams,
    } : null,
    output: {
      format: 'mp4',
      codec: 'h264',
      audioCodec: 'aac',
      resolution: `${timeline.resolution.width}x${timeline.resolution.height}`,
      fps: timeline.fps,
    },
    requiredCapabilities: ['timeline_render', 'caption_effects', 'output_normalize'],
    constraints: {
      maxWidth: 3840,
      maxHeight: 2160,
      maxFrameRate: 60,
      maxDurationSec: 3600,
    },
    allowDegrade: true,
    preferredProviders: [],
    blockedProviders: [],
  };
}
```

## Editor State → PreviewProps Conversion

```typescript
function buildPreviewProps(store: EditorStore): PreviewProps {
  const { timeline, captions, templates, playback } = store;

  return {
    compositionId: 'main-editor-composition',
    canvas: {
      width: timeline.resolution.width,
      height: timeline.resolution.height,
      fps: timeline.fps,
    },
    clips: timeline.tracks
      .filter(track => track.type === 'video' || track.type === 'audio')
      .flatMap(track => track.clips)
      .map(clip => ({
        id: clip.id,
        src: resolveAssetUri(clip.assetId),
        startTime: clip.startTime,
        duration: clip.duration,
        sourceInPoint: clip.sourceInPoint,
        sourceOutPoint: clip.sourceOutPoint,
      })),
    captions: {
      items: captions.captions.map(caption => ({
        id: caption.id,
        text: caption.text,
        startTime: caption.startTime,
        endTime: caption.endTime,
        x: caption.style.position.x,
        y: caption.style.position.y,
        fontFamily: caption.style.fontFamily,
        fontSize: caption.style.fontSize,
        fontColor: caption.style.fontColor,
        backgroundColor: caption.style.backgroundColor,
      })),
      fontAssets: loadFontAssets(captions.captions.map(c => c.style.fontFamily)),
    },
    template: templates.selectedTemplateId ? {
      id: templates.selectedTemplateId,
      params: templates.templateParams,
    } : null,
    currentTime: playback.currentTime,
    isPlaying: playback.isPlaying,
  };
}
```

## Reminder: What Editor State is NOT

Editor State is NOT:
- A RenderJob (contains UI state like zoom, scroll)
- A backend API payload (needs conversion)
- A Remotion Composition input (needs PreviewProps)
- A provider-specific format (provider-agnostic)

Editor State IS:
- A local representation of the editor
- Used for UI rendering
- Converted to RenderJob for backend submission
- Converted to PreviewProps for Remotion preview
