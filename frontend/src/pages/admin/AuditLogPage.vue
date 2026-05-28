<script setup lang="ts">
import { ref, onMounted, computed, watch } from 'vue'
import { AuditAPI } from '@/api/admin/audit'
import type { AuditRecordSummary, AuditRecordDetail } from '@/api/admin/audit'
import LoadingState from '@/components/ui/LoadingState.vue'
import EmptyState from '@/components/ui/EmptyState.vue'

const loading = ref(true)
const exporting = ref(false)
const records = ref<AuditRecordSummary[]>([])
const error = ref<string | null>(null)
const selectedRecord = ref<AuditRecordDetail | null>(null)
const showDetail = ref(false)

// Filters
const filterCategory = ref('')
const filterAction = ref('')
const filterActorId = ref('')
const filterResult = ref('')
const filterTargetTenantId = ref('')
const filterFrom = ref('')
const filterTo = ref('')

// Pagination
const page = ref(0)
const pageSize = ref(50)
const total = ref(0)
const totalPages = computed(() => Math.ceil(total.value / pageSize.value))

// Sensitive keys to redact in payload display
const SENSITIVE_KEYS = new Set([
  'authorization', 'cookie', 'token', 'accesstoken', 'refreshtoken',
  'apikey', 'api_key', 'key', 'secret', 'password', 'passwd',
  'signedurl', 'signed_url', 'virtualkey', 'virtual_key',
  'litellmkey', 'litellm_key', 'bearer', 'apikey'
]);

function sanitizePayload(payload: Record<string, unknown> | undefined): Record<string, unknown> {
  if (!payload) return {};
  const result: Record<string, unknown> = {};
  for (const [key, value] of Object.entries(payload)) {
    const normalizedKey = key.toLowerCase().replace(/[-_]/g, '');
    if (SENSITIVE_KEYS.has(normalizedKey)) {
      result[key] = '[REDACTED]';
    } else if (value && typeof value === 'object' && !Array.isArray(value)) {
      result[key] = sanitizePayload(value as Record<string, unknown>);
    } else {
      result[key] = value;
    }
  }
  return result;
}

const sanitizedPayload = computed(() => sanitizePayload(selectedRecord.value?.payload));

// Categories for dropdown
const categories = ref<string[]>([])
const resultOptions = ['SUCCESS', 'DENIED', 'FAILED', 'ERROR']

async function loadCategories() {
  try {
    categories.value = await AuditAPI.listAuditCategories()
  } catch {
    categories.value = []
  }
}

async function loadRecords() {
  loading.value = true
  error.value = null
  try {
    const response = await AuditAPI.listAuditRecords({
      page: page.value,
      size: pageSize.value,
      category: filterCategory.value || undefined,
      action: filterAction.value || undefined,
      actorId: filterActorId.value || undefined,
      result: filterResult.value || undefined,
      targetTenantId: filterTargetTenantId.value || undefined,
      from: filterFrom.value || undefined,
      to: filterTo.value || undefined,
    })
    records.value = response.items
    total.value = response.total
  } catch (err) {
    error.value = err instanceof Error ? err.message : String(err)
  } finally {
    loading.value = false
  }
}

async function openDetail(record: AuditRecordSummary) {
  try {
    selectedRecord.value = await AuditAPI.getAuditRecord(record.id)
    showDetail.value = true
  } catch (err) {
    error.value = err instanceof Error ? err.message : String(err)
  }
}

function closeDetail() {
  showDetail.value = false
  selectedRecord.value = null
}

function applyFilters() {
  page.value = 0
  loadRecords()
}

function resetFilters() {
  filterCategory.value = ''
  filterAction.value = ''
  filterActorId.value = ''
  filterResult.value = ''
  filterTargetTenantId.value = ''
  filterFrom.value = ''
  filterTo.value = ''
  page.value = 0
  loadRecords()
}

