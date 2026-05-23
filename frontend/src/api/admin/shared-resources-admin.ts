import api from '../index'

export interface SharedResourceGrantRow {
  grantId: string
  id: string
  name: string
  type: string
  sharedWithUserId: string
  sharedBy: string
  permission: string
  grantStatus: string
  status: string
  createdAt: string
}

export const SharedResourcesAdminAPI = {
  async listGrants(tenantId?: string, includeRevoked = false): Promise<SharedResourceGrantRow[]> {
    const { data } = await api.get('/admin/shared-resources/grants', {
      params: { tenantId, includeRevoked },
    })
    return data
  },

  async revokeGrant(grantId: string): Promise<{ grantId: string; revoked: boolean; status: string }> {
    const { data } = await api.delete(`/admin/shared-resources/grants/${grantId}`)
    return data
  },

  /** Uses the same endpoint as MeEntitlementAPI.grantSharedResource; pass tenantId for admin context. */
  async grantSharedResource(body: {
    tenantId?: string
    resourceType?: string
    resourceId: string
    resourceName?: string
    sharedWithUserId: string
    permission?: string
    sharedByUserId?: string
  }): Promise<{ grantId: string; resourceId: string; sharedWithUserId: string; permission: string; status: string }> {
    const { data } = await api.post('/me/shared-resources/grants', body)
    return data
  },
}
