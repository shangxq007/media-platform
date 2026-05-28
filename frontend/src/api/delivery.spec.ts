import { describe, it, expect, vi, beforeEach } from 'vitest'

vi.mock('./index', () => ({
  default: {
    get: vi.fn(),
    post: vi.fn(),
    patch: vi.fn(),
    delete: vi.fn(),
  },
}))

import api from './index'
import { DeliveryAPI } from './delivery'

describe('DeliveryAPI', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('listDestinations URL contains tenantId', async () => {
    vi.mocked(api.get).mockResolvedValue({ data: [] })

    await DeliveryAPI.listDestinations('t1')

    expect(api.get).toHaveBeenCalledWith('/tenants/t1/delivery/destinations')
  })

  it('createDestination URL contains tenantId', async () => {
    vi.mocked(api.post).mockResolvedValue({ data: { id: 'd1' } })

    await DeliveryAPI.createDestination('t1', {
      name: 'S3 Mirror',
      protocol: 'S3_MIRROR',
      config: {},
    })

    expect(api.post).toHaveBeenCalledWith(
      '/tenants/t1/delivery/destinations',
      expect.any(Object)
    )
  })

  it('updateDestination URL contains tenantId', async () => {
    vi.mocked(api.patch).mockResolvedValue({ data: { id: 'd1' } })

    await DeliveryAPI.updateDestination('t1', 'd1', { enabled: false })

    expect(api.patch).toHaveBeenCalledWith(
      '/tenants/t1/delivery/destinations/d1',
      expect.any(Object)
    )
  })

  it('deleteDestination URL contains tenantId', async () => {
    vi.mocked(api.delete).mockResolvedValue({ data: undefined })

    await DeliveryAPI.deleteDestination('t1', 'd1')

    expect(api.delete).toHaveBeenCalledWith('/tenants/t1/delivery/destinations/d1')
  })

  it('probeDestination URL contains tenantId', async () => {
    vi.mocked(api.post).mockResolvedValue({ data: { ok: true, message: 'OK' } })

    await DeliveryAPI.probeDestination('t1', 'd1')

    expect(api.post).toHaveBeenCalledWith(
      '/tenants/t1/delivery/destinations/d1/probe'
    )
  })

  it('listPolicies URL contains tenantId', async () => {
    vi.mocked(api.get).mockResolvedValue({ data: [] })

    await DeliveryAPI.listPolicies('t1', 'proj-1')

    expect(api.get).toHaveBeenCalledWith(
      '/tenants/t1/projects/proj-1/delivery/policies'
    )
  })

  it('createPolicy URL contains tenantId', async () => {
    vi.mocked(api.post).mockResolvedValue({ data: { policyId: 'p1' } })

    await DeliveryAPI.createPolicy('t1', 'proj-1', {
      destinationId: 'd1',
      triggerMode: 'AUTO',
    })

    expect(api.post).toHaveBeenCalledWith(
      '/tenants/t1/projects/proj-1/delivery/policies',
      expect.any(Object)
    )
  })

  it('listDeliveries URL contains tenantId', async () => {
    vi.mocked(api.get).mockResolvedValue({ data: [] })

    await DeliveryAPI.listDeliveries('t1', 'proj-1', 'j1')

    expect(api.get).toHaveBeenCalledWith(
      '/tenants/t1/projects/proj-1/render-jobs/j1/deliveries'
    )
  })

  it('triggerDeliver URL contains tenantId', async () => {
    vi.mocked(api.post).mockResolvedValue({ data: { deliveryJobId: 'dj1' } })

    await DeliveryAPI.triggerDeliver('t1', 'proj-1', 'j1', 'd1')

    expect(api.post).toHaveBeenCalledWith(
      '/tenants/t1/projects/proj-1/render-jobs/j1/deliver',
      null,
      expect.objectContaining({ params: { destinationId: 'd1' } })
    )
  })

  it('retryDelivery URL contains tenantId', async () => {
    vi.mocked(api.post).mockResolvedValue({ data: { deliveryJobId: 'dj1', status: 'QUEUED' } })

    await DeliveryAPI.retryDelivery('t1', 'proj-1', 'j1', 'dj1')

    expect(api.post).toHaveBeenCalledWith(
      '/tenants/t1/projects/proj-1/render-jobs/j1/deliveries/dj1/retry'
    )
  })
})
