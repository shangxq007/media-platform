# Timeline Interactions

> **Module:** `frontend/src/components/timeline/`
> **Last Updated:** 2026-05-18

## Timeline Structure

The timeline is a multi-track view of the video project. It displays clips, subtitles, and effects along a time axis.

```
Time →  00:00    00:30    01:00    01:30    02:00
        ├────────┼────────┼────────┼────────┤
Track 1 │[====Clip A====][==Clip B==]       │
        ├────────┼────────┼────────┼────────┤
Track 2 │[======Clip C======]               │
        ├────────┼────────┼────────┼────────┤
Sub     │[__Sub 1__][__Sub 2__][__Sub 3]   │
        ├────────┼────────┼────────┼────────┤
        ▲                                  ▲
     Playhead                          Duration
```

## Clip Operations

| Operation | Description | Status |
|-----------|-------------|--------|
| Insert clip | Add clip from library to timeline | ✅ |
| Move clip | Drag clip to new position | ✅ |
| Resize clip | Trim clip duration | ✅ |
| Delete clip | Remove clip from timeline | ✅ |
| Split clip | Split clip at playhead position | ✅ |
| Copy/paste | Duplicate clip | ✅ |
| Undo/redo | Revert/restore operations | ✅ |

## Timeline State

```typescript
interface TimelineState {
  tracks: Track[];
  clips: Clip[];
  subtitleTracks: SubtitleTrack[];
  playheadPosition: number;
  selectedClipId: string | null;
  zoom: number;
  duration: number;
}
```

## Clip Data Model

```typescript
interface Clip {
  id: string;
  trackId: string;
  startTime: number;    // seconds
  duration: number;     // seconds
  sourceOffset: number; // trim start in source
  sourceDuration: number;
  name: string;
  thumbnailUrl?: string;
  effects: Effect[];
}
```

## Subtitle Track

```typescript
interface SubtitleCue {
  id: string;
  startTime: number;
  endTime: number;
  text: string;
  style?: SubtitleStyle;
}
```

## Playhead Control

- Click on timeline to seek
- Drag playhead to scrub
- Space bar to play/pause
- Arrow keys for frame-by-frame

## Zoom

- Mouse wheel to zoom in/out
- Zoom range: 10% – 1000%
- Fit-to-view button
