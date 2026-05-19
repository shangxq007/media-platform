<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { WorkspaceEntitlementAPI } from '@/api/workspace'
import type { WorkspaceEntitlementPool } from '@/types'

const route = useRoute()
const workspaceId = route.params.workspaceId as string

const loading = ref(true)
const allocations = ref<WorkspaceEntitlementPool[]>([])
const editingKey = ref<string | null>(null)
const editValue = ref(0)
const saving = ref(false)

onMounted(loadAllocations)

async function loadAllocations() {
  loading.value = true
  try {
    allocations.value = await WorkspaceEntitlementAPI.getQuotaAllocations(workspaceId)
  } catch { /* backend may not be running */ }
  loading.value = false
}

function startEdit(pool: WorkspaceEntitlementPool) {
  editingKey.value = pool.featureKey
  editValue.value = pool.totalQuota
}

async function saveEdit(featureKey: string) {
  saving.value = true
  try {
    await WorkspaceEntitlementAPI.updateQuotaAllocation(workspaceId, featureKey, editValue.value)
    editingKey.value = null
    await loadAllocations()
  } catch { /* handle error */ }
  saving.value = false
}
</script>

<template>
  <div class="bg-gray-800 border border-gray-700 rounded-lg p-4 space-y-4">
    <div class="flex items-center justify-between">
      <h3 class="text-sm font-semibold text-gray-300">Quota Allocations</h3>
      <button class="text-[10px] text-blue-400 hover:text-blue-300" @click="loadAllocations">Refresh</button>
    </div>

    <div v-if="loading" class="text-gray-500 text-xs">Loading...</div>
    <div v-else-if="allocations.length === 0" class="text-gray-500 text-xs">No allocations</div>
    <div v-else class="space-y-2">
      <div v-for="pool in allocations" :key="pool.featureKey" class="flex items-center justify-between p-2 rounded bg-gray-700/20">
        <div class="flex-1">
          <div class="text-xs text-white">{{ pool.featureName }}</div>
          <div class="text-[10px] text-gray-500">{{ pool.allocated }} / {{ pool.totalQuota }} {{ pool.unit }}</div>
        </div>
        <div v-if="editingKey === pool.featureKey" class="flex items-center gap-2">
          <input v-model.number="editValue" type="number" class="w-20 bg-gray-700 border border-gray-600 rounded px-2 py-1 text-xs text-white" />
          <button class="px-2 py-1 bg-blue-600 text-white text-[10px] rounded" :disabled="saving" @click="saveEdit(pool.featureKey)">Save</button>
          <button class="px-2 py-1 bg-gray-600 text-white text-[10px] rounded" @click="editingKey = null">Cancel</button>
        </div>
        <button v-else class="text-[10px] text-blue-400 hover:text-blue-300" @click="startEdit(pool)">Edit</button>
      </div>
    </div>
  </div>
</template>
