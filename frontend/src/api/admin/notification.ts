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
}
