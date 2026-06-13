// =============================================================================
// TrimClipCommand
// =============================================================================
// Trims a clip's start or end edge.
// =============================================================================

import type {
  TimelineCommand,
  CommandResult,
  CommandMetadata,
  TrimClipPayload,
} from '../types'
import type { TimelineCanvasState, TimelineClip } from '../../model/timeline'
import { generateCommandId } from '../types'

export class TrimClipCommand implements TimelineCommand {
  readonly id: string
  readonly type = 'TRIM_CLIP'
  readonly metadata: CommandMetadata
  readonly payload: TrimClipPayload

  constructor(payload: TrimClipPayload, metadata: CommandMetadata) {
    this.id = generateCommandId()
    this.payload = payload
    this.metadata = metadata
  }

  execute(timeline: TimelineCanvasState): CommandResult {
    const { clipId, side, toTime } = this.payload
    const errors: string[] = []

    const clip = timeline.clips[clipId]
    if (!clip) {
      return { success: false, timeline, errors: [`Clip "${clipId}" not found`] }
    }

    const track = timeline.tracks[clip.trackId]
    if (track?.locked) {
      return { success: false, timeline, errors: [`Track "${track.name}" is locked`] }
    }

    const MIN_DURATION = 0.1
    let newStart = clip.timing.start
    let newEnd = clip.timing.end

    if (side === 'start') {
      newStart = Math.max(0, Math.min(toTime, clip.timing.end - MIN_DURATION))
    } else {
      newEnd = Math.max(toTime, clip.timing.start + MIN_DURATION)
    }

    const newDuration = newEnd - newStart

    const updatedClip: TimelineClip = {
      ...clip,
      timing: { start: newStart, end: newEnd, duration: newDuration },
    }

    const clips = { ...timeline.clips, [clipId]: updatedClip }
    const maxEnd = Object.values(clips).reduce(
      (max, c) => Math.max(max, c.timing.end),
      0
    )

    return {
      success: true,
      timeline: { ...timeline, clips, duration: maxEnd },
      errors,
    }
  }

  undo(timeline: TimelineCanvasState): CommandResult {
    const { clipId, fromTime, side } = this.payload
    const errors: string[] = []

    const clip = timeline.clips[clipId]
    if (!clip) {
      return { success: false, timeline, errors: [`Clip "${clipId}" not found`] }
    }

    let newStart = clip.timing.start
    let newEnd = clip.timing.end

    if (side === 'start') {
      newStart = fromTime
    } else {
      newEnd = fromTime
    }

    const newDuration = newEnd - newStart

    const restoredClip: TimelineClip = {
      ...clip,
      timing: { start: newStart, end: newEnd, duration: newDuration },
    }

    const clips = { ...timeline.clips, [clipId]: restoredClip }
    const maxEnd = Object.values(clips).reduce(
      (max, c) => Math.max(max, c.timing.end),
      0
    )

    return {
      success: true,
      timeline: { ...timeline, clips, duration: maxEnd },
      errors,
    }
  }
}
