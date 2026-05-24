<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { AuditAPI } from '@/api/admin/audit'
import type { AuditRecord } from '@/api/admin/audit'
import LoadingState from '@/components/ui/LoadingState.vue'
import EmptyState from '@/components/ui/EmptyState.vue'

interface EnrichedAuditRecord extends AuditRecord {
  beforeJson?: string
  afterJson?: string
  matchedRule?: string
  resourceDisplay?: string
}

const loading = ref(true)
const records = ref<EnrichedAuditRecord[]>([])
const error = ref<string | null>(null)

const filterCategory = ref('')
const filterTenant = ref('')
const filterWorkspace = ref('')
const filterUser = ref('')
const filterActor = ref('')
const expandedRecord = ref<string | null>(null)

const page = ref(0)
const pageSize = 25
const total = ref(0)

const totalPages = computed(() => Math.ceil(total.value / pageSize))

const categories = [
  'ALL',
  'FEATURE_FLAG_CREATE', 'FEATURE_FLAG_UPDATE', 'FEATURE_FLAG_DELETE',
  'FEATURE_FLAG_ENABLE', 'FEATURE_FLAG_DISABLE', 'FEATURE_FLAG_ARCHIVE',
  'POLICY_CREATE', 'POLICY_UPDATE', 'POLICY_DELETE', 'POLICY_ARCHIVE',
  'ACCESS_GRANT', 'ACCESS_DENY', 'ACCESS_REVIEW',
  'NAVIGATION_ACCESS', 'NAVIGATION_DENY',
  'CONFIG_CHANGE', 'TENANT_CHANGE',
]

const filteredRecords = computed(() => {
  let result = records.value
  if (filterCategory.value && filterCategory.value !== 'ALL') {
    result = result.filter(r => r.category === filterCategory.value)
  }
  if (filterTenant.value) {
    result = result.filter(r => r.details?.includes(filterTenant.value))
  }
  if (filterWorkspace.value) {
    result = result.filter(r => r.details?.includes(filterWorkspace.value))
  }
  if (filterUser.value) {
    result = result.filter(r => r.resourceId?.includes(filterUser.value))
  }
  if (filterActor.value) {
    result = result.filter(r => r.actor?.includes(filterActor.value))
  }
  return result
})

async function loadRecords() {
  loading.value = true
  error.value = null
  try {
    const allRecords = await AuditAPI.listRecent()
    records.value = allRecords.map(r => ({
      ...r,
      resourceDisplay: r.resourceType && r.resourceId ? `${r.resourceType}:${r.resourceId}` : '—',
    }))
    total.value = records.value.length
  } catch (err) {
    error.value = err instanceof Error ? err.message : String(err)
  } finally {
    loading.value = false
  }
}

function toggleExpand(record: EnrichedAuditRecord) {
  const id = record.id || ''
  expandedRecord.value = expandedRecord.value === id ? null : id
}

function maskSensitive(value: string): string {
  const sensitivePatterns = [/password/i, /secret/i, /token/i, /key/i, /credential/i]
  for (const pattern of sensitivePatterns) {
    if (pattern.test(value)) {
      return '••••••••'
    }
  }
  return value
}

function categoryClass(category?: string): string {
  if (!category) return 'bg-surface-4/20 text-text-secondary'
  if (category.startsWith('FEATURE_FLAG_')) return 'bg-accent-500/10 text-purple-300'
  if (category.startsWith('POLICY_')) return 'bg-blue-600/20 text-info'
  if (category.startsWith('ACCESS_')) return 'bg-success-muted text-success'
  if (category.startsWith('NAVIGATION_')) return 'bg-yellow-600/20 text-yellow-300'
  return 'bg-surface-4/20 text-text-secondary'
}

onMounted(loadRecords)
</script>

