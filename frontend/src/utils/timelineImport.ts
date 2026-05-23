import type { Clip, TimelineState } from '@/types'

export interface EditorTimelinePayload {
  tracks?: TimelineState['tracks']
  clips?: Clip[]
  duration?: number
  currentTime?: number
  zoom?: number
  playing?: boolean
  schemaVersion?: string
  [key: string]: unknown
}

/**
 * Normalizes editor v2 JSON (from sync API or snapshot) into store state + clip catalog.
 */
export function parseEditorTimelinePayload(raw: string | EditorTimelinePayload): {
  state: TimelineState
  clips: Clip[]
} {
  const payload: EditorTimelinePayload =
    typeof raw === 'string' ? (JSON.parse(raw) as EditorTimelinePayload) : raw

  const clipList = Array.isArray(payload.clips) ? [...payload.clips] : []
  const tracks = Array.isArray(payload.tracks) ? payload.tracks : []

  const state: TimelineState = {
    tracks,
    duration: typeof payload.duration === 'number' ? payload.duration : 60,
    currentTime: typeof payload.currentTime === 'number' ? payload.currentTime : 0,
    zoom: typeof payload.zoom === 'number' ? payload.zoom : 1,
    playing: Boolean(payload.playing),
  }

  return { state, clips: clipList }
}

export function isDemoProjectId(projectId: string | undefined | null): boolean {
  return !!projectId && (projectId.startsWith('demo_') || projectId.startsWith('demo_project_'))
}
