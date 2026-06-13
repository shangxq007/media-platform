// =============================================================================
// Auto Layout Engine
// =============================================================================
// Automatically optimizes timeline layout.
// Generates commands for layout improvements without direct mutation.
// =============================================================================

import type { TimelineCanvasState, TimelineClip, TimelineTrack } from '../model/timeline'
import type { TimelineCommand } from '../commands/types'
import { createMetadata } from '../commands/types'
import { MoveClipCommand } from '../commands/commands/MoveClipCommand'
import { DeleteClipCommand } from '../commands/commands/DeleteClipCommand'

// ---------------------------------------------------------------------------
// Layout Result
// ---------------------------------------------------------------------------
export interface LayoutResult {
  readonly commands: readonly TimelineCommand[]
  readonly description: string
  readonly affectedClips: number
}

// ---------------------------------------------------------------------------
// Auto Stack Clips
// ---------------------------------------------------------------------------
// Shifts clips right to eliminate overlaps on each track.
export function autoStackClips(timeline: TimelineCanvasState): LayoutResult {
  const commands: TimelineCommand[] = []
  const metadata = createMetadata('Auto-stack clips to remove overlaps', 'system')

  for (const track of Object.values(timeline.tracks)) {
    if (track.locked) continue

    const clips = getTrackClipsSorted(timeline, track.id)
    let lastEnd = 0

    for (const clip of clips) {
      if (clip.timing.start < lastEnd) {
        // Need to shift this clip right
        const command = new MoveClipCommand(
          {
            clipId: clip.id,
            fromStart: clip.timing.start,
            fromTrackId: clip.trackId,
            toStart: lastEnd,
            toTrackId: clip.trackId,
          },
          metadata
        )
        commands.push(command)
      }
      lastEnd = Math.max(lastEnd, clip.timing.end)
    }
  }

  return {
    commands,
    description: `Auto-stacked ${commands.length} clips to remove overlaps`,
    affectedClips: commands.length,
  }
}

// ---------------------------------------------------------------------------
// Compact Timeline
// ---------------------------------------------------------------------------
// Removes gaps between clips, compacting the timeline.
export function compactTimeline(timeline: TimelineCanvasState, threshold: number = 0.5): LayoutResult {
  const commands: TimelineCommand[] = []
  const metadata = createMetadata('Compact timeline by removing gaps', 'system')

  for (const track of Object.values(timeline.tracks)) {
    if (track.locked) continue

    const clips = getTrackClipsSorted(timeline, track.id)
    if (clips.length < 2) continue

    let shiftAmount = 0
    let lastEnd = clips[0].timing.end

    for (let i = 1; i < clips.length; i++) {
      const clip = clips[i]
      const gap = clip.timing.start - lastEnd

      if (gap > threshold) {
        shiftAmount += gap
      }

      if (shiftAmount > 0) {
        const newStart = clip.timing.start - shiftAmount
        const command = new MoveClipCommand(
          {
            clipId: clip.id,
            fromStart: clip.timing.start,
            fromTrackId: clip.trackId,
            toStart: Math.max(0, newStart),
            toTrackId: clip.trackId,
          },
          metadata
        )
        commands.push(command)
      }

      lastEnd = clip.timing.end
    }
  }

  return {
    commands,
    description: `Compacted timeline by removing ${commands.length} gaps`,
    affectedClips: commands.length,
  }
}

