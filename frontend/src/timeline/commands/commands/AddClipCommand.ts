// =============================================================================
// AddClipCommand
// =============================================================================
// Adds a new clip to the timeline.
// =============================================================================

import type {
  TimelineCommand,
  CommandResult,
  CommandMetadata,
  AddClipPayload,
} from '../types'
import type { TimelineCanvasState } from '../../model/timeline'
import { generateCommandId } from '../types'

export class AddClipCommand implements TimelineCommand {
  readonly id: string
  readonly type = 'ADD_CLIP'
  readonly metadata: CommandMetadata
  readonly payload: AddClipPayload

  constructor(payload: AddClipPayload, metadata: CommandMetadata) {
    this.id = generateCommandId()
    this.payload = payload
    this.metadata = metadata
  }

  execute(timeline: TimelineCanvasState): CommandResult {
    const { clip } = this.payload
    const errors: string[] = []

    // Validate track exists
    const track = timeline.tracks[clip.trackId]
    if (!track) {
      return { success: false, timeline, errors: [`Track "${clip.trackId}" not found`] }
    }

    if (track.locked) {
      return { success: false, timeline, errors: [`Track "${track.name}" is locked`] }
    }

    // Add clip
    const clips = { ...timeline.clips, [clip.id]: clip }

    // Update track's clip list
    const tracks = {
      ...timeline.tracks,
      [clip.trackId]: {
        ...track,
        clipIds: [...track.clipIds, clip.id],
      },
    }

    // Recalculate duration
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
    const { clip } = this.payload
    const errors: string[] = []

    // Check clip exists
    if (!timeline.clips[clip.id]) {
      return { success: false, timeline, errors: [`Clip "${clip.id}" not found`] }
    }

    // Remove clip
    const { [clip.id]: _, ...remainingClips } = timeline.clips

    // Update track
    const track = timeline.tracks[clip.trackId]
    const tracks = track
      ? {
          ...timeline.tracks,
          [clip.trackId]: {
            ...track,
            clipIds: track.clipIds.filter((id) => id !== clip.id),
          },
        }
      : timeline.tracks

    // Recalculate duration
    const maxEnd = Object.values(remainingClips).reduce(
      (max, c) => Math.max(max, c.timing.end),
      0
    )

    return {
      success: true,
      timeline: { ...timeline, tracks, clips: remainingClips, duration: maxEnd },
      errors,
    }
  }
}
