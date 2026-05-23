import api from './index'
import type {
  MyCapabilities,
  UsageSummary,
  BillingLedgerEntry,
  Invoice,
  CreditWallet,
  CreditTransaction,
  SubscriptionPlan,
  UpgradeOption,
  EntitlementExplanation,
  Project,
  RenderJobDetailed
} from '@/types'

export interface ActiveSubscription {
  contractId: string
  planKey: string
  productCode: string
  contractRole: string
  lifecycleState: string
  periodStartAt?: string
  periodEndAt?: string
  basePriceMinor: number
  includedQuota?: Record<string, number>
}

export interface DashboardData {
  tenantId: string | null
  userId: string
  timestamp: string
  workspace: {
    id?: string
    name?: string
    status?: string
    role: string
  }
  capabilities: {
    tier: string
    monthlyRenderMinutes?: number
    maxConcurrentJobs?: number
    gpuAllowed?: boolean
    remoteWorkerAllowed?: boolean
    customFontsAllowed?: boolean
    watermark?: boolean
    allowedExportFormats?: string[]
    allowedPresets?: string[]
    exportFormats?: string[]
    exportPresets?: string[]
    maxExportResolutionWidth?: number
    maxExportResolutionHeight?: number
    gpuExportAllowed?: boolean
    maxConcurrentExports?: number
    error?: string
  }
  featureFlags: Array<{
    flagKey: string
    displayName: string
    enabled: boolean
    description: string
  }>
  recentProjects: Project[]
  quickActions: Array<{
    key: string
    label: string
    icon: string
    path: string
    enabled: boolean
    visible: boolean
    disabledReason?: string
  }>
  usage: {
    period: string
    renderMinutesUsed?: number
    renderMinutesLimit?: number
    storageGbUsed?: number
    storageGbLimit?: number
    apiCallsUsed?: number
    apiCallsLimit?: number
    exportsUsed?: number
    exportsLimit?: number
  }
  onboarding: {
    hasProjects: boolean
    hasCompletedProfile: boolean
    hasInvitedTeamMembers: boolean
    hasCompletedFirstExport: boolean
    hasSetBilling: boolean
  }
}

export interface SharedResourcesData {
  sharedProjects: Project[]
  sharedExports: RenderJobDetailed[]
  totalShared: number
}

export interface NotificationItem {
  id: string
  type: 'INFO' | 'WARNING' | 'ERROR' | 'SUCCESS'
  title: string
  message: string
  read: boolean
  createdAt: string
  link?: string
}

export interface FeedbackItem {
  id: string
  type: 'BUG' | 'FEATURE' | 'GENERAL'
  severity: 'low' | 'medium' | 'high' | 'critical'
  status: 'OPEN' | 'IN_PROGRESS' | 'RESOLVED' | 'CLOSED'
  title: string
  description?: string
  createdAt: string
}

