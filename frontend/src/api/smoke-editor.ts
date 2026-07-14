import api from './index'
import { getTenantId } from '@/utils/tenant'

const API_BASE = import.meta.env.VITE_API_BASE_URL || ''

export interface SmokeTimelineInput {
  projectId: string
  assetUri: string
  clipStart: number
  clipEnd: number
  subtitleText?: string
  effectKey?: string
  effectParams?: Record<string, unknown>
  profile?: string
}

export interface SmokeRenderJobResult {
  jobId: string
  status: string
  profile?: string
}

export interface SmokeJobStatus {
  jobId: string
  status: string
  artifactUri?: string
  errorMessage?: string
  createdAt?: string
}

export const SmokeEditorAPI = {
  async submitRenderJob(input: SmokeTimelineInput): Promise<SmokeRenderJobResult> {
    const timelineJson = buildTimelineJson(input)
    const snapResp = await api.post('/render/timeline-snapshots', {
      projectId: input.projectId,
      editorTimeline: timelineJson,
      schemaVersion: '2.0.0',
      ensureInternal: true,
    })
    const snapshotId = snapResp.data?.snapshotId

    const tenantId = getTenantId() || 'default'
    const jobResp = await api.post(`/tenants/${tenantId}/projects/${input.projectId}/render-jobs`, {
      projectId: input.projectId,
      timelineSnapshotId: snapshotId,
      profile: input.profile || 'default_1080p',
    })
    return {
      jobId: jobResp.data?.id,
      status: jobResp.data?.status,
      profile: jobResp.data?.profile,
    }
  },

  async getJobStatus(jobId: string): Promise<SmokeJobStatus> {
    const { data } = await api.get(`/render/jobs/${jobId}`)
    return {
      jobId: data?.id,
      status: data?.status,
      artifactUri: data?.artifactId,
      errorMessage: data?.errorMessage,
      createdAt: data?.createdAt,
    }
  },

  async getArtifacts(jobId: string): Promise<Array<{ id: string; storageUri: string; format?: string }>> {
    const { data } = await api.get(`/render/jobs/${jobId}/artifacts`)
    return data ?? []
  },
}

function buildTimelineJson(input: SmokeTimelineInput): Record<string, unknown> {
  const clips: Record<string, unknown>[] = [{
    id: 'clip-1',
    assetRef: { id: 'asset-1', uri: input.assetUri },
    timelineStart: 0.0,
    assetInPoint: input.clipStart,
    assetOutPoint: input.clipEnd,
    clipDuration: input.clipEnd - input.clipStart,
    effects: input.effectKey ? [{
      effectKey: input.effectKey,
      parameters: input.effectParams ?? {},
    }] : [],
  }]

  const tracks: Record<string, unknown>[] = [{
    id: 'track-video-1',
    name: 'Video 1',
    type: 'VIDEO',
    layer: 0,
    clips,
    muted: false,
    locked: false,
  }]

  const textOverlays: Record<string, unknown>[] = []
  if (input.subtitleText) {
    textOverlays.push({
      id: 'subtitle-1',
      text: input.subtitleText,
      startTime: input.clipStart,
      duration: input.clipEnd - input.clipStart,
    })
  }

  return {
    tracks,
    textOverlays,
    outputSpec: {
      resolution: '1920x1080',
      format: 'mp4',
    },
  }
}
