import api from '../index'

export interface AdminNotification {
  id?: string
  tenantId?: string
  type?: string
  title?: string
  status?: string
  createdAt?: string
}

export interface NotificationDelivery {
  id?: string
  notificationId?: string
  channel?: string
  status?: string
  sentAt?: string
}

export interface NotificationEventDefinition {
  eventKey: string
  name: string
  description: string
  category: string
  severity: string
  visibility: string
  userConfigurable: boolean
  critical: boolean
  defaultEnabled: boolean
  supportedChannels: string[]
  featureFlagKey?: string
  archived: boolean
  createdAt?: string
  updatedAt?: string
}

export interface NotificationDeliveryLog {
  id: string
  tenantId: string
  notificationId: string
  eventKey: string
  channel: string
  status: 'PENDING' | 'SENT' | 'DELIVERED' | 'FAILED' | 'BOUNCED'
  destinationMasked: string
  errorMessage?: string
  retryCount: number
  createdAt: string
  sentAt?: string
  deliveredAt?: string
}

export interface NotificationProviderStatus {
  provider: string
  channel: string
  status: 'ACTIVE' | 'DEGRADED' | 'DOWN'
  successRate: number
  avgLatencyMs: number
  lastFailureAt?: string
  lastSuccessAt?: string
}

export const NotificationAPI = {
  async listNotifications(tenantId: string): Promise<AdminNotification[]> {
    const { data } = await api.get(`/tenants/${tenantId}/notifications`)
    return data
  },
  async getNotification(tenantId: string, notificationId: string): Promise<AdminNotification> {
    const { data } = await api.get(`/tenants/${tenantId}/notifications/${notificationId}`)
    return data
  },
  async getDeliveries(tenantId: string, notificationId: string): Promise<NotificationDelivery[]> {
    const { data } = await api.get(`/tenants/${tenantId}/notifications/${notificationId}/deliveries`)
    return data
  },
  async retryNotification(tenantId: string, notificationId: string): Promise<void> {
    await api.post(`/tenants/${tenantId}/notifications/${notificationId}/retry`)
  },
  async publishEvent(event: {
    type: string
    tenantId?: string
    payload?: Record<string, unknown>
  }): Promise<void> {
    await api.post('/notifications/events', event)
  },

  // Event definitions
  async getEventDefinitions(): Promise<NotificationEventDefinition[]> {
    const { data } = await api.get('/admin/notifications/event-definitions')
    return data
  },
  async createEventDefinition(def: Omit<NotificationEventDefinition, 'archived' | 'createdAt' | 'updatedAt'>): Promise<NotificationEventDefinition> {
    const { data } = await api.post('/admin/notifications/event-definitions', def)
    return data
  },
  async updateEventDefinition(eventKey: string, def: Partial<NotificationEventDefinition>): Promise<NotificationEventDefinition> {
    const { data } = await api.put(`/admin/notifications/event-definitions/${eventKey}`, def)
    return data
  },
  async archiveEventDefinition(eventKey: string): Promise<void> {
    await api.post(`/admin/notifications/event-definitions/${eventKey}/archive`)
  },

  // Delivery logs
  async getDeliveryLogs(params: {
    page?: number
    size?: number
    status?: string
    channel?: string
    eventKey?: string
    tenantId?: string
  } = {}): Promise<{ items: NotificationDeliveryLog[]; total: number; page: number; size: number }> {
    const { data } = await api.get('/admin/notifications/delivery-logs', { params })
    return data
  },
  async retryDelivery(logId: string): Promise<void> {
    await api.post(`/admin/notifications/delivery-logs/${logId}/retry`)
  },

  // Provider status
  async getProviderStatus(): Promise<NotificationProviderStatus[]> {
    const { data } = await api.get('/admin/notifications/providers')
    return data
  },
}
