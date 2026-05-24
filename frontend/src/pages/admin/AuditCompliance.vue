<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { AuditAPI } from '@/api/admin/audit'
import type { AuditRecord, OutboxEvent } from '@/api/admin/audit'

type Tab = 'audit' | 'outbox'

const loading = ref(true)
const activeTab = ref<Tab>('audit')
const auditRecords = ref<AuditRecord[]>([])
const outboxRecent = ref<OutboxEvent[]>([])
const outboxFailed = ref<OutboxEvent[]>([])
const outboxOverview = ref<{ pending: number; failed: number; processed: number } | null>(null)
const auditCategory = ref('ALL')

onMounted(loadData)

async function loadData() {
  loading.value = true
  try {
    const [records, recent, failed, overview] = await Promise.allSettled([
      AuditAPI.listRecent(),
      AuditAPI.listOutboxRecent(),
      AuditAPI.listOutboxFailed(),
      AuditAPI.getOutboxOverview(),
    ])
    if (records.status === 'fulfilled') auditRecords.value = records.value
    if (recent.status === 'fulfilled') outboxRecent.value = recent.value
    if (failed.status === 'fulfilled') outboxFailed.value = failed.value
    if (overview.status === 'fulfilled') outboxOverview.value = overview.value
  } catch { /* backend may not be running */ }
  loading.value = false
}

async function retryOutbox(outboxId: string) {
  await AuditAPI.retryOutbox(outboxId)
  await loadData()
}

async function deadLetter(outboxId: string) {
  await AuditAPI.deadLetterOutbox(outboxId)
  await loadData()
}

const filteredAudit = (() => {
  if (auditCategory.value === 'ALL') return auditRecords.value
  return auditRecords.value.filter(r => r.category === auditCategory.value)
})()

const categories = (() => {
  const cats = new Set(auditRecords.value.map(r => r.category).filter(Boolean))
  return ['ALL', ...Array.from(cats)]
})()
</script>

