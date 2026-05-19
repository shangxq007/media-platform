import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { Track } from '@/types'

export interface HistoryState {
  tracks: Track[]
  clips: any[]
}

export const useHistoryStore = defineStore('history', () => {
  const undoStack = ref<HistoryState[]>([])
  const redoStack = ref<HistoryState[]>([])
  const maxHistorySize = 50

  function saveState(timelineStore: any) {
    const state: HistoryState = {
      tracks: JSON.parse(JSON.stringify(timelineStore.state.tracks)),
      clips: JSON.parse(JSON.stringify(timelineStore.clips))
    }
    undoStack.value.push(state)
    if (undoStack.value.length > maxHistorySize) {
      undoStack.value.shift()
    }
    redoStack.value = []
  }

  function canUndo(): boolean {
    return undoStack.value.length > 0
  }

  function canRedo(): boolean {
    return redoStack.value.length > 0
  }

  function undo(timelineStore: any) {
    if (!canUndo()) return
    redoStack.value.push({
      tracks: JSON.parse(JSON.stringify(timelineStore.state.tracks)),
      clips: JSON.parse(JSON.stringify(timelineStore.clips))
    })
    const state = undoStack.value.pop()!
    timelineStore.state.tracks = state.tracks
    timelineStore.clips = state.clips
  }

  function redo(timelineStore: any) {
    if (!canRedo()) return
    const state = redoStack.value.pop()!
    undoStack.value.push({
      tracks: JSON.parse(JSON.stringify(timelineStore.state.tracks)),
      clips: JSON.parse(JSON.stringify(timelineStore.clips))
    })
    timelineStore.state.tracks = state.tracks
    timelineStore.clips = state.clips
  }

  return {
    undoStack, redoStack,
    saveState, canUndo, canRedo, undo, redo
  }
})