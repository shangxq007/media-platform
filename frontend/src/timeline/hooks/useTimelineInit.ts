// =============================================================================
// useTimelineInit Hook
// =============================================================================
// Initializes the timeline store with demo data for testing.
// =============================================================================

import { useEffect, useRef } from 'react'
import { useTimelineStore } from '../store/timelineStore'
import { createClip, createTrack, type TimelineCanvasState } from '../model/timeline'

export function useTimelineInit() {
  const { setTimeline } = useTimelineStore()
  const initialized = useRef(false)

  useEffect(() => {
    if (initialized.current) return
    initialized.current = true

    // Create demo tracks
    const videoTrack = createTrack('track-video', 'Main Video', 'video', 0)
    const audioTrack = createTrack('track-audio', 'Audio', 'audio', 1)
    const subtitleTrack = createTrack('track-subtitle', 'Subtitles', 'subtitle', 2)

    // Build complete initial timeline with clips
    const clips = [
      createClip('clip-1', 'track-video', 'Intro.mp4', 'video', 0, 5, 'asset-1'),
      createClip('clip-2', 'track-video', 'Scene1.mp4', 'video', 5, 8, 'asset-2'),
      createClip('clip-3', 'track-video', 'Scene2.mp4', 'video', 13, 6, 'asset-3'),
      createClip('clip-4', 'track-audio', 'BGM.mp3', 'audio', 0, 19, 'asset-4'),
      createClip('clip-5', 'track-subtitle', 'Subtitles', 'subtitle', 0, 19, null),
    ]

    // Build clips record
    const clipsRecord: Record<string, typeof clips[0]> = {}
    for (const clip of clips) {
      clipsRecord[clip.id] = clip
    }

    // Build tracks with clip ids
    const tracks: Record<string, TimelineCanvasState['tracks'][string]> = {
      [videoTrack.track.id]: {
        ...videoTrack.track,
        clipIds: clips.filter(c => c.trackId === 'track-video').map(c => c.id),
      },
      [audioTrack.track.id]: {
        ...audioTrack.track,
        clipIds: clips.filter(c => c.trackId === 'track-audio').map(c => c.id),
      },
      [subtitleTrack.track.id]: {
        ...subtitleTrack.track,
        clipIds: clips.filter(c => c.trackId === 'track-subtitle').map(c => c.id),
      },
    }

    // Calculate total duration
    const duration = Math.max(...clips.map(c => c.timing.end))

    // Set complete timeline at once
    setTimeline({
      tracks,
      clips: clipsRecord,
      trackOrder: [videoTrack.track.id, audioTrack.track.id, subtitleTrack.track.id],
      layouts: {
        [videoTrack.layout.trackId]: videoTrack.layout,
        [audioTrack.layout.trackId]: audioTrack.layout,
        [subtitleTrack.layout.trackId]: subtitleTrack.layout,
      },
      duration,
    })
  }, [setTimeline])
}
