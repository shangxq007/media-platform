// =============================================================================
// Suggestion Engine
// =============================================================================
// Analyzes timeline and generates intelligent editing suggestions.
// Each suggestion includes an optional command for one-click application.
// =============================================================================

import type { TimelineCanvasState, TimelineClip } from '../model/timeline'
import type { TimelineCommand } from '../commands/types'
import { createMetadata } from '../commands/types'
import { MoveClipCommand } from '../commands/commands/MoveClipCommand'
import { TrimClipCommand } from '../commands/commands/TrimClipCommand'
import { AddClipCommand } from '../commands/commands/AddClipCommand'
import { createClip } from '../model/timeline'

// ---------------------------------------------------------------------------
// Suggestion Types
// ---------------------------------------------------------------------------
export type SuggestionType =
  | 'TRIM_OPTIMIZATION'
  | 'TRANSITION_INSERTION'
  | 'CLIP_GROUPING'
  | 'TRACK_REORDERING'
  | 'GAP_FILL'
  | 'DURATION_MATCH'

export type SuggestionConfidence = 'high' | 'medium' | 'low'

export interface Suggestion {
  readonly id: string
  readonly type: SuggestionType
  readonly confidence: SuggestionConfidence
  readonly title: string
  readonly description: string
  readonly clipIds: readonly string[]
  readonly trackIds: readonly string[]
  readonly command: TimelineCommand | null
  readonly autoApplicable: boolean
}

// ---------------------------------------------------------------------------
// Suggestion ID Generator
// ---------------------------------------------------------------------------
let suggestionCounter = 0
function generateSuggestionId(): string {
  return `suggestion-${Date.now()}-${++suggestionCounter}`
}

// ---------------------------------------------------------------------------
// Suggest Trim Optimization
// ---------------------------------------------------------------------------
// Finds clips that could be trimmed to better align with adjacent clips.
export function suggestTrimOptimization(timeline: TimelineCanvasState): Suggestion[] {
  const suggestions: Suggestion[] = []

  for (const track of Object.values(timeline.tracks)) {
    if (track.locked) continue

    const clips = getTrackClipsSorted(timeline, track.id)

    for (let i = 1; i < clips.length; i++) {
      const prev = clips[i - 1]
      const curr = clips[i]
      const gap = curr.timing.start - prev.timing.end

      // If gap is very small (< 0.2s), suggest trimming to align
      if (gap > 0 && gap < 0.2) {
        const command = new TrimClipCommand(
          {
            clipId: prev.id,
            side: 'end',
            fromTime: prev.timing.end,
            toTime: curr.timing.start,
          },
          createMetadata('Trim to align with next clip', 'system')
        )

        suggestions.push({
          id: generateSuggestionId(),
          type: 'TRIM_OPTIMIZATION',
          confidence: 'high',
          title: 'Align clips',
          description: `Trim "${prev.name}" to align with "${curr.name}" (${gap.toFixed(2)}s gap)`,
          clipIds: [prev.id, curr.id],
          trackIds: [track.id],
          command,
          autoApplicable: true,
        })
      }
    }
  }

  return suggestions
}

// ---------------------------------------------------------------------------
// Suggest Transition Insertion
// ---------------------------------------------------------------------------
// Finds points where transitions could be added between clips.
export function suggestTransitionInsertion(timeline: TimelineCanvasState): Suggestion[] {
  const suggestions: Suggestion[] = []

  // Find video tracks
  const videoTracks = Object.values(timeline.tracks).filter(t => t.type === 'video')

  for (const track of videoTracks) {
    if (track.locked) continue

    const clips = getTrackClipsSorted(timeline, track.id)

    for (let i = 1; i < clips.length; i++) {
      const prev = clips[i - 1]
      const curr = clips[i]

      // Check if clips are adjacent (no gap)
      if (Math.abs(prev.timing.end - curr.timing.start) < 0.01) {
        // Check if either clip already has a transition effect
        const hasTransition = prev.effectIds.length > 0 || curr.effectIds.length > 0

        if (!hasTransition) {
          suggestions.push({
            id: generateSuggestionId(),
            type: 'TRANSITION_INSERTION',
            confidence: 'medium',
            title: 'Add transition',
            description: `Add transition between "${prev.name}" and "${curr.name}"`,
            clipIds: [prev.id, curr.id],
            trackIds: [track.id],
            command: null, // Would need a new command type for adding effects
            autoApplicable: false,
          })
        }
      }
    }
  }

  return suggestions
}

