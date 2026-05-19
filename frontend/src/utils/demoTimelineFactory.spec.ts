import { describe, it, expect } from 'vitest'
import { createDemoProject } from './demoProjectFactory'
import { createDemoTimeline } from './demoTimelineFactory'

describe('createDemoTimeline', () => {
  it('creates video and audio tracks', () => {
    const project = createDemoProject()
    const timeline = createDemoTimeline(project)
    expect(timeline.tracks).toHaveLength(2)
    expect(timeline.tracks[0].type).toBe('video')
    expect(timeline.tracks[1].type).toBe('audio')
  })

  it('places video clips on the video track sequentially', () => {
    const project = createDemoProject()
    const timeline = createDemoTimeline(project)
    const videoTrack = timeline.tracks[0]
    expect(videoTrack.clips).toHaveLength(2)
    expect(videoTrack.clips[0].start).toBe(0)
    expect(videoTrack.clips[1].start).toBe(10)
  })

  it('places audio clips on the audio track', () => {
    const project = createDemoProject()
    const timeline = createDemoTimeline(project)
    const audioTrack = timeline.tracks[1]
    expect(audioTrack.clips).toHaveLength(1)
    expect(audioTrack.clips[0].start).toBe(0)
  })

  it('copies clips into the clips array', () => {
    const project = createDemoProject()
    const timeline = createDemoTimeline(project)
    expect(timeline.clips).toHaveLength(3)
  })

  it('sets duration to at least 30 seconds', () => {
    const project = createDemoProject()
    const timeline = createDemoTimeline(project)
    expect(timeline.duration).toBeGreaterThanOrEqual(30)
  })

  it('tracks are not muted or locked', () => {
    const project = createDemoProject()
    const timeline = createDemoTimeline(project)
    for (const track of timeline.tracks) {
      expect(track.muted).toBe(false)
      expect(track.locked).toBe(false)
    }
  })

  it('creates trackClips linking clips to tracks', () => {
    const project = createDemoProject()
    const timeline = createDemoTimeline(project)
    expect(timeline.trackClips).toHaveLength(3)
    for (const tc of timeline.trackClips) {
      expect(tc.clipId).toBeTruthy()
      expect(tc.trackId).toBeTruthy()
      expect(tc.duration).toBeGreaterThan(0)
    }
  })

  it('includes subtitle tracks and cues from project', () => {
    const project = createDemoProject()
    const timeline = createDemoTimeline(project)
    expect(timeline.subtitleTracks).toHaveLength(1)
    expect(timeline.subtitleCues).toHaveLength(4)
  })

  it('sets correct clipStart and clipEnd on trackClips', () => {
    const project = createDemoProject()
    const timeline = createDemoTimeline(project)
    const firstTc = timeline.trackClips[0]
    expect(firstTc.clipStart).toBe(0)
    expect(firstTc.clipEnd).toBeGreaterThan(0)
  })
})
