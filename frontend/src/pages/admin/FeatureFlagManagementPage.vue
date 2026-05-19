<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { FeatureFlagAPI } from '@/api/admin/feature-flags'
import type { FeatureFlagDefinition } from '@/api/admin/feature-flags'
import FeatureFlagEditorVue from './FeatureFlagEditor.vue'
import FeatureFlagEvaluationPreviewVue from './FeatureFlagEvaluationPreview.vue'
import FeatureFlagEvaluationLogVue from './FeatureFlagEvaluationLog.vue'
import LoadingState from '@/components/ui/LoadingState.vue'
import EmptyState from '@/components/ui/EmptyState.vue'
import StatusBadge from '@/components/ui/StatusBadge.vue'

type Tab = 'flags' | 'preview' | 'logs'

const loading = ref(true)
const flags = ref<FeatureFlagDefinition[]>([])
const error = ref<string | null>(null)
const activeTab = ref<Tab>('flags')
const showEditor = ref(false)
const editingFlag = ref<FeatureFlagDefinition | null>(null)

const searchQuery = ref('')
const filterType = ref<string>('ALL')
const filterStatus = ref<string>('ALL')

const filteredFlags = computed(() => {
  let result = flags.value
  if (searchQuery.value) {
    const q = searchQuery.value.toLowerCase()
    result = result.filter(f =>
      f.flagKey.toLowerCase().includes(q) ||
      f.name.toLowerCase().includes(q) ||
      f.owner.toLowerCase().includes(q)
    )
  }
  if (filterType.value !== 'ALL') {
    result = result.filter(f => f.type === filterType.value)
  }
  if (filterStatus.value === 'ACTIVE') {
    result = result.filter(f => f.enabled)
  } else if (filterStatus.value === 'DISABLED') {
    result = result.filter(f => !f.enabled)
  }
  return result
})

async function loadFlags() {
  loading.value = true
  error.value = null
  try {
    flags.value = await FeatureFlagAPI.listFeatureFlags()
  } catch (err) {
    const msg = err instanceof Error ? err.message : String(err)
    error.value = msg
  } finally {
    loading.value = false
  }
}

function createFlag() {
  editingFlag.value = null
  showEditor.value = true
}

function editFlag(flag: FeatureFlagDefinition) {
  editingFlag.value = { ...flag, variants: [...flag.variants], targetingRules: [...flag.targetingRules] }
  showEditor.value = true
}

async function toggleFlag(flag: FeatureFlagDefinition) {
  try {
    if (flag.enabled) {
      await FeatureFlagAPI.disableFeatureFlag(flag.flagKey)
    } else {
      await FeatureFlagAPI.enableFeatureFlag(flag.flagKey)
    }
    await loadFlags()
  } catch (err) {
    console.error('[FeatureFlagManagement] Failed to toggle flag:', err)
  }
}

async function archiveFlag(flag: FeatureFlagDefinition) {
  try {
    await FeatureFlagAPI.archiveFeatureFlag(flag.flagKey)
    await loadFlags()
  } catch (err) {
    console.error('[FeatureFlagManagement] Failed to archive flag:', err)
  }
}

function onEditorClose() {
  showEditor.value = false
  editingFlag.value = null
}

function onEditorSave() {
  showEditor.value = false
  editingFlag.value = null
  loadFlags()
}

function typeVariant(type: string): 'success' | 'warning' | 'danger' | 'info' | 'neutral' {
  switch (type) {
    case 'BOOLEAN': return 'info'
    case 'STRING': return 'success'
    case 'NUMBER': return 'warning'
    case 'JSON': return 'danger'
    default: return 'neutral'
  }
}

onMounted(loadFlags)
</script>

