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
  const timelineTracks = timeline.tracks as Record<string, unknown>[] | undefined
  if (!timelineTracks) return { name: 'media-platform-timeline', tracks: [] }
  timelineTracks.forEach((track: Record<string, unknown>) => {
    const trackClips = track.clips as Record<string, unknown>[] | undefined
    const clips: Record<string, unknown>[] = (trackClips ?? []).map((clip: Record<string, unknown>) => ({
      name: clip.clipId,
      source_range: {
        start_time: clip.clipStart as number,
        duration: (clip.clipEnd as number) - (clip.clipStart as number)
      },
      transforms: []
    }))
    tracks.push({
      name: track.name as string,
      children: clips
    })
  })
  return { name: 'media-platform-timeline', tracks }
}

export function importFromOTIO(otioData: OTIOTimeline, timelineStore: { state: { tracks: unknown[] }; addTrack: (name: string, type: "audio" | "video" | "image" | "text" | "subtitle") => unknown }) {
  timelineStore.state.tracks = []

  otioData.tracks.forEach((otioTrack: OTIOTrack) => {
    const type = otioTrack.name.includes('video') ? 'video' : 
                otioTrack.name.includes('audio') ? 'audio' : 'text'
    timelineStore.addTrack(otioTrack.name, type)
  })
}