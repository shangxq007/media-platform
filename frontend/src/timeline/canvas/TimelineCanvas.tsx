// =============================================================================
// TimelineCanvas Component
// =============================================================================
// Main timeline canvas component.
// Renders multi-track timeline with ruler, track views, and zoom controls.
// Wraps content with DndContext for drag and drop support.
// =============================================================================

import { useRef, useCallback, useEffect, useState } from 'react'
import { DndContext, PointerSensor, useSensor, useSensors, DragOverlay } from '@dnd-kit/core'
import { useTimelineStore } from '../store/timelineStore'
import { TrackView } from '../components/TrackView'
import { TimelineRuler } from '../components/TimelineRuler'
import { TimelineIntelligencePanel } from '../components/TimelineIntelligencePanel'
import { useClipDrag } from '../interaction/useClipDrag'
import { useClipTrim } from '../interaction/useClipTrim'
import { useTimelineKeyboard } from '../hooks/useTimelineKeyboard'
import type { ZoomLevel } from '../store/timelineStore'
import type { TimelineClip } from '../model/timeline'
import { CLIP_TYPE_COLORS } from '../model/timeline'

// Pixels per second at 1x zoom
const BASE_PX_PER_SECOND = 40

interface TimelineCanvasProps {
  height?: number
}

export function TimelineCanvas({ height = 400 }: TimelineCanvasProps) {
  const {
    timeline,
    zoomLevel,
    cycleZoom,
    clearSelection,
    getOrderedTracks,
    commandHistory,
    canUndo,
    canRedo,
  } = useTimelineStore()

  const [showIntelligence, setShowIntelligence] = useState(false)

  const containerRef = useRef<HTMLDivElement>(null)
  const tracks = getOrderedTracks()
  const pixelsPerSecond = BASE_PX_PER_SECOND * zoomLevel
  const rulerWidth = Math.max(timeline.duration * pixelsPerSecond, 800)

  // Keyboard shortcuts
  useTimelineKeyboard()

  // Drag and drop
  const { dragState, handlers: dragHandlers } = useClipDrag(pixelsPerSecond)

  // Trim
  const { trimState, startTrim, updateTrim, endTrim } = useClipTrim(pixelsPerSecond)

  // Configure sensors
  const sensors = useSensors(
    useSensor(PointerSensor, {
      activationConstraint: {
        distance: 5,
      },
    })
  )

  // Handle mouse move for trimming
  const handleMouseMove = useCallback(
    (e: MouseEvent) => {
      if (trimState.isTrimming) {
        updateTrim(e.clientX)
      }
    },
    [trimState.isTrimming, updateTrim]
  )

  // Handle mouse up for trimming
  const handleMouseUp = useCallback(() => {
    if (trimState.isTrimming) {
      endTrim()
    }
  }, [trimState.isTrimming, endTrim])

  // Add global mouse event listeners for trim
  useEffect(() => {
    if (trimState.isTrimming) {
      window.addEventListener('mousemove', handleMouseMove)
      window.addEventListener('mouseup', handleMouseUp)
      return () => {
        window.removeEventListener('mousemove', handleMouseMove)
        window.removeEventListener('mouseup', handleMouseUp)
      }
    }
  }, [trimState.isTrimming, handleMouseMove, handleMouseUp])

  const handleCanvasClick = (e: React.MouseEvent) => {
    if (e.target === e.currentTarget) {
      clearSelection()
    }
  }

  // Get clip being dragged for overlay
  const activeClip = dragState.clipId ? timeline.clips[dragState.clipId] : null

  return (
    <DndContext
      sensors={sensors}
      onDragStart={dragHandlers.onDragStart}
      onDragMove={dragHandlers.onDragMove}
      onDragEnd={dragHandlers.onDragEnd}
      onDragCancel={dragHandlers.onDragCancel}
    >
      <div className="flex gap-4">
        {/* Main Timeline */}
        <div
          className="flex-1 rounded-lg border border-gray-800 bg-gray-950 overflow-hidden"
          style={{ height: `${height}px` }}
        >
          {/* Toolbar */}
          <div className="flex items-center justify-between px-3 py-2 border-b border-gray-800 bg-gray-900">
            <div className="flex items-center gap-3">
              <span className="text-xs font-medium text-gray-400">Timeline</span>
              <span className="text-xs text-gray-600">
                {tracks.length} track{tracks.length !== 1 ? 's' : ''} · {Object.keys(timeline.clips).length} clips
              </span>
              {trimState.isTrimming && (
                <span className="text-xs text-yellow-400">Trimming...</span>
              )}
            </div>
            <div className="flex items-center gap-2">
              {/* Undo/Redo buttons */}
              <UndoRedoButtons canUndo={canUndo()} canRedo={canRedo()} />
              <ZoomButton zoom={zoomLevel} onCycle={cycleZoom} />
              <IntelligenceToggle
                isOpen={showIntelligence}
                onToggle={() => setShowIntelligence(!showIntelligence)}
              />
            </div>
          </div>

          {/* Timeline area */}
          <div className="flex overflow-hidden" style={{ height: `${height - 40}px` }}>
            {/* Track headers column (fixed) */}
            <div className="w-40 flex-shrink-0 bg-gray-900 border-r border-gray-800 overflow-y-auto">
              {/* Ruler placeholder */}
              <div className="h-8 border-b border-gray-800" />

              {/* Track headers */}
              {tracks.map((track) => {
                const layout = timeline.layouts[track.id]
                return (
                  <div
                    key={track.id}
                    className="flex items-center gap-2 px-3 border-b border-gray-800"
                    style={{ height: `${track.height}px` }}
                  >
                    <div
                      className="w-3 h-3 rounded-full flex-shrink-0"
                      style={{ backgroundColor: layout?.color ?? '#6b7280' }}
                    />
                    <div className="min-w-0">
                      <div className="text-xs font-medium text-gray-200 truncate">
                        {track.name}
                      </div>
                      <div className="text-[10px] text-gray-500 uppercase">
                        {track.type}
                      </div>
                    </div>
                  </div>
                )
              })}

              {tracks.length === 0 && (
                <div className="p-4 text-xs text-gray-600 text-center">
                  No tracks
                </div>
              )}
            </div>

            {/* Scrollable timeline body */}
            <div
              ref={containerRef}
              className="flex-1 overflow-auto"
              onClick={handleCanvasClick}
            >
              {/* Ruler */}
              <div className="sticky top-0 z-10">
                <TimelineRuler pixelsPerSecond={pixelsPerSecond} />
              </div>

              {/* Tracks */}
              <div style={{ width: `${rulerWidth}px` }}>
                {tracks.map((track) => {
                  const layout = timeline.layouts[track.id]
                  if (!layout) return null
                  return (
                    <TrackView
                      key={track.id}
                      track={track}
                      layout={layout}
                      pixelsPerSecond={pixelsPerSecond}
                      onTrimStart={startTrim}
                    />
                  )
                })}
              </div>

              {tracks.length === 0 && (
                <div className="flex items-center justify-center h-32 text-gray-600 text-sm">
                  Add tracks to begin editing
                </div>
              )}
            </div>
          </div>

          {/* Status bar */}
          <div className="flex items-center justify-between px-3 py-1 border-t border-gray-800 bg-gray-900 text-[10px] text-gray-600">
            <div className="flex items-center gap-3">
              <span>History: {commandHistory.undoStack.length} undo, {commandHistory.redoStack.length} redo</span>
              <span>Executed: {commandHistory.totalExecuted}</span>
            </div>
            <div className="flex items-center gap-2">
              <span>Ctrl+Z: Undo</span>
              <span>Ctrl+Y: Redo</span>
              <span>Del: Delete</span>
            </div>
          </div>
        </div>

        {/* Intelligence Panel (collapsible) */}
        {showIntelligence && (
          <div className="w-80 flex-shrink-0">
            <TimelineIntelligencePanel />
          </div>
        )}
      </div>

      {/* Drag overlay */}
      <DragOverlay>
        {activeClip && (
          <DragOverlayClip clip={activeClip} pixelsPerSecond={pixelsPerSecond} />
        )}
      </DragOverlay>
    </DndContext>
  )
}

