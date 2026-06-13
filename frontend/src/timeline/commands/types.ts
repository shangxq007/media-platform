// =============================================================================
// Timeline Command Types
// =============================================================================
// Defines the command pattern interface for all timeline mutations.
// Every mutation must be expressed as a command with execute/undo semantics.
//
// Rules:
// - Commands are immutable once created
// - execute() applies the mutation and returns new state
// - undo() reverts the mutation and returns previous state
// - metadata tracks provenance for debugging and replay
// =============================================================================

import type { TimelineCanvasState, TimelineClip } from '../model/timeline'

// ---------------------------------------------------------------------------
// Command Metadata
// ---------------------------------------------------------------------------
export interface CommandMetadata {
  readonly timestamp: number
  readonly userAction: string      // human-readable action description
  readonly source: 'ui' | 'api' | 'replay' | 'system'
  readonly userId?: string
  readonly sessionId?: string
}

// ---------------------------------------------------------------------------
// Command Result
// ---------------------------------------------------------------------------
export interface CommandResult {
  readonly success: boolean
  readonly timeline: TimelineCanvasState
  readonly errors: readonly string[]
}

// ---------------------------------------------------------------------------
// Base Command Interface
// ---------------------------------------------------------------------------
export interface TimelineCommand {
  readonly id: string
  readonly type: CommandType
  readonly metadata: CommandMetadata

  /**
   * Execute the command, transforming the timeline state.
   * Returns the new timeline state on success.
   */
  execute(timeline: TimelineCanvasState): CommandResult

  /**
   * Undo the command, reverting to the previous state.
   * Returns the timeline state before this command was executed.
   */
  undo(timeline: TimelineCanvasState): CommandResult
}

// ---------------------------------------------------------------------------
// Command Types
// ---------------------------------------------------------------------------
export type CommandType =
  | 'MOVE_CLIP'
  | 'TRIM_CLIP'
  | 'ADD_CLIP'
  | 'DELETE_CLIP'
  | 'ADD_TRACK'
  | 'DELETE_TRACK'
  | 'COMPOSITE'      // group of commands

// ---------------------------------------------------------------------------
// Move Clip Command
// ---------------------------------------------------------------------------
export interface MoveClipPayload {
  readonly clipId: string
  readonly fromStart: number
  readonly fromTrackId: string
  readonly toStart: number
  readonly toTrackId: string
}

// ---------------------------------------------------------------------------
// Trim Clip Command
// ---------------------------------------------------------------------------
export interface TrimClipPayload {
  readonly clipId: string
  readonly side: 'start' | 'end'
  readonly fromTime: number
  readonly toTime: number
}

// ---------------------------------------------------------------------------
// Add Clip Command
// ---------------------------------------------------------------------------
export interface AddClipPayload {
  readonly clip: TimelineClip
}

// ---------------------------------------------------------------------------
// Delete Clip Command
// ---------------------------------------------------------------------------
export interface DeleteClipPayload {
  readonly clip: TimelineClip
  readonly trackId: string
}

// ---------------------------------------------------------------------------
// Composite Command
// ---------------------------------------------------------------------------
export interface CompositePayload {
  readonly commands: readonly TimelineCommand[]
  readonly description: string
}

// ---------------------------------------------------------------------------
// Command Factory Helper
// ---------------------------------------------------------------------------
let commandCounter = 0

export function generateCommandId(): string {
  return `cmd-${Date.now()}-${++commandCounter}`
}

export function createMetadata(
  userAction: string,
  source: CommandMetadata['source'] = 'ui'
): CommandMetadata {
  return {
    timestamp: Date.now(),
    userAction,
    source,
  }
}
