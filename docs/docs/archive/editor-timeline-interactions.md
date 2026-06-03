# Editor: Timeline Interactions Guide

> **Purpose:** Document the timeline structure, clip operations, drag-and-drop, undo/redo, and keyboard shortcuts.  
> **Component:** `TimelineEditor.vue`, `ClipLibrary.vue`, `PropertiesPanel.vue`  
> **Last Updated:** 2026-05-16 (Prompt 62)

---

## Timeline Structure

The timeline is the central editing surface of the video editor. It consists of **tracks** (horizontal lanes) containing **clips** (media segments positioned by time).

### Track Types

| Track Type | Icon | Contains | Default Name |
|------------|------|----------|--------------|
| Video      | 🎬   | Video clips, Image clips | "Video 1" |
| Audio      | 🎵   | Audio clips | "Audio 1" |
| Text       | 📄   | Text elements, Subtitle clips | "Text 1" |
| Image      | 🖼️   | Image overlays | "Image 1" |
| Subtitle   | 📝   | Subtitle cues | "Subtitle 1" |

### Track Properties

Each track has the following properties:

```typescript
interface Track {
  id: string              // Unique identifier (e.g., "track_1715000000000")
  name: string            // Display name (e.g., "Video 1")
  type: 'video' | 'audio' | 'text' | 'image' | 'subtitle'
  clips: TrackClip[]      // Array of clips on this track
  muted: boolean          // Audio muted (audio tracks only)
  locked: boolean         // Prevents modifications when true
}
```

### TrackClip Structure

A TrackClip represents the placement of a Clip on a specific track:

```typescript
interface TrackClip {
  id: string              // Unique identifier
  clipId: string          // Reference to the source Clip
  trackId: string         // Reference to the parent Track
  start: number           // Start time in seconds
  duration: number        // Duration in seconds
  clipStart: number       // Offset into the source clip
  clipEnd: number         // End point in the source clip
  effects: ClipEffect[]   // Applied effects (optional)
}
```

### Timeline State

The overall timeline state is managed by `useTimelineStore()`:

```typescript
interface TimelineState {
  tracks: Track[]         // All tracks
  duration: number        // Total timeline duration in seconds
  currentTime: number     // Playhead position in seconds
  zoom: number            // Zoom level (0.1x to 10x)
  playing: boolean        // Playback state
}
```

---

## Clip Operations

### Selecting a Clip

- **Single click** on a clip in the timeline selects it
- Selected clip is highlighted with a distinct border
- The Properties Panel updates to show the clip's properties
- `timelineStore.selectClip(trackClipId)` sets `selectedClipId`
- `timelineStore.deseelectClip()` clears the selection

### Moving a Clip

- **Drag** a clip horizontally to reposition it on the same track
- The clip's `start` time is updated in the store via `moveClip(trackId, clipId, newStart)`
- Clips cannot be moved before time 0 (clamped to 0)
- Clips cannot be placed on a locked track

### Deleting a Clip

- Click the **✕** button on a clip in the Clip Library to remove it from both the library and timeline
- Click the **🗑 Delete** button in the Properties Panel to remove the selected clip from the timeline
- `timelineStore.deleteSelectedClip()` removes the clip from the track and from `clips`

### Duplicating a Clip

- Click the **⧉ Duplicate** button in the Properties Panel
- `timelineStore.duplicateSelectedClip()` creates:
  - A new Clip with `(copy)` appended to the name
  - A new TrackClip positioned immediately after the original
- The duplicated clip becomes the new selection

### Trimming a Clip

- Trim is done via the Properties Panel's **Start** and **End** time inputs
- `timelineStore.updateTrackClipTime(trackClipId, trackId, start, duration)` applies changes
- Start time is clamped to >= 0
- Duration is clamped to >= 0.1 seconds
- End time must be greater than start time

### Splitting a Clip

- Split is available via context menu or keyboard shortcut
- Creates two TrackClips from one at the playhead position
- Both clips reference the same source Clip

---

## Drag and Drop from Clip Library

Clips in the Clip Library are draggable to the timeline:

1. **Start drag** — `onDragStart(e, clip)` sets `dataTransfer` with the clip ID
2. **Drop on timeline** — The timeline receives the clip and adds it to the appropriate track
3. **Track mapping** — Clip types map to track types:
   - `video` → video track
   - `audio` → audio track
   - `image` → video track
   - `subtitle` → text track

### Insert Operations

The Clip Library provides two ways to add clips to the timeline:

| Operation | Button | Method | Behavior |
|-----------|--------|--------|----------|
| Insert at playhead | ➕ | `insertClipAtPlayhead()` | Creates track if needed, places clip at current playhead position |
| Append | (via store) | `appendClip()` | Creates track if needed, places clip at end of track |

---

## Undo/Redo Support

The undo/redo system is managed by the `useHistoryStore()`:

### How It Works