// ---------------------------------------------------------------------------
// Normalize Track Distribution
// ---------------------------------------------------------------------------
// Moves clips to fill empty tracks and balance load.
export function normalizeTrackDistribution(timeline: TimelineCanvasState): LayoutResult {
  const commands: TimelineCommand[] = []
  const metadata = createMetadata('Normalize track distribution', 'system')

  const videoTracks = Object.values(timeline.tracks)
    .filter(t => t.type === 'video' && !t.locked)
    .sort((a, b) => a.clipIds.length - b.clipIds.length)

  if (videoTracks.length < 2) return { commands, description: 'Not enough tracks to normalize', affectedClips: 0 }

  const mostLoaded = videoTracks[videoTracks.length - 1]
  const leastLoaded = videoTracks[0]

  if (mostLoaded.clipIds.length - leastLoaded.clipIds.length < 2) {
    return { commands, description: 'Tracks are already balanced', affectedClips: 0 }
  }

  // Move half of the clips from the most loaded to the least loaded
  const clipsToMove = getTrackClipsSorted(timeline, mostLoaded.id)
    .slice(Math.floor(mostLoaded.clipIds.length / 2))

  for (const clip of clipsToMove) {
    const command = new MoveClipCommand(
      {
        clipId: clip.id,
        fromStart: clip.timing.start,
        fromTrackId: clip.trackId,
        toStart: clip.timing.start,
        toTrackId: leastLoaded.id,
      },
      metadata
    )
    commands.push(command)
  }

  return {
    commands,
    description: `Moved ${commands.length} clips to balance tracks`,
    affectedClips: commands.length,
  }
}

// ---------------------------------------------------------------------------
// Balance Track Load
// ---------------------------------------------------------------------------
// Distributes clips evenly across tracks of the same type.
export function balanceTrackLoad(timeline: TimelineCanvasState, trackType: TimelineTrack['type']): LayoutResult {
  const commands: TimelineCommand[] = []
  const metadata = createMetadata(`Balance ${trackType} track load`, 'system')

  const tracks = Object.values(timeline.tracks)
    .filter(t => t.type === trackType && !t.locked)
    .sort((a, b) => {
      const orderA = timeline.layouts[a.id]?.order ?? 0
      const orderB = timeline.layouts[b.id]?.order ?? 0
      return orderA - orderB
    })

  if (tracks.length < 2) return { commands, description: 'Not enough tracks to balance', affectedClips: 0 }

  // Collect all clips from these tracks
  const allClips: TimelineClip[] = []
  for (const track of tracks) {
    allClips.push(...getTrackClipsSorted(timeline, track.id))
  }

  // Sort clips by start time
  allClips.sort((a, b) => a.timing.start - b.timing.start)

  // Distribute clips round-robin across tracks
  for (let i = 0; i < allClips.length; i++) {
    const clip = allClips[i]
    const targetTrack = tracks[i % tracks.length]

    if (clip.trackId !== targetTrack.id) {
      const command = new MoveClipCommand(
        {
          clipId: clip.id,
          fromStart: clip.timing.start,
          fromTrackId: clip.trackId,
          toStart: clip.timing.start,
          toTrackId: targetTrack.id,
        },
        metadata
      )
      commands.push(command)
    }
  }

  return {
    commands,
    description: `Balanced ${commands.length} clips across ${tracks.length} ${trackType} tracks`,
    affectedClips: commands.length,
  }
}

// ---------------------------------------------------------------------------
// Remove Empty Tracks
// ---------------------------------------------------------------------------
export function removeEmptyTracks(timeline: TimelineCanvasState): LayoutResult {
  const commands: TimelineCommand[] = []
  const metadata = createMetadata('Remove empty tracks', 'system')

  for (const track of Object.values(timeline.tracks)) {
    if (track.clipIds.length === 0) {
      // We don't have a DeleteTrackCommand yet, so we'll just report
      // This would need a new command type
    }
  }

  return {
    commands,
    description: `Found ${commands.length} empty tracks to remove`,
    affectedClips: 0,
  }
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------
function getTrackClipsSorted(timeline: TimelineCanvasState, trackId: string): TimelineClip[] {
  const track = timeline.tracks[trackId]
  if (!track) return []

  return track.clipIds
    .map(id => timeline.clips[id])
    .filter((c): c is TimelineClip => c != null)
    .sort((a, b) => a.timing.start - b.timing.start)
}