// ---------------------------------------------------------------------------
// Suggest Clip Grouping
// ---------------------------------------------------------------------------
// Finds clips that are temporally close and could be grouped.
export function suggestClipGrouping(timeline: TimelineCanvasState): Suggestion[] {
  const suggestions: Suggestion[] = []

  // Find video tracks
  const videoTracks = Object.values(timeline.tracks).filter(t => t.type === 'video')

  for (const track of videoTracks) {
    if (track.locked) continue

    const clips = getTrackClipsSorted(timeline, track.id)
    if (clips.length < 3) continue

    // Find groups of clips with small gaps
    let groupStart = 0
    let groupSize = 1

    for (let i = 1; i < clips.length; i++) {
      const gap = clips[i].timing.start - clips[i - 1].timing.end

      if (gap < 1.0) {
        groupSize++
      } else {
        if (groupSize >= 3) {
          const groupClips = clips.slice(groupStart, groupStart + groupSize)
          suggestions.push({
            id: generateSuggestionId(),
            type: 'CLIP_GROUPING',
            confidence: 'low',
            title: 'Group clips',
            description: `Group ${groupSize} clips: ${groupClips.map(c => c.name).join(', ')}`,
            clipIds: groupClips.map(c => c.id),
            trackIds: [track.id],
            command: null, // Would need a grouping command
            autoApplicable: false,
          })
        }
        groupStart = i
        groupSize = 1
      }
    }

    // Check last group
    if (groupSize >= 3) {
      const groupClips = clips.slice(groupStart, groupStart + groupSize)
      suggestions.push({
        id: generateSuggestionId(),
        type: 'CLIP_GROUPING',
        confidence: 'low',
        title: 'Group clips',
        description: `Group ${groupSize} clips: ${groupClips.map(c => c.name).join(', ')}`,
        clipIds: groupClips.map(c => c.id),
        trackIds: [track.id],
        command: null,
        autoApplicable: false,
      })
    }
  }

  return suggestions
}

// ---------------------------------------------------------------------------
// Suggest Track Reordering
// ---------------------------------------------------------------------------
// Suggests moving clips to balance track load.
export function suggestTrackReordering(timeline: TimelineCanvasState): Suggestion[] {
  const suggestions: Suggestion[] = []

  const videoTracks = Object.values(timeline.tracks)
    .filter(t => t.type === 'video' && !t.locked)
    .sort((a, b) => a.clipIds.length - b.clipIds.length)

  if (videoTracks.length < 2) return suggestions

  const mostLoaded = videoTracks[videoTracks.length - 1]
  const leastLoaded = videoTracks[0]

  if (mostLoaded.clipIds.length - leastLoaded.clipIds.length >= 3) {
    const clipsToMove = getTrackClipsSorted(timeline, mostLoaded.id)
      .slice(Math.floor(mostLoaded.clipIds.length * 0.7))

    const commands: TimelineCommand[] = []
    for (const clip of clipsToMove) {
      commands.push(
        new MoveClipCommand(
          {
            clipId: clip.id,
            fromStart: clip.timing.start,
            fromTrackId: clip.trackId,
            toStart: clip.timing.start,
            toTrackId: leastLoaded.id,
          },
          createMetadata('Balance track load', 'system')
        )
      )
    }

    suggestions.push({
      id: generateSuggestionId(),
      type: 'TRACK_REORDERING',
      confidence: 'medium',
      title: 'Balance tracks',
      description: `Move ${clipsToMove.length} clips from "${mostLoaded.name}" to "${leastLoaded.name}"`,
      clipIds: clipsToMove.map(c => c.id),
      trackIds: [mostLoaded.id, leastLoaded.id],
      command: null, // Composite command would be needed
      autoApplicable: false,
    })
  }

  return suggestions
}

// ---------------------------------------------------------------------------
// Suggest Gap Fill
// ---------------------------------------------------------------------------
// Suggests filling gaps with placeholder clips.
export function suggestGapFill(timeline: TimelineCanvasState): Suggestion[] {
  const suggestions: Suggestion[] = []

  const videoTracks = Object.values(timeline.tracks).filter(t => t.type === 'video')

  for (const track of videoTracks) {
    if (track.locked) continue

    const clips = getTrackClipsSorted(timeline, track.id)
    if (clips.length < 2) continue

    for (let i = 1; i < clips.length; i++) {
      const prev = clips[i - 1]
      const curr = clips[i]
      const gap = curr.timing.start - prev.timing.end

      if (gap > 2.0) {
        suggestions.push({
          id: generateSuggestionId(),
          type: 'GAP_FILL',
          confidence: 'low',
          title: 'Fill gap',
          description: `${gap.toFixed(1)}s gap between "${prev.name}" and "${curr.name}"`,
          clipIds: [prev.id, curr.id],
          trackIds: [track.id],
          command: null, // Would need a gap fill command
          autoApplicable: false,
        })
      }
    }
  }

  return suggestions
}

// ---------------------------------------------------------------------------
// Generate All Suggestions
// ---------------------------------------------------------------------------
export function generateSuggestions(timeline: TimelineCanvasState): Suggestion[] {
  return [
    ...suggestTrimOptimization(timeline),
    ...suggestTransitionInsertion(timeline),
    ...suggestClipGrouping(timeline),
    ...suggestTrackReordering(timeline),
    ...suggestGapFill(timeline),
  ].sort((a, b) => {
    const confidenceOrder = { high: 0, medium: 1, low: 2 }
    return confidenceOrder[a.confidence] - confidenceOrder[b.confidence]
  })
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
