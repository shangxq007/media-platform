export type RenderLocation = 'CLIENT' | 'SERVER'

export interface EditorClip {
  id: string
  name?: string
  sourceUrl?: string
  duration?: number
}

export interface TrackClipRef {
  id: string
  clipId: string
  start: number
  duration: number
  clipStart?: number
  clipEnd?: number
}

export interface EditorTrack {
  id: string
  name?: string
  type?: string
  clips?: TrackClipRef[]
}

export interface ParsedTimeline {
  duration: number
  width: number
  height: number
  fps: number
  clips: EditorClip[]
  tracks: EditorTrack[]
}

export interface ClientExportProgress {
  phase: 'preparing' | 'rendering' | 'encoding' | 'done' | 'error'
  progress: number
  message?: string
}

export interface ClientExportResult {
  blob: Blob
  mimeType: string
  durationSeconds: number
}