export const MeEntitlementAPI = {
  async getMyCapabilities(): Promise<MyCapabilities> {
    const { data } = await api.get('/entitlements/me/capabilities')
    return data
  },

  async getUsageSummary(): Promise<UsageSummary> {
    const { data } = await api.get('/entitlements/me/usage')
    return data
  },

  async getBillingHistory(page = 0, size = 20): Promise<{ entries: BillingLedgerEntry[]; total: number }> {
    const { data } = await api.get('/billing/me/history', { params: { page, size } })
    return data
  },

  async getInvoices(): Promise<Invoice[]> {
    const { data } = await api.get('/billing/me/invoices')
    return data
  },

  async getInvoice(invoiceId: string): Promise<Invoice> {
    const { data } = await api.get(`/billing/me/invoices/${invoiceId}`)
    return data
  },

  async getCreditBalance(): Promise<CreditWallet> {
    const { data } = await api.get('/billing/me/credits')
    return data
  },

  async getCreditTransactions(page = 0, size = 20): Promise<{ transactions: CreditTransaction[]; total: number }> {
    const { data } = await api.get('/billing/me/credits/transactions', { params: { page, size } })
    return data
  },

  async getCurrentPlan(): Promise<SubscriptionPlan> {
    const { data } = await api.get('/billing/me/plan')
    return data
  },

  async getActiveSubscriptions(): Promise<ActiveSubscription[]> {
    const { data } = await api.get('/billing/me/subscriptions')
    return data
  },

  async getEffectiveQuota(): Promise<Record<string, number>> {
    const { data } = await api.get('/billing/me/effective-quota')
    return data
  },

  async getUpgradeOptions(): Promise<UpgradeOption[]> {
    const { data } = await api.get('/billing/me/upgrades')
    return data
  },

  async getEntitlementExplanation(featureKey: string): Promise<EntitlementExplanation> {
    const { data } = await api.get(`/entitlements/me/explain/${featureKey}`)
    return data
  },

  async topUpCredits(amount: number, currency = 'USD'): Promise<CreditTransaction> {
    const { data } = await api.post('/billing/me/credits/topup', { amount, currency })
    return data
  },

  async submitPayment(paymentId: string): Promise<void> {
    await api.post('/payments/confirm', { paymentId })
  },

  // New /api/v1/me/* endpoints
  async getDashboard(): Promise<DashboardData> {
    const { data } = await api.get('/me/dashboard')
    return data
  },

  async getMyProjects(page = 0, size = 20): Promise<{ projects: Project[]; total: number; page: number; size: number }> {
    const { data } = await api.get('/me/projects', { params: { page, size } })
    return data
  },

  async getSharedResources(): Promise<SharedResourcesData> {
    const { data } = await api.get('/me/shared-resources')
    return data
  },

  async grantSharedResource(body: {
    resourceType?: string
    resourceId: string
    resourceName?: string
    sharedWithUserId: string
    permission?: string
  }): Promise<{ grantId: string; resourceId: string; sharedWithUserId: string; permission: string }> {
    const { data } = await api.post('/me/shared-resources/grants', body)
    return data
  },

  async getMyExports(page = 0, size = 20): Promise<{ exports: RenderJobDetailed[]; total: number; page: number; size: number }> {
    const { data } = await api.get('/me/exports', { params: { page, size } })
    return data
  },

  async getMyReports(page = 0, size = 20): Promise<{ reports: unknown[]; total: number; page: number; size: number }> {
    const { data } = await api.get('/me/reports', { params: { page, size } })
    return data
  },

  async getMyNotifications(page = 0, size = 20, status?: string): Promise<{ notifications: NotificationItem[]; total: number; page: number; size: number; unreadCount: number }> {
    const { data } = await api.get('/me/notifications', { params: { page, size, status } })
    return data
  },

  async markNotificationRead(id: string): Promise<void> {
    await api.post(`/me/notifications/${id}/read`)
  },

  async getMyFeedback(page = 0, size = 20): Promise<{ feedback: FeedbackItem[]; total: number; page: number; size: number }> {
    const { data } = await api.get('/me/feedback', { params: { page, size } })
    return data
  },

  async submitFeedback(type: string, severity: string, title: string, description?: string): Promise<FeedbackItem> {
    const { data } = await api.post('/me/feedback', { type, severity, title, description })
    return data
  },

  // Notification channel bindings
  async getNotificationChannels(): Promise<NotificationChannelBinding[]> {
    const { data } = await api.get('/me/notification-channels')
    return data
  },
  async bindNotificationChannel(channelType: string, destination: string, webhookSecret?: string): Promise<NotificationChannelBinding> {
    const { data } = await api.post('/me/notification-channels', { channelType, destination, webhookSecret })
    return data
  },
  async verifyNotificationChannel(bindingId: string): Promise<void> {
    await api.post(`/me/notification-channels/${bindingId}/verify`)
  },
  async testNotificationChannel(bindingId: string): Promise<void> {
    await api.post(`/me/notification-channels/${bindingId}/test`)
  },
  async disableNotificationChannel(bindingId: string): Promise<void> {
    await api.post(`/me/notification-channels/${bindingId}/disable`)
  },
  async deleteNotificationChannel(bindingId: string): Promise<void> {
    await api.delete(`/me/notification-channels/${bindingId}`)
  },
  // Notification subscriptions
  async getNotificationSubscriptions(): Promise<NotificationSubscription[]> {
    const { data } = await api.get('/me/notification-subscriptions')
    return data
  },
  async updateNotificationSubscription(eventKey: string, enabled: boolean, channels?: string[]): Promise<NotificationSubscription> {
    const { data } = await api.put(`/me/notification-subscriptions/${eventKey}`, { enabled, channels })
    return data
  },
  async batchUpdateNotificationSubscriptions(updates: Array<{ eventKey: string; enabled: boolean; channels?: string[] }>): Promise<{ results: NotificationSubscription[]; errors: Array<{ eventKey: string; errorCode: string; message: string }> }> {
    const { data } = await api.post('/me/notification-subscriptions/batch-update', { updates })
    return data
  },
  // Notification preferences
  async getNotificationPreferences(): Promise<NotificationPreference> {
    const { data } = await api.get('/me/notification-preferences')
    return data
  },
  async updateNotificationPreferences(prefs: Partial<NotificationPreference>): Promise<NotificationPreference> {
    const { data } = await api.put('/me/notification-preferences', prefs)
    return data
  },
  // Notification inbox
  async getNotificationInbox(page = 0, size = 20): Promise<{ items: NotificationInboxItem[]; total: number; page: number; size: number; unreadCount: number }> {
    const { data } = await api.get('/me/notifications/inbox', { params: { page, size } })
    return data
  },
  async markInboxNotificationRead(id: string): Promise<void> {
    await api.post(`/me/notifications/inbox/${id}/read`)
  },
  async markAllInboxNotificationsRead(): Promise<void> {
    await api.post('/me/notifications/inbox/read-all')
  },
  // Notification event catalog
  async getNotificationEventCatalog(): Promise<NotificationEventCatalogItem[]> {
    const { data } = await api.get('/notifications/events')
    return data
  }
}

export interface NotificationChannelBinding {
  bindingId: string
  tenantId: string
  workspaceId?: string
  userId: string
  channelType: 'IN_APP' | 'EMAIL' | 'SMS' | 'WEBHOOK' | 'CHAT' | 'PUSH'
  destinationMasked: string
  verified: boolean
  verificationStatus: string
  enabled: boolean
  provider: string
  failureCount: number
  createdAt: string
}

export interface NotificationSubscription {
  subscriptionId: string
  tenantId: string
  userId: string
  eventKey: string
  enabled: boolean
  channels: string[]
  frequency: string
  createdAt: string
  updatedAt: string
}

export interface NotificationPreference {
  preferenceId: string
  tenantId: string
  userId: string
  globalEnabled: boolean
  channelEnabled: Record<string, boolean>
  quietHoursStart?: string
  quietHoursEnd?: string
  quietHoursTimezone?: string
  digestMode: string
  criticalOverride: boolean
}

export interface NotificationInboxItem {
  id: string
  tenantId: string
  userId: string
  eventKey: string
  type: 'INFO' | 'WARNING' | 'ERROR' | 'SUCCESS'
  title: string
  message: string
  read: boolean
  link?: string
  actorId?: string
  resourceType?: string
  resourceId?: string
  createdAt: string
  readAt?: string
}

export interface NotificationEventCatalogItem {
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
}
