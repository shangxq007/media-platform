// =============================================================================
// Snap System
// =============================================================================
// Provides snap-to-grid and snap-to-adjacent-clip functionality.
// Used by drag and trim operations to align clips precisely.
// =============================================================================

import type { TimelineClip, TimelineCanvasState } from '../model/timeline'

// ---------------------------------------------------------------------------
// Snap Configuration
// ---------------------------------------------------------------------------
export interface SnapConfig {
  readonly enabled: boolean
  readonly gridSnap: boolean
  readonly clipSnap: boolean
  readonly threshold: number  // seconds - snap activates within this distance
}

export const DEFAULT_SNAP_CONFIG: SnapConfig = {
  enabled: true,
  gridSnap: true,
  clipSnap: true,
  threshold: 0.1,
}

// ---------------------------------------------------------------------------
// Snap Target
// ---------------------------------------------------------------------------
export interface SnapTarget {
  readonly time: number
  readonly type: 'grid' | 'clip-start' | 'clip-end'
  readonly sourceId?: string  // clip id for clip snaps
}

// ---------------------------------------------------------------------------
// Snap Result
// ---------------------------------------------------------------------------
export interface SnapResult {
  readonly snappedTime: number
  readonly snapped: boolean
  readonly target?: SnapTarget
}

// ---------------------------------------------------------------------------
// Grid Snap Points
// ---------------------------------------------------------------------------
function getGridSnapPoints(duration: number, zoomLevel: number): number[] {
  const points: number[] = []
  // Grid interval based on zoom: 1x=1s, 2x=0.5s, 4x=0.25s
  const interval = 1 / zoomLevel

  for (let t = 0; t <= duration + interval; t += interval) {
    points.push(Math.round(t * 1000) / 1000) // avoid floating point issues
  }
  return points
}

// ---------------------------------------------------------------------------
// Clip Snap Points
// ---------------------------------------------------------------------------
function getClipSnapPoints(
  clips: Record<string, TimelineClip>,
  excludeClipId?: string
): SnapTarget[] {
  const targets: SnapTarget[] = []

  for (const clip of Object.values(clips)) {
    if (clip.id === excludeClipId) continue

    targets.push({
      time: clip.timing.start,
      type: 'clip-start',
      sourceId: clip.id,
    })
    targets.push({
      time: clip.timing.end,
      type: 'clip-end',
      sourceId: clip.id,
    })
  }

  return targets
}

// ---------------------------------------------------------------------------
// Find Nearest Snap
// ---------------------------------------------------------------------------
export function findNearestSnap(
  time: number,
  timeline: TimelineCanvasState,
  zoomLevel: number,
  excludeClipId?: string,
  config: SnapConfig = DEFAULT_SNAP_CONFIG
): SnapResult {
  if (!config.enabled) {
    return { snappedTime: time, snapped: false }
  }

  const allTargets: SnapTarget[] = []

  // Grid snap points
  if (config.gridSnap) {
    const gridPoints = getGridSnapPoints(timeline.duration, zoomLevel)
    for (const t of gridPoints) {
      allTargets.push({ time: t, type: 'grid' })
    }
  }

  // Clip snap points
  if (config.clipSnap) {
    const clipTargets = getClipSnapPoints(timeline.clips, excludeClipId)
    allTargets.push(...clipTargets)
  }

  // Find nearest target within threshold
  let nearestTarget: SnapTarget | undefined
  let nearestDist = config.threshold

  for (const target of allTargets) {
    const dist = Math.abs(time - target.time)
    if (dist < nearestDist) {
      nearestDist = dist
      nearestTarget = target
    }
  }

  if (nearestTarget) {
    return {
      snappedTime: nearestTarget.time,
      snapped: true,
      target: nearestTarget,
    }
  }

  return { snappedTime: time, snapped: false }
}

// ---------------------------------------------------------------------------
// Snap for Trim Operations
// ---------------------------------------------------------------------------
export function findTrimSnap(
  time: number,
  timeline: TimelineCanvasState,
  zoomLevel: number,
  clipId: string,
  trimSide: 'start' | 'end',
  config: SnapConfig = DEFAULT_SNAP_CONFIG
): SnapResult {
  // For trim, we only snap to the opposite edge of the same clip
  // and to other clip edges
  const clip = timeline.clips[clipId]
  if (!clip) {
    return { snappedTime: time, snapped: false }
  }

  // Add the opposite edge as a snap target
  const additionalTargets: SnapTarget[] = []
  if (trimSide === 'start') {
    // When trimming start, snap to end edge is not useful
    // But we should not snap to own start
  } else {
    // When trimming end, snap to start edge is not useful
  }

  return findNearestSnap(time, timeline, zoomLevel, clipId, config)
}
