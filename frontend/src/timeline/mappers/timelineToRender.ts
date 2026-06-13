// =============================================================================
// Timeline → Render Mapping
// =============================================================================
// Converts TimelineCanvasState to the JSON format expected by the render backend.
// Ensures frontend timeline stays aligned with backend rendering engine.
//
// Output format matches the RenderJob timeline JSON contract.
// =============================================================================

import type {
  TimelineCanvasState,
  TimelineTrack,
  TimelineClip,
} from '../model/timeline'

// ---------------------------------------------------------------------------
// Render Timeline JSON Types
// ---------------------------------------------------------------------------

export interface RenderTimelineClip {
  id: string
  name: string
  type: string
  sourceUrl: string | null
  start: number
  duration: number
  end: number
  effectKeys: string[]
  metadata: Record<string, string>
}

export interface RenderTimelineTrack {
  id: string
  name: string
  type: string
  clips: RenderTimelineClip[]
  muted: boolean
  locked: boolean
}

export interface RenderTimeline {
  version: '2.0.0'
  tracks: RenderTimelineTrack[]
  duration: number
}

// ---------------------------------------------------------------------------
// Mapper: Clip → Render Clip
// ---------------------------------------------------------------------------

function mapClipToRender(clip: TimelineClip, assetUriMap: Record<string, string>): RenderTimelineClip {
  return {
    id: clip.id,
    name: clip.name,
    type: clip.type,
    sourceUrl: clip.assetId ? (assetUriMap[clip.assetId] ?? null) : null,
    start: clip.timing.start,
    duration: clip.timing.duration,
    end: clip.timing.end,
    effectKeys: [...clip.effectIds],
    metadata: { ...clip.metadata },
  }
}

// ---------------------------------------------------------------------------
// Mapper: Track → Render Track
// ---------------------------------------------------------------------------

function mapTrackToRender(
  track: TimelineTrack,
  clips: Record<string, TimelineClip>,
  assetUriMap: Record<string, string>
): RenderTimelineTrack {
  const renderClips = track.clipIds
    .map((id) => clips[id])
    .filter((c): c is TimelineClip => c != null)
    .map((clip) => mapClipToRender(clip, assetUriMap))

  return {
    id: track.id,
    name: track.name,
    type: track.type,
    clips: renderClips,
    muted: track.muted,
    locked: track.locked,
  }
}

// ---------------------------------------------------------------------------
// Mapper: TimelineCanvasState → RenderTimeline
// ---------------------------------------------------------------------------

/**
 * Convert timeline canvas state to render backend JSON format.
 *
 * @param timeline - The canonical timeline state
 * @param assetUriMap - Map of assetId → storage URI (e.g., "storage://...")
 * @returns RenderTimeline JSON ready for submission
 */
export function mapTimelineToRender(
  timeline: TimelineCanvasState,
  assetUriMap: Record<string, string> = {}
): RenderTimeline {
  const renderTracks = timeline.trackOrder
    .map((id) => timeline.tracks[id])
    .filter((t): t is TimelineTrack => t != null)
    .map((track) => mapTrackToRender(track, timeline.clips, assetUriMap))

  return {
    version: '2.0.0',
    tracks: renderTracks,
    duration: timeline.duration,
  }
}

// ---------------------------------------------------------------------------
// Validation: check timeline is submittable
// ---------------------------------------------------------------------------

export interface TimelineValidationResult {
  valid: boolean
  errors: string[]
}

export function validateTimelineForRender(timeline: TimelineCanvasState): TimelineValidationResult {
  const errors: string[] = []

  if (timeline.trackOrder.length === 0) {
    errors.push('Timeline has no tracks')
  }

  if (timeline.duration <= 0) {
    errors.push('Timeline has no duration')
  }

  const hasVideo = timeline.trackOrder.some((id) => {
    const track = timeline.tracks[id]
    return track?.type === 'video' && track.clipIds.length > 0
  })

  if (!hasVideo) {
    errors.push('Timeline must have at least one video clip')
  }

  // Check for overlapping clips on same track
  for (const trackId of timeline.trackOrder) {
    const track = timeline.tracks[trackId]
    if (!track) continue

    const clips = track.clipIds
      .map((id) => timeline.clips[id])
      .filter((c): c is TimelineClip => c != null)
      .sort((a, b) => a.timing.start - b.timing.start)

    for (let i = 1; i < clips.length; i++) {
      if (clips[i].timing.start < clips[i - 1].timing.end) {
        errors.push(
          `Overlapping clips on track "${track.name}": "${clips[i - 1].name}" and "${clips[i].name}"`
        )
      }
    }
  }

  return { valid: errors.length === 0, errors }
}
