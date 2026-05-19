import api from '../index'

export interface AdminTenant {
  id: string
  name: string
  status: string
  createdAt?: string
}

export interface AdminProject {
  id: string
  tenantId: string
  name: string
  description?: string
  status?: string
  createdAt?: string
}

export interface AdminUser {
  id: string
  tenantId?: string
  name?: string
  email?: string
  status?: string
  createdAt?: string
}

export interface AdminApiKey {
  id?: string
  tenantId?: string
  key?: string
  name?: string
  status?: string
  createdAt?: string
}

export interface ServiceAccount {
  id?: string
  name?: string
  status?: string
}

export const IdentityAPI = {
  // Tenants
  async createTenant(name: string): Promise<AdminTenant> {
    const { data } = await api.post('/identity/tenants', { name })
    return data
  },
  async getTenant(tenantId: string): Promise<AdminTenant> {
    const { data } = await api.get(`/identity/tenants/${tenantId}`)
    return data
  },

  // Projects
  async createProject(tenantId: string, name: string, description?: string): Promise<AdminProject> {
    const { data } = await api.post(`/identity/tenants/${tenantId}/projects`, { name, description })
    return data
  },
  async listProjects(tenantId: string): Promise<AdminProject[]> {
    const { data } = await api.get(`/identity/tenants/${tenantId}/projects`)
    return data
  },
  async getProject(projectId: string): Promise<AdminProject> {
    const { data } = await api.get(`/identity/projects/${projectId}`)
    return data
  },

  // Users
  async createUser(tenantId: string, name: string, email: string): Promise<AdminUser> {
    const { data } = await api.post(`/identity/tenants/${tenantId}/users`, { name, email })
    return data
  },
  async listUsers(tenantId: string): Promise<AdminUser[]> {
    const { data } = await api.get(`/identity/tenants/${tenantId}/users`)
    return data
  },

  // API Keys
  async createApiKey(tenantId: string, name: string): Promise<AdminApiKey> {
    const { data } = await api.post(`/identity/tenants/${tenantId}/apikeys`, { name })
    return data
  },
  async listApiKeys(tenantId: string): Promise<AdminApiKey[]> {
    const { data } = await api.get(`/identity/tenants/${tenantId}/apikeys`)
    return data
  },

  // Access overview
  async getAccessOverview(): Promise<{ tenants: number; users: number; serviceAccounts: number }> {
    const { data } = await api.get('/identity/access/overview')
    return data
  },
  async getServiceAccounts(): Promise<ServiceAccount[]> {
    const { data } = await api.get('/identity/access/service-accounts')
    return data
  },
}
