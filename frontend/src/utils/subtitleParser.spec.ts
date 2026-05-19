import { describe, it, expect } from 'vitest'
import { parseSRT, parseVTT, parseASS, cuesToSRT } from '@/utils/subtitleParser'

describe('SubtitleParser', () => {
  it('parses SRT content correctly', () => {
    const srt = `1
00:00:01,000 --> 00:00:03,500
Hello world

2
00:00:04,000 --> 00:00:06,000
Second line`

    const cues = parseSRT(srt)
    expect(cues).toHaveLength(2)
    expect(cues[0].startTime).toBeCloseTo(1.0, 1)
    expect(cues[0].endTime).toBeCloseTo(3.5, 1)
    expect(cues[0].text).toBe('Hello world')
    expect(cues[1].text).toBe('Second line')
  })

  it('parses VTT content correctly', () => {
    const vtt = `WEBVTT

00:00:01.000 --> 00:00:03.500
Hello VTT

00:00:04.000 --> 00:00:06.000
Second cue`

    const cues = parseVTT(vtt)
    expect(cues).toHaveLength(2)
    expect(cues[0].text).toBe('Hello VTT')
    expect(cues[1].text).toBe('Second cue')
  })

  it('parses ASS content correctly', () => {
    const ass = `[Script Info]
Title: Test

[V Styles]
Format: Name, Fontname, Fontsize
Style: Default, Arial, 20

[Events]
Format: Start, End, Text
Dialogue: 0:00:01.00,0:00:03.50,Hello ASS
Dialogue: 0:00:04.00,0:00:06.00,Second line`

    const cues = parseASS(ass)
    expect(cues).toHaveLength(2)
    expect(cues[0].text).toBe('Hello ASS')
    expect(cues[1].text).toBe('Second line')
  })

  it('handles empty content', () => {
    expect(parseSRT('')).toHaveLength(0)
    expect(parseVTT('')).toHaveLength(0)
    expect(parseASS('')).toHaveLength(0)
  })

  it('converts cues back to SRT', () => {
    const cues = [
      { id: '1', index: 1, startTime: 1.0, endTime: 3.5, text: 'Hello' },
      { id: '2', index: 2, startTime: 4.0, endTime: 6.0, text: 'World' }
    ]
    const srt = cuesToSRT(cues)
    expect(srt).toContain('00:00:01')
    expect(srt).toContain('Hello')
    expect(srt).toContain('World')
  })

  it('parses time string correctly', () => {
    const srt = `1
00:01:30,500 --> 00:02:45,250
Test`

    const cues = parseSRT(srt)
    expect(cues[0].startTime).toBeCloseTo(90.5, 1)
    expect(cues[0].endTime).toBeCloseTo(165.25, 1)
  })
})
