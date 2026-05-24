import api from '@/api/index'

export interface ExportConfig {
  sessionId: string
  preset: string
  resolution: string
  fps: number
  format: string
  videoCodec: string
  audioCodec: string
  watermarkEnabled: boolean
  videoBitrate: number
  audioBitrate: number
  maxDurationSec: number
  renderLocation: 'CLIENT' | 'SERVER'
  availablePresets: PresetInfo[]
}

export interface PresetInfo {
  name: string
  displayName: string
  resolution: string
  format: string
  watermark: boolean
  renderLocation: 'CLIENT' | 'SERVER'
}

export interface ClientExportSession {
  id: string
  tenantId: string
  workspaceId: string
  projectId: string
  userId: string
  exportType: string
  preset: string
  status: string
  progress: number
  resolution: string
  fps: number
  format: string
  watermarkEnabled: boolean
  outputUri: string | null
  artifactId: string | null
  downloadPath: string | null
  errorCode: string | null
  errorMessage: string | null
  createdAt: string
  updatedAt: string
}

export interface ProgressUpdate {
  sessionId: string
  status: string
  progress: number
}

export const ClientExportAPI = {
  async startSession(params: {
    projectId: string
    workspaceId?: string
    userId?: string
    tier?: string
    preset?: string
    timelineSnapshotId?: string
  }): Promise<ExportConfig> {
    const { data } = await api.post<ExportConfig>('/render/client-exports', {
      projectId: params.projectId,
      workspaceId: params.workspaceId,
      userId: params.userId,
      tier: params.tier || 'FREE',
      preset: params.preset,
      timelineSnapshotId: params.timelineSnapshotId,
    })
    return data
  },

  async updateProgress(sessionId: string, status: string, progress: number): Promise<ProgressUpdate> {
    const { data } = await api.post<ProgressUpdate>(
      `/render/client-exports/${sessionId}/progress`,
      { status, progress }
    )
    return data
  },

  async uploadAndComplete(
    sessionId: string,
    file: Blob,
    options?: { durationSeconds?: number; registerArtifact?: boolean }
  ) {
    const form = new FormData()
    const ext = file.type.includes('webm') ? 'webm' : 'mp4'
    form.append('file', file, `export.${ext}`)
    if (options?.durationSeconds != null) {
      form.append('durationSeconds', String(options.durationSeconds))
    }
    form.append('registerArtifact', String(options?.registerArtifact !== false))
    const { data } = await api.post(
      `/render/client-exports/${sessionId}/upload`,
      form,
      { headers: { 'Content-Type': 'multipart/form-data' } }
    )
    return data
  },

  async failSession(sessionId: string, errorCode: string, errorMessage: string) {
    const { data } = await api.post(
      `/render/client-exports/${sessionId}/fail`,
      { errorCode, errorMessage }
    )
    return data
  },

  async cancelSession(sessionId: string) {
    const { data } = await api.post(`/render/client-exports/${sessionId}/cancel`)
    return data
  },

  async getSession(sessionId: string): Promise<ClientExportSession> {
    const { data } = await api.get<ClientExportSession>(`/render/client-exports/${sessionId}`)
    return data
  },

  async listSessions(params?: { projectId?: string; limit?: number; offset?: number }): Promise<ClientExportSession[]> {
    const { data } = await api.get<ClientExportSession[]>('/render/client-exports', {
      params: { projectId: params?.projectId, limit: params?.limit || 50, offset: params?.offset || 0 },
    })
    return data
  },
}
