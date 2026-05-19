import { describe, it, expect } from 'vitest'
import { createDemoProject } from './demoProjectFactory'

describe('createDemoProject', () => {
  it('returns a demo project with correct name', () => {
    const project = createDemoProject()
    expect(project.name).toBe('Demo Editing Project')
  })

  it('creates 3 clips (2 video, 1 audio)', () => {
    const project = createDemoProject()
    expect(project.clips).toHaveLength(3)
    expect(project.clips.filter(c => c.type === 'video')).toHaveLength(2)
    expect(project.clips.filter(c => c.type === 'audio')).toHaveLength(1)
  })

  it('marks all clips as demo in metadata', () => {
    const project = createDemoProject()
    for (const clip of project.clips) {
      expect(clip.metadata.isDemo).toBe('true')
    }
  })

  it('gives clips unique ids', () => {
    const project = createDemoProject()
    const ids = project.clips.map(c => c.id)
    const uniqueIds = new Set(ids)
    expect(uniqueIds.size).toBe(ids.length)
  })

  it('sets correct durations for clips', () => {
    const project = createDemoProject()
    const video1 = project.clips.find(c => c.name === 'Demo Video - Intro')!
    expect(video1.duration).toBe(10)
    expect(video1.startTime).toBe(0)
    expect(video1.endTime).toBe(10)

    const video2 = project.clips.find(c => c.name === 'Demo Video - Main')!
    expect(video2.duration).toBe(15)

    const audio = project.clips.find(c => c.name === 'Demo Audio - Background')!
    expect(audio.duration).toBe(30)
  })

  it('creates 4 subtitle cues', () => {
    const project = createDemoProject()
    expect(project.subtitleCues).toHaveLength(4)
  })

  it('creates 1 subtitle track with correct cues', () => {
    const project = createDemoProject()
    expect(project.subtitleTracks).toHaveLength(1)
    expect(project.subtitleTracks[0].cues).toHaveLength(4)
    expect(project.subtitleTracks[0].language).toBe('en')
    expect(project.subtitleTracks[0].format).toBe('srt')
  })

  it('assigns sequential indices to subtitle cues', () => {
    const project = createDemoProject()
    project.subtitleCues.forEach((cue, i) => {
      expect(cue.index).toBe(i)
    })
  })

  it('sets correct subtitle cue text', () => {
    const project = createDemoProject()
    expect(project.subtitleCues[0].text).toBe('Welcome to Media Platform')
    expect(project.subtitleCues[1].text).toBe('This is a demo project')
    expect(project.subtitleCues[2].text).toBe('Try editing and exporting')
    expect(project.subtitleCues[3].text).toBe('Enjoy creating!')
  })

  it('creates effects for demo clips', () => {
    const project = createDemoProject()
    expect(project.effects.length).toBeGreaterThan(0)
    expect(project.effects[0].effectKey).toBe('video.fade_in')
    expect(project.effects[0].parameters).toEqual({ duration: 1 })
  })

  it('creates transitions between demo clips', () => {
    const project = createDemoProject()
    expect(project.transitions.length).toBeGreaterThan(0)
    expect(project.transitions[0].type).toBe('crossfade')
    expect(project.transitions[0].duration).toBe(1)
  })

  it('links transitions to video clips', () => {
    const project = createDemoProject()
    const videoClips = project.clips.filter(c => c.type === 'video')
    const transition = project.transitions[0]
    expect(transition.fromClipId).toBe(videoClips[0].id)
    expect(transition.toClipId).toBe(videoClips[1].id)
  })

  it('initializes tracks and trackClips as empty arrays', () => {
    const project = createDemoProject()
    expect(project.tracks).toEqual([])
    expect(project.trackClips).toEqual([])
  })
})