1. **Save state** — Before a destructive operation, `historyStore.saveState(timelineStore)` serializes the timeline
2. **Undo** — `historyStore.undo(timelineStore)` restores the previous state
3. **Redo** — `historyStore.redo(timelineStore)` restores the next state

### History Stack

- States are pushed onto a stack before each modification
- Undo pops from the stack and pushes to a redo stack
- Redo pops from the redo stack and pushes back to the undo stack
- New operations after undo clear the redo stack

### Operations That Save History

| Operation | Store Method | Saves History |
|-----------|-------------|---------------|
| Insert clip at playhead | `insertClipAtPlayhead()` | Yes (via ClipLibrary) |
| Append clip | `appendClip()` | Yes (via ClipLibrary) |
| Move clip | `moveClip()` | No (continuous) |
| Resize clip | `resizeClip()` | No (continuous) |
| Delete clip | `deleteSelectedClip()` | No |
| Duplicate clip | `duplicateSelectedClip()` | No |

---

## Keyboard Shortcuts

### Playback

| Key | Action |
|-----|--------|
| `Space` | Toggle play/pause |
| `←` | Step backward 1 second |
| `→` | Step forward 1 second |
| `Shift + ←` | Step backward 5 seconds |
| `Shift + →` | Step forward 5 seconds |
| `Home` | Seek to start |
| `End` | Seek to end |

### Editing

| Key | Action |
|-----|--------|
| `Delete` / `Backspace` | Delete selected clip |
| `Ctrl + D` | Duplicate selected clip |
| `Ctrl + Z` | Undo |
| `Ctrl + Shift + Z` / `Ctrl + Y` | Redo |
| `Ctrl + S` | Save project |
| `Ctrl + E` | Open export panel |

### Navigation

| Key | Action |
|-----|--------|
| `+` | Zoom in |
| `-` | Zoom out |
| `0` | Reset zoom to 1x |
| `Scroll wheel` | Scroll timeline horizontally |
| `Shift + Scroll wheel` | Zoom in/out |

---

## Properties Panel Integration

When a clip is selected on the timeline, the **Properties Panel** (`PropertiesPanel.vue`) displays:

### Clip Properties

| Field | Editable | Description |
|-------|----------|-------------|
| Name | ✅ | Clip display name |
| Start | ✅ | Start time in seconds |
| End | ✅ | End time in seconds |
| Duration | ❌ (auto) | Computed from start/end |
| Track | ❌ | Track name |
| Resolution | ❌ | Width × Height (if available) |
| File size | ❌ | In MB |
| Volume | ✅ | 0-100% slider |
| Opacity | ✅ | 0-100% slider |
| Effects | ❌ | List of applied effects |

### Project Properties (No Clip Selected)

When no clip is selected, the Properties Panel shows project-level information:

| Field | Description |
|-------|-------------|
| Project Name | Current project name (read-only) |
| Duration | Total timeline duration |
| Tracks | Number of tracks |
| Clips | Total clip count |
| Subtitles | Number of subtitle tracks |
| Effects | Total effects across all clips |

---

## Timeline State Management

All timeline state is managed by the Pinia store `useTimelineStore()`:

### Key Computed Properties

```typescript
const trackCount = computed(() => state.value.tracks.length)
const selectedTrackClip = computed(() => { /* finds trackClip by selectedClipId */ })
const selectedClip = computed(() => { /* finds clip by selectedTrackClip */ })
```

### Key Actions

```typescript
addTrack(name, type)              // Create a new track
removeTrack(trackId)              // Remove a track and its clips
addClipToTrack(trackId, clip, start)  // Add clip to track at position
removeClipFromTrack(trackId, id)      // Remove clip from track
moveClip(trackId, clipId, newStart)   // Reposition clip
resizeClip(trackId, clipId, duration) // Change clip duration
setCurrentTime(time)              // Move playhead
togglePlayback()                  // Toggle play/pause
setZoom(zoom)                     // Set zoom level (0.1-10)
loadDemoProject(project)          // Load demo project data
loadFromJSON(json)                // Restore from serialized state
toJSON()                          // Serialize to JSON
selectClip(trackClipId)           // Set selected clip
deselectClip()                    // Clear selection
deleteSelectedClip()              // Remove selected clip
duplicateSelectedClip()           // Duplicate selected clip
addEffectToClip(trackClipId, effect)  // Apply effect to clip
removeEffectFromClip(trackClipId, id) // Remove effect from clip
updateEffectParams(trackClipId, id, params) // Update effect parameters
```

---

## Component Reference

| Component | File | Purpose |
|-----------|------|---------|
| `TimelineEditor` | `components/timeline/TimelineEditor.vue` | Timeline rendering and interaction |
| `ClipLibrary` | `components/clip-library/ClipLibrary.vue` | Clip list with drag support |
| `PropertiesPanel` | `components/editor/PropertiesPanel.vue` | Clip/project property editing |
| `useTimelineStore` | `stores/timeline.ts` | Timeline state management |
| `useHistoryStore` | `stores/history.ts` | Undo/redo history management |
