import api from './index'
import type {
  WorkspaceMember,
  WorkspaceRole,
  WorkspaceEntitlementPool,
  WorkspaceMemberGrant,
  WorkspaceGroupGrant,
  EntitlementDecision,
  AccessDecisionDebug,
  EntitlementGrant
} from '@/types'

export const WorkspaceEntitlementAPI = {
  async getMembers(workspaceId: string): Promise<WorkspaceMember[]> {
    const { data } = await api.get(`/workspace/${workspaceId}/members`)
    return data
  },

  async getMemberEntitlements(workspaceId: string, memberId: string): Promise<EntitlementGrant[]> {
    const { data } = await api.get(`/workspace/${workspaceId}/members/${memberId}/entitlements`)
    return data
  },

  async getRoles(workspaceId: string): Promise<WorkspaceRole[]> {
    const { data } = await api.get(`/workspace/${workspaceId}/roles`)
    return data
  },

  async createRole(workspaceId: string, role: { name: string; description: string; permissions: string[] }): Promise<WorkspaceRole> {
    const { data } = await api.post(`/workspace/${workspaceId}/roles`, role)
    return data
  },

  async updateRole(workspaceId: string, roleId: string, updates: Partial<{ name: string; description: string; permissions: string[] }>): Promise<WorkspaceRole> {
    const { data } = await api.put(`/workspace/${workspaceId}/roles/${roleId}`, updates)
    return data
  },

  async deleteRole(workspaceId: string, roleId: string): Promise<void> {
    await api.delete(`/workspace/${workspaceId}/roles/${roleId}`)
  },

  async getEntitlementPool(workspaceId: string): Promise<WorkspaceEntitlementPool[]> {
    const { data } = await api.get(`/workspace/${workspaceId}/entitlements/pool`)
    return data
  },

  async getMemberGrants(workspaceId: string): Promise<WorkspaceMemberGrant[]> {
    const { data } = await api.get(`/workspace/${workspaceId}/entitlements/grants`)
    return data
  },

  async grantMemberEntitlement(workspaceId: string, memberId: string, featureKey: string, expiresAt?: string): Promise<WorkspaceMemberGrant> {
    const { data } = await api.post(`/workspace/${workspaceId}/entitlements/grants/member`, {
      memberId, featureKey, expiresAt
    })
    return data
  },

  async revokeMemberEntitlement(workspaceId: string, grantId: string): Promise<void> {
    await api.delete(`/workspace/${workspaceId}/entitlements/grants/${grantId}`)
  },

  async getGroupGrants(workspaceId: string): Promise<WorkspaceGroupGrant[]> {
    const { data } = await api.get(`/workspace/${workspaceId}/entitlements/groups`)
    return data
  },

  async grantGroupEntitlement(workspaceId: string, groupId: string, featureKey: string, expiresAt?: string): Promise<WorkspaceGroupGrant> {
    const { data } = await api.post(`/workspace/${workspaceId}/entitlements/groups/grant`, {
      groupId, featureKey, expiresAt
    })
    return data
  },

  async revokeGroupEntitlement(workspaceId: string, grantId: string): Promise<void> {
    await api.delete(`/workspace/${workspaceId}/entitlements/groups/${grantId}`)
  },

  async getQuotaAllocations(workspaceId: string): Promise<WorkspaceEntitlementPool[]> {
    const { data } = await api.get(`/workspace/${workspaceId}/entitlements/quota`)
    return data
  },

  async updateQuotaAllocation(workspaceId: string, featureKey: string, totalQuota: number): Promise<WorkspaceEntitlementPool> {
    const { data } = await api.put(`/workspace/${workspaceId}/entitlements/quota/${featureKey}`, { totalQuota })
    return data
  },

  async previewDecision(workspaceId: string, memberId: string, featureKey: string): Promise<EntitlementDecision> {
    const { data } = await api.post(`/workspace/${workspaceId}/entitlements/preview`, {
      memberId, featureKey
    })
    return data
  },

  async debugAccessDecision(workspaceId: string, memberId: string, featureKey: string): Promise<AccessDecisionDebug> {
    const { data } = await api.post(`/workspace/${workspaceId}/entitlements/debug`, {
      memberId, featureKey
    })
    return data
  }
}
