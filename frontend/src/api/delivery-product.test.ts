import { afterEach, describe, expect, it, vi } from 'vitest'
import { versionlessApi as api } from './app/versionless-api'
import { DeliveryAPI, Delivery } from './delivery'

const record = { deliveryId: 'dlv-1', projectId: 'p', sourceRenderJobId: 'rj-1', timelineRevisionId: 'rev', artifactId: 'a', status: 'READY', createdAt: '2026-11-10T00:00:00Z', resultPath: '/api/projects/p/deliveries/dlv-1/result' }
afterEach(() => vi.restoreAllMocks())
describe('formal delivery transport', () => {
  it('resolves the actual versionless backend URL through the existing authenticated transport', async () => {
    const post = vi.spyOn(api, 'post').mockResolvedValue({ data: record })
    await DeliveryAPI.create('p', { sourceRenderJobId: 'rj-1' }, 'key')
    const url = post.mock.calls[0][0]
    expect(api.getUri({ url })).toBe('/api/projects/p/deliveries')
    const get = vi.spyOn(api, 'get').mockResolvedValue({ data: new Blob(['media']) })
    await DeliveryAPI.download('p', 'dlv-1')
    expect(api.getUri({ url: get.mock.calls[0][0] })).toBe('/api/projects/p/deliveries/dlv-1/result')
  })
  it('preserves independent identity and sends a scoped request with idempotency header', async () => {
    const post = vi.spyOn(api, 'post').mockResolvedValue({ data: record })
    expect((await DeliveryAPI.create('p', { sourceRenderJobId: 'rj-1' }, 'key')).deliveryId).toBe('dlv-1')
    expect(post).toHaveBeenCalledWith('/projects/p/deliveries', { sourceRenderJobId: 'rj-1' }, expect.objectContaining({ signal: expect.any(AbortSignal), headers: { 'Idempotency-Key': 'key' } }))
  })
  it('queries scoped records and downloads through the authenticated result route', async () => {
    const get = vi.spyOn(api, 'get').mockResolvedValueOnce({ data: [record] }).mockResolvedValueOnce({ data: record }).mockResolvedValueOnce({ data: new Blob(['media']) })
    expect(await DeliveryAPI.list('p')).toEqual([record])
    expect(await DeliveryAPI.get('p', 'dlv-1')).toEqual(record)
    expect(await (await DeliveryAPI.download('p', 'dlv-1')).text()).toBe('media')
    expect(get).toHaveBeenLastCalledWith('/projects/p/deliveries/dlv-1/result', expect.objectContaining({ signal: expect.any(AbortSignal), responseType: 'blob' }))
  })
  it('rejects legacy execution states and absolute bearer links', () => {
    expect(Delivery.safeParse({ ...record, status: 'PROCESSING' }).success).toBe(false)
    expect(Delivery.safeParse({ ...record, resultPath: 'https://storage.example/private-token' }).success).toBe(false)
  })
})
