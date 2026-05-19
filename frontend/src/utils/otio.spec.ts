import { describe, it, expect } from 'vitest'
import { exportToOTIO, importFromOTIO } from '@/utils/otio'

describe('OTIO Utils', () => {
  const mockTimeline = {
    tracks: [
      {
        id: 'track_1',
        name: 'Video 1',
        type: 'video',
        clips: [
          {
            id: 'tc_1',
            clipId: 'clip_1',
            trackId: 'track_1',
            start: 0,
            duration: 5,
            clipStart: 0,
            clipEnd: 5
          }
        ]
      },
      {
        id: 'track_2',
        name: 'Audio 1',
        type: 'audio',
        clips: []
      }
    ]
  }

  it('exports timeline to OTIO format', () => {
    const otio = exportToOTIO(mockTimeline)
    expect(otio.name).toBe('media-platform-timeline')
    expect(otio.tracks).toHaveLength(2)
    expect(otio.tracks[0].name).toBe('Video 1')
    expect(otio.tracks[0].children).toHaveLength(1)
    expect(otio.tracks[0].children[0].name).toBe('clip_1')
    expect(otio.tracks[0].children[0].source_range.start_time).toBe(0)
    expect(otio.tracks[0].children[0].source_range.duration).toBe(5)
  })

  it('exports empty tracks', () => {
    const otio = exportToOTIO({ tracks: [] })
    expect(otio.tracks).toHaveLength(0)
  })

  it('import clears and rebuilds timeline', () => {
    const otioData = {
      name: 'test-timeline',
      tracks: [
        { name: 'video-track', children: [] },
        { name: 'audio-track', children: [] }
      ]
    }

    const mockStore = {
      state: { tracks: [{ id: 'old', name: 'Old', clips: [] }] },
      addTrack: (name: string, type: string) => {
        const track = { id: `track_${Date.now()}`, name, type, clips: [] }
        mockStore.state.tracks.push(track)
        return track
      }
    }

    importFromOTIO(otioData, mockStore)
    expect(mockStore.state.tracks).toHaveLength(2)
  })
})
