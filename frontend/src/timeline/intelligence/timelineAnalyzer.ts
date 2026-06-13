// =============================================================================
// Timeline Analysis Engine
// =============================================================================
// Analyzes timeline structure and detects issues, warnings, and suggestions.
// Provides structural understanding of the timeline for intelligent editing.
// =============================================================================

import type { TimelineCanvasState, TimelineClip, TimelineTrack } from '../model/timeline'

// ---------------------------------------------------------------------------
// Analysis Report Types
// ---------------------------------------------------------------------------
export type IssueSeverity = 'error' | 'warning' | 'info'

export interface TimelineIssue {
  readonly id: string
  readonly severity: IssueSeverity
  readonly type: IssueType
  readonly message: string
  readonly clipIds: readonly string[]
  readonly trackIds: readonly string[]
  readonly autoFixable: boolean
}

export type IssueType =
  | 'OVERLAP'
  | 'GAP'
  | 'ORPHAN_CLIP'
  | 'SILENT_REGION'
  | 'EMPTY_TRACK'
  | 'TRACK_IMBALANCE'
  | 'MISSING_VIDEO'

export interface TrackDensity {
  readonly trackId: string
  readonly trackName: string
  readonly clipCount: number
  readonly totalDuration: number
  readonly coverage: number      // 0-1, percentage of timeline covered
  readonly averageGap: number    // average gap between clips
}

export interface TimelineAnalysisReport {
  readonly issues: readonly TimelineIssue[]
  readonly warnings: readonly TimelineIssue[]
  readonly suggestions: readonly TimelineIssue[]
  readonly trackDensity: readonly TrackDensity[]
  readonly overallHealth: number  // 0-100
  readonly analyzedAt: number
}

// ---------------------------------------------------------------------------
// Issue ID Generator
// ---------------------------------------------------------------------------
let issueCounter = 0
function generateIssueId(): string {
  return `issue-${Date.now()}-${++issueCounter}`
}

// ---------------------------------------------------------------------------
// Detect Overlaps
// ---------------------------------------------------------------------------
export function detectOverlaps(timeline: TimelineCanvasState): TimelineIssue[] {
  const issues: TimelineIssue[] = []

  for (const track of Object.values(timeline.tracks)) {
    const clips = getTrackClipsSorted(timeline, track.id)

    for (let i = 1; i < clips.length; i++) {
      const prev = clips[i - 1]
      const curr = clips[i]

      if (curr.timing.start < prev.timing.end) {
        const overlapDuration = prev.timing.end - curr.timing.start
        issues.push({
          id: generateIssueId(),
          severity: 'error',
          type: 'OVERLAP',
          message: `Clips "${prev.name}" and "${curr.name}" overlap by ${overlapDuration.toFixed(2)}s`,
          clipIds: [prev.id, curr.id],
          trackIds: [track.id],
          autoFixable: true,
        })
      }
    }
  }

  return issues
}

// ---------------------------------------------------------------------------
// Detect Gaps
// ---------------------------------------------------------------------------
export function detectGaps(timeline: TimelineCanvasState, threshold: number = 0.5): TimelineIssue[] {
  const issues: TimelineIssue[] = []

  for (const track of Object.values(timeline.tracks)) {
    if (track.type !== 'video') continue // Only check video tracks for gaps

    const clips = getTrackClipsSorted(timeline, track.id)
    if (clips.length < 2) continue

    for (let i = 1; i < clips.length; i++) {
      const prev = clips[i - 1]
      const curr = clips[i]
      const gap = curr.timing.start - prev.timing.end

      if (gap > threshold) {
        issues.push({
          id: generateIssueId(),
          severity: 'warning',
          type: 'GAP',
          message: `${gap.toFixed(2)}s gap between "${prev.name}" and "${curr.name}"`,
          clipIds: [prev.id, curr.id],
          trackIds: [track.id],
          autoFixable: true,
        })
      }
    }
  }

  return issues
}

// ---------------------------------------------------------------------------
// Detect Orphan Clips
// ---------------------------------------------------------------------------
export function detectOrphanClips(timeline: TimelineCanvasState): TimelineIssue[] {
  const issues: TimelineIssue[] = []

  // Find clips not referenced by any track
  for (const clip of Object.values(timeline.clips)) {
    const track = timeline.tracks[clip.trackId]
    if (!track || !track.clipIds.includes(clip.id)) {
      issues.push({
        id: generateIssueId(),
        severity: 'error',
        type: 'ORPHAN_CLIP',
        message: `Clip "${clip.name}" is not attached to any track`,
        clipIds: [clip.id],
        trackIds: [],
        autoFixable: false,
      })
    }
  }

  // Find track references to non-existent clips
  for (const track of Object.values(timeline.tracks)) {
    for (const clipId of track.clipIds) {
      if (!timeline.clips[clipId]) {
        issues.push({
          id: generateIssueId(),
          severity: 'error',
          type: 'ORPHAN_CLIP',
          message: `Track "${track.name}" references missing clip "${clipId}"`,
          clipIds: [],
          trackIds: [track.id],
          autoFixable: true,
        })
      }
    }
  }

  return issues
}

