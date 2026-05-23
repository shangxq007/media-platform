import { describe, expect, it } from 'vitest'
import {
  extractEntityIdsFromPatchPaths,
  resolveEditorTrackClipIds,
  resolveInternalClipIdFromPath,
} from './timelinePatchHighlight'
import type { Clip, Track } from '@/types'

const SAMPLE_INTERNAL = JSON.stringify({
  composition: {
    tracks: [
      {
        id: 'track-v1',
        clips: [{ id: 'clip-alpha' }, { id: 'clip-beta' }],
      },
      {
        id: 'track-a1',
        clips: [{ id: 'clip-gamma' }],
      },
    ],
  },
})

describe('timelinePatchHighlight', () => {
  it('extracts named entity ids from patch paths', () => {
    const ids = extractEntityIdsFromPatchPaths([
      { op: 'replace', path: '/composition/tracks/vt1/clips/clip-a/start' },
    ])
    expect(ids).toContain('vt1')
    expect(ids).toContain('clip-a')
  })

  it('resolves numeric clip index via internal timeline', () => {
    const index = {
      clipIdsByTrackIndex: new Map([
        [0, ['clip-alpha', 'clip-beta']],
        [1, ['clip-gamma']],
      ]),
      trackIdsByIndex: new Map([
        [0, 'track-v1'],
        [1, 'track-a1'],
      ]),
    }
    expect(resolveInternalClipIdFromPath('/composition/tracks/0/clips/1', index)).toBe('clip-beta')
    expect(resolveInternalClipIdFromPath('/composition/tracks/1/clips/0', index)).toBe('clip-gamma')
  })

  it('extracts clip ids from numeric paths with internal json', () => {
    const ids = extractEntityIdsFromPatchPaths(
      [{ op: 'replace', path: '/composition/tracks/0/clips/0/start' }],
      SAMPLE_INTERNAL
    )
    expect(ids).toContain('clip-alpha')
  })

  it('maps entity ids to editor track clip ids', () => {
    const tracks: Track[] = [
      {
        id: 'tr1',
        name: 'V1',
        type: 'video',
        clips: [
          {
            id: 'tc-1',
            clipId: 'clip-alpha',
            trackId: 'tr1',
            start: 0,
            duration: 5,
            clipStart: 0,
            clipEnd: 5,
          },
        ],
        muted: false,
        locked: false,
      },
    ]
    const catalog: Clip[] = [
      { id: 'clip-alpha', name: 'A', type: 'video', duration: 5, startTime: 0, endTime: 5, metadata: {} },
    ]
    const resolved = resolveEditorTrackClipIds(['clip-alpha', 'tc-1'], tracks, catalog)
    expect(resolved).toContain('tc-1')
  })
})
