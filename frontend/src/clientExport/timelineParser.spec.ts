import { describe, expect, it } from 'vitest'
import { extractEffectKeys, parseEditorTimeline } from './timelineParser'

describe('timelineParser', () => {
  it('parses duration from tracks', () => {
    const json = JSON.stringify({
      tracks: [{ clips: [{ start: 0, duration: 5, clipId: 'c1' }] }],
      clips: [{ id: 'c1', sourceUrl: 'https://example.com/v.mp4' }],
    })
    const t = parseEditorTimeline(json)
    expect(t.duration).toBe(5)
  })

  it('extracts effect keys', () => {
    const json = '{"effectKey":"video.natron_vignette","effects":["video.fade_in"]}'
    const keys = extractEffectKeys(json)
    expect(keys).toContain('video.natron_vignette')
    expect(keys).toContain('video.fade_in')
  })
})
