# Timeline Model

## Overview

The timeline model defines the data structure for video editing timelines in the React frontend.

## Core Types

### Timeline

```typescript
interface Timeline {
  id: string;
  tracks: TimelineTrack[];
  duration: number;
  fps: number;
  resolution: Resolution;
}

interface Resolution {
  width: number;
  height: number;
}
```

### Track

```typescript
interface TimelineTrack {
  id: string;
  name: string;
  type: TrackType;
  clips: TimelineClip[];
  muted: boolean;
  locked: boolean;
  visible: boolean;
  layer: number;  // Vertical ordering
}

type TrackType = 'video' | 'audio' | 'subtitle';
```

### Clip

```typescript
interface TimelineClip {
  id: string;
  assetId: string;
  trackId: string;
  startTime: number;       // Start time on timeline (seconds)
  duration: number;        // Duration on timeline (seconds)
  sourceInPoint: number;   // Source media in-point (seconds)
  sourceOutPoint: number;  // Source media out-point (seconds)
  effects: AppliedEffect[];
  transitions: Transition[];
}

interface AppliedEffect {
  id: string;
  effectId: string;
  name: string;
  params: Record<string, EffectParamValue>;
  startTime: number;
  duration: number;
  enabled: boolean;
}

type EffectParamValue = string | number | boolean | ColorValue;

interface ColorValue {
  r: number;
  g: number;
  b: number;
  a: number;
}

interface Transition {
  id: string;
  type: TransitionType;
  duration: number;
  params: Record<string, TransitionParamValue>;
}

type TransitionType = 'dissolve' | 'wipe' | 'slide' | 'zoom' | 'fade';
type TransitionParamValue = string | number;
```

## Timeline Operations

### Add Track

```typescript
function addTrack(timeline: Timeline, type: TrackType): Timeline {
  const newTrack: TimelineTrack = {
    id: generateId(),
    name: `${type} ${timeline.tracks.filter(t => t.type === type).length + 1}`,
    type,
    clips: [],
    muted: false,
    locked: false,
    visible: true,
    layer: timeline.tracks.filter(t => t.type === type).length,
  };
  return {
    ...timeline,
    tracks: [...timeline.tracks, newTrack],
  };
}
```

### Add Clip

```typescript
function addClip(timeline: Timeline, trackId: string, clip: TimelineClip): Timeline {
  return {
    ...timeline,
    tracks: timeline.tracks.map(track =>
      track.id === trackId
        ? { ...track, clips: [...track.clips, clip] }
        : track
    ),
  };
}
```

### Move Clip

```typescript
function moveClip(
  timeline: Timeline,
  trackId: string,
  clipId: string,
  newStartTime: number
): Timeline {
  return {
    ...timeline,
    tracks: timeline.tracks.map(track =>
      track.id === trackId
        ? {
            ...track,
            clips: track.clips.map(clip =>
              clip.id === clipId
                ? { ...clip, startTime: newStartTime }
                : clip
            ),
          }
        : track
    ),
  };
}
```

### Split Clip

```typescript
function splitClip(timeline: Timeline, clipId: string, splitTime: number): Timeline {
  return {
    ...timeline,
    tracks: timeline.tracks.map(track => ({
      ...track,
      clips: track.clips.flatMap(clip => {
        if (clip.id !== clipId) return [clip];
        const relativeSplit = splitTime - clip.startTime;
        if (relativeSplit <= 0 || relativeSplit >= clip.duration) return [clip];
        return [
          {
            ...clip,
            duration: relativeSplit,
            sourceOutPoint: clip.sourceInPoint + relativeSplit,
          },
          {
            ...clip,
            id: generateId(),
            startTime: splitTime,
            duration: clip.duration - relativeSplit,
            sourceInPoint: clip.sourceInPoint + relativeSplit,
          },
        ];
      }),
    })),
  };
}
```

### Delete Clip

```typescript
function deleteClip(timeline: Timeline, trackId: string, clipId: string): Timeline {
  return {
    ...timeline,
    tracks: timeline.tracks.map(track =>
      track.id === trackId
        ? { ...track, clips: track.clips.filter(clip => clip.id !== clipId) }
        : track
    ),
  };
}
```

## Drag and Drop

Using dnd-kit for drag and drop:

```typescript
import {
  DndContext,
  DragEndEvent,
  useDraggable,
  useDroppable,
} from '@dnd-kit/core';
import { SortableContext, useSortable } from '@dnd-kit/sortable';

interface DragState {
  activeClipId: string | null;
  sourceTrackId: string | null;
  targetTrackId: string | null;
  targetPosition: number | null;
}

function handleDragEnd(event: DragEndEvent, timeline: Timeline): Timeline {
  const { active, over } = event;
  if (!over) return timeline;

  const clipId = active.id as string;
  const targetTrackId = over.id as string;

  // Calculate new position based on pointer location
  const newPosition = calculateDropPosition(event, targetTrackId);

  return moveClip(timeline, targetTrackId, clipId, newPosition);
}
```

## Timeline Rendering

### Virtual Scrolling

Using TanStack Virtual for efficient rendering of long timelines:

```typescript
import { useVirtualizer } from '@tanstack/react-virtual';

function TimelineRuler({ duration, zoom }: { duration: number; zoom: number }) {
  const parentRef = useRef<HTMLDivElement>(null);
  const totalWidth = duration * zoom;

  const virtualizer = useVirtualizer({
    count: Math.ceil(totalWidth / 10),
    getScrollElement: () => parentRef.current,
    estimateSize: () => 10,
  });

  return (
    <div ref={parentRef} className="overflow-x-auto">
      <div style={{ width: totalWidth, position: 'relative' }}>
        {virtualizer.getVirtualItems().map(virtualItem => (
          <div
            key={virtualItem.key}
            style={{
              position: 'absolute',
              left: virtualItem.start,
              width: virtualItem.size,
            }}
          >
            {formatTime(virtualItem.index / zoom)}
          </div>
        ))}
      </div>
    </div>
  );
}
```

## Timeline to RenderJob Conversion

```typescript
function timelineToRenderJobTimeline(timeline: Timeline): RenderJob['timeline'] {
  return {
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
        effects: clip.effects.map(effect => ({
          id: effect.id,
          effectId: effect.effectId,
          params: effect.params,
          startTime: effect.startTime,
          duration: effect.duration,
        })),
      })),
    })),
  };
}
```

## Snap Guidelines

```typescript
function calculateSnapPosition(
  clip: TimelineClip,
  track: TimelineTrack,
  snapThreshold: number = 0.1
): { position: number; snapType: 'edge' | 'gap' | null } {
  const otherClips = track.clips.filter(c => c.id !== clip.id);

  for (const other of otherClips) {
    // Snap to end of another clip
    if (Math.abs(clip.startTime - (other.startTime + other.duration)) < snapThreshold) {
      return { position: other.startTime + other.duration, snapType: 'edge' };
    }
    // Snap to start of another clip
    if (Math.abs(clip.startTime + clip.duration - other.startTime) < snapThreshold) {
      return { position: other.startTime - clip.duration, snapType: 'edge' };
    }
  }

  return { position: clip.startTime, snapType: null };
}
```
