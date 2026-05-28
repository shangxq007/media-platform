import api from '../index'

export interface AuditRecord {
  id?: string
  category?: string
  action?: string
  resourceType?: string
  resourceId?: string
  actor?: string
  details?: string
  createdAt?: string
}

export interface AuditRecordSummary {
  id: string
  createdAt: string
  category: string
  action: string
  actorType: string
  actorId: string
  resourceType: string
  resourceId: string
  targetTenantId: string | null
  result: string | null
  requestId: string | null
  traceId: string | null
}

export interface AuditRecordDetail {
  id: string
  createdAt: string
  category: string
  action: string
  actorType: string
  actorId: string
  resourceType: string
  resourceId: string
  payload: Record<string, unknown>
}

export interface AuditRecordListResponse {
  items: AuditRecordSummary[]
  page: number
  size: number
  total: number
}

export interface AuditRecordQueryParams {
  page?: number
  size?: number
  category?: string
  action?: string
  actorType?: string
  actorId?: string
  resourceType?: string
  resourceId?: string
  targetTenantId?: string
  result?: string
  from?: string
  to?: string
}

export interface OutboxEvent {
  id?: string
  eventType?: string
  status?: string
  retryCount?: number
  createdAt?: string
}

export const AuditAPI = {
  async getOverview(): Promise<{ total: number; categories: Record<string, number> }> {
    const { data } = await api.get('/audit/compliance/overview')
    return data
  },
  async createRecord(record: {
    category: string
    action: string
    resourceType?: string
    resourceId?: string
    actor?: string
    details?: string
  }): Promise<AuditRecord> {
    const { data } = await api.post('/audit/compliance/records', record)
    return data
  },
  async listRecent(): Promise<AuditRecord[]> {
    const { data } = await api.get('/audit/compliance/records')
    return data
  },
  async listByCategory(category: string): Promise<AuditRecord[]> {
    const { data } = await api.get(`/audit/compliance/records/category/${category}`)
    return data
  },
  async listByResource(resourceType: string, resourceId: string): Promise<AuditRecord[]> {
    const { data } = await api.get('/audit/compliance/records/resource', {
      params: { resourceType, resourceId }
    })
    return data
  },

  // Outbox
  async getOutboxOverview(): Promise<{ pending: number; failed: number; processed: number }> {
    const { data } = await api.get('/outbox/overview')
    return data
  },
  async listOutboxRecent(): Promise<OutboxEvent[]> {
    const { data } = await api.get('/outbox/recent')
    return data
  },
  async listOutboxFailed(): Promise<OutboxEvent[]> {
    const { data } = await api.get('/outbox/failed')
    return data
  },
  async retryOutbox(outboxId: string): Promise<void> {
    await api.post(`/outbox/retry/${outboxId}`)
  },
  async deadLetterOutbox(outboxId: string): Promise<void> {
    await api.post(`/outbox/dead-letter/${outboxId}`)
  },

  // Admin audit log queries (admin-only)
  async listAuditRecords(params?: AuditRecordQueryParams): Promise<AuditRecordListResponse> {
    const { data } = await api.get('/audit/admin/records', { params })
    return data
  },

  async getAuditRecord(id: string): Promise<AuditRecordDetail> {
    const { data } = await api.get(`/audit/admin/records/${id}`)
    return data
  },

  async listAuditCategories(): Promise<string[]> {
    const { data } = await api.get('/audit/admin/categories')
    return data
  },

  async exportAuditRecords(params?: AuditRecordQueryParams & { limit?: number }): Promise<Blob> {
    const { data } = await api.get('/audit/admin/records/export', {
      params,
      responseType: 'blob',
    })
    return data
  },
}
