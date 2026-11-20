import type { AssetGateway, AssetSourcePin } from '../../product/timeline/gateways'
import { artifactId, exactMediaTime, mediaAssetId, mediaStreamId, sourceContentDigest } from '../../product/timeline/types'
import { gatewayFailure, responseValidationFailure } from './gateway-error'
import { versionlessApi } from './versionless-api'
import { z } from 'zod'

const Source = z.object({
  artifactId: z.string().min(1).max(64),
  mediaAssetId: z.string().min(1).max(64),
  name: z.string().min(1).max(256),
  contentDigest: z.string().regex(/^[0-9a-fA-F]{64}$/),
  byteLength: z.number().int().positive(),
  duration: z.string().regex(/^[0-9]+(?:\/[1-9][0-9]*)?$/),
  streams: z.array(z.object({ streamId: z.string().min(1).max(64), kind: z.enum(['VIDEO', 'AUDIO']),
    duration: z.string().regex(/^[0-9]+(?:\/[1-9][0-9]*)?$/) })).min(1),
})

function pin(value: z.infer<typeof Source>): AssetSourcePin {
  if (value.mediaAssetId !== value.artifactId) throw new Error('Artifact-native source identity mismatch')
  return {
    artifactId: artifactId(value.artifactId), mediaAssetId: mediaAssetId(value.mediaAssetId),
    name: value.name, contentDigest: sourceContentDigest(value.contentDigest),
    byteLength: value.byteLength, duration: exactMediaTime(value.duration),
    streams: value.streams.map(stream => ({ streamId: mediaStreamId(stream.streamId), kind: stream.kind,
      duration: exactMediaTime(stream.duration) })),
    mediaStreamId: mediaStreamId(value.streams.find(stream => stream.kind === 'VIDEO')?.streamId ?? value.streams[0].streamId),
  }
}

function path(tenantId: string, projectId: string) {
  return `/tenants/${encodeURIComponent(tenantId)}/projects/${encodeURIComponent(projectId)}/source-media`
}

function contentPath(tenantId: string, projectId: string, artifactId: string) {
  return `/tenants/${encodeURIComponent(tenantId)}/projects/${encodeURIComponent(projectId)}/source-artifacts/${encodeURIComponent(artifactId)}/content`
}

export const assetGateway: AssetGateway = {
  async listSourcePins(tenantId, projectId) {
    try {
      const response = await versionlessApi.get(path(tenantId, projectId))
      const parsed = Source.array().safeParse(response.data)
      if (!parsed.success) return responseValidationFailure('Source browser response did not contain accepted Artifact pins.')
      return { ok: true, value: parsed.data.map(pin) }
    } catch (error) { return gatewayFailure(error) }
  },
  async uploadSource(tenantId, projectId, file, idempotencyKey, onProgress) {
    if (!tenantId || !projectId || !file || !/^[A-Za-z0-9._:-]{1,128}$/.test(idempotencyKey)) {
      return responseValidationFailure('Project, file and bounded upload command ID are required.')
    }
    try {
      const body = new FormData()
      body.append('file', file)
      const response = await versionlessApi.post(path(tenantId, projectId), body, {
        headers: { 'Content-Type': undefined, 'Idempotency-Key': idempotencyKey },
        onUploadProgress: event => { if (event.total) onProgress?.(Math.round(100 * event.loaded / event.total)) },
      })
      const parsed = Source.safeParse(response.data)
      if (!parsed.success) return responseValidationFailure('Source upload response did not contain a valid logical Artifact pin.')
      return { ok: true, value: pin(parsed.data) }
    } catch (error) { return gatewayFailure(error) }
  },
  async readSourceContent(tenantId, projectId, artifactId, signal) {
    if (!tenantId || !projectId || !artifactId) {
      return responseValidationFailure('Project and source Artifact identity are required.')
    }
    try {
      const response = await versionlessApi.get(contentPath(tenantId, projectId, artifactId), { responseType: 'blob', signal })
      const data = response.data
      if (!(data instanceof Blob) || data.size === 0) {
        return responseValidationFailure('Source media response did not contain bytes.')
      }
      if (!/^(video|audio)\//.test(data.type)) {
        return responseValidationFailure('Source media response was not an audio or video representation.')
      }
      return { ok: true, value: data }
    } catch (error) { return gatewayFailure(error) }
  },
}