<template>
  <div class="flex-1 overflow-y-auto p-6">
    <div class="flex items-center justify-between mb-6">
      <div>
        <h1 class="text-xl font-bold">Feature Flag Management</h1>
        <p class="text-sm text-gray-400 mt-1">Manage feature flags, targeting rules, and rollouts</p>
      </div>
      <div class="flex gap-2">
        <button class="px-3 py-1.5 bg-blue-600 hover:bg-blue-500 text-sm rounded text-white" @click="createFlag">
          + New Flag
        </button>
        <button class="px-3 py-1.5 bg-gray-700 hover:bg-gray-600 text-sm rounded text-white" @click="loadFlags">
          Refresh
        </button>
      </div>
    </div>

    <!-- Tabs -->
    <div class="flex border-b border-gray-700 mb-4">
      <button
        class="px-4 py-2 text-sm"
        :class="activeTab === 'flags' ? 'text-blue-400 border-b-2 border-blue-400' : 'text-gray-400 hover:text-white'"
        @click="activeTab = 'flags'"
      >
        Flags ({{ flags.length }})
      </button>
      <button
        class="px-4 py-2 text-sm"
        :class="activeTab === 'preview' ? 'text-blue-400 border-b-2 border-blue-400' : 'text-gray-400 hover:text-white'"
        @click="activeTab = 'preview'"
      >
        Evaluation Preview
      </button>
      <button
        class="px-4 py-2 text-sm"
        :class="activeTab === 'logs' ? 'text-blue-400 border-b-2 border-blue-400' : 'text-gray-400 hover:text-white'"
        @click="activeTab = 'logs'"
      >
        Evaluation Logs
      </button>
    </div>

    <div v-if="error" class="mb-4 p-3 bg-red-900/30 border border-red-700 rounded-lg text-red-300 text-sm">
      {{ error }}
    </div>

    <template v-if="activeTab === 'flags'">
      <LoadingState v-if="loading" message="Loading feature flags..." />

      <template v-else>
        <div class="flex items-center gap-4 mb-4">
          <input
            v-model="searchQuery"
            type="text"
            class="flex-1 bg-gray-800 border border-gray-700 rounded px-3 py-1.5 text-sm text-gray-200"
            placeholder="Search by key, name, or owner..."
          />
          <select v-model="filterType" class="bg-gray-800 border border-gray-700 rounded px-2 py-1.5 text-sm text-gray-200">
            <option value="ALL">All Types</option>
            <option value="BOOLEAN">Boolean</option>
            <option value="STRING">String</option>
            <option value="NUMBER">Number</option>
            <option value="JSON">JSON</option>
          </select>
          <select v-model="filterStatus" class="bg-gray-800 border border-gray-700 rounded px-2 py-1.5 text-sm text-gray-200">
            <option value="ALL">All Status</option>
            <option value="ACTIVE">Active</option>
            <option value="DISABLED">Disabled</option>
          </select>
          <span class="text-xs text-gray-500">{{ filteredFlags.length }} flags</span>
        </div>

        <EmptyState
          v-if="filteredFlags.length === 0"
          icon="🚩"
          title="No feature flags found"
          description="Create your first feature flag to get started."
        >
          <template #action>
            <button class="px-3 py-1.5 bg-blue-600 hover:bg-blue-500 text-sm rounded text-white" @click="createFlag">
              + New Flag
            </button>
          </template>
        </EmptyState>

        <div v-else class="bg-gray-800 border border-gray-700 rounded-lg overflow-hidden">
          <table class="w-full text-sm">
            <thead>
              <tr class="border-b border-gray-700 text-xs text-gray-400">
                <th class="text-left px-3 py-2">Key</th>
                <th class="text-left px-3 py-2">Name</th>
                <th class="text-left px-3 py-2">Type</th>
                <th class="text-center px-3 py-2">Status</th>
                <th class="text-left px-3 py-2">Owner</th>
                <th class="text-left px-3 py-2">Rules</th>
                <th class="text-left px-3 py-2">Modified</th>
                <th class="text-center px-3 py-2">Actions</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="flag in filteredFlags" :key="flag.flagKey" class="border-b border-gray-700/50 hover:bg-gray-700/30">
                <td class="px-3 py-2">
                  <span class="text-xs font-mono text-blue-300">{{ flag.flagKey }}</span>
                </td>
                <td class="px-3 py-2 text-xs text-gray-200">{{ flag.name }}</td>
                <td class="px-3 py-2">
                  <StatusBadge :variant="typeVariant(flag.type)" :label="flag.type" />
                </td>
                <td class="px-3 py-2 text-center">
                  <span
                    class="text-xs px-1.5 py-0.5 rounded"
                    :class="flag.enabled ? 'bg-green-600/20 text-green-300' : 'bg-gray-600/20 text-gray-400'"
                  >
                    {{ flag.enabled ? 'Active' : 'Disabled' }}
                  </span>
                </td>
                <td class="px-3 py-2 text-xs text-gray-400">{{ flag.owner }}</td>
                <td class="px-3 py-2 text-xs text-gray-400">{{ flag.targetingRules.length }}</td>
                <td class="px-3 py-2 text-xs text-gray-500">{{ flag.updatedAt?.slice(0, 10) || '—' }}</td>
                <td class="px-3 py-2">
                  <div class="flex items-center justify-center gap-1">
                    <button class="text-[10px] text-blue-400 hover:text-blue-300 px-1" @click="editFlag(flag)">Edit</button>
                    <button
                      class="text-[10px] px-1"
                      :class="flag.enabled ? 'text-yellow-400 hover:text-yellow-300' : 'text-green-400 hover:text-green-300'"
                      @click="toggleFlag(flag)"
                    >
                      {{ flag.enabled ? 'Disable' : 'Enable' }}
                    </button>
                    <button class="text-[10px] text-red-400 hover:text-red-300 px-1" @click="archiveFlag(flag)">Archive</button>
                  </div>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </template>
    </template>

    <FeatureFlagEvaluationPreviewVue v-else-if="activeTab === 'preview'" :flags="flags" />
    <FeatureFlagEvaluationLogVue v-else-if="activeTab === 'logs'" />

    <FeatureFlagEditorVue
      v-if="showEditor"
      :flag="editingFlag"
      @save="onEditorSave"
      @close="onEditorClose"
    />
  </div>
</template>
