<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { FeatureFlagAPI } from '@/api/admin/feature-flags'
import type { FeatureFlagEvaluationLogEntry } from '@/api/admin/feature-flags'
import LoadingState from '@/components/ui/LoadingState.vue'
import EmptyState from '@/components/ui/EmptyState.vue'

const loading = ref(true)
const error = ref<string | null>(null)
const entries = ref<FeatureFlagEvaluationLogEntry[]>([])
const total = ref(0)

const filterFlagKey = ref('')
const filterTenant = ref('')
const filterWorkspace = ref('')
const filterUser = ref('')
const filterResult = ref('')
const page = ref(0)
const pageSize = 25

const totalPages = computed(() => Math.ceil(total.value / pageSize))

async function loadLogs() {
  loading.value = true
  error.value = null
  try {
    const result = await FeatureFlagAPI.getEvaluationLogs({
      flagKey: filterFlagKey.value || undefined,
      tenant: filterTenant.value || undefined,
      workspace: filterWorkspace.value || undefined,
      user: filterUser.value || undefined,
      result: filterResult.value || undefined,
      page: page.value,
      size: pageSize,
    })
    entries.value = result.entries
    total.value = result.total
  } catch (err) {
    error.value = err instanceof Error ? err.message : String(err)
  } finally {
    loading.value = false
  }
}

function applyFilters() {
  page.value = 0
  loadLogs()
}

function nextPage() {
  if (page.value < totalPages.value - 1) {
    page.value++
    loadLogs()
  }
}

function prevPage() {
  if (page.value > 0) {
    page.value--
    loadLogs()
  }
}

onMounted(loadLogs)
</script>

<template>
  <div class="space-y-4">
    <div class="bg-gray-800 border border-gray-700 rounded-lg p-4">
      <h2 class="text-sm font-semibold text-gray-300 mb-3">Filters</h2>
      <div class="grid grid-cols-5 gap-3">
        <div>
          <label class="block text-xs text-gray-400 mb-1">Flag Key</label>
          <input v-model="filterFlagKey" type="text" class="w-full bg-gray-900 border border-gray-700 rounded px-2 py-1.5 text-sm text-gray-200" placeholder="Any" />
        </div>
        <div>
          <label class="block text-xs text-gray-400 mb-1">Tenant</label>
          <input v-model="filterTenant" type="text" class="w-full bg-gray-900 border border-gray-700 rounded px-2 py-1.5 text-sm text-gray-200" placeholder="Any" />
        </div>
        <div>
          <label class="block text-xs text-gray-400 mb-1">Workspace</label>
          <input v-model="filterWorkspace" type="text" class="w-full bg-gray-900 border border-gray-700 rounded px-2 py-1.5 text-sm text-gray-200" placeholder="Any" />
        </div>
        <div>
          <label class="block text-xs text-gray-400 mb-1">User</label>
          <input v-model="filterUser" type="text" class="w-full bg-gray-900 border border-gray-700 rounded px-2 py-1.5 text-sm text-gray-200" placeholder="Any" />
        </div>
        <div>
          <label class="block text-xs text-gray-400 mb-1">Result</label>
          <select v-model="filterResult" class="w-full bg-gray-900 border border-gray-700 rounded px-2 py-1.5 text-sm text-gray-200">
            <option value="">Any</option>
            <option value="ENABLED">Enabled</option>
            <option value="DISABLED">Disabled</option>
          </select>
        </div>
      </div>
      <button class="mt-3 px-3 py-1.5 bg-blue-600 hover:bg-blue-500 text-white text-sm rounded" @click="applyFilters">
        Apply Filters
      </button>
    </div>

    <div v-if="error" class="p-3 bg-red-900/30 border border-red-700 rounded-lg text-red-300 text-sm">
      {{ error }}
    </div>

    <LoadingState v-if="loading" message="Loading evaluation logs..." />

    <EmptyState
      v-else-if="entries.length === 0"
      icon="📋"
      title="No evaluation logs"
      description="No evaluation logs found matching the current filters."
    />

    <template v-else>
      <div class="bg-gray-800 border border-gray-700 rounded-lg overflow-hidden">
        <table class="w-full text-sm">
          <thead>
            <tr class="border-b border-gray-700 text-xs text-gray-400">
              <th class="text-left px-3 py-2">Timestamp</th>
              <th class="text-left px-3 py-2">Flag Key</th>
              <th class="text-left px-3 py-2">Tenant</th>
              <th class="text-left px-3 py-2">Workspace</th>
              <th class="text-left px-3 py-2">User</th>
              <th class="text-center px-3 py-2">Result</th>
              <th class="text-left px-3 py-2">Variant</th>
              <th class="text-left px-3 py-2">Matched Rule</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(entry, idx) in entries" :key="entry.id || idx" class="border-b border-gray-700/50 hover:bg-gray-700/30">
              <td class="px-3 py-2 text-xs text-gray-400">{{ entry.timestamp?.slice(0, 19) || '—' }}</td>
              <td class="px-3 py-2 text-xs font-mono text-blue-300">{{ entry.flagKey }}</td>
              <td class="px-3 py-2 text-xs text-gray-400">{{ entry.tenant || '—' }}</td>
              <td class="px-3 py-2 text-xs text-gray-400">{{ entry.workspace || '—' }}</td>
              <td class="px-3 py-2 text-xs text-gray-400">{{ entry.user || '—' }}</td>
              <td class="px-3 py-2 text-center">
                <span
                  class="text-xs px-1.5 py-0.5 rounded"
                  :class="entry.result === 'ENABLED' ? 'bg-green-600/20 text-green-300' : 'bg-red-600/20 text-red-300'"
                >
                  {{ entry.result }}
                </span>
              </td>
              <td class="px-3 py-2 text-xs text-gray-400">{{ entry.variant || '—' }}</td>
              <td class="px-3 py-2 text-xs text-gray-400">{{ entry.matchedRule || '—' }}</td>
            </tr>
          </tbody>
        </table>
      </div>

      <div class="flex items-center justify-between">
        <span class="text-xs text-gray-500">{{ total }} total entries</span>
        <div class="flex items-center gap-2">
          <button
            class="px-3 py-1 text-xs bg-gray-700 hover:bg-gray-600 text-white rounded disabled:opacity-50"
            :disabled="page === 0"
            @click="prevPage"
          >
            ← Prev
          </button>
          <span class="text-xs text-gray-400">Page {{ page + 1 }} of {{ totalPages || 1 }}</span>
          <button
            class="px-3 py-1 text-xs bg-gray-700 hover:bg-gray-600 text-white rounded disabled:opacity-50"
            :disabled="page >= totalPages - 1"
            @click="nextPage"
          >
            Next →
          </button>
        </div>
      </div>
    </template>
  </div>
</template>
