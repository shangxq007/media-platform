import api from '@/api/index'

export interface ClientExportSessionResponse {
  sessionId: string
  uploadUrl: string
  status: string
}

export interface ClientExportCompleteResponse {
  sessionId: string
  status: string
  storageUri: string
  artifactId: string
  downloadUrl: string
}

export const ClientExportAPI = {
  async startSession(projectId: string, timelineSnapshotId: string, preset: string) {
    const { data } = await api.post<ClientExportSessionResponse>('/render/client-exports', {
      projectId,
      timelineSnapshotId,
      preset,
    })
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
    const { data } = await api.post<ClientExportCompleteResponse>(
      `/render/client-exports/${sessionId}/upload`,
      form,
      { headers: { 'Content-Type': 'multipart/form-data' } }
    )
    return data
  },
}
