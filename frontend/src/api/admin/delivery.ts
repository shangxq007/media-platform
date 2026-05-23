import api from '../index'

export interface AdminDeliveryJob {
  id: string
  tenantId: string
  projectId: string
  renderJobId: string
  destinationId: string
  status: string
  sourceUri?: string | null
  remoteUri?: string | null
  bytesTransferred?: number | null
  attemptCount?: number | null
  errorCode?: string | null
  errorMessage?: string | null
  createdAt?: string
  completedAt?: string | null
}

export interface AdminDeliveryDestination {
  id: string
  tenantId: string
  name: string
  protocol: string
  enabled: boolean
}

export const DeliveryAdminAPI = {
  async listJobs(params: {
    tenantId?: string
    status?: string
    page?: number
    size?: number
  }): Promise<AdminDeliveryJob[]> {
    const { data } = await api.get<AdminDeliveryJob[]>('/admin/delivery/jobs', { params })
    return data
  },

  async listDestinations(tenantId?: string): Promise<AdminDeliveryDestination[]> {
    const { data } = await api.get<AdminDeliveryDestination[]>('/admin/delivery/destinations', {
      params: tenantId ? { tenantId } : undefined,
    })
    return data
  },

  async retryJob(deliveryJobId: string): Promise<AdminDeliveryJob> {
    const { data } = await api.post<AdminDeliveryJob>(`/admin/delivery/jobs/${deliveryJobId}/retry`)
    return data
  },
}
