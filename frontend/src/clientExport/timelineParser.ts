import type { EditorClip, EditorTrack, ParsedTimeline } from './types'

export function parseEditorTimeline(json: string, fallbackDuration = 60): ParsedTimeline {
  const data = JSON.parse(json) as {
    tracks?: EditorTrack[]
    clips?: EditorClip[]
    duration?: number
  }
  const tracks = data.tracks ?? []
  const clips = data.clips ?? []
  let duration = data.duration ?? 0
  for (const track of tracks) {
    for (const tc of track.clips ?? []) {
      duration = Math.max(duration, (tc.start ?? 0) + (tc.duration ?? 0))
    }
  }
  if (duration <= 0) {
    duration = fallbackDuration
  }
  return {
    duration,
    width: 1280,
    height: 720,
    fps: 30,
    clips,
    tracks,
  }
}

export function extractEffectKeys(timelineJson: string): string[] {
  const keys = new Set<string>()
  const re = /"(?:effectKey|effects)"\s*:\s*(?:"([^"]+)"|\[([^\]]*)])/g
  let m: RegExpExecArray | null
  while ((m = re.exec(timelineJson)) !== null) {
    if (m[1]) {
      keys.add(m[1])
    }
    if (m[2]) {
      for (const part of m[2].split(',')) {
        const k = part.replace(/["\s]/g, '')
        if (k.startsWith('video.') || k.startsWith('audio.')) {
          keys.add(k)
        }
      }
    }
  }
  return [...keys]
}

export function activeVideoClipsAt(
  timeline: ParsedTimeline,
  timeSec: number
): Array<{ clip: EditorClip; trackClip: { clipStart: number } }> {
  const out: Array<{ clip: EditorClip; trackClip: { clipStart: number } }> = []
  for (const track of timeline.tracks) {
    if (track.type && track.type !== 'video') {
      continue
    }
    for (const tc of track.clips ?? []) {
      const start = tc.start ?? 0
      const end = start + (tc.duration ?? 0)
      if (timeSec >= start && timeSec < end) {
        const clip = timeline.clips.find((c) => c.id === tc.clipId)
        if (clip?.sourceUrl) {
          out.push({
            clip,
            trackClip: { clipStart: tc.clipStart ?? 0 },
          })
        }
      }
    }
  }
  return out
}
