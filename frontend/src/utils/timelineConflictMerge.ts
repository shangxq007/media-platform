import type { Clip, Track, TrackClip, TimelineState } from '@/types'
import type { EditorTimelinePayload } from './timelineImport'
import { hashTimelineJson } from './timelineSyncHash'

export type ConflictResolution = 'keep-local' | 'use-server' | 'merge'

export interface TimelineConflictSummary {
  projectId: string
  snapshotId: string
  serverRevision: number
  headRevisionId: string | null
  headRevisionNumber: number | null
  baselineRevisionNumber: number | null
  baselineRevisionId: string | null
  localTrackCount: number
  serverTrackCount: number
  localClipCount: number
  serverClipCount: number
  baselineHash: string | null
  localHash: string
  serverHash: string
}

export interface PendingTimelineConflict extends TimelineConflictSummary {
  serverEditorJson: string
  serverInternalTimelineJson: string | null
  localEditorJson: string
  baselineEditorJson: string | null
}

export function buildCanonicalEditorJson(payload: EditorTimelinePayload): string {
  const normalized: EditorTimelinePayload = {
    tracks: payload.tracks ?? [],
    clips: payload.clips ?? [],
    duration: payload.duration ?? 60,
    currentTime: payload.currentTime ?? 0,
    zoom: payload.zoom ?? 1,
    playing: false,
    schemaVersion: '2.0.0',
  }
  return JSON.stringify(normalized)
}

export function hashEditorPayload(payload: EditorTimelinePayload): string {
  return hashTimelineJson(buildCanonicalEditorJson(payload))
}

/**
 * True when local edits diverge from server and server differs from last synced baseline.
 */
export function detectTimelineConflict(
  isDirty: boolean,
  baselineHash: string | null,
  localHash: string,
  serverHash: string
): boolean {
  if (!isDirty) {
    return false
  }
  if (localHash === serverHash) {
    return false
  }
  if (!baselineHash) {
    return true
  }
  if (localHash === baselineHash) {
    // Local unchanged since sync but server moved — fast-forward, not a merge conflict.
    return false
  }
  return serverHash !== baselineHash
}

export function shouldFastForwardServer(
  isDirty: boolean,
  baselineHash: string | null,
  localHash: string,
  serverHash: string
): boolean {
  return isDirty && !!baselineHash && localHash === baselineHash && serverHash !== baselineHash
}

function trackClipKey(tc: TrackClip): string {
  return tc.id
}

function mergeTrackClips(
  baseClips: TrackClip[],
  localClips: TrackClip[],
  remoteClips: TrackClip[]
): TrackClip[] {
  const baseMap = new Map(baseClips.map(c => [trackClipKey(c), c]))
  const result = new Map<string, TrackClip>()

  for (const remote of remoteClips) {
    result.set(trackClipKey(remote), { ...remote })
  }

  for (const local of localClips) {
    const base = baseMap.get(trackClipKey(local))
    if (!base || !trackClipsEqual(base, local)) {
      result.set(trackClipKey(local), { ...local })
    }
  }

  for (const remote of remoteClips) {
    const key = trackClipKey(remote)
    if (!baseMap.has(key) && !localClips.some(l => trackClipKey(l) === key)) {
      result.set(key, { ...remote })
    }
  }

  return Array.from(result.values()).sort((a, b) => a.start - b.start)
}

function trackClipsEqual(a: TrackClip, b: TrackClip): boolean {
  return (
    a.start === b.start &&
    a.duration === b.duration &&
    a.clipId === b.clipId &&
    a.clipStart === b.clipStart &&
    a.clipEnd === b.clipEnd
  )
}

function mergeClipCatalog(base: Clip[], local: Clip[], remote: Clip[]): Clip[] {
  const map = new Map<string, Clip>()
  for (const c of base) {
    map.set(c.id, { ...c })
  }
  for (const c of remote) {
    map.set(c.id, { ...c })
  }
  for (const c of local) {
    map.set(c.id, { ...c })
  }
  return Array.from(map.values())
}

/**
 * Three-way merge: remote structure + local edits on top of baseline + server-only additions.
 */
export function mergeEditorTimelines(
  baseline: EditorTimelinePayload,
  local: EditorTimelinePayload,
  remote: EditorTimelinePayload
): { state: TimelineState; clips: Clip[] } {
  const baseTracks = baseline.tracks ?? []
  const localTracks = local.tracks ?? []
  const remoteTracks = remote.tracks ?? []

  const trackIds = new Set<string>([
    ...baseTracks.map(t => t.id),
    ...localTracks.map(t => t.id),
    ...remoteTracks.map(t => t.id),
  ])

  const mergedTracks: Track[] = []
  for (const trackId of trackIds) {
    const baseTrack = baseTracks.find(t => t.id === trackId)
    const localTrack = localTracks.find(t => t.id === trackId)
    const remoteTrack = remoteTracks.find(t => t.id === trackId)
    const template = localTrack || remoteTrack || baseTrack
    if (!template) {
      continue
    }
    mergedTracks.push({
      ...template,
      clips: mergeTrackClips(
        baseTrack?.clips ?? [],
        localTrack?.clips ?? [],
        remoteTrack?.clips ?? []
      ),
    })
  }

  const mergedClips = mergeClipCatalog(
    baseline.clips ?? [],
    local.clips ?? [],
    remote.clips ?? []
  )

  const duration = Math.max(
    local.duration ?? 0,
    remote.duration ?? 0,
    baseline.duration ?? 0,
    60
  )

  const state: TimelineState = {
    tracks: mergedTracks,
    duration,
    currentTime: Math.min(local.currentTime ?? 0, duration),
    zoom: local.zoom ?? remote.zoom ?? 1,
    playing: false,
  }

  return { state, clips: mergedClips }
}
