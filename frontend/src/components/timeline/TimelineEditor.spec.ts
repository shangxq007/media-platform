import { describe, it, expect, beforeEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useTimelineStore } from '@/stores/timeline'
import { createDemoProject } from '@/utils/demoProjectFactory'
import type { Clip } from '@/types'

describe('TimelineStore', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('adds a track', () => {
    const store = useTimelineStore()
    const track = store.addTrack('Video 1', 'video')
    expect(track.name).toBe('Video 1')
    expect(track.type).toBe('video')
    expect(store.state.tracks).toHaveLength(1)
  })

  it('removes a track', () => {
    const store = useTimelineStore()
    const track = store.addTrack('Video 1', 'video')
    store.removeTrack(track.id)
    expect(store.state.tracks).toHaveLength(0)
  })

  it('adds a clip to a track', () => {
    const store = useTimelineStore()
    const track = store.addTrack('Video 1', 'video')
    const clip: Clip = {
      id: 'clip_1', name: 'Intro', type: 'video',
      duration: 5, startTime: 0, endTime: 5, metadata: {}
    }
    const tc = store.addClipToTrack(track.id, clip, 0)
    expect(tc).not.toBeNull()
    expect(tc!.clipId).toBe('clip_1')
    expect(track.clips).toHaveLength(1)
  })

  it('does not add clip to locked track', () => {
    const store = useTimelineStore()
    const track = store.addTrack('Video 1', 'video')
    track.locked = true
    const clip: Clip = {
      id: 'clip_1', name: 'Intro', type: 'video',
      duration: 5, startTime: 0, endTime: 5, metadata: {}
    }
    const tc = store.addClipToTrack(track.id, clip, 0)
    expect(tc).toBeNull()
  })

  it('removes a clip from a track', () => {
    const store = useTimelineStore()
    const track = store.addTrack('Video 1', 'video')
    const clip: Clip = {
      id: 'clip_1', name: 'Intro', type: 'video',
      duration: 5, startTime: 0, endTime: 5, metadata: {}
    }
    const tc = store.addClipToTrack(track.id, clip, 0)
    expect(track.clips).toHaveLength(1)
    store.removeClipFromTrack(track.id, tc!.id)
    expect(track.clips).toHaveLength(0)
  })

  it('moves a clip', () => {
    const store = useTimelineStore()
    const track = store.addTrack('Video 1', 'video')
    const clip: Clip = {
      id: 'clip_1', name: 'Intro', type: 'video',
      duration: 5, startTime: 0, endTime: 5, metadata: {}
    }
    const tc = store.addClipToTrack(track.id, clip, 0)!
    store.moveClip(track.id, tc.id, 10)
    expect(tc.start).toBe(10)
  })

  it('does not move clip beyond start (clamps to 0)', () => {
    const store = useTimelineStore()
    const track = store.addTrack('Video 1', 'video')
    const clip: Clip = {
      id: 'clip_1', name: 'Intro', type: 'video',
      duration: 5, startTime: 0, endTime: 5, metadata: {}
    }
    const tc = store.addClipToTrack(track.id, clip, 5)!
    store.moveClip(track.id, tc.id, -10)
    expect(tc.start).toBe(0)
  })

  it('resizes a clip', () => {
    const store = useTimelineStore()
    const track = store.addTrack('Video 1', 'video')
    const clip: Clip = {
      id: 'clip_1', name: 'Intro', type: 'video',
      duration: 5, startTime: 0, endTime: 5, metadata: {}
    }
    const tc = store.addClipToTrack(track.id, clip, 0)!
    store.resizeClip(track.id, tc.id, 10)
    expect(tc.duration).toBe(10)
  })

  it('sets current time within bounds', () => {
    const store = useTimelineStore()
    store.setCurrentTime(30)
    expect(store.state.currentTime).toBe(30)
    store.setCurrentTime(-5)
    expect(store.state.currentTime).toBe(0)
    store.setCurrentTime(store.state.duration + 10)
    expect(store.state.currentTime).toBe(store.state.duration)
  })

  it('toggles playback', () => {
    const store = useTimelineStore()
    expect(store.state.playing).toBe(false)
    store.togglePlayback()
    expect(store.state.playing).toBe(true)
    store.togglePlayback()
    expect(store.state.playing).toBe(false)
  })

  it('sets zoom within bounds', () => {
    const store = useTimelineStore()
    store.setZoom(5)
    expect(store.state.zoom).toBe(5)
    store.setZoom(20)
    expect(store.state.zoom).toBe(10)
    store.setZoom(0.01)
    expect(store.state.zoom).toBe(0.1)
  })

  it('loads a demo project into the timeline', () => {
    const store = useTimelineStore()
    const demoProject = createDemoProject()
    store.loadDemoProject(demoProject)
    expect(store.state.tracks.length).toBeGreaterThan(0)
    expect(store.clips.length).toBe(3)
    expect(store.state.duration).toBeGreaterThanOrEqual(30)
    expect(store.state.currentTime).toBe(0)
    expect(store.state.playing).toBe(false)
    const videoTrack = store.state.tracks.find(t => t.type === 'video')
    expect(videoTrack).toBeTruthy()
    expect(videoTrack!.clips.length).toBe(2)
    const audioTrack = store.state.tracks.find(t => t.type === 'audio')
    expect(audioTrack).toBeTruthy()
    expect(audioTrack!.clips.length).toBe(1)
  })

  it('serializes and deserializes timeline', () => {
    const store = useTimelineStore()
    store.addTrack('Video 1', 'video')
    store.addTrack('Audio 1', 'audio')
    const json = store.toJSON()
    expect(json.tracks).toHaveLength(2)

    store.loadFromJSON({
      tracks: [],
      duration: 120,
      currentTime: 0,
      zoom: 1,
      playing: false
    })
    expect(store.state.tracks).toHaveLength(0)
    expect(store.state.duration).toBe(120)
  })
})
