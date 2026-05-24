<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'

interface AdminFeedback {
  id: string
  type: 'BUG' | 'FEATURE_REQUEST' | 'USABILITY' | 'PERFORMANCE' | 'DATA_QUALITY'
  severity: 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL'
  status: 'OPEN' | 'IN_PROGRESS' | 'RESOLVED' | 'CLOSED' | 'WONT_FIX'
  title: string
  description: string
  tenant?: string
  user?: string
  sentryIssueId?: string
  openReplaySessionUrl?: string
  createdAt: string
  updatedAt?: string
}

const loading = ref(true)
const feedback = ref<AdminFeedback[]>([])
const error = ref<string | null>(null)

const searchQuery = ref('')
const filterType = ref<string>('ALL')
const filterSeverity = ref<string>('ALL')
const filterStatus = ref<string>('ALL')
const filterTenant = ref('')

const filteredFeedback = computed(() => {
  let result = feedback.value
  if (searchQuery.value) {
    const q = searchQuery.value.toLowerCase()
    result = result.filter(f =>
      f.title.toLowerCase().includes(q) ||
      f.description.toLowerCase().includes(q) ||
      f.id.toLowerCase().includes(q)
    )
  }
  if (filterType.value !== 'ALL') {
    result = result.filter(f => f.type === filterType.value)
  }
  if (filterSeverity.value !== 'ALL') {
    result = result.filter(f => f.severity === filterSeverity.value)
  }
  if (filterStatus.value !== 'ALL') {
    result = result.filter(f => f.status === filterStatus.value)
  }
  if (filterTenant.value) {
    result = result.filter(f => f.tenant === filterTenant.value)
  }
  return result
})

const uniqueTenants = computed(() => {
  const tenants = new Set(feedback.value.map(f => f.tenant).filter(Boolean))
  return Array.from(tenants).sort()
})

async function loadFeedback() {
  loading.value = true
  error.value = null
  try {
    const resp = await fetch('/api/v1/admin/feedback')
    if (!resp.ok) throw new Error('Failed to fetch feedback')
    feedback.value = await resp.json()
  } catch {
    feedback.value = []
  } finally {
    loading.value = false
  }
}

async function updateStatus(item: AdminFeedback, status: AdminFeedback['status']) {
  try {
    await fetch(`/api/v1/admin/feedback/${item.id}/status`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ status }),
    })
    item.status = status
  } catch (err) {
    console.error('[FeedbackAdmin] Failed to update status:', err)
  }
}

function severityClass(severity: string): string {
  switch (severity) {
    case 'CRITICAL': return 'bg-danger-muted text-danger'
    case 'HIGH': return 'bg-orange-600/20 text-orange-300'
    case 'MEDIUM': return 'bg-yellow-600/20 text-warning'
    case 'LOW': return 'bg-success-muted text-success'
    default: return 'bg-surface-4/20 text-text-secondary'
  }
}

function statusClass(status: string): string {
  switch (status) {
    case 'OPEN': return 'bg-info-muted text-info'
    case 'IN_PROGRESS': return 'bg-yellow-600/20 text-warning'
    case 'RESOLVED': return 'bg-success-muted text-success'
    case 'CLOSED': return 'bg-surface-4/20 text-text-secondary'
    case 'WONT_FIX': return 'bg-surface-4/20 text-text-tertiary'
    default: return 'bg-surface-4/20 text-text-secondary'
  }
}

function typeClass(type: string): string {
  switch (type) {
    case 'BUG': return 'bg-danger-muted text-danger'
    case 'FEATURE_REQUEST': return 'bg-accent-500/10 text-accent-300'
    case 'USABILITY': return 'bg-info-muted text-info'
    case 'PERFORMANCE': return 'bg-orange-600/20 text-orange-300'
    case 'DATA_QUALITY': return 'bg-yellow-600/20 text-warning'
    default: return 'bg-surface-4/20 text-text-secondary'
  }
}

onMounted(loadFeedback)
</script>

