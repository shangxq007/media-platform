import { afterEach, describe, expect, it, vi } from 'vitest'
import api from './index'
import { DeliveryAPI, Delivery } from './delivery'

const record = { deliveryId: 'dlv-1', projectId: 'p', sourceRenderJobId: 'rj-1', timelineRevisionId: 'rev', artifactId: 'a', status: 'READY', createdAt: '2026-11-10T00:00:00Z', resultPath: '/api/projects/p/deliveries/dlv-1/result' }
afterEach(() => vi.restoreAllMocks())
describe('formal delivery transport', () => {
  it('preserves independent identity and sends a scoped request with idempotency header', async () => {
    const post = vi.spyOn(api, 'post').mockResolvedValue({ data: record })
    expect((await DeliveryAPI.create('p', { sourceRenderJobId: 'rj-1' }, 'key')).deliveryId).toBe('dlv-1')
    expect(post).toHaveBeenCalledWith('/projects/p/deliveries', { sourceRenderJobId: 'rj-1' }, { headers: { 'Idempotency-Key': 'key' } })
  })
  it('queries scoped records and downloads through the authenticated result route', async () => {
    const get = vi.spyOn(api, 'get').mockResolvedValueOnce({ data: [record] }).mockResolvedValueOnce({ data: record }).mockResolvedValueOnce({ data: new Blob(['media']) })
    expect(await DeliveryAPI.list('p')).toEqual([record])
    expect(await DeliveryAPI.get('p', 'dlv-1')).toEqual(record)
    expect(await (await DeliveryAPI.download('p', 'dlv-1')).text()).toBe('media')
    expect(get).toHaveBeenLastCalledWith('/projects/p/deliveries/dlv-1/result', { responseType: 'blob' })
  })
  it('rejects legacy execution states and absolute bearer links', () => {
    expect(Delivery.safeParse({ ...record, status: 'PROCESSING' }).success).toBe(false)
    expect(Delivery.safeParse({ ...record, resultPath: 'https://storage.example/private-token' }).success).toBe(false)
  })
})
