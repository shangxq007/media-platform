<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { WorkspaceEntitlementAPI } from '@/api/workspace'
import type { WorkspaceEntitlementPool } from '@/types'

const route = useRoute()
const workspaceId = route.params.workspaceId as string

const loading = ref(true)
const pools = ref<WorkspaceEntitlementPool[]>([])

onMounted(loadPool)

async function loadPool() {
  loading.value = true
  try {
    pools.value = await WorkspaceEntitlementAPI.getEntitlementPool(workspaceId)
  } catch { /* backend may not be running */ }
  loading.value = false
}

function percent(pool: WorkspaceEntitlementPool): number {
  if (!pool.totalQuota || pool.totalQuota === 0) return 0
  return Math.min(100, Math.round((pool.allocated / pool.totalQuota) * 100))
}

function barClass(pct: number): string {
  if (pct > 80) return 'bg-red-500'
  if (pct > 50) return 'bg-yellow-500'
  return 'bg-green-500'
}
</script>

<template>
  <div class="bg-gray-800 border border-gray-700 rounded-lg p-4">
    <div class="flex items-center justify-between mb-3">
      <h3 class="text-sm font-semibold text-gray-300">Entitlement Pool</h3>
      <button class="text-[10px] text-blue-400 hover:text-blue-300" @click="loadPool">Refresh</button>
    </div>

    <div v-if="loading" class="text-gray-500 text-xs">Loading...</div>
    <div v-else-if="pools.length === 0" class="text-gray-500 text-xs">No pool data</div>
    <div v-else class="space-y-3">
      <div v-for="pool in pools" :key="pool.featureKey">
        <div class="flex items-center justify-between text-xs mb-1">
          <span class="text-gray-300">{{ pool.featureName }}</span>
          <span class="text-gray-500">{{ pool.allocated }} / {{ pool.totalQuota }} {{ pool.unit }} ({{ pool.remaining }} remaining)</span>
        </div>
        <div class="w-full bg-gray-700 rounded-full h-2">
          <div class="h-2 rounded-full transition-all" :class="barClass(percent(pool))"
            :style="{ width: percent(pool) + '%' }" />
        </div>
      </div>
    </div>
  </div>
</template>