async function exportCsv() {
  exporting.value = true
  error.value = null
  try {
    const blob = await AuditAPI.exportAuditRecords({
      category: filterCategory.value || undefined,
      action: filterAction.value || undefined,
      actorId: filterActorId.value || undefined,
      result: filterResult.value || undefined,
      targetTenantId: filterTargetTenantId.value || undefined,
      from: filterFrom.value || undefined,
      to: filterTo.value || undefined,
      limit: 1000,
    })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `audit-records-${new Date().toISOString().slice(0, 19).replace(/[T:]/g, '-')}.csv`
    document.body.appendChild(a)
    a.click()
    document.body.removeChild(a)
    URL.revokeObjectURL(url)
  } catch (err) {
    error.value = err instanceof Error ? err.message : String(err)
  } finally {
    exporting.value = false
  }
}

function categoryClass(category?: string): string {
  if (!category) return 'bg-surface-4/20 text-text-secondary'
  if (category.startsWith('ADMIN_')) return 'bg-accent-500/10 text-accent-300'
  if (category.startsWith('RENDER_')) return 'bg-info-muted text-info'
  if (category.startsWith('DATA_')) return 'bg-success-muted text-success'
  if (category.startsWith('IDENTITY_')) return 'bg-purple-500/10 text-purple-300'
  return 'bg-surface-4/20 text-text-secondary'
}

function resultClass(result?: string | null): string {
  if (!result) return 'bg-surface-4/20 text-text-secondary'
  if (result === 'SUCCESS') return 'bg-success-muted text-success'
  if (result === 'DENIED') return 'bg-danger-muted text-danger'
  if (result === 'FAILED') return 'bg-warning-muted text-warning'
  if (result === 'ERROR') return 'bg-danger-muted text-danger'
  return 'bg-surface-4/20 text-text-secondary'
}

watch([filterCategory, filterAction, filterActorId, filterResult, filterTargetTenantId, filterFrom, filterTo], () => {
  page.value = 0
})

onMounted(async () => {
  await loadCategories()
  await loadRecords()
})
</script>

