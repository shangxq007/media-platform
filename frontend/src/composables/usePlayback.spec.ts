import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { usePlayback } from './usePlayback'

describe('usePlayback', () => {
  beforeEach(() => {
    vi.useFakeTimers()
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('initializes with default state', () => {
    const { isPlaying, currentTime } = usePlayback(() => 60)
    expect(isPlaying.value).toBe(false)
    expect(currentTime.value).toBe(0)
  })

  it('toggles playback on', () => {
    const { isPlaying, togglePlayback } = usePlayback(() => 60)
    togglePlayback()
    expect(isPlaying.value).toBe(true)
  })

  it('toggles playback off', () => {
    const { isPlaying, togglePlayback } = usePlayback(() => 60)
    togglePlayback()
    togglePlayback()
    expect(isPlaying.value).toBe(false)
  })

  it('increments currentTime while playing', () => {
    const { currentTime, togglePlayback } = usePlayback(() => 60, 30)
    togglePlayback()
    vi.advanceTimersByTime(1000)
    expect(currentTime.value).toBeGreaterThan(0)
  })

  it('stops at total duration', () => {
    const { isPlaying, currentTime, togglePlayback } = usePlayback(() => 0.5, 30)
    togglePlayback()
    vi.advanceTimersByTime(5000)
    expect(currentTime.value).toBeLessThanOrEqual(0.5)
    expect(isPlaying.value).toBe(false)
  })

  it('steps forward', () => {
    const { currentTime, stepForward } = usePlayback(() => 60)
    stepForward(5)
    expect(currentTime.value).toBe(5)
  })

  it('steps forward by default 1', () => {
    const { currentTime, stepForward } = usePlayback(() => 60)
    stepForward()
    expect(currentTime.value).toBe(1)
  })

  it('steps backward', () => {
    const { currentTime, stepForward, stepBackward } = usePlayback(() => 60)
    stepForward(10)
    stepBackward(3)
    expect(currentTime.value).toBe(7)
  })

  it('does not go below 0 when stepping backward', () => {
    const { currentTime, stepBackward } = usePlayback(() => 60)
    stepBackward(5)
    expect(currentTime.value).toBe(0)
  })

  it('seeks to a specific time', () => {
    const { currentTime, seek } = usePlayback(() => 60)
    seek(30)
    expect(currentTime.value).toBe(30)
  })

  it('clamps seek to duration', () => {
    const { currentTime, seek } = usePlayback(() => 60)
    seek(100)
    expect(currentTime.value).toBe(60)
  })

  it('clamps seek to 0', () => {
    const { currentTime, seek } = usePlayback(() => 60)
    seek(-10)
    expect(currentTime.value).toBe(0)
  })

  it('stops playback when seeking to end', () => {
    const { isPlaying, togglePlayback, seek } = usePlayback(() => 10)
    togglePlayback()
    seek(10)
    expect(isPlaying.value).toBe(false)
  })
})