<template>
  <div class="flex-1 overflow-y-auto p-6">
    <div class="flex items-center justify-between mb-6">
      <div>
        <h1 class="text-xl font-bold">Audit Log</h1>
        <p class="text-sm text-text-secondary mt-1">Comprehensive audit trail with sensitive field masking</p>
      </div>
      <button class="px-3 py-1.5 bg-surface-3 hover:bg-surface-4 text-sm rounded text-white" @click="loadRecords">
        Refresh
      </button>
    </div>

    <div v-if="error" class="mb-4 p-3 bg-red-900/30 border border-red-700 rounded-lg text-danger text-sm">
      {{ error }}
    </div>

    <!-- Filters -->
    <div class="bg-surface-2 border border-border-subtle rounded-lg p-4 mb-4">
      <h2 class="text-sm font-semibold text-text-primary mb-3">Filters</h2>
      <div class="grid grid-cols-5 gap-3">
        <div>
          <label class="block text-xs text-text-secondary mb-1">Category</label>
          <select v-model="filterCategory" class="w-full bg-surface-0 border border-border-subtle rounded px-2 py-1.5 text-sm text-text-primary">
            <option v-for="cat in categories" :key="cat" :value="cat === 'ALL' ? '' : cat">{{ cat }}</option>
          </select>
        </div>
        <div>
          <label class="block text-xs text-text-secondary mb-1">Tenant</label>
          <input v-model="filterTenant" type="text" class="w-full bg-surface-0 border border-border-subtle rounded px-2 py-1.5 text-sm text-text-primary" placeholder="Filter by tenant" />
        </div>
        <div>
          <label class="block text-xs text-text-secondary mb-1">Workspace</label>
          <input v-model="filterWorkspace" type="text" class="w-full bg-surface-0 border border-border-subtle rounded px-2 py-1.5 text-sm text-text-primary" placeholder="Filter by workspace" />
        </div>
        <div>
          <label class="block text-xs text-text-secondary mb-1">User</label>
          <input v-model="filterUser" type="text" class="w-full bg-surface-0 border border-border-subtle rounded px-2 py-1.5 text-sm text-text-primary" placeholder="Filter by user" />
        </div>
        <div>
          <label class="block text-xs text-text-secondary mb-1">Actor</label>
          <input v-model="filterActor" type="text" class="w-full bg-surface-0 border border-border-subtle rounded px-2 py-1.5 text-sm text-text-primary" placeholder="Filter by actor" />
        </div>
      </div>
    </div>

    <LoadingState v-if="loading" message="Loading audit logs..." />

    <EmptyState
      v-else-if="filteredRecords.length === 0"
      icon="📋"
      title="No audit records found"
      description="No audit records match the current filters."
    />

    <template v-else>
      <div class="bg-surface-2 border border-border-subtle rounded-lg overflow-hidden">
        <table class="w-full text-sm">
          <thead>
            <tr class="border-b border-border-subtle text-xs text-text-secondary">
              <th class="text-left px-3 py-2 w-8"></th>
              <th class="text-left px-3 py-2">Timestamp</th>
              <th class="text-left px-3 py-2">Category</th>
              <th class="text-left px-3 py-2">Operation</th>
              <th class="text-left px-3 py-2">Resource</th>
              <th class="text-left px-3 py-2">Actor</th>
              <th class="text-left px-3 py-2">Details</th>
            </tr>
          </thead>
          <tbody>
            <template v-for="record in filteredRecords" :key="record.id || Math.random()">
              <tr
                class="border-b border-border-subtle/50 hover:bg-surface-3/30 cursor-pointer"
                @click="toggleExpand(record)"
              >
                <td class="px-3 py-2 text-center">
                  <span class="text-xs text-text-tertiary">{{ expandedRecord === record.id ? '▼' : '▶' }}</span>
                </td>
                <td class="px-3 py-2 text-xs text-text-secondary whitespace-nowrap">{{ record.createdAt?.slice(0, 19) || '—' }}</td>
                <td class="px-3 py-2">
                  <span class="text-xs px-1.5 py-0.5 rounded" :class="categoryClass(record.category)">
                    {{ record.category || '—' }}
                  </span>
                </td>
                <td class="px-3 py-2 text-xs text-text-primary">{{ record.action || '—' }}</td>
                <td class="px-3 py-2 text-xs font-mono text-text-secondary">{{ record.resourceDisplay }}</td>
                <td class="px-3 py-2 text-xs text-text-secondary">{{ record.actor || '—' }}</td>
                <td class="px-3 py-2 text-xs text-text-tertiary truncate max-w-xs">{{ maskSensitive(record.details || '—') }}</td>
              </tr>
              <tr v-if="expandedRecord === record.id" class="bg-surface-0/50">
                <td colspan="7" class="px-6 py-3">
                  <div class="grid grid-cols-2 gap-4 text-xs">
                    <div>
                      <div class="text-text-secondary mb-1">Full Details</div>
                      <pre class="text-text-primary bg-surface-2 rounded p-2 overflow-x-auto max-h-32">{{ record.details || '—' }}</pre>
                    </div>
                    <div>
                      <div class="text-text-secondary mb-1">Before / After</div>
                      <div v-if="record.beforeJson" class="mb-2">
                        <span class="text-text-tertiary">Before:</span>
                        <pre class="text-danger bg-surface-2 rounded p-2 overflow-x-auto max-h-24 mt-1">{{ record.beforeJson }}</pre>
                      </div>
                      <div v-if="record.afterJson">
                        <span class="text-text-tertiary">After:</span>
                        <pre class="text-success bg-surface-2 rounded p-2 overflow-x-auto max-h-24 mt-1">{{ record.afterJson }}</pre>
                      </div>
                      <div v-if="record.matchedRule" class="mt-2">
                        <span class="text-text-tertiary">Matched Rule:</span>
                        <span class="text-info ml-1">{{ record.matchedRule }}</span>
                      </div>
                    </div>
                  </div>
                </td>
              </tr>
            </template>
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
  </div>
</template>
