import { describe, it, expect } from 'vitest'
import {
  buildCanonicalEditorJson,
  detectTimelineConflict,
  hashEditorPayload,
  mergeEditorTimelines,
  shouldFastForwardServer,
} from './timelineConflictMerge'
import type { EditorTimelinePayload } from './timelineImport'

const basePayload = (): EditorTimelinePayload => ({
  schemaVersion: '2.0.0',
  tracks: [
    {
      id: 't1',
      name: 'V1',
      type: 'video',
      muted: false,
      locked: false,
      clips: [
        {
          id: 'tc1',
          clipId: 'c1',
          trackId: 't1',
          start: 0,
          duration: 5,
          clipStart: 0,
          clipEnd: 5,
        },
      ],
    },
  ],
  clips: [
    { id: 'c1', name: 'A', type: 'video', duration: 5, startTime: 0, endTime: 5, metadata: {} },
  ],
  duration: 30,
  currentTime: 0,
  zoom: 1,
  playing: false,
})

describe('timelineConflictMerge', () => {
  it('detects conflict when local and server diverge from baseline', () => {
    const base = basePayload()
    const local = { ...base, duration: 45 }
    const remote = { ...base, duration: 60 }
    const baseHash = hashEditorPayload(base)
    const localHash = hashEditorPayload(local)
    const remoteHash = hashEditorPayload(remote)
    expect(detectTimelineConflict(true, baseHash, localHash, remoteHash)).toBe(true)
  })

  it('fast-forwards when only server changed', () => {
    const base = basePayload()
    const remote = { ...base, duration: 60 }
    const baseHash = hashEditorPayload(base)
    const remoteHash = hashEditorPayload(remote)
    expect(shouldFastForwardServer(true, baseHash, baseHash, remoteHash)).toBe(true)
    expect(detectTimelineConflict(true, baseHash, baseHash, remoteHash)).toBe(false)
  })

  it('merges local clip edits with server-only clips', () => {
    const base = basePayload()
    const local = JSON.parse(buildCanonicalEditorJson(base)) as EditorTimelinePayload
    local.tracks![0].clips[0].start = 2

    const remote = JSON.parse(buildCanonicalEditorJson(base)) as EditorTimelinePayload
    remote.tracks![0].clips.push({
      id: 'tc2',
      clipId: 'c2',
      trackId: 't1',
      start: 10,
      duration: 3,
      clipStart: 0,
      clipEnd: 3,
    })
    remote.clips = [
      ...remote.clips!,
      { id: 'c2', name: 'B', type: 'video', duration: 3, startTime: 0, endTime: 3, metadata: {} },
    ]

    const { state, clips } = mergeEditorTimelines(base, local, remote)
    expect(state.tracks[0].clips.some(c => c.id === 'tc1' && c.start === 2)).toBe(true)
    expect(state.tracks[0].clips.some(c => c.id === 'tc2')).toBe(true)
    expect(clips.some(c => c.id === 'c2')).toBe(true)
  })
})
