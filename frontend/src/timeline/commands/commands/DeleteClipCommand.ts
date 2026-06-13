// =============================================================================
// DeleteClipCommand
// =============================================================================
// Deletes a clip from the timeline.
// =============================================================================

import type {
  TimelineCommand,
  CommandResult,
  CommandMetadata,
  DeleteClipPayload,
} from '../types'
import type { TimelineCanvasState } from '../../model/timeline'
import { generateCommandId } from '../types'

export class DeleteClipCommand implements TimelineCommand {
  readonly id: string
  readonly type = 'DELETE_CLIP'
  readonly metadata: CommandMetadata
  readonly payload: DeleteClipPayload

  constructor(payload: DeleteClipPayload, metadata: CommandMetadata) {
    this.id = generateCommandId()
    this.payload = payload
    this.metadata = metadata
  }

  execute(timeline: TimelineCanvasState): CommandResult {
    const { clip, trackId } = this.payload
    const errors: string[] = []

    // Check clip exists
    if (!timeline.clips[clip.id]) {
      return { success: false, timeline, errors: [`Clip "${clip.id}" not found`] }
    }

    // Check track not locked
    const track = timeline.tracks[trackId]
    if (track?.locked) {
      return { success: false, timeline, errors: [`Track "${track.name}" is locked`] }
    }

    // Remove clip
    const { [clip.id]: _, ...remainingClips } = timeline.clips

    // Update track
    const tracks = track
      ? {
          ...timeline.tracks,
          [trackId]: {
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

  undo(timeline: TimelineCanvasState): CommandResult {
    const { clip, trackId } = this.payload
    const errors: string[] = []

    // Validate track exists
    const track = timeline.tracks[trackId]
    if (!track) {
      return { success: false, timeline, errors: [`Track "${trackId}" not found`] }
    }

    // Restore clip
    const clips = { ...timeline.clips, [clip.id]: clip }

    // Update track
    const tracks = {
      ...timeline.tracks,
      [trackId]: {
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
}
