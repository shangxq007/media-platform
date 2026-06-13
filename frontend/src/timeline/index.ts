// =============================================================================
// Timeline Module
// =============================================================================
// Timeline Canvas Foundation Layer for the media platform editor.
//
// Architecture:
// - model/        → Domain models (TimelineCanvasState, Track, Clip, ClipTiming)
// - store/        → Zustand UI state (selection, zoom, playhead, history)
// - canvas/       → Main TimelineCanvas component
// - components/   → TrackView, ClipBlock, TimelineRuler, IntelligencePanel
// - mappers/      → Timeline → Render JSON mapping
// - engine/       → Mutation engine, snap system
// - commands/     → Command pattern (execute, undo, redo)
// - interaction/  → Drag & drop, trim hooks
// - intelligence/ → Analysis, auto-layout, conflict resolution, suggestions
// - hooks/        → Initialization, keyboard, utility hooks
// =============================================================================

export * from './model'
export * from './store/timelineStore'
export * from './canvas/TimelineCanvas'
export * from './mappers/timelineToRender'
export * from './engine'
export * from './commands'
export * from './interaction'
export * from './intelligence'
