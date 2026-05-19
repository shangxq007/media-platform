// OpenTimelineIO (OTIO) types and utilities for media-platform frontend

export interface OTIOClip {
  name: string
  source_range: {
    start_time: number
    duration: number
  }
  transforms?: any[]
}

export interface OTIOTrack {
  name: string
  children: any[]
}

export interface OTIOTimeline {
  name: string
  tracks: OTIOTrack[]
}

export function exportToOTIO(timeline: any): OTIOTimeline {
  const tracks: OTIOTrack[] = []
  timeline.tracks.forEach((track: any) => {
    const clips: any[] = track.clips.map((clip: any) => ({
      name: clip.clipId,
      source_range: {
        start_time: clip.clipStart,
        duration: clip.clipEnd - clip.clipStart
      },
      transforms: []
    }))
    tracks.push({
      name: track.name,
      children: clips
    })
  })
  return { name: 'media-platform-timeline', tracks }
}

export function importFromOTIO(otioData: OTIOTimeline, timelineStore: any) {
  // Clear existing timeline
  timelineStore.state.tracks = []

  otioData.tracks.forEach((otioTrack: OTIOTrack) => {
    const type = otioTrack.name.includes('video') ? 'video' : 
                otioTrack.name.includes('audio') ? 'audio' : 'text'
    timelineStore.addTrack(otioTrack.name, type)
    // TODO: Add clips to track based on otioTrack.children
  })
}