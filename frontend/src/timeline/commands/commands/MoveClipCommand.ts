// =============================================================================
// MoveClipCommand
// =============================================================================
// Moves a clip to a new position and/or track.
// =============================================================================

import type {
  TimelineCommand,
  CommandResult,
  CommandMetadata,
  MoveClipPayload,
} from '../types'
import type { TimelineCanvasState, TimelineClip } from '../../model/timeline'
import { generateCommandId } from '../types'

export class MoveClipCommand implements TimelineCommand {
  readonly id: string
  readonly type = 'MOVE_CLIP'
  readonly metadata: CommandMetadata
  readonly payload: MoveClipPayload

  private readonly originalClip: TimelineClip | null

  constructor(
    payload: MoveClipPayload,
    metadata: CommandMetadata,
    originalClip: TimelineClip | null = null
  ) {
    this.id = generateCommandId()
    this.payload = payload
    this.metadata = metadata
    this.originalClip = originalClip
  }

  execute(timeline: TimelineCanvasState): CommandResult {
    const { clipId, toStart, toTrackId } = this.payload
    const errors: string[] = []

    // Validate clip exists
    const clip = timeline.clips[clipId]
    if (!clip) {
      return { success: false, timeline, errors: [`Clip "${clipId}" not found`] }
    }

    // Validate target track
    const targetTrack = timeline.tracks[toTrackId]
    if (!targetTrack) {
      return { success: false, timeline, errors: [`Track "${toTrackId}" not found`] }
    }

    if (targetTrack.locked) {
      return { success: false, timeline, errors: [`Track "${targetTrack.name}" is locked`] }
    }

    const finalStart = Math.max(0, toStart)
    const duration = clip.timing.duration
    const newEnd = finalStart + duration

    // Create updated clip
    const updatedClip: TimelineClip = {
      ...clip,
      trackId: toTrackId,
      timing: { start: finalStart, end: newEnd, duration },
    }

    // Update tracks
    let tracks = { ...timeline.tracks }

    // Remove from old track if changing tracks
    if (clip.trackId !== toTrackId) {
      const oldTrack = tracks[clip.trackId]
      if (oldTrack) {
        tracks[clip.trackId] = {
          ...oldTrack,
          clipIds: oldTrack.clipIds.filter((id) => id !== clipId),
        }
      }
      tracks[toTrackId] = {
        ...targetTrack,
        clipIds: [...targetTrack.clipIds, clipId],
      }
    }

    const clips = { ...timeline.clips, [clipId]: updatedClip }
    const maxEnd = Object.values(clips).reduce(
      (max, c) => Math.max(max, c.timing.end),
      0
    )

    return {
      success: true,
      timeline: { ...timeline, tracks, clips, duration: maxEnd },
      errors,
    }
  }

  undo(timeline: TimelineCanvasState): CommandResult {
    const { clipId, fromStart, fromTrackId } = this.payload
    const errors: string[] = []

    const clip = timeline.clips[clipId]
    if (!clip) {
      return { success: false, timeline, errors: [`Clip "${clipId}" not found`] }
    }

    const duration = clip.timing.duration
    const fromEnd = fromStart + duration

    // Restore original clip
    const restoredClip: TimelineClip = {
      ...clip,
      trackId: fromTrackId,
      timing: { start: fromStart, end: fromEnd, duration },
    }

    // Update tracks
    let tracks = { ...timeline.tracks }

    if (clip.trackId !== fromTrackId) {
      const currentTrack = tracks[clip.trackId]
      if (currentTrack) {
        tracks[clip.trackId] = {
          ...currentTrack,
          clipIds: currentTrack.clipIds.filter((id) => id !== clipId),
        }
      }
      const targetTrack = tracks[fromTrackId]
      if (targetTrack) {
        tracks[fromTrackId] = {
          ...targetTrack,
          clipIds: [...targetTrack.clipIds, clipId],
        }
      }
    }

    const clips = { ...timeline.clips, [clipId]: restoredClip }
    const maxEnd = Object.values(clips).reduce(
      (max, c) => Math.max(max, c.timing.end),
      0
    )

    return {
      success: true,
      timeline: { ...timeline, tracks, clips, duration: maxEnd },
      errors,
    }
  }
}
