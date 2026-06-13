// =============================================================================
// Timeline Canvas Domain Model
// =============================================================================
// Canonical timeline representation for the canvas editor.
// All types are readonly and immutable. UI components consume only these types.
//
// Rules:
// - Normalized state (keyed by id)
// - No API types exposed to UI
// - Supports future extension (transform, keyframes) without breaking changes
// =============================================================================

// ---------------------------------------------------------------------------
// Clip Timing
// ---------------------------------------------------------------------------
export interface ClipTiming {
  readonly start: number    // start position on timeline (seconds)
  readonly end: number      // end position on timeline (seconds)
  readonly duration: number // clip duration (seconds)
}

// ---------------------------------------------------------------------------
// Clip Transform (reserved for future use)
// ---------------------------------------------------------------------------
export interface ClipTransform {
  readonly x: number
  readonly y: number
  readonly scaleX: number
  readonly scaleY: number
  readonly rotation: number
  readonly opacity: number
}

// ---------------------------------------------------------------------------
// Timeline Clip
// ---------------------------------------------------------------------------
export type ClipType = 'video' | 'audio' | 'subtitle' | 'image' | 'effect'

export interface TimelineClip {
  readonly id: string
  readonly trackId: string
  readonly assetId: string | null       // null for effect-only clips
  readonly name: string
  readonly type: ClipType
  readonly timing: ClipTiming
  readonly transform: ClipTransform | null
  readonly effectIds: readonly string[]
  readonly metadata: Readonly<Record<string, string>>
}

// ---------------------------------------------------------------------------
// Track
// ---------------------------------------------------------------------------
export type TrackType = 'video' | 'audio' | 'subtitle' | 'overlay' | 'effect'

export interface TimelineTrack {
  readonly id: string
  readonly name: string
  readonly type: TrackType
  readonly clipIds: readonly string[]   // ordered clip ids
  readonly muted: boolean
  readonly locked: boolean
  readonly height: number               // display height in pixels
}

// ---------------------------------------------------------------------------
// Track Layout Metadata
// ---------------------------------------------------------------------------
export interface TrackLayout {
  readonly trackId: string
  readonly order: number
  readonly collapsed: boolean
  readonly color: string
}

// ---------------------------------------------------------------------------
// Timeline Canvas State
// ---------------------------------------------------------------------------
export interface TimelineCanvasState {
  readonly tracks: Readonly<Record<string, TimelineTrack>>
  readonly clips: Readonly<Record<string, TimelineClip>>
  readonly trackOrder: readonly string[]  // ordered track ids
  readonly layouts: Readonly<Record<string, TrackLayout>>
  readonly duration: number               // total timeline duration (seconds)
}

// ---------------------------------------------------------------------------
// Helper: create empty timeline
// ---------------------------------------------------------------------------
export function createEmptyTimeline(): TimelineCanvasState {
  return {
    tracks: {},
    clips: {},
    trackOrder: [],
    layouts: {},
    duration: 0,
  }
}

// ---------------------------------------------------------------------------
// Helper: create clip with defaults
// ---------------------------------------------------------------------------
export function createClip(
  id: string,
  trackId: string,
  name: string,
  type: ClipType,
  start: number,
  duration: number,
  assetId: string | null = null,
  effectIds: string[] = []
): TimelineClip {
  return {
    id,
    trackId,
    assetId,
    name,
    type,
    timing: { start, end: start + duration, duration },
    transform: null,
    effectIds,
    metadata: {},
  }
}

// ---------------------------------------------------------------------------
// Helper: create track with defaults
// ---------------------------------------------------------------------------
export function createTrack(
  id: string,
  name: string,
  type: TrackType,
  order: number = 0
): { track: TimelineTrack; layout: TrackLayout } {
  return {
    track: {
      id,
      name,
      type,
      clipIds: [],
      muted: false,
      locked: false,
      height: type === 'video' ? 64 : type === 'audio' ? 48 : 40,
    },
    layout: {
      trackId: id,
      order,
      collapsed: false,
      color: TRACK_TYPE_COLORS[type],
    },
  }
}

// ---------------------------------------------------------------------------
// Track type → default color mapping
// ---------------------------------------------------------------------------
export const TRACK_TYPE_COLORS: Record<TrackType, string> = {
  video: '#3b82f6',    // blue-500
  audio: '#22c55e',    // green-500
  subtitle: '#a855f7', // purple-500
  overlay: '#f97316',  // orange-500
  effect: '#ec4899',   // pink-500
}

// ---------------------------------------------------------------------------
// Clip type → display color mapping
// ---------------------------------------------------------------------------
export const CLIP_TYPE_COLORS: Record<ClipType, string> = {
  video: '#2563eb',    // blue-600
  audio: '#16a34a',    // green-600
  subtitle: '#9333ea', // purple-600
  image: '#0891b2',    // cyan-600
  effect: '#db2777',   // pink-600
}