<template>
  <div class="flex-1 overflow-y-auto p-6">
    <div class="flex items-center justify-between mb-6">
      <h1 class="text-xl font-bold">Audit & Outbox</h1>
      <button class="px-3 py-1.5 bg-surface-3 hover:bg-surface-4 text-sm rounded" @click="loadData">Refresh</button>
    </div>

    <!-- Tabs -->
    <div class="flex border-b border-border-subtle mb-4">
      <button
        class="px-4 py-2 text-sm"
        :class="activeTab === 'audit' ? 'text-info border-b-2 border-blue-400' : 'text-text-secondary hover:text-white'"
        @click="activeTab = 'audit'"
      >
        Audit Records
      </button>
      <button
        class="px-4 py-2 text-sm"
        :class="activeTab === 'outbox' ? 'text-info border-b-2 border-blue-400' : 'text-text-secondary hover:text-white'"
        @click="activeTab = 'outbox'"
      >
        Outbox
      </button>
    </div>

    <div v-if="loading" class="text-text-secondary text-sm">Loading...</div>

    <!-- Audit Records -->
    <template v-else-if="activeTab === 'audit'">
      <div class="flex items-center gap-2 mb-4">
        <button
          v-for="cat in categories"
          :key="cat"
          class="text-xs px-2 py-1 rounded border"
          :class="auditCategory === cat ? 'bg-blue-600/30 border-info text-info' : 'border-border-default text-text-secondary hover:text-white'"
          @click="auditCategory = cat || 'ALL'"
        >
          {{ cat }}
        </button>
      </div>

      <div v-if="filteredAudit.length === 0" class="text-text-tertiary text-sm">No audit records</div>
      <div v-else class="bg-surface-2 border border-border-subtle rounded-lg overflow-hidden">
        <table class="w-full text-sm">
          <thead>
            <tr class="border-b border-border-subtle text-xs text-text-secondary">
              <th class="text-left px-3 py-2">Time</th>
              <th class="text-left px-3 py-2">Category</th>
              <th class="text-left px-3 py-2">Action</th>
              <th class="text-left px-3 py-2">Resource</th>
              <th class="text-left px-3 py-2">Actor</th>
              <th class="text-left px-3 py-2">Details</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(r, idx) in filteredAudit" :key="idx" class="border-b border-border-subtle/50 hover:bg-surface-3/30">
              <td class="px-3 py-2 text-xs text-text-secondary">{{ r.createdAt || '—' }}</td>
              <td class="px-3 py-2"><span class="text-xs px-1.5 py-0.5 rounded bg-surface-3">{{ r.category }}</span></td>
              <td class="px-3 py-2 text-xs">{{ r.action }}</td>
              <td class="px-3 py-2 text-xs font-mono">{{ r.resourceType }}:{{ r.resourceId }}</td>
              <td class="px-3 py-2 text-xs">{{ r.actor || '—' }}</td>
              <td class="px-3 py-2 text-xs text-text-secondary truncate max-w-xs">{{ r.details || '—' }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </template>

    <!-- Outbox -->
    <template v-else>
      <!-- Overview -->
      <div class="grid grid-cols-3 gap-4 mb-6">
        <div class="bg-surface-2 border border-border-subtle rounded-lg p-3 text-center">
          <div class="text-xs text-text-secondary">Pending</div>
          <div class="text-lg font-bold text-warning">{{ outboxOverview?.pending ?? '—' }}</div>
        </div>
        <div class="bg-surface-2 border border-border-subtle rounded-lg p-3 text-center">
          <div class="text-xs text-text-secondary">Processed</div>
          <div class="text-lg font-bold text-success">{{ outboxOverview?.processed ?? '—' }}</div>
        </div>
        <div class="bg-surface-2 border border-border-subtle rounded-lg p-3 text-center">
          <div class="text-xs text-text-secondary">Failed</div>
          <div class="text-lg font-bold" :class="(outboxOverview?.failed ?? 0) > 0 ? 'text-danger' : ''">
            {{ outboxOverview?.failed ?? '—' }}
          </div>
        </div>
      </div>

      <!-- Failed Events -->
      <h2 class="text-sm font-semibold mb-3 text-text-primary">Failed Events</h2>
      <div v-if="outboxFailed.length === 0" class="text-text-tertiary text-sm mb-6">No failed events</div>
      <div v-else class="space-y-2 mb-6">
        <div v-for="evt in outboxFailed" :key="evt.id" class="bg-surface-2 border border-red-900/30 rounded-lg p-3 flex items-center justify-between">
          <div>
            <span class="text-xs font-mono">{{ evt.id }}</span>
            <span class="text-xs text-text-tertiary ml-2">{{ evt.eventType }}</span>
            <span class="text-xs text-text-tertiary ml-2">Retries: {{ evt.retryCount || 0 }}</span>
          </div>
          <div class="flex gap-2">
            <button class="text-xs px-2 py-0.5 bg-info-muted text-info rounded" @click="retryOutbox(evt.id!)">Retry</button>
            <button class="text-xs px-2 py-0.5 bg-surface-4/20 text-text-primary rounded" @click="deadLetter(evt.id!)">Dead Letter</button>
          </div>
        </div>
      </div>

      <!-- Recent Events -->
      <h2 class="text-sm font-semibold mb-3 text-text-primary">Recent Events</h2>
      <div v-if="outboxRecent.length === 0" class="text-text-tertiary text-sm">No recent events</div>
      <div v-else class="bg-surface-2 border border-border-subtle rounded-lg overflow-hidden">
        <table class="w-full text-sm">
          <thead>
            <tr class="border-b border-border-subtle text-xs text-text-secondary">
              <th class="text-left px-3 py-2">ID</th>
              <th class="text-left px-3 py-2">Type</th>
              <th class="text-left px-3 py-2">Status</th>
              <th class="text-left px-3 py-2">Created</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="evt in outboxRecent" :key="evt.id" class="border-b border-border-subtle/50">
              <td class="px-3 py-2 text-xs font-mono">{{ evt.id?.slice(0, 16) }}...</td>
              <td class="px-3 py-2 text-xs">{{ evt.eventType }}</td>
              <td class="px-3 py-2">
                <span class="text-xs px-1.5 py-0.5 rounded" :class="evt.status === 'FAILED' ? 'bg-danger-muted text-danger' : 'bg-success-muted text-success'">
                  {{ evt.status }}
                </span>
              </td>
              <td class="px-3 py-2 text-xs text-text-secondary">{{ evt.createdAt || '—' }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </template>
  </div>
</template>
