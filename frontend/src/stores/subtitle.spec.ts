import { describe, it, expect } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useSubtitleStore } from '@/stores/subtitle'
import { parseSubtitleFile, cuesToSRT } from '@/utils/subtitleParser'

describe('SubtitleStore', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('starts with empty tracks', () => {
    const store = useSubtitleStore()
    expect(store.tracks).toHaveLength(0)
    expect(store.fonts).toHaveLength(0)
  })

  it('parses SRT content', () => {
    const srt = `1
00:00:01,000 --> 00:00:03,500
Hello world`

    const cues = parseSubtitleFile(srt, 'srt')
    expect(cues).toHaveLength(1)
    expect(cues[0].text).toBe('Hello world')
    expect(cues[0].startTime).toBeCloseTo(1.0, 1)
  })

  it('parses VTT content', () => {
    const vtt = `WEBVTT

00:00:01.000 --> 00:00:03.500
Hello VTT`

    const cues = parseSubtitleFile(vtt, 'vtt')
    expect(cues).toHaveLength(1)
    expect(cues[0].text).toBe('Hello VTT')
  })

  it('converts cues to SRT', () => {
    const cues = [
      { id: '1', index: 1, startTime: 1.0, endTime: 3.0, text: 'Hello' }
    ]
    const srt = cuesToSRT(cues)
    expect(srt).toContain('Hello')
    expect(srt).toContain('00:00:01')
  })

  it('registers font', () => {
    const store = useSubtitleStore()
    const font = store.uploadFont(new File(['dummy'], 'test.ttf'))
    expect(font.family).toBe('test')
    expect(font.format).toBe('ttf')
    expect(store.fonts).toHaveLength(1)
  })

  it('removes track', () => {
    const store = useSubtitleStore()
    store.tracks.push({
      id: 'test', language: 'en', label: 'Test', format: 'srt',
      cues: [], fallbackFontIds: [], burnIn: true
    })
    expect(store.tracks).toHaveLength(1)
    store.removeTrack('test')
    expect(store.tracks).toHaveLength(0)
  })
})
