import type { Track, TrackClip, SubtitleTrack, SubtitleCue, Clip } from '@/types'
import type { DemoProject } from './demoProjectFactory'

export interface DemoTimeline {
  tracks: Track[]
  trackClips: TrackClip[]
  clips: Clip[]
  subtitleTracks: SubtitleTrack[]
  subtitleCues: SubtitleCue[]
  duration: number
}

export function createDemoTimeline(project: DemoProject): DemoTimeline {
  const tracks: Track[] = []
  const trackClips: TrackClip[] = []
  const clips: Clip[] = [...project.clips]
  let clipTime = 0

  const videoTrack: Track = {
    id: `demo_track_video_${Date.now()}`,
    name: 'Video 1',
    type: 'video',
    clips: [],
    muted: false,
    locked: false,
  }

  const videoClips = project.clips.filter(c => c.type === 'video')
  for (const clip of videoClips) {
    const tc: TrackClip = {
      id: `demo_tc_${clip.id}`,
      clipId: clip.id,
      trackId: videoTrack.id,
      start: clipTime,
      duration: clip.endTime - clip.startTime,
      clipStart: clip.startTime,
      clipEnd: clip.endTime,
    }
    videoTrack.clips.push(tc)
    trackClips.push(tc)
    clipTime += clip.endTime - clip.startTime
  }
  tracks.push(videoTrack)

  const audioTrack: Track = {
    id: `demo_track_audio_${Date.now()}`,
    name: 'Audio 1',
    type: 'audio',
    clips: [],
    muted: false,
    locked: false,
  }

  const audioClips = project.clips.filter(c => c.type === 'audio')
  let audioTime = 0
  for (const clip of audioClips) {
    const tc: TrackClip = {
      id: `demo_tc_${clip.id}`,
      clipId: clip.id,
      trackId: audioTrack.id,
      start: audioTime,
      duration: clip.endTime - clip.startTime,
      clipStart: clip.startTime,
      clipEnd: clip.endTime,
    }
    audioTrack.clips.push(tc)
    trackClips.push(tc)
    audioTime += clip.endTime - clip.startTime
  }
  tracks.push(audioTrack)

  const duration = Math.max(clipTime, audioTime, 30)

  return {
    tracks,
    trackClips,
    clips,
    subtitleTracks: project.subtitleTracks,
    subtitleCues: project.subtitleCues,
    duration,
  }
}
