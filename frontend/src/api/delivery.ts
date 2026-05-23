import api from './index'

export type DeliveryProtocol = 'S3_MIRROR' | 'SFTP' | 'WEBDAV' | 'SMB' | 'HTTPS_PUT'

export interface DeliveryDestination {
  id: string
  tenantId: string
  name: string
  protocol: DeliveryProtocol | string
  enabled: boolean
  credentialRef?: string | null
  credentialsConfigured?: boolean
}

export interface DeliveryJob {
  id: string
  renderJobId: string
  destinationId: string
  status: string
  sourceUri?: string | null
  remoteUri?: string | null
  bytesTransferred?: number | null
  errorMessage?: string | null
}

export interface CreateDeliveryDestinationRequest {
  name: string
  protocol: DeliveryProtocol | string
  config: Record<string, unknown>
  /** e.g. vault:media-platform/delivery/tenants/t1/destinations/dst_x */
  credentialRef?: string
  credentials?: Record<string, string>
  enabled?: boolean
}

export interface DeliveryPolicy {
  id: string
  tenantId: string
  projectId: string
  destinationId: string
  artifactSelector: string
  pathTemplate: string
  triggerMode: string
  enabled: boolean
}

export interface CreateDeliveryPolicyRequest {
  destinationId: string
  artifactSelector?: string
  pathTemplate?: string
  triggerMode?: 'AUTO' | 'MANUAL'
}

export interface ProbeDestinationResponse {
  ok: boolean
  message: string
}

export const DeliveryAPI = {
  async listDestinations(tenantId: string): Promise<DeliveryDestination[]> {
    const { data } = await api.get(`/tenants/${tenantId}/delivery/destinations`)
    return data
  },

  async createDestination(
    tenantId: string,
    body: CreateDeliveryDestinationRequest
  ): Promise<DeliveryDestination> {
    const { data } = await api.post(`/tenants/${tenantId}/delivery/destinations`, body)
    return data
  },

  async updateDestination(
    tenantId: string,
    destinationId: string,
    body: Partial<CreateDeliveryDestinationRequest>
  ): Promise<DeliveryDestination> {
    const { data } = await api.patch(`/tenants/${tenantId}/delivery/destinations/${destinationId}`, body)
    return data
  },

  async deleteDestination(tenantId: string, destinationId: string): Promise<void> {
    await api.delete(`/tenants/${tenantId}/delivery/destinations/${destinationId}`)
  },

  async probeDestination(tenantId: string, destinationId: string): Promise<ProbeDestinationResponse> {
    const { data } = await api.post(
      `/tenants/${tenantId}/delivery/destinations/${destinationId}/probe`
    )
    return data
  },

  async listPolicies(tenantId: string, projectId: string): Promise<DeliveryPolicy[]> {
    const { data } = await api.get(`/tenants/${tenantId}/projects/${projectId}/delivery/policies`)
    return data
  },

  async updatePolicy(
    tenantId: string,
    projectId: string,
    policyId: string,
    enabled: boolean
  ): Promise<{ policyId: string; enabled: string }> {
    const { data } = await api.patch(
      `/tenants/${tenantId}/projects/${projectId}/delivery/policies/${policyId}`,
      { enabled }
    )
    return data
  },

  async deletePolicy(tenantId: string, projectId: string, policyId: string): Promise<void> {
    await api.delete(`/tenants/${tenantId}/projects/${projectId}/delivery/policies/${policyId}`)
  },

  async createPolicy(
    tenantId: string,
    projectId: string,
    body: CreateDeliveryPolicyRequest
  ): Promise<{ policyId: string }> {
    const { data } = await api.post(`/tenants/${tenantId}/projects/${projectId}/delivery/policies`, body)
    return data
  },

  async listDeliveries(
    tenantId: string,
    projectId: string,
    jobId: string
  ): Promise<DeliveryJob[]> {
    const { data } = await api.get(
      `/tenants/${tenantId}/projects/${projectId}/render-jobs/${jobId}/deliveries`
    )
    return data
  },

  async triggerDeliver(
    tenantId: string,
    projectId: string,
    jobId: string,
    destinationId: string
  ): Promise<{ deliveryJobId: string }> {
    const { data } = await api.post(
      `/tenants/${tenantId}/projects/${projectId}/render-jobs/${jobId}/deliver`,
      null,
      { params: { destinationId } }
    )
    return data
  },

  async retryDelivery(
    tenantId: string,
    projectId: string,
    jobId: string,
    deliveryJobId: string
  ): Promise<{ deliveryJobId: string; status: string }> {
    const { data } = await api.post(
      `/tenants/${tenantId}/projects/${projectId}/render-jobs/${jobId}/deliveries/${deliveryJobId}/retry`
    )
    return data
  },
}
