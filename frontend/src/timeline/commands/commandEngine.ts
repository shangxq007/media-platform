// =============================================================================
// Timeline Command Engine
// =============================================================================
// Central engine for executing, undoing, and redoing timeline commands.
// All timeline mutations MUST go through this engine.
//
// Features:
// - Execute commands with validation
// - Undo/redo with history stack
// - Replay commands from history
// - Configurable max history size
// =============================================================================

import type {
  TimelineCommand,
  CommandResult,
  CommandType,
} from './types'
import type { TimelineCanvasState } from '../model/timeline'

// ---------------------------------------------------------------------------
// History Entry
// ---------------------------------------------------------------------------
export interface HistoryEntry {
  readonly command: TimelineCommand
  readonly result: CommandResult
  readonly executedAt: number
}

// ---------------------------------------------------------------------------
// Command Engine State
// ---------------------------------------------------------------------------
export interface CommandEngineState {
  readonly undoStack: readonly HistoryEntry[]
  readonly redoStack: readonly HistoryEntry[]
  readonly lastCommand: HistoryEntry | null
  readonly isReplaying: boolean
  readonly totalExecuted: number
}

// ---------------------------------------------------------------------------
// Command Engine Configuration
// ---------------------------------------------------------------------------
export interface CommandEngineConfig {
  readonly maxHistorySize: number
  readonly enableLogging: boolean
}

const DEFAULT_CONFIG: CommandEngineConfig = {
  maxHistorySize: 100,
  enableLogging: false,
}

// ---------------------------------------------------------------------------
// Command Engine
// ---------------------------------------------------------------------------
export class CommandEngine {
  private undoStack: HistoryEntry[] = []
  private redoStack: HistoryEntry[] = []
  private lastCommand: HistoryEntry | null = null
  private isReplaying = false
  private totalExecuted = 0
  private config: CommandEngineConfig

  constructor(config: Partial<CommandEngineConfig> = {}) {
    this.config = { ...DEFAULT_CONFIG, ...config }
  }

  // -------------------------------------------------------------------------
  // Execute
  // -------------------------------------------------------------------------
  execute(
    command: TimelineCommand,
    currentTimeline: TimelineCanvasState
  ): CommandResult {
    if (this.isReplaying) {
      return { success: false, timeline: currentTimeline, errors: ['Cannot execute during replay'] }
    }

    const result = command.execute(currentTimeline)

    if (!result.success) {
      if (this.config.enableLogging) {
        console.warn(`[CommandEngine] Command ${command.type} failed:`, result.errors)
      }
      return result
    }

    // Record in history
    const entry: HistoryEntry = {
      command,
      result,
      executedAt: Date.now(),
    }

    this.undoStack.push(entry)

    // Trim history if needed
    if (this.undoStack.length > this.config.maxHistorySize) {
      this.undoStack = this.undoStack.slice(-this.config.maxHistorySize)
    }

    // Clear redo stack on new action
    this.redoStack = []
    this.lastCommand = entry
    this.totalExecuted++

    if (this.config.enableLogging) {
      console.log(`[CommandEngine] Executed ${command.type}: ${command.metadata.userAction}`)
    }

    return result
  }

  // -------------------------------------------------------------------------
  // Undo
  // -------------------------------------------------------------------------
  undo(currentTimeline: TimelineCanvasState): CommandResult {
    if (this.undoStack.length === 0) {
      return { success: false, timeline: currentTimeline, errors: ['Nothing to undo'] }
    }

    const entry = this.undoStack.pop()!
    const result = entry.command.undo(currentTimeline)

    if (!result.success) {
      // Restore entry if undo failed
      this.undoStack.push(entry)
      return result
    }

    this.redoStack.push(entry)
    this.lastCommand = this.undoStack.length > 0 ? this.undoStack[this.undoStack.length - 1] : null

    if (this.config.enableLogging) {
      console.log(`[CommandEngine] Undid ${entry.command.type}: ${entry.command.metadata.userAction}`)
    }

    return result
  }

  // -------------------------------------------------------------------------
  // Redo
  // -------------------------------------------------------------------------
  redo(currentTimeline: TimelineCanvasState): CommandResult {
    if (this.redoStack.length === 0) {
      return { success: false, timeline: currentTimeline, errors: ['Nothing to redo'] }
    }

    const entry = this.redoStack.pop()!
    const result = entry.command.execute(currentTimeline)

    if (!result.success) {
      // Restore entry if redo failed
      this.redoStack.push(entry)
      return result
    }

    this.undoStack.push(entry)
    this.lastCommand = entry

    if (this.config.enableLogging) {
      console.log(`[CommandEngine] Redid ${entry.command.type}: ${entry.command.metadata.userAction}`)
    }

    return result
  }

  // -------------------------------------------------------------------------
  // Replay
  // -------------------------------------------------------------------------
  replay(
    commands: readonly TimelineCommand[],
    initialTimeline: TimelineCanvasState
  ): { timeline: TimelineCanvasState; errors: string[] } {
    this.isReplaying = true
    let currentTimeline = initialTimeline
    const allErrors: string[] = []

    for (const command of commands) {
      const result = command.execute(currentTimeline)
      if (result.success) {
        currentTimeline = result.timeline
      } else {
        allErrors.push(...result.errors)
        if (this.config.enableLogging) {
          console.warn(`[CommandEngine] Replay failed at ${command.type}:`, result.errors)
        }
        break
      }
    }

    this.isReplaying = false
    return { timeline: currentTimeline, errors: allErrors }
  }

  // -------------------------------------------------------------------------
  // Getters
  // -------------------------------------------------------------------------
  getState(): CommandEngineState {
    return {
      undoStack: [...this.undoStack],
      redoStack: [...this.redoStack],
      lastCommand: this.lastCommand,
      isReplaying: this.isReplaying,
      totalExecuted: this.totalExecuted,
    }
  }

  canUndo(): boolean {
    return this.undoStack.length > 0
  }

  canRedo(): boolean {
    return this.redoStack.length > 0
  }

  getHistory(): readonly HistoryEntry[] {
    return [...this.undoStack]
  }

  getRedoStack(): readonly HistoryEntry[] {
    return [...this.redoStack]
  }

  // -------------------------------------------------------------------------
  // Clear
  // -------------------------------------------------------------------------
  clear(): void {
    this.undoStack = []
    this.redoStack = []
    this.lastCommand = null
    this.totalExecuted = 0
  }
}

// ---------------------------------------------------------------------------
// Singleton Instance
// ---------------------------------------------------------------------------
export const commandEngine = new CommandEngine({ enableLogging: true })
