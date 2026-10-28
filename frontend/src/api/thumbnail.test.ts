import { afterEach, describe, expect, it, vi } from 'vitest'
import { cancelThumbnail, getThumbnail, readThumbnailImage, retryThumbnail, thumbnailExecutionSchema } from './thumbnail'
import api from './index'
vi.mock('./index', () => ({ default: { get: vi.fn() } }))
vi.mock('../auth/oidcConfig', () => ({ isOidcEnabled: () => false }))
const handle = { executionId: 'exe_123', generation: 2, state: 'COMPLETED', artifactId: 'artifact-1', failureCode: null, retryBlocked: false }
afterEach(() => { vi.restoreAllMocks(); vi.unstubAllGlobals() })
describe('thumbnail uses formal Execution and authenticated binary transport', () => {
  it('accepts the Execution handle and rejects the retired task lifecycle shape', () => {
    expect(thumbnailExecutionSchema.parse(handle)).toEqual(handle)
    expect(thumbnailExecutionSchema.safeParse({ taskId: 'old', status: 'COMPLETED' }).success).toBe(false)
    expect(thumbnailExecutionSchema.safeParse({ ...handle, generation: 0 }).success).toBe(false)
  })
  it('binds cancellation and retry to the current generation and exact escaped scope', async () => {
    const fetch = vi.fn().mockResolvedValue({ ok: true, json: async () => handle })
    vi.stubGlobal('fetch', fetch)
    await cancelThumbnail({ baseUrl: '' }, 'tenant', 'project', 'execution/one', 2)
    await retryThumbnail({ baseUrl: '' }, 'tenant', 'project', 'execution/one', 2)
    expect(fetch.mock.calls[0][0]).toBe('/api/tenants/tenant/projects/project/thumbnails/execution%2Fone/cancel')
    expect(JSON.parse(fetch.mock.calls[0][1].body)).toEqual({ generation: 2 })
    expect(JSON.parse(fetch.mock.calls[1][1].body)).toEqual({ generation: 2 })
    expect((await getThumbnail({ baseUrl: '' }, 'tenant', 'project', 'execution/one')).success).toBe(true)
  })
  it('reads image bytes through the existing authenticated API client and supports abort', async () => {
    const controller = new AbortController()
    const image = new Blob([new Uint8Array([1, 2, 3])], { type: 'image/png' })
    vi.mocked(api.get).mockResolvedValue({ data: image })
    expect(await readThumbnailImage({ baseUrl: '' }, 'tenant', 'project', 'execution', controller.signal)).toBe(image)
    expect(api.get).toHaveBeenCalledWith('/api/tenants/tenant/projects/project/thumbnails/execution/image', expect.objectContaining({ responseType: 'blob', signal: controller.signal, baseURL: '' }))
    vi.mocked(api.get).mockResolvedValue({ data: new Blob(['not media'], { type: 'text/html' }) })
    await expect(readThumbnailImage({ baseUrl: '' }, 'tenant', 'project', 'execution')).rejects.toThrow('Image content unavailable')
  })
})
