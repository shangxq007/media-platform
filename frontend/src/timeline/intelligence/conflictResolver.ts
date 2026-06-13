// =============================================================================
// Conflict Resolution Engine
// =============================================================================
// Detects and resolves timeline conflicts using various strategies.
// All resolutions generate commands for undo/redo support.
// =============================================================================

import type { TimelineCanvasState, TimelineClip } from '../model/timeline'
import type { TimelineCommand } from '../commands/types'
import { createMetadata } from '../commands/types'
import { MoveClipCommand } from '../commands/commands/MoveClipCommand'
import { TrimClipCommand } from '../commands/commands/TrimClipCommand'

// ---------------------------------------------------------------------------
// Overlap Resolution Strategy
// ---------------------------------------------------------------------------
export type OverlapStrategy = 'SHIFT_RIGHT' | 'TRIM_CLIP' | 'MERGE_TRACK'

// ---------------------------------------------------------------------------
// Conflict Types
// ---------------------------------------------------------------------------
export interface TimelineConflict {
  readonly id: string
  readonly type: 'OVERLAP' | 'GAP' | 'BOUNDARY'
  readonly clipA: TimelineClip
  readonly clipB: TimelineClip
  readonly trackId: string
  readonly overlapDuration: number
}

export interface ResolutionResult {
  readonly commands: readonly TimelineCommand[]
  readonly conflictsResolved: number
  readonly strategy: OverlapStrategy
  readonly description: string
}

// ---------------------------------------------------------------------------
// Conflict ID Generator
// ---------------------------------------------------------------------------
let conflictCounter = 0
function generateConflictId(): string {
  return `conflict-${Date.now()}-${++conflictCounter}`
}

// ---------------------------------------------------------------------------
// Detect All Conflicts
// ---------------------------------------------------------------------------
export function detectConflicts(timeline: TimelineCanvasState): TimelineConflict[] {
  const conflicts: TimelineConflict[] = []

  for (const track of Object.values(timeline.tracks)) {
    const clips = getTrackClipsSorted(timeline, track.id)

    for (let i = 1; i < clips.length; i++) {
      const prev = clips[i - 1]
      const curr = clips[i]

      if (curr.timing.start < prev.timing.end) {
        conflicts.push({
          id: generateConflictId(),
          type: 'OVERLAP',
          clipA: prev,
          clipB: curr,
          trackId: track.id,
          overlapDuration: prev.timing.end - curr.timing.start,
        })
      }
    }
  }

  return conflicts
}

// ---------------------------------------------------------------------------
// Resolve with Shift Right Strategy
// ---------------------------------------------------------------------------
// Moves the second clip to start after the first clip ends.
export function resolveWithShiftRight(
  timeline: TimelineCanvasState,
  conflict: TimelineConflict
): ResolutionResult {
  const commands: TimelineCommand[] = []
  const metadata = createMetadata(
    `Resolve overlap: shift "${conflict.clipB.name}" right`,
    'system'
  )

  const newStart = conflict.clipA.timing.end

  const command = new MoveClipCommand(
    {
      clipId: conflict.clipB.id,
      fromStart: conflict.clipB.timing.start,
      fromTrackId: conflict.clipB.trackId,
      toStart: newStart,
      toTrackId: conflict.clipB.trackId,
    },
    metadata
  )
  commands.push(command)

  return {
    commands,
    conflictsResolved: 1,
    strategy: 'SHIFT_RIGHT',
    description: `Shifted "${conflict.clipB.name}" to start at ${newStart.toFixed(2)}s`,
  }
}

// ---------------------------------------------------------------------------
// Resolve with Trim Strategy
// ---------------------------------------------------------------------------
// Trims the end of the first clip to eliminate overlap.
export function resolveWithTrim(
  timeline: TimelineCanvasState,
  conflict: TimelineConflict
): ResolutionResult {
  const commands: TimelineCommand[] = []
  const metadata = createMetadata(
    `Resolve overlap: trim "${conflict.clipA.name}" end`,
    'system'
  )

  const command = new TrimClipCommand(
    {
      clipId: conflict.clipA.id,
      side: 'end',
      fromTime: conflict.clipA.timing.end,
      toTime: conflict.clipB.timing.start,
    },
    metadata
  )
  commands.push(command)

  return {
    commands,
    conflictsResolved: 1,
    strategy: 'TRIM_CLIP',
    description: `Trimmed "${conflict.clipA.name}" to end at ${conflict.clipB.timing.start.toFixed(2)}s`,
  }
}

// ---------------------------------------------------------------------------
// Resolve All Conflicts
// ---------------------------------------------------------------------------
export function resolveTimelineConflicts(
  timeline: TimelineCanvasState,
  strategy: OverlapStrategy = 'SHIFT_RIGHT'
): ResolutionResult {
  const conflicts = detectConflicts(timeline)

  if (conflicts.length === 0) {
    return {
      commands: [],
      conflictsResolved: 0,
      strategy,
      description: 'No conflicts to resolve',
    }
  }

  const allCommands: TimelineCommand[] = []

  // Apply strategy to each conflict
  // We apply them in order to avoid cascading issues
  let currentTimeline = timeline

  for (const conflict of conflicts) {
    let result: ResolutionResult

    switch (strategy) {
      case 'SHIFT_RIGHT':
        result = resolveWithShiftRight(currentTimeline, conflict)
        break
      case 'TRIM_CLIP':
        result = resolveWithTrim(currentTimeline, conflict)
        break
      case 'MERGE_TRACK':
        // Merge track strategy: move all clips from one track to another
        // For now, fall back to shift right
        result = resolveWithShiftRight(currentTimeline, conflict)
        break
      default:
        result = resolveWithShiftRight(currentTimeline, conflict)
    }

    allCommands.push(...result.commands)

    // Apply commands to get updated timeline for next iteration
    for (const cmd of result.commands) {
      const cmdResult = cmd.execute(currentTimeline)
      if (cmdResult.success) {
        currentTimeline = cmdResult.timeline
      }
    }
  }

  return {
    commands: allCommands,
    conflictsResolved: conflicts.length,
    strategy,
    description: `Resolved ${conflicts.length} conflicts using ${strategy} strategy`,
  }
}

// ---------------------------------------------------------------------------
// Resolve Specific Conflict
// ---------------------------------------------------------------------------
export function resolveConflict(
  timeline: TimelineCanvasState,
  conflictId: string,
  strategy: OverlapStrategy
): ResolutionResult {
  const conflicts = detectConflicts(timeline)
  const conflict = conflicts.find(c => c.id === conflictId)

  if (!conflict) {
    return {
      commands: [],
      conflictsResolved: 0,
      strategy,
      description: `Conflict "${conflictId}" not found`,
    }
  }

  switch (strategy) {
    case 'SHIFT_RIGHT':
      return resolveWithShiftRight(timeline, conflict)
    case 'TRIM_CLIP':
      return resolveWithTrim(timeline, conflict)
    case 'MERGE_TRACK':
      return resolveWithShiftRight(timeline, conflict)
    default:
      return resolveWithShiftRight(timeline, conflict)
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
