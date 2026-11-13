import { afterEach, describe, expect, it, vi } from 'vitest'
import { versionlessApi } from './versionless-api'
import { assetGateway } from './asset.gateway'
import { projectId } from '../../product/timeline/types'

const source = { artifactId: 'art-1', mediaAssetId: 'art-1', name: 'camera.mp4',
  contentDigest: 'a'.repeat(64), byteLength: 1024, duration: '3/1',
  streams: [{ streamId: 'stream-1', kind: 'VIDEO', duration: '3/1' }] }
afterEach(() => vi.restoreAllMocks())

describe('Artifact-native source browser', () => {
  it('lists only validated logical pins in the scoped authenticated route', async () => {
    const get = vi.spyOn(versionlessApi, 'get').mockResolvedValue({ data: [source] })
    const result = await assetGateway.listSourcePins('tenant-1', projectId('project-1'))
    expect(result.ok).toBe(true)
    if (result.ok) expect(result.value[0]).toMatchObject({ name: 'camera.mp4', artifactId: 'art-1', mediaAssetId: 'art-1' })
    expect(versionlessApi.getUri({ url: get.mock.calls[0][0] })).toBe('/api/tenants/tenant-1/projects/project-1/source-media')
  })
  it('uploads a real File with one bounded idempotency key and reports transport progress', async () => {
    const post = vi.spyOn(versionlessApi, 'post').mockImplementation(async (_url, _body, config) => {
      config?.onUploadProgress?.({ loaded: 50, total: 100 } as Parameters<NonNullable<typeof config.onUploadProgress>>[0])
      return { data: source }
    })
    const progress: number[] = []
    const result = await assetGateway.uploadSource('tenant-1', projectId('project-1'), new File(['media'], 'camera.mp4', { type: 'video/mp4' }), 'source-key', value => progress.push(value))
    expect(result.ok).toBe(true)
    expect(progress).toEqual([50])
    expect(post.mock.calls[0][1]).toBeInstanceOf(FormData)
    expect(post.mock.calls[0][2]?.headers).toMatchObject({ 'Idempotency-Key': 'source-key' })
  })
  it('rejects a source identity that is not the accepted Artifact', async () => {
    vi.spyOn(versionlessApi, 'get').mockResolvedValue({ data: [{ ...source, mediaAssetId: 'fake-1' }] })
    const result = await assetGateway.listSourcePins('tenant-1', projectId('project-1'))
    expect(result.ok).toBe(false)
  })
})
