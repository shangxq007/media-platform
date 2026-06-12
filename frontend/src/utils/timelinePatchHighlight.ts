import { TimelineRevisionAPI, type TimelineEntityChange, type TimelinePatchPath } from '@/api/timelineRevision'
import type { Clip, Track, TrackClip } from '@/types'

interface TimelineStoreLike {
  state: { tracks: Track[] }
  clips: Clip[]
  setPatchHighlightClipIds(ids: string[]): void
  clearPatchHighlightClipIds(): void
}

interface InternalTimelineIndex {
  clipIdsByTrackIndex: Map<number, string[]>
  trackIdsByIndex: Map<number, string>
}

function buildInternalIndex(internalTimelineJson: string | null | undefined): InternalTimelineIndex | null {
  if (!internalTimelineJson?.trim()) {
    return null
  }
  try {
    const root = JSON.parse(internalTimelineJson) as {
      composition?: { tracks?: Array<{ id?: string; clips?: Array<{ id?: string }> }> }
    }
    const tracks = root.composition?.tracks
    if (!Array.isArray(tracks)) {
      return null
    }
    const clipIdsByTrackIndex = new Map<number, string[]>()
    const trackIdsByIndex = new Map<number, string>()
    tracks.forEach((track, trackIndex) => {
      if (track.id) {
        trackIdsByIndex.set(trackIndex, track.id)
      }
      const clipIds: string[] = []
      if (Array.isArray(track.clips)) {
        track.clips.forEach(clip => {
          if (clip?.id) {
            clipIds.push(clip.id)
          }
        })
      }
      clipIdsByTrackIndex.set(trackIndex, clipIds)
    })
    return { clipIdsByTrackIndex, trackIdsByIndex }
  } catch {
    return null
  }
}

/** Resolve Internal JSON pointer with numeric indices, e.g. /composition/tracks/0/clips/1 */
export function resolveInternalClipIdFromPath(
  path: string,
  index: InternalTimelineIndex | null
): string | null {
  if (!index) {
    return null
  }
  const trackIdxMatch = path.match(/\/composition\/tracks\/(\d+)(?:\/|$)/)
  if (!trackIdxMatch) {
    return null
  }
  const trackIndex = parseInt(trackIdxMatch[1], 10)
  const clipIdxMatch = path.match(/\/composition\/tracks\/\d+\/clips\/(\d+)(?:\/|$)/)
  if (clipIdxMatch) {
    const clipIndex = parseInt(clipIdxMatch[1], 10)
    const clips = index.clipIdsByTrackIndex.get(trackIndex)
    if (clips && clipIndex >= 0 && clipIndex < clips.length) {
      return clips[clipIndex]
    }
    return null
  }
  const trackId = index.trackIdsByIndex.get(trackIndex)
  return trackId ?? null
}

/** Extract entity ids from RFC6902 paths (named ids + numeric index via Internal JSON). */
export function extractEntityIdsFromPatchPaths(
  paths: TimelinePatchPath[],
  internalTimelineJson?: string | null
): string[] {
  const ids = new Set<string>()
  const index = buildInternalIndex(internalTimelineJson ?? null)

  for (const p of paths) {
    const named = p.path.matchAll(/\/(?:clips|tracks|assets)\/([a-zA-Z][a-zA-Z0-9_-]*)/g)
    for (const m of named) {
      ids.add(m[1])
    }
    const fromIndex = resolveInternalClipIdFromPath(p.path, index)
    if (fromIndex) {
      ids.add(fromIndex)
    }
    if (!fromIndex && index) {
      const trackOnly = p.path.match(/\/composition\/tracks\/(\d+)(?:\/|$)/)
      if (trackOnly && !p.path.includes('/clips/')) {
        const ti = parseInt(trackOnly[1], 10)
        const trackClips = index.clipIdsByTrackIndex.get(ti) ?? []
        trackClips.forEach(id => ids.add(id))
      }
    }
  }
  return [...ids]
}

