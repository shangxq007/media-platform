import api from '../index'

export interface ConfigEntry {
  key?: string
  value?: string
  namespace?: string
  updatedAt?: string
}

export const ConfigAPI = {
  async list(namespace: string): Promise<ConfigEntry[]> {
    const { data } = await api.get('/configs', { params: { namespace } })
    return data
  },
  async upsert(namespace: string, key: string, value: string): Promise<void> {
    await api.post('/configs', { namespace, key, value })
  },
}
