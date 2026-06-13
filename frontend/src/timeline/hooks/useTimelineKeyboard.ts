// =============================================================================
// useTimelineKeyboard Hook
// =============================================================================
// Handles keyboard shortcuts for timeline operations.
// - Ctrl+Z / Cmd+Z → Undo
// - Ctrl+Y / Cmd+Y → Redo
// - Ctrl+Shift+Z / Cmd+Shift+Z → Redo (alternative)
// - Delete / Backspace → Delete selected clip
// =============================================================================

import { useEffect, useCallback } from 'react'
import { useTimelineStore } from '../store/timelineStore'
import { DeleteClipCommand } from '../commands/commands/DeleteClipCommand'
import { createMetadata } from '../commands/types'

export function useTimelineKeyboard() {
  const {
    undo,
    redo,
    canUndo,
    canRedo,
    selectedClipId,
    timeline,
    executeCommand,
    setSelectedClip,
  } = useTimelineStore()

  const handleKeyDown = useCallback(
    (e: KeyboardEvent) => {
      const isMod = e.metaKey || e.ctrlKey

      // Undo: Ctrl+Z / Cmd+Z
      if (isMod && e.key === 'z' && !e.shiftKey) {
        e.preventDefault()
        if (canUndo()) {
          undo()
        }
        return
      }

      // Redo: Ctrl+Y / Cmd+Y or Ctrl+Shift+Z / Cmd+Shift+Z
      if ((isMod && e.key === 'y') || (isMod && e.key === 'z' && e.shiftKey)) {
        e.preventDefault()
        if (canRedo()) {
          redo()
        }
        return
      }

      // Delete selected clip
      if ((e.key === 'Delete' || e.key === 'Backspace') && selectedClipId) {
        e.preventDefault()
        const clip = timeline.clips[selectedClipId]
        if (clip) {
          const command = new DeleteClipCommand(
            { clip, trackId: clip.trackId },
            createMetadata(`Delete clip "${clip.name}"`)
          )
          executeCommand(command)
          setSelectedClip(null)
        }
        return
      }
    },
    [undo, redo, canUndo, canRedo, selectedClipId, timeline.clips, executeCommand, setSelectedClip]
  )

  useEffect(() => {
    window.addEventListener('keydown', handleKeyDown)
    return () => window.removeEventListener('keydown', handleKeyDown)
  }, [handleKeyDown])
}