<template>
  <div class="flex-1 overflow-y-auto p-6">
    <div class="flex items-center justify-between mb-6">
      <div>
        <h1 class="text-xl font-bold">Audit Log</h1>
        <p class="text-sm text-text-secondary mt-1">Server-side filtered audit trail with pagination</p>
      </div>
      <div class="flex gap-2">
        <button class="px-3 py-1.5 bg-surface-3 hover:bg-surface-4 text-sm rounded text-white" @click="loadRecords">
          Refresh
        </button>
        <button
          class="px-3 py-1.5 bg-accent-500 hover:bg-accent-600 text-sm rounded text-white disabled:opacity-50"
          :disabled="exporting"
          @click="exportCsv"
        >
          <span v-if="exporting">Exporting...</span>
          <span v-else>Export CSV</span>
        </button>
      </div>
    </div>

    <div v-if="error" class="mb-4 p-3 bg-danger-muted border border-danger rounded-lg text-danger text-sm">
      {{ error }}
    </div>

    <!-- Filters -->
    <div class="bg-surface-2 border border-border-subtle rounded-lg p-4 mb-4">
      <div class="flex items-center justify-between mb-3">
        <h2 class="text-sm font-semibold text-text-primary">Filters</h2>
        <button class="text-xs text-text-secondary hover:text-text-primary" @click="resetFilters">
          Reset
        </button>
      </div>
      <div class="grid grid-cols-4 gap-3">
        <div>
          <label class="block text-xs text-text-secondary mb-1">Category</label>
          <select v-model="filterCategory" class="w-full bg-surface-0 border border-border-subtle rounded px-2 py-1.5 text-sm text-text-primary" @change="applyFilters">
            <option value="">All</option>
            <option v-for="cat in categories" :key="cat" :value="cat">{{ cat }}</option>
          </select>
        </div>
        <div>
          <label class="block text-xs text-text-secondary mb-1">Action</label>
          <input v-model="filterAction" type="text" class="w-full bg-surface-0 border border-border-subtle rounded px-2 py-1.5 text-sm text-text-primary" placeholder="Filter by action" @keyup.enter="applyFilters" />
        </div>
        <div>
          <label class="block text-xs text-text-secondary mb-1">Actor ID</label>
          <input v-model="filterActorId" type="text" class="w-full bg-surface-0 border border-border-subtle rounded px-2 py-1.5 text-sm text-text-primary" placeholder="Filter by actor" @keyup.enter="applyFilters" />
        </div>
        <div>
          <label class="block text-xs text-text-secondary mb-1">Result</label>
          <select v-model="filterResult" class="w-full bg-surface-0 border border-border-subtle rounded px-2 py-1.5 text-sm text-text-primary" @change="applyFilters">
            <option value="">All</option>
            <option v-for="r in resultOptions" :key="r" :value="r">{{ r }}</option>
          </select>
        </div>
        <div>
          <label class="block text-xs text-text-secondary mb-1">Target Tenant</label>
          <input v-model="filterTargetTenantId" type="text" class="w-full bg-surface-0 border border-border-subtle rounded px-2 py-1.5 text-sm text-text-primary" placeholder="Filter by tenant" @keyup.enter="applyFilters" />
        </div>
        <div>
          <label class="block text-xs text-text-secondary mb-1">From</label>
          <input v-model="filterFrom" type="datetime-local" class="w-full bg-surface-0 border border-border-subtle rounded px-2 py-1.5 text-sm text-text-primary" @change="applyFilters" />
        </div>
        <div>
          <label class="block text-xs text-text-secondary mb-1">To</label>
          <input v-model="filterTo" type="datetime-local" class="w-full bg-surface-0 border border-border-subtle rounded px-2 py-1.5 text-sm text-text-primary" @change="applyFilters" />
        </div>
        <div class="flex items-end">
          <button class="w-full px-3 py-1.5 bg-accent-500 hover:bg-accent-600 text-sm rounded text-white" @click="applyFilters">
            Apply
          </button>
        </div>
      </div>
    </div>

    <LoadingState v-if="loading" message="Loading audit logs..." />

    <EmptyState
      v-else-if="records.length === 0"
      icon="clipboard"
      title="No audit records found"
      description="No audit records match the current filters."
    />

    <template v-else>
      <div class="bg-surface-2 border border-border-subtle rounded-lg overflow-hidden">
        <table class="w-full text-sm">
          <thead>
            <tr class="border-b border-border-subtle text-xs text-text-secondary">
              <th class="text-left px-3 py-2">Timestamp</th>
              <th class="text-left px-3 py-2">Category</th>
              <th class="text-left px-3 py-2">Action</th>
              <th class="text-left px-3 py-2">Actor</th>
              <th class="text-left px-3 py-2">Resource</th>
              <th class="text-left px-3 py-2">Target Tenant</th>
              <th class="text-left px-3 py-2">Result</th>
            </tr>
          </thead>
          <tbody>
            <tr
              v-for="record in records"
              :key="record.id"
              class="border-b border-border-subtle/50 hover:bg-surface-3/30 cursor-pointer"
              @click="openDetail(record)"
            >
              <td class="px-3 py-2 text-xs text-text-secondary whitespace-nowrap">
                {{ record.createdAt?.slice(0, 19) || '—' }}
              </td>
              <td class="px-3 py-2">
                <span class="text-xs px-1.5 py-0.5 rounded" :class="categoryClass(record.category)">
                  {{ record.category || '—' }}
                </span>
              </td>
              <td class="px-3 py-2 text-xs text-text-primary font-mono">
                {{ record.action || '—' }}
              </td>
              <td class="px-3 py-2 text-xs text-text-secondary">
                {{ record.actorId || '—' }}
              </td>
              <td class="px-3 py-2 text-xs text-text-secondary font-mono">
                {{ record.resourceType && record.resourceId ? `${record.resourceType}:${record.resourceId}` : '—' }}
              </td>
              <td class="px-3 py-2 text-xs text-text-secondary">
                {{ record.targetTenantId || '—' }}
              </td>
              <td class="px-3 py-2">
                <span class="text-xs px-1.5 py-0.5 rounded" :class="resultClass(record.result)">
                  {{ record.result || '—' }}
                </span>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <div class="flex items-center justify-between mt-4">
        <span class="text-xs text-text-tertiary">{{ total }} total records</span>
        <div class="flex items-center gap-2">
          <button
            class="px-3 py-1 text-xs bg-surface-3 hover:bg-surface-4 text-white rounded disabled:opacity-50"
            :disabled="page === 0"
            @click="page--; loadRecords()"
          >
            ← Prev
          </button>
          <span class="text-xs text-text-secondary">Page {{ page + 1 }} of {{ totalPages || 1 }}</span>
          <button
            class="px-3 py-1 text-xs bg-surface-3 hover:bg-surface-4 text-white rounded disabled:opacity-50"
            :disabled="page >= totalPages - 1"
            @click="page++; loadRecords()"
          >
            Next →
          </button>
        </div>
      </div>
    </template>

    <!-- Detail Drawer -->
    <div v-if="showDetail" class="fixed inset-0 z-50 flex justify-end" @click.self="closeDetail">
      <div class="w-full max-w-lg bg-surface-1 border-l border-border-subtle overflow-y-auto shadow-xl">
        <div class="flex items-center justify-between p-4 border-b border-border-subtle">
          <h2 class="text-lg font-semibold">Audit Record Detail</h2>
          <button class="text-text-secondary hover:text-text-primary" @click="closeDetail">✕</button>
        </div>
        <div v-if="selectedRecord" class="p-4 space-y-4">
          <div class="grid grid-cols-2 gap-4 text-sm">
            <div>
              <div class="text-text-secondary text-xs mb-1">ID</div>
              <div class="font-mono text-xs">{{ selectedRecord.id }}</div>
            </div>
            <div>
              <div class="text-text-secondary text-xs mb-1">Timestamp</div>
              <div>{{ selectedRecord.createdAt }}</div>
            </div>
            <div>
              <div class="text-text-secondary text-xs mb-1">Category</div>
              <span class="text-xs px-1.5 py-0.5 rounded" :class="categoryClass(selectedRecord.category)">
                {{ selectedRecord.category }}
              </span>
            </div>
            <div>
              <div class="text-text-secondary text-xs mb-1">Action</div>
              <div class="font-mono text-xs">{{ selectedRecord.action }}</div>
            </div>
            <div>
              <div class="text-text-secondary text-xs mb-1">Actor Type</div>
              <div>{{ selectedRecord.actorType }}</div>
            </div>
            <div>
              <div class="text-text-secondary text-xs mb-1">Actor ID</div>
              <div>{{ selectedRecord.actorId }}</div>
            </div>
            <div>
              <div class="text-text-secondary text-xs mb-1">Resource Type</div>
              <div>{{ selectedRecord.resourceType }}</div>
            </div>
            <div>
              <div class="text-text-secondary text-xs mb-1">Resource ID</div>
              <div class="font-mono text-xs">{{ selectedRecord.resourceId }}</div>
            </div>
          </div>
          <div>
            <div class="text-text-secondary text-xs mb-1">Payload (sanitized)</div>
            <pre class="text-text-primary bg-surface-2 rounded p-2 overflow-x-auto max-h-64 text-xs">{{ JSON.stringify(sanitizedPayload, null, 2) }}</pre>
          </div>
        </div>
        <div v-else class="p-4 text-text-secondary">Loading...</div>
      </div>
    </div>
  </div>
</template>
