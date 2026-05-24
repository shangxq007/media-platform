// OpenTimelineIO (OTIO) types and utilities for media-platform frontend

export interface OTIOClip {
  name: string
  source_range: {
    start_time: number
    duration: number
  }
  transforms?: unknown[]
}

export interface OTIOTrack {
  name: string
  children: Record<string, unknown>[]
}

export interface OTIOTimeline {
  name: string
  tracks: OTIOTrack[]
}

export function exportToOTIO(timeline: Record<string, unknown>): OTIOTimeline {
  const tracks: OTIOTrack[] = []
  timeline.tracks.forEach((track: Record<string, unknown>) => {
    const clips: Record<string, unknown>[] = (track.clips as Record<string, unknown>[]).map((clip: Record<string, unknown>) => ({
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

export function importFromOTIO(otioData: OTIOTimeline, timelineStore: Record<string, unknown>) {
  // Clear existing timeline
  timelineStore.state.tracks = []

  otioData.tracks.forEach((otioTrack: OTIOTrack) => {
    const type = otioTrack.name.includes('video') ? 'video' : 
                otioTrack.name.includes('audio') ? 'audio' : 'text'
    timelineStore.addTrack(otioTrack.name, type)
    // TODO: Add clips to track based on otioTrack.children
  })
}