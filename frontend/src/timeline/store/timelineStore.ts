// =============================================================================
// Timeline Store
// =============================================================================
// Zustand store for timeline UI state.
// Manages selection, zoom, playhead, and command history.
//
// Rules:
// - Only UI state lives here
// - Timeline data (tracks, clips) comes from domain model
// - No API calls in this store
// - All mutations go through command engine
// =============================================================================

import { create } from 'zustand'
import type { TimelineCanvasState, TimelineClip, TimelineTrack, TrackLayout } from '../model/timeline'
import { createEmptyTimeline } from '../model/timeline'
import { commandEngine, type CommandEngineState } from '../commands/commandEngine'
import type { TimelineCommand } from '../commands/types'

// ---------------------------------------------------------------------------
// Zoom Level
// ---------------------------------------------------------------------------
export type ZoomLevel = 1 | 2 | 4

const ZOOM_LEVELS: ZoomLevel[] = [1, 2, 4]

export function nextZoom(current: ZoomLevel): ZoomLevel {
  const idx = ZOOM_LEVELS.indexOf(current)
  return ZOOM_LEVELS[(idx + 1) % ZOOM_LEVELS.length]
}

// ---------------------------------------------------------------------------
// Timeline UI State
// ---------------------------------------------------------------------------
export interface TimelineUIState {
  // Timeline data
  timeline: TimelineCanvasState

  // Selection
  selectedClipId: string | null
  selectedTrackId: string | null

  // Viewport
  zoomLevel: ZoomLevel
  playheadPosition: number   // seconds
  scrollLeft: number         // pixels

  // Command History
  commandHistory: CommandEngineState
  isReplaying: boolean

  // Actions - Selection
  setSelectedClip: (clipId: string | null) => void
  setSelectedTrack: (trackId: string | null) => void
  clearSelection: () => void

  // Actions - Viewport
  setZoom: (zoom: ZoomLevel) => void
  cycleZoom: () => void
  setPlayhead: (position: number) => void
  setScrollLeft: (scroll: number) => void

  // Actions - Timeline Data (via command engine)
  setTimeline: (timeline: TimelineCanvasState) => void
  executeCommand: (command: TimelineCommand) => boolean
  undo: () => boolean
  redo: () => boolean
  canUndo: () => boolean
  canRedo: () => boolean

  // Actions - Direct mutations (for initialization only)
  addTrack: (track: TimelineTrack, layout: TrackLayout) => void

  // Selectors (computed)
  getSelectedClip: () => TimelineClip | null
  getSelectedTrack: () => TimelineTrack | null
  getTrackClips: (trackId: string) => TimelineClip[]
  getOrderedTracks: () => TimelineTrack[]
}

// ---------------------------------------------------------------------------
// Store Implementation
// ---------------------------------------------------------------------------
export const useTimelineStore = create<TimelineUIState>((set, get) => ({
  // Initial state
  timeline: createEmptyTimeline(),
  selectedClipId: null,
  selectedTrackId: null,
  zoomLevel: 1,
  playheadPosition: 0,
  scrollLeft: 0,
  commandHistory: commandEngine.getState(),
  isReplaying: false,

  // Selection actions
  setSelectedClip: (clipId) => set({ selectedClipId: clipId, selectedTrackId: null }),
  setSelectedTrack: (trackId) => set({ selectedTrackId: trackId, selectedClipId: null }),
  clearSelection: () => set({ selectedClipId: null, selectedTrackId: null }),

  // Viewport actions
  setZoom: (zoom) => set({ zoomLevel: zoom }),
  cycleZoom: () => set((state) => ({ zoomLevel: nextZoom(state.zoomLevel) })),
  setPlayhead: (position) => set({ playheadPosition: Math.max(0, position) }),
  setScrollLeft: (scroll) => set({ scrollLeft: scroll }),

  // Set timeline directly (for initialization)
  setTimeline: (timeline) => set({ timeline, commandHistory: commandEngine.getState() }),

  // Execute command through engine
  executeCommand: (command) => {
    const state = get()
    const result = commandEngine.execute(command, state.timeline)

    if (result.success) {
      set({
        timeline: result.timeline,
        commandHistory: commandEngine.getState(),
      })
      return true
    }
    return false
  },

  // Undo
  undo: () => {
    const state = get()
    const result = commandEngine.undo(state.timeline)

    if (result.success) {
      set({
        timeline: result.timeline,
        commandHistory: commandEngine.getState(),
      })
      return true
    }
    return false
  },

  // Redo
  redo: () => {
    const state = get()
    const result = commandEngine.redo(state.timeline)

    if (result.success) {
      set({
        timeline: result.timeline,
        commandHistory: commandEngine.getState(),
      })
      return true
    }
    return false
  },

  canUndo: () => commandEngine.canUndo(),
  canRedo: () => commandEngine.canRedo(),

  // Add track (direct mutation for initialization)
  addTrack: (track, layout) => set((state) => ({
    timeline: {
      ...state.timeline,
      tracks: { ...state.timeline.tracks, [track.id]: track },
      trackOrder: [...state.timeline.trackOrder, track.id],
      layouts: { ...state.timeline.layouts, [layout.trackId]: layout },
    },
  })),

  // Selectors
  getSelectedClip: () => {
    const state = get()
    if (!state.selectedClipId) return null
    return state.timeline.clips[state.selectedClipId] ?? null
  },
  getSelectedTrack: () => {
    const state = get()
    if (!state.selectedTrackId) return null
    return state.timeline.tracks[state.selectedTrackId] ?? null
  },
  getTrackClips: (trackId) => {
    const state = get()
    const track = state.timeline.tracks[trackId]
    if (!track) return []
    return track.clipIds
      .map((id) => state.timeline.clips[id])
      .filter((c): c is TimelineClip => c != null)
  },
  getOrderedTracks: () => {
    const state = get()
    return state.timeline.trackOrder
      .map((id) => state.timeline.tracks[id])
      .filter((t): t is TimelineTrack => t != null)
  },
}))
