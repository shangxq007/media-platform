import { describe, it, expect, beforeEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useHistoryStore } from '@/stores/history'
import { useTimelineStore } from '@/stores/timeline'

describe('HistoryStore', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('starts with empty stacks', () => {
    const history = useHistoryStore()
    expect(history.canUndo()).toBe(false)
    expect(history.canRedo()).toBe(false)
  })

  it('saves state and can undo', () => {
    const history = useHistoryStore()
    const timeline = useTimelineStore()

    timeline.addTrack('Video 1', 'video')
    history.saveState(timeline)

    expect(history.canUndo()).toBe(true)
    expect(history.canRedo()).toBe(false)
  })

  it('undo restores previous state', () => {
    const history = useHistoryStore()
    const timeline = useTimelineStore()

    timeline.addTrack('Video 1', 'video')
    history.saveState(timeline)

    timeline.addTrack('Audio 1', 'audio')
    expect(timeline.state.tracks).toHaveLength(2)

    history.undo(timeline)
    expect(timeline.state.tracks).toHaveLength(1)
    expect(history.canRedo()).toBe(true)
  })

  it('redo restores undone state', () => {
    const history = useHistoryStore()
    const timeline = useTimelineStore()

    timeline.addTrack('Video 1', 'video')
    history.saveState(timeline)
    timeline.addTrack('Audio 1', 'audio')

    history.undo(timeline)
    expect(timeline.state.tracks).toHaveLength(1)

    history.redo(timeline)
    expect(timeline.state.tracks).toHaveLength(2)
  })

  it('clears redo stack on new save', () => {
    const history = useHistoryStore()
    const timeline = useTimelineStore()

    timeline.addTrack('Video 1', 'video')
    history.saveState(timeline)
    timeline.addTrack('Audio 1', 'audio')
    history.saveState(timeline)

    history.undo(timeline)
    expect(history.canRedo()).toBe(true)

    timeline.addTrack('Text 1', 'text')
    history.saveState(timeline)
    expect(history.canRedo()).toBe(false)
  })

  it('does nothing when undo with empty stack', () => {
    const history = useHistoryStore()
    const timeline = useTimelineStore()

    timeline.addTrack('Video 1', 'video')
    history.undo(timeline)
    expect(timeline.state.tracks).toHaveLength(1)
  })

  it('does nothing when redo with empty stack', () => {
    const history = useHistoryStore()
    const timeline = useTimelineStore()

    timeline.addTrack('Video 1', 'video')
    history.redo(timeline)
    expect(timeline.state.tracks).toHaveLength(1)
  })
})
