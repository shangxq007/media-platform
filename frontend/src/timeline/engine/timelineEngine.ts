// =============================================================================
// Timeline Mutation Engine
// =============================================================================
// Central engine for all timeline mutations.
// All changes to timeline state MUST go through this engine.
// UI cannot directly mutate Zustand state.
//
// Rules:
// - Validates all mutations before applying
// - Resolves overlaps automatically
// - Enforces track constraints
// - Maintains normalized state integrity
// =============================================================================

import type {
  TimelineCanvasState,
  TimelineClip,
  TimelineTrack,
} from '../model/timeline'
import { findNearestSnap, type SnapConfig, DEFAULT_SNAP_CONFIG } from './snap'

// ---------------------------------------------------------------------------
// Mutation Result
// ---------------------------------------------------------------------------
export interface MutationResult {
  readonly success: boolean
  readonly timeline: TimelineCanvasState
  readonly errors: readonly string[]
}

// ---------------------------------------------------------------------------
// Clip Move Operation
// ---------------------------------------------------------------------------
export interface ClipMoveOperation {
  readonly clipId: string
  readonly newStart: number
  readonly newTrackId: string
  readonly snapConfig?: SnapConfig
  readonly zoomLevel?: number
}

// ---------------------------------------------------------------------------
// Clip Trim Operation
// ---------------------------------------------------------------------------
export interface ClipTrimOperation {
  readonly clipId: string
  readonly side: 'start' | 'end'
  readonly newTime: number
  readonly snapConfig?: SnapConfig
  readonly zoomLevel?: number
}

// ---------------------------------------------------------------------------
// Constants
// ---------------------------------------------------------------------------
const MIN_CLIP_DURATION = 0.1 // seconds

// ---------------------------------------------------------------------------
// Validation: check if timeline state is valid
// ---------------------------------------------------------------------------
export function validateTimeline(timeline: TimelineCanvasState): string[] {
  const errors: string[] = []

  // Check all track references
  for (const track of Object.values(timeline.tracks)) {
    for (const clipId of track.clipIds) {
      if (!timeline.clips[clipId]) {
        errors.push(`Track "${track.name}" references missing clip "${clipId}"`)
      }
    }
  }

  // Check all clip track references
  for (const clip of Object.values(timeline.clips)) {
    if (!timeline.tracks[clip.trackId]) {
      errors.push(`Clip "${clip.name}" references missing track "${clip.trackId}"`)
    }
    if (clip.timing.duration < 0) {
      errors.push(`Clip "${clip.name}" has negative duration`)
    }
    if (clip.timing.end < clip.timing.start) {
      errors.push(`Clip "${clip.name}" has end before start`)
    }
  }

  // Check track order references
  for (const trackId of timeline.trackOrder) {
    if (!timeline.tracks[trackId]) {
      errors.push(`Track order references missing track "${trackId}"`)
    }
  }

  return errors
}

// ---------------------------------------------------------------------------
// Validation: check for overlapping clips on same track
// ---------------------------------------------------------------------------
export function findOverlaps(timeline: TimelineCanvasState): Array<{
  trackId: string
  clipA: TimelineClip
  clipB: TimelineClip
}> {
  const overlaps: Array<{
    trackId: string
    clipA: TimelineClip
    clipB: TimelineClip
  }> = []

  for (const track of Object.values(timeline.tracks)) {
    const clips = track.clipIds
      .map((id) => timeline.clips[id])
      .filter((c): c is TimelineClip => c != null)
      .sort((a, b) => a.timing.start - b.timing.start)

    for (let i = 1; i < clips.length; i++) {
      if (clips[i].timing.start < clips[i - 1].timing.end) {
        overlaps.push({
          trackId: track.id,
          clipA: clips[i - 1],
          clipB: clips[i],
        })
      }
    }
  }

  return overlaps
}

// ---------------------------------------------------------------------------
// Resolve overlaps by pushing clips right
// ---------------------------------------------------------------------------
export function resolveOverlaps(timeline: TimelineCanvasState): TimelineCanvasState {
  const result = { ...timeline, clips: { ...timeline.clips } }
  const tracks = { ...timeline.tracks }

  for (const track of Object.values(tracks)) {
    const clips = track.clipIds
      .map((id) => result.clips[id])
      .filter((c): c is TimelineClip => c != null)
      .sort((a, b) => a.timing.start - b.timing.start)

    let lastEnd = 0
    for (const clip of clips) {
      if (clip.timing.start < lastEnd) {
        const duration = clip.timing.duration
        const newClip: TimelineClip = {
          ...clip,
          timing: {
            start: lastEnd,
            end: lastEnd + duration,
            duration,
          },
        }
        result.clips[clip.id] = newClip
      }
      lastEnd = result.clips[clip.id].timing.end
    }
  }

  // Recalculate duration
  const maxEnd = Object.values(result.clips).reduce(
    (max, c) => Math.max(max, c.timing.end),
    0
  )
  result.duration = maxEnd
  result.tracks = tracks

  return result
}

// ---------------------------------------------------------------------------
// Apply clip move
// ---------------------------------------------------------------------------
export function applyClipMove(
  timeline: TimelineCanvasState,
  operation: ClipMoveOperation
): MutationResult {
  const { clipId, newStart, newTrackId, snapConfig, zoomLevel } = operation
  const errors: string[] = []

  // Validate clip exists
  const clip = timeline.clips[clipId]
  if (!clip) {
    return { success: false, timeline, errors: [`Clip "${clipId}" not found`] }
  }

  // Validate target track exists
  const targetTrack = timeline.tracks[newTrackId]
  if (!targetTrack) {
    return { success: false, timeline, errors: [`Track "${newTrackId}" not found`] }
  }

  // Check track is not locked
  if (targetTrack.locked) {
    return { success: false, timeline, errors: [`Track "${targetTrack.name}" is locked`] }
  }

  // Apply snap if configured
  let finalStart = newStart
  if (snapConfig?.enabled && zoomLevel) {
    const snapResult = findNearestSnap(
      newStart,
      timeline,
      zoomLevel,
      clipId,
      snapConfig
    )
    finalStart = snapResult.snappedTime
  }

  // Ensure clip doesn't go negative
  finalStart = Math.max(0, finalStart)

  const duration = clip.timing.duration
  const newEnd = finalStart + duration

  // Update clip
  const updatedClip: TimelineClip = {
    ...clip,
    trackId: newTrackId,
    timing: {
      start: finalStart,
      end: newEnd,
      duration,
    },
  }

  // Update tracks
  const tracks = { ...timeline.tracks }

  // Remove from old track
  if (clip.trackId !== newTrackId) {
    const oldTrack = tracks[clip.trackId]
    if (oldTrack) {
      tracks[clip.trackId] = {
        ...oldTrack,
        clipIds: oldTrack.clipIds.filter((id) => id !== clipId),
      }
    }

    // Add to new track
    tracks[newTrackId] = {
      ...targetTrack,
      clipIds: [...targetTrack.clipIds, clipId],
    }
  }

  // Update clips
  const clips = { ...timeline.clips, [clipId]: updatedClip }

  // Recalculate duration
  const maxEnd = Object.values(clips).reduce(
    (max, c) => Math.max(max, c.timing.end),
    0
  )

  const newTimeline: TimelineCanvasState = {
    ...timeline,
    tracks,
    clips,
    duration: maxEnd,
  }

  // Validate result
  const validationErrors = validateTimeline(newTimeline)
  if (validationErrors.length > 0) {
    return { success: false, timeline, errors: validationErrors }
  }

  return { success: true, timeline: newTimeline, errors }
}

// ---------------------------------------------------------------------------
// Apply clip trim
// ---------------------------------------------------------------------------
export function applyClipTrim(
  timeline: TimelineCanvasState,
  operation: ClipTrimOperation
): MutationResult {
  const { clipId, side, newTime, snapConfig, zoomLevel } = operation
  const errors: string[] = []

  // Validate clip exists
  const clip = timeline.clips[clipId]
  if (!clip) {
    return { success: false, timeline, errors: [`Clip "${clipId}" not found`] }
  }

  // Check track is not locked
  const track = timeline.tracks[clip.trackId]
  if (track?.locked) {
    return { success: false, timeline, errors: [`Track "${track.name}" is locked`] }
  }

  // Apply snap if configured
  let finalTime = newTime
  if (snapConfig?.enabled && zoomLevel) {
    const snapResult = findNearestSnap(
      newTime,
      timeline,
      zoomLevel,
      clipId,
      snapConfig
    )
    finalTime = snapResult.snappedTime
  }

  let newStart = clip.timing.start
  let newEnd = clip.timing.end
  let newDuration = clip.timing.duration

  if (side === 'start') {
    // Trim start: cannot go past end
    finalTime = Math.min(finalTime, clip.timing.end - MIN_CLIP_DURATION)
    finalTime = Math.max(0, finalTime)
    newStart = finalTime
    newDuration = newEnd - newStart
  } else {
    // Trim end: cannot go before start
    finalTime = Math.max(finalTime, clip.timing.start + MIN_CLIP_DURATION)
    newEnd = finalTime
    newDuration = newEnd - newStart
  }

  // Update clip
  const updatedClip: TimelineClip = {
    ...clip,
    timing: {
      start: newStart,
      end: newEnd,
      duration: newDuration,
    },
  }

  // Update clips
  const clips = { ...timeline.clips, [clipId]: updatedClip }

  // Recalculate duration
  const maxEnd = Object.values(clips).reduce(
    (max, c) => Math.max(max, c.timing.end),
    0
  )

  const newTimeline: TimelineCanvasState = {
    ...timeline,
    clips,
    duration: maxEnd,
  }

  // Validate result
  const validationErrors = validateTimeline(newTimeline)
  if (validationErrors.length > 0) {
    return { success: false, timeline, errors: validationErrors }
  }

  return { success: true, timeline: newTimeline, errors }
}

// ---------------------------------------------------------------------------
// Validate mutation before applying
// ---------------------------------------------------------------------------
export function validateTimelineMutation(
  timeline: TimelineCanvasState,
  clipId: string,
  newStart: number,
  newDuration: number,
  newTrackId?: string
): string[] {
  const errors: string[] = []

  const clip = timeline.clips[clipId]
  if (!clip) {
    errors.push(`Clip "${clipId}" not found`)
    return errors
  }

  const trackId = newTrackId ?? clip.trackId
  const track = timeline.tracks[trackId]
  if (!track) {
    errors.push(`Track "${trackId}" not found`)
    return errors
  }

  if (track.locked) {
    errors.push(`Track "${track.name}" is locked`)
  }

  if (newDuration < MIN_CLIP_DURATION) {
    errors.push(`Duration must be at least ${MIN_CLIP_DURATION}s`)
  }

  if (newStart < 0) {
    errors.push('Start time cannot be negative')
  }

  // Check for overlaps with other clips on target track
  const otherClips = track.clipIds
    .filter((id) => id !== clipId)
    .map((id) => timeline.clips[id])
    .filter((c): c is TimelineClip => c != null)

  const newEnd = newStart + newDuration
  for (const other of otherClips) {
    if (newStart < other.timing.end && newEnd > other.timing.start) {
      errors.push(`Would overlap with clip "${other.name}"`)
    }
  }

  return errors
}
