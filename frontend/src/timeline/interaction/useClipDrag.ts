// =============================================================================
// useClipDrag Hook
// =============================================================================
// Manages clip drag and drop operations using commands.
// =============================================================================

import { useState, useCallback, useRef } from 'react'
import type { DragStartEvent, DragEndEvent, DragMoveEvent } from '@dnd-kit/core'
import { useTimelineStore } from '../store/timelineStore'
import { MoveClipCommand } from '../commands/commands/MoveClipCommand'
import { createMetadata } from '../commands/types'

// ---------------------------------------------------------------------------
// Drag State
// ---------------------------------------------------------------------------
export interface DragState {
  readonly isDragging: boolean
  readonly clipId: string | null
  readonly startX: number
  readonly currentX: number
  readonly startTrackId: string | null
}

const INITIAL_DRAG_STATE: DragState = {
  isDragging: false,
  clipId: null,
  startX: 0,
  currentX: 0,
  startTrackId: null,
}

// ---------------------------------------------------------------------------
// useClipDrag Hook
// ---------------------------------------------------------------------------
export function useClipDrag(pixelsPerSecond: number) {
  const { timeline, executeCommand } = useTimelineStore()
  const [dragState, setDragState] = useState<DragState>(INITIAL_DRAG_STATE)
  const dragStartRef = useRef<{ x: number; time: number; trackId: string }>({ x: 0, time: 0, trackId: '' })

  const handleDragStart = useCallback(
    (event: DragStartEvent) => {
      const clipId = event.active.id as string
      const clip = timeline.clips[clipId]
      if (!clip) return

      dragStartRef.current = {
        x: event.active.rect?.current?.translated?.left ?? 0,
        time: clip.timing.start,
        trackId: clip.trackId,
      }

      setDragState({
        isDragging: true,
        clipId,
        startX: dragStartRef.current.x,
        currentX: dragStartRef.current.x,
        startTrackId: clip.trackId,
      })
    },
    [timeline.clips]
  )

  const handleDragMove = useCallback(
    (event: DragMoveEvent) => {
      if (!dragState.isDragging || !dragState.clipId) return

      const deltaX = event.delta.x
      const currentX = dragStartRef.current.x + deltaX

      setDragState((prev) => ({ ...prev, currentX }))
    },
    [dragState.isDragging, dragState.clipId]
  )

  const handleDragEnd = useCallback(
    (event: DragEndEvent) => {
      if (!dragState.isDragging || !dragState.clipId) {
        setDragState(INITIAL_DRAG_STATE)
        return
      }

      const deltaX = event.delta.x
      const deltaSeconds = deltaX / pixelsPerSecond
      const clip = timeline.clips[dragState.clipId]
      if (!clip) {
        setDragState(INITIAL_DRAG_STATE)
        return
      }

      const newStart = Math.max(0, clip.timing.start + deltaSeconds)

      // Determine target track
      const overId = event.over?.id as string | undefined
      const targetTrackId = overId && timeline.tracks[overId]
        ? overId
        : clip.trackId

      // Create and execute move command
      const command = new MoveClipCommand(
        {
          clipId: dragState.clipId,
          fromStart: dragStartRef.current.time,
          fromTrackId: dragStartRef.current.trackId,
          toStart: newStart,
          toTrackId: targetTrackId,
        },
        createMetadata(`Move clip "${clip.name}"`)
      )

      executeCommand(command)
      setDragState(INITIAL_DRAG_STATE)
    },
    [dragState, timeline, pixelsPerSecond, executeCommand]
  )

  const handleDragCancel = useCallback(() => {
    setDragState(INITIAL_DRAG_STATE)
  }, [])

  return {
    dragState,
    handlers: {
      onDragStart: handleDragStart,
      onDragMove: handleDragMove,
      onDragEnd: handleDragEnd,
      onDragCancel: handleDragCancel,
    },
  }
}
