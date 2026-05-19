import api from '../index'

export interface UserProfile {
  userId?: string
  tenantId?: string
  eventCount?: number
  lastActive?: string
  segments?: string[]
}

export interface UserHabit {
  userId?: string
  mostUsedFeature?: string
  avgSessionMinutes?: number
  renderFrequency?: string
}

export interface UserSegment {
  segmentId?: string
  name?: string
  userCount?: number
  criteria?: string
}

export const AdminAnalyticsAPI = {
  async listProfiles(tenantId: string): Promise<UserProfile[]> {
    const { data } = await api.get('/analytics/profiles', { params: { tenantId } })
    return data
  },
  async getProfile(userId: string): Promise<UserProfile> {
    const { data } = await api.get(`/analytics/profiles/${userId}`)
    return data
  },
  async getHabits(userId: string): Promise<UserHabit> {
    const { data } = await api.get(`/analytics/habits/${userId}`)
    return data
  },
  async listSegments(tenantId: string): Promise<UserSegment[]> {
    const { data } = await api.get('/analytics/segments', { params: { tenantId } })
    return data
  },
  async computeActiveSegment(tenantId: string): Promise<void> {
    await api.post('/analytics/segments/active', null, { params: { tenantId } })
  },
  async computePowerUsersSegment(tenantId: string): Promise<void> {
    await api.post('/analytics/segments/power-users', null, { params: { tenantId } })
  },
  async listEvents(tenantId: string, eventType?: string): Promise<unknown[]> {
    const params: Record<string, string> = { tenantId }
    if (eventType) params.eventType = eventType
    const { data } = await api.get('/analytics/events', { params })
    return data
  },
}