// ---------------------------------------------------------------------------
// Undo/Redo Buttons
// ---------------------------------------------------------------------------

function UndoRedoButtons({
  canUndo,
  canRedo,
}: {
  canUndo: boolean
  canRedo: boolean
}) {
  const { undo, redo } = useTimelineStore()

  return (
    <div className="flex items-center gap-1">
      <button
        type="button"
        onClick={undo}
        disabled={!canUndo}
        className="rounded bg-gray-800 px-2 py-1 text-xs text-gray-300 hover:bg-gray-700 disabled:opacity-30 disabled:cursor-not-allowed transition-colors"
        title="Undo (Ctrl+Z)"
      >
        ↩
      </button>
      <button
        type="button"
        onClick={redo}
        disabled={!canRedo}
        className="rounded bg-gray-800 px-2 py-1 text-xs text-gray-300 hover:bg-gray-700 disabled:opacity-30 disabled:cursor-not-allowed transition-colors"
        title="Redo (Ctrl+Y)"
      >
        ↪
      </button>
    </div>
  )
}

// ---------------------------------------------------------------------------
// Intelligence Toggle
// ---------------------------------------------------------------------------

function IntelligenceToggle({
  isOpen,
  onToggle,
}: {
  isOpen: boolean
  onToggle: () => void
}) {
  return (
    <button
      type="button"
      onClick={onToggle}
      className={`rounded px-2 py-1 text-xs transition-colors ${
        isOpen
          ? 'bg-blue-600 text-white'
          : 'bg-gray-800 text-gray-300 hover:bg-gray-700'
      }`}
      title="Toggle Intelligence Panel"
    >
      AI
    </button>
  )
}

// ---------------------------------------------------------------------------
// Drag Overlay Clip
// ---------------------------------------------------------------------------

function DragOverlayClip({
  clip,
  pixelsPerSecond,
}: {
  clip: TimelineClip
  pixelsPerSecond: number
}) {
  const width = clip.timing.duration * pixelsPerSecond
  const color = CLIP_TYPE_COLORS[clip.type]

  return (
    <div
      className="rounded px-2 py-1 text-xs font-medium text-white shadow-lg"
      style={{
        width: `${Math.max(width, 40)}px`,
        backgroundColor: color,
        opacity: 0.9,
      }}
    >
      {clip.name}
    </div>
  )
}

// ---------------------------------------------------------------------------
// Zoom Button
// ---------------------------------------------------------------------------

function ZoomButton({ zoom, onCycle }: { zoom: ZoomLevel; onCycle: () => void }) {
  return (
    <button
      type="button"
      onClick={onCycle}
      className="rounded bg-gray-800 px-2 py-1 text-xs font-mono text-gray-300 hover:bg-gray-700 transition-colors"
      title="Click to cycle zoom level"
    >
      {zoom}x
    </button>
  )
}
