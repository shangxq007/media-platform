import { describe, it, expect } from 'vitest'
import { isDemoProjectId, parseEditorTimelinePayload } from './timelineImport'
import type { EditorTimelinePayload } from './timelineImport'

describe('timelineImport', () => {
  it('parses editor payload with tracks and clips', () => {
    const payload: EditorTimelinePayload = {
      schemaVersion: '2.0.0',
      tracks: [{ id: 't1', name: 'V1', type: 'video', clips: [], muted: false, locked: false }],
      clips: [{ id: 'c1', name: 'Clip', type: 'video', duration: 5, startTime: 0, endTime: 5, metadata: {} }],
      duration: 30,
      currentTime: 0,
      zoom: 1,
      playing: false,
    }
    const { state, clips } = parseEditorTimelinePayload(payload)
    expect(state.tracks).toHaveLength(1)
    expect(state.duration).toBe(30)
    expect(clips).toHaveLength(1)
    expect(clips[0].id).toBe('c1')
  })

  it('detects demo project ids', () => {
    expect(isDemoProjectId('demo_project_123')).toBe(true)
    expect(isDemoProjectId('prj_real')).toBe(false)
  })
})