/** Map internal / catalog entity ids to editor track-clip instance ids. */
export function resolveEditorTrackClipIds(
  entityIds: string[],
  tracks: Track[],
  catalogClips: Clip[]
): string[] {
  const out = new Set<string>()
  const idSet = new Set(entityIds.filter(Boolean))

  for (const track of tracks) {
    if (idSet.has(track.id)) {
      track.clips.forEach(tc => out.add(tc.id))
    }
    for (const tc of track.clips) {
      if (idSet.has(tc.id) || idSet.has(tc.clipId)) {
        out.add(tc.id)
      }
    }
  }

  for (const clip of catalogClips) {
    if (!idSet.has(clip.id)) {
      continue
    }
    for (const track of tracks) {
      for (const tc of track.clips) {
        if (tc.clipId === clip.id) {
          out.add(tc.id)
        }
      }
    }
  }

  return [...out]
}

export function describeTrackClipLabel(
  trackClipId: string,
  tracks: Track[],
  catalogClips: Clip[]
): string {
  for (const track of tracks) {
    const tc = track.clips.find(c => c.id === trackClipId)
    if (tc) {
      const name = catalogClips.find(c => c.id === tc.clipId)?.name ?? tc.clipId
      return `${track.name} · ${name}`
    }
  }
  return trackClipId
}

export function buildHighlightLabels(
  trackClipIds: string[],
  tracks: Track[],
  catalogClips: Clip[]
): { trackClipId: string; label: string }[] {
  return trackClipIds.map(id => ({
    trackClipId: id,
    label: describeTrackClipLabel(id, tracks, catalogClips),
  }))
}

export function findTrackClipById(tracks: Track[], trackClipId: string): TrackClip | null {
  for (const track of tracks) {
    const tc = track.clips.find(c => c.id === trackClipId)
    if (tc) {
      return tc
    }
  }
  return null
}

export function resolveHighlightsFromCompare(
  entityChanges: TimelineEntityChange[],
  patchPaths: TimelinePatchPath[],
  tracks: Track[],
  catalogClips: Clip[],
  internalTimelineJson?: string | null
): string[] {
  const fromEntities = entityChanges
    .filter(e => (e.kind === 'clip' || e.kind === 'track') && e.entityId)
    .map(e => e.entityId)
  const fromPaths = extractEntityIdsFromPatchPaths(patchPaths, internalTimelineJson)
  return resolveEditorTrackClipIds([...fromEntities, ...fromPaths], tracks, catalogClips)
}

export async function loadRevisionInternalJson(
  projectId: string,
  revisionId: string
): Promise<string | null> {
  try {
    const snap = await TimelineRevisionAPI.revisionSnapshot(projectId, revisionId)
    return snap.internalTimelineJson ?? null
  } catch {
    return null
  }
}

export async function loadConflictClipHighlights(
  projectId: string,
  baselineRevisionId: string,
  headRevisionId: string,
  timelineStore: TimelineStoreLike,
  serverInternalTimelineJson?: string | null
): Promise<void> {
  try {
    const cmp = await TimelineRevisionAPI.compare(projectId, baselineRevisionId, headRevisionId)
    const ids = resolveHighlightsFromCompare(
      cmp.entityChanges,
      cmp.patchPaths ?? [],
      timelineStore.state.tracks,
      timelineStore.clips,
      serverInternalTimelineJson ?? null
    )
    timelineStore.setPatchHighlightClipIds(ids)
  } catch {
    timelineStore.clearPatchHighlightClipIds()
  }
}

export function applyPatchPathHighlights(
  patchPaths: TimelinePatchPath[],
  entityChanges: TimelineEntityChange[],
  timelineStore: TimelineStoreLike,
  internalTimelineJson?: string | null
): void {
  const ids = resolveHighlightsFromCompare(
    entityChanges,
    patchPaths,
    timelineStore.state.tracks,
    timelineStore.clips,
    internalTimelineJson
  )
  timelineStore.setPatchHighlightClipIds(ids)
}