<template>
  <div class="flex-1 overflow-y-auto p-6">
    <div class="flex items-center justify-between mb-6">
      <div>
        <h1 class="text-xl font-bold">Feedback Management</h1>
        <p class="text-sm text-text-secondary mt-1">Review and manage user feedback, linked to Sentry and OpenReplay</p>
      </div>
      <button class="px-3 py-1.5 bg-surface-3 hover:bg-surface-4 text-sm rounded text-white" @click="loadFeedback">
        Refresh
      </button>
    </div>

    <div v-if="error" class="mb-4 p-3 bg-danger-muted border border-danger rounded-lg text-danger text-sm">
      {{ error }}
    </div>

    <!-- Filters -->
    <div class="flex items-center gap-4 mb-4">
      <input
        v-model="searchQuery"
        type="text"
        class="flex-1 bg-surface-2 border border-border-subtle rounded px-3 py-1.5 text-sm text-text-primary"
        placeholder="Search feedback..."
      />
      <select v-model="filterType" class="bg-surface-2 border border-border-subtle rounded px-2 py-1.5 text-sm text-text-primary">
        <option value="ALL">All Types</option>
        <option value="BUG">Bug</option>
        <option value="FEATURE_REQUEST">Feature Request</option>
        <option value="USABILITY">Usability</option>
        <option value="PERFORMANCE">Performance</option>
        <option value="DATA_QUALITY">Data Quality</option>
      </select>
      <select v-model="filterSeverity" class="bg-surface-2 border border-border-subtle rounded px-2 py-1.5 text-sm text-text-primary">
        <option value="ALL">All Severity</option>
        <option value="CRITICAL">Critical</option>
        <option value="HIGH">High</option>
        <option value="MEDIUM">Medium</option>
        <option value="LOW">Low</option>
      </select>
      <select v-model="filterStatus" class="bg-surface-2 border border-border-subtle rounded px-2 py-1.5 text-sm text-text-primary">
        <option value="ALL">All Status</option>
        <option value="OPEN">Open</option>
        <option value="IN_PROGRESS">In Progress</option>
        <option value="RESOLVED">Resolved</option>
        <option value="CLOSED">Closed</option>
        <option value="WONT_FIX">Won't Fix</option>
      </select>
      <select v-model="filterTenant" class="bg-surface-2 border border-border-subtle rounded px-2 py-1.5 text-sm text-text-primary">
        <option value="">All Tenants</option>
        <option v-for="t in uniqueTenants" :key="t" :value="t">{{ t }}</option>
      </select>
      <span class="text-xs text-text-tertiary">{{ filteredFeedback.length }} items</span>
    </div>

    <div v-if="loading" class="text-text-secondary text-sm">Loading feedback...</div>

    <div v-else-if="filteredFeedback.length === 0" class="text-text-tertiary text-sm">
      No feedback found matching the current filters.
    </div>

    <div v-else class="space-y-3">
      <div
        v-for="item in filteredFeedback"
        :key="item.id"
        class="bg-surface-2 border border-border-subtle rounded-lg p-4"
      >
        <div class="flex items-start justify-between mb-2">
          <div class="flex items-center gap-2 flex-wrap">
            <span class="text-xs px-1.5 py-0.5 rounded" :class="typeClass(item.type)">{{ item.type.replace('_', ' ') }}</span>
            <span class="text-xs px-1.5 py-0.5 rounded" :class="severityClass(item.severity)">{{ item.severity }}</span>
            <span class="text-xs px-1.5 py-0.5 rounded" :class="statusClass(item.status)">{{ item.status.replace('_', ' ') }}</span>
            <span class="text-xs font-mono text-text-tertiary">{{ item.id.slice(0, 8) }}</span>
          </div>
          <span class="text-xs text-text-tertiary">{{ item.createdAt?.slice(0, 10) || '—' }}</span>
        </div>
        <h3 class="text-sm font-medium text-white mb-1">{{ item.title }}</h3>
        <p class="text-xs text-text-secondary mb-3 line-clamp-2">{{ item.description }}</p>
        <div class="flex items-center justify-between">
          <div class="flex items-center gap-3 text-xs text-text-tertiary">
            <span v-if="item.tenant">Tenant: {{ item.tenant }}</span>
            <span v-if="item.user">User: {{ item.user }}</span>
            <a
              v-if="item.sentryIssueId"
              :href="'https://sentry.io/issues/' + item.sentryIssueId"
              target="_blank"
              class="text-orange-400 hover:text-orange-300"
            >
              share-2 Sentry #{{ item.sentryIssueId.slice(0, 8) }}
            </a>
            <a
              v-if="item.openReplaySessionUrl"
              :href="item.openReplaySessionUrl"
              target="_blank"
              class="text-info hover:text-info"
            >
              🎥 OpenReplay
            </a>
          </div>
          <div class="flex gap-1">
            <button
              v-if="item.status === 'OPEN'"
              class="text-[10px] px-2 py-0.5 bg-yellow-600/20 text-warning rounded hover:bg-yellow-600/30"
              @click="updateStatus(item, 'IN_PROGRESS')"
            >
              Start
            </button>
            <button
              v-if="item.status === 'IN_PROGRESS'"
              class="text-[10px] px-2 py-0.5 bg-success-muted text-success rounded hover:bg-green-600/30"
              @click="updateStatus(item, 'RESOLVED')"
            >
              Resolve
            </button>
            <button
              v-if="item.status !== 'CLOSED' && item.status !== 'WONT_FIX'"
              class="text-[10px] px-2 py-0.5 bg-surface-4/20 text-text-primary rounded hover:bg-surface-4/30"
              @click="updateStatus(item, 'CLOSED')"
            >
              Close
            </button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