// ---------------------------------------------------------------------------
// Detect Silent Regions
// ---------------------------------------------------------------------------
export function detectSilentRegions(timeline: TimelineCanvasState, threshold: number = 2): TimelineIssue[] {
  const issues: TimelineIssue[] = []

  // Find video clips without corresponding audio
  const videoClips = Object.values(timeline.clips).filter(c => c.type === 'video')
  const audioClips = Object.values(timeline.clips).filter(c => c.type === 'audio')

  for (const videoClip of videoClips) {
    const hasAudio = audioClips.some(audioClip =>
      audioClip.timing.start <= videoClip.timing.start &&
      audioClip.timing.end >= videoClip.timing.end
    )

    if (!hasAudio && videoClip.timing.duration > threshold) {
      issues.push({
        id: generateIssueId(),
        severity: 'info',
        type: 'SILENT_REGION',
        message: `Video "${videoClip.name}" has no matching audio`,
        clipIds: [videoClip.id],
        trackIds: [],
        autoFixable: false,
      })
    }
  }

  return issues
}

// ---------------------------------------------------------------------------
// Detect Empty Tracks
// ---------------------------------------------------------------------------
export function detectEmptyTracks(timeline: TimelineCanvasState): TimelineIssue[] {
  const issues: TimelineIssue[] = []

  for (const track of Object.values(timeline.tracks)) {
    if (track.clipIds.length === 0) {
      issues.push({
        id: generateIssueId(),
        severity: 'info',
        type: 'EMPTY_TRACK',
        message: `Track "${track.name}" has no clips`,
        clipIds: [],
        trackIds: [track.id],
        autoFixable: true,
      })
    }
  }

  return issues
}

// ---------------------------------------------------------------------------
// Detect Missing Video
// ---------------------------------------------------------------------------
export function detectMissingVideo(timeline: TimelineCanvasState): TimelineIssue[] {
  const issues: TimelineIssue[] = []

  const hasVideo = Object.values(timeline.tracks).some(t => t.type === 'video' && t.clipIds.length > 0)

  if (!hasVideo && Object.values(timeline.tracks).length > 0) {
    issues.push({
      id: generateIssueId(),
      severity: 'error',
      type: 'MISSING_VIDEO',
      message: 'Timeline has no video content',
      clipIds: [],
      trackIds: [],
      autoFixable: false,
    })
  }

  return issues
}

// ---------------------------------------------------------------------------
// Analyze Track Density
// ---------------------------------------------------------------------------
export function analyzeTrackDensity(timeline: TimelineCanvasState): TrackDensity[] {
  return Object.values(timeline.tracks).map(track => {
    const clips = getTrackClipsSorted(timeline, track.id)
    const totalDuration = clips.reduce((sum, c) => sum + c.timing.duration, 0)
    const coverage = timeline.duration > 0 ? totalDuration / timeline.duration : 0

    // Calculate average gap
    let totalGap = 0
    let gapCount = 0
    for (let i = 1; i < clips.length; i++) {
      const gap = clips[i].timing.start - clips[i - 1].timing.end
      if (gap > 0) {
        totalGap += gap
        gapCount++
      }
    }
    const averageGap = gapCount > 0 ? totalGap / gapCount : 0

    return {
      trackId: track.id,
      trackName: track.name,
      clipCount: clips.length,
      totalDuration,
      coverage,
      averageGap,
    }
  })
}

// ---------------------------------------------------------------------------
// Analyze Track Imbalance
// ---------------------------------------------------------------------------
export function detectTrackImbalance(timeline: TimelineCanvasState): TimelineIssue[] {
  const issues: TimelineIssue[] = []
  const densities = analyzeTrackDensity(timeline)

  if (densities.length < 2) return issues

  const avgClipCount = densities.reduce((s, d) => s + d.clipCount, 0) / densities.length

  for (const density of densities) {
    if (density.clipCount > avgClipCount * 2 && density.clipCount > 5) {
      issues.push({
        id: generateIssueId(),
        severity: 'warning',
        type: 'TRACK_IMBALANCE',
        message: `Track "${density.trackName}" has ${density.clipCount} clips (avg: ${avgClipCount.toFixed(1)})`,
        clipIds: [],
        trackIds: [density.trackId],
        autoFixable: true,
      })
    }
  }

  return issues
}

// ---------------------------------------------------------------------------
// Full Analysis
// ---------------------------------------------------------------------------
export function analyzeTimeline(timeline: TimelineCanvasState): TimelineAnalysisReport {
  const overlaps = detectOverlaps(timeline)
  const gaps = detectGaps(timeline)
  const orphans = detectOrphanClips(timeline)
  const silentRegions = detectSilentRegions(timeline)
  const emptyTracks = detectEmptyTracks(timeline)
  const missingVideo = detectMissingVideo(timeline)
  const trackImbalance = detectTrackImbalance(timeline)
  const trackDensity = analyzeTrackDensity(timeline)

  const allIssues = [...overlaps, ...orphans, ...missingVideo]
  const allWarnings = [...gaps, ...trackImbalance]
  const allSuggestions = [...silentRegions, ...emptyTracks]

  // Calculate health score
  const errorCount = allIssues.length
  const warningCount = allWarnings.length
  const health = Math.max(0, 100 - (errorCount * 15) - (warningCount * 5))

  return {
    issues: allIssues,
    warnings: allWarnings,
    suggestions: allSuggestions,
    trackDensity,
    overallHealth: health,
    analyzedAt: Date.now(),
  }
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------
function getTrackClipsSorted(timeline: TimelineCanvasState, trackId: string): TimelineClip[] {
  const track = timeline.tracks[trackId]
  if (!track) return []

  return track.clipIds
    .map(id => timeline.clips[id])
    .filter((c): c is TimelineClip => c != null)
    .sort((a, b) => a.timing.start - b.timing.start)
}
