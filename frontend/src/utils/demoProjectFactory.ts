import type { Clip, Track, TrackClip, SubtitleTrack, SubtitleCue } from '@/types'

export interface DemoClipEffect {
  clipId: string
  effectKey: string
  parameters: Record<string, unknown>
}

export interface DemoTransition {
  fromClipId: string
  toClipId: string
  type: string
  duration: number
}

export interface DemoProject {
  name: string
  clips: Clip[]
  tracks: Track[]
  trackClips: TrackClip[]
  subtitleTracks: SubtitleTrack[]
  subtitleCues: SubtitleCue[]
  effects: DemoClipEffect[]
  transitions: DemoTransition[]
}

export function createDemoProject(): DemoProject {
  const now = Date.now()

  const videoClip1: Clip = {
    id: `demo_video_1_${now}`,
    name: 'Demo Video - Intro',
    type: 'video',
    sourceUrl: '',
    duration: 10,
    startTime: 0,
    endTime: 10,
    metadata: { resolution: '1920x1080', codec: 'h264', size: '2.4 MB', isDemo: 'true' },
  }

  const videoClip2: Clip = {
    id: `demo_video_2_${now}`,
    name: 'Demo Video - Main',
    type: 'video',
    sourceUrl: '',
    duration: 15,
    startTime: 0,
    endTime: 15,
    metadata: { resolution: '1920x1080', codec: 'h264', size: '3.1 MB', isDemo: 'true' },
  }

  const audioClip: Clip = {
    id: `demo_audio_1_${now}`,
    name: 'Demo Audio - Background',
    type: 'audio',
    sourceUrl: '',
    duration: 30,
    startTime: 0,
    endTime: 30,
    metadata: { codec: 'aac', size: '1.2 MB', isDemo: 'true' },
  }

  const subtitleCues: SubtitleCue[] = [
    { id: `demo_sub_1_${now}`, index: 0, startTime: 1, endTime: 4, text: 'Welcome to Media Platform' },
    { id: `demo_sub_2_${now}`, index: 1, startTime: 5, endTime: 8, text: 'This is a demo project' },
    { id: `demo_sub_3_${now}`, index: 2, startTime: 10, endTime: 14, text: 'Try editing and exporting' },
    { id: `demo_sub_4_${now}`, index: 3, startTime: 20, endTime: 25, text: 'Enjoy creating!' },
  ]

  const subtitleTrack: SubtitleTrack = {
    id: `demo_sub_track_${now}`,
    language: 'en',
    label: 'English Subtitles',
    format: 'srt',
    cues: subtitleCues,
    fontId: '',
    fallbackFontIds: [],
    burnIn: false,
  }

  return {
    name: 'Demo Editing Project',
    clips: [videoClip1, videoClip2, audioClip],
    tracks: [],
    trackClips: [],
    subtitleTracks: [subtitleTrack],
    subtitleCues,
    effects: [
      { clipId: videoClip1.id, effectKey: 'video.fade_in', parameters: { duration: 1 } },
    ],
    transitions: [
      { fromClipId: videoClip1.id, toClipId: videoClip2.id, type: 'crossfade', duration: 1 },
    ],
  }
}
