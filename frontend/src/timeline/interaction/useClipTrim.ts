// =============================================================================
// useClipTrim Hook
// =============================================================================
// Manages clip edge trimming operations using commands.
// =============================================================================

import { useState, useCallback, useRef } from 'react'
import { useTimelineStore } from '../store/timelineStore'
import { TrimClipCommand } from '../commands/commands/TrimClipCommand'
import { createMetadata } from '../commands/types'

// ---------------------------------------------------------------------------
// Trim State
// ---------------------------------------------------------------------------
export interface TrimState {
  readonly isTrimming: boolean
  readonly clipId: string | null
  readonly side: 'start' | 'end' | null
  readonly startX: number
  readonly currentX: number
  readonly originalTime: number
}

const INITIAL_TRIM_STATE: TrimState = {
  isTrimming: false,
  clipId: null,
  side: null,
  startX: 0,
  currentX: 0,
  originalTime: 0,
}

// ---------------------------------------------------------------------------
// useClipTrim Hook
// ---------------------------------------------------------------------------
export function useClipTrim(pixelsPerSecond: number) {
  const { timeline, executeCommand } = useTimelineStore()
  const [trimState, setTrimState] = useState<TrimState>(INITIAL_TRIM_STATE)
  const trimStartRef = useRef<{ x: number; time: number }>({ x: 0, time: 0 })

  const startTrim = useCallback(
    (clipId: string, side: 'start' | 'end', clientX: number) => {
      const clip = timeline.clips[clipId]
      if (!clip) return

      const time = side === 'start' ? clip.timing.start : clip.timing.end
      trimStartRef.current = { x: clientX, time }

      setTrimState({
        isTrimming: true,
        clipId,
        side,
        startX: clientX,
        currentX: clientX,
        originalTime: time,
      })
    },
    [timeline.clips]
  )

  const updateTrim = useCallback(
    (clientX: number) => {
      if (!trimState.isTrimming || !trimState.clipId || !trimState.side) {
        return
      }

      setTrimState((prev) => ({ ...prev, currentX: clientX }))
    },
    [trimState.isTrimming, trimState.clipId, trimState.side]
  )

  const endTrim = useCallback(() => {
    if (!trimState.isTrimming || !trimState.clipId || !trimState.side) {
      setTrimState(INITIAL_TRIM_STATE)
      return
    }

    const clip = timeline.clips[trimState.clipId]
    if (!clip) {
      setTrimState(INITIAL_TRIM_STATE)
      return
    }

    // Calculate the new time based on mouse position
    const deltaX = trimState.currentX - trimState.startX
    const deltaSeconds = deltaX / pixelsPerSecond
    const newTime = trimState.originalTime + deltaSeconds

    // Create and execute trim command
    const command = new TrimClipCommand(
      {
        clipId: trimState.clipId,
        side: trimState.side,
        fromTime: trimState.originalTime,
        toTime: newTime,
      },
      createMetadata(`Trim clip "${clip.name}" ${trimState.side}`)
    )

    executeCommand(command)
    setTrimState(INITIAL_TRIM_STATE)
  }, [trimState, timeline.clips, pixelsPerSecond, executeCommand])

  const cancelTrim = useCallback(() => {
    setTrimState(INITIAL_TRIM_STATE)
  }, [])

  return {
    trimState,
    startTrim,
    updateTrim,
    endTrim,
    cancelTrim,
  }
}
