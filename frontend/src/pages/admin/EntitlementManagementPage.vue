<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { EntitlementAdminAPI } from '@/api/admin/entitlement-admin'
import type { EntitlementBundle, TenantOverride, UserGrant } from '@/types'
import EntitlementBundleList from './EntitlementBundleList.vue'
import TenantOverridePanel from './TenantOverridePanel.vue'
import UserGrantPanel from './UserGrantPanel.vue'
import SharedGrantsAdminPage from './SharedGrantsAdminPage.vue'

const featureFlaggedEntitlements = ref<Set<string>>(new Set())

onMounted(async () => {
  try {
    const bundles = await EntitlementAdminAPI.getBundles()
    const flagged = new Set<string>()
    for (const b of bundles) {
      if ((b as any)?.featureFlagKeys?.length) {
        flagged.add(b.bundleId)
      }
    }
    featureFlaggedEntitlements.value = flagged
  } catch { /* backend may not be running */ }
})



const activeTab = ref<'bundles' | 'overrides' | 'grants' | 'shared'>('bundles')

const bundles = ref<EntitlementBundle[]>([])
const overrides = ref<TenantOverride[]>([])
const grants = ref<UserGrant[]>([])
const loading = ref(true)

onMounted(loadData)

async function loadData() {
  loading.value = true
  try {
    const [b, o, g] = await Promise.allSettled([
      EntitlementAdminAPI.getBundles(),
      EntitlementAdminAPI.getTenantOverrides(),
      EntitlementAdminAPI.getUserGrants()
    ])
    if (b.status === 'fulfilled') bundles.value = b.value
    if (o.status === 'fulfilled') overrides.value = o.value
    if (g.status === 'fulfilled') grants.value = g.value
  } catch { /* backend may not be running */ }
  loading.value = false
}
</script>

<template>
  <div class="flex-1 overflow-y-auto p-6 space-y-6">
    <div class="flex items-center justify-between">
      <h1 class="text-xl font-bold text-white">Entitlement Management</h1>
      <button class="px-3 py-1.5 bg-gray-700 hover:bg-gray-600 text-sm rounded text-white" @click="loadData">Refresh</button>
    </div>

    <div class="flex gap-1 border-b border-gray-700">
      <button class="px-4 py-2 text-sm" :class="activeTab === 'bundles' ? 'text-white border-b-2 border-blue-500' : 'text-gray-400 hover:text-white'" @click="activeTab = 'bundles'">
        Bundles ({{ bundles.length }})
      </button>
      <button class="px-4 py-2 text-sm" :class="activeTab === 'overrides' ? 'text-white border-b-2 border-blue-500' : 'text-gray-400 hover:text-white'" @click="activeTab = 'overrides'">
        Tenant Overrides ({{ overrides.length }})
      </button>
      <button class="px-4 py-2 text-sm" :class="activeTab === 'grants' ? 'text-white border-b-2 border-blue-500' : 'text-gray-400 hover:text-white'" @click="activeTab = 'grants'">
        User Grants ({{ grants.length }})
      </button>
      <button class="px-4 py-2 text-sm" :class="activeTab === 'shared' ? 'text-white border-b-2 border-blue-500' : 'text-gray-400 hover:text-white'" @click="activeTab = 'shared'">
        Shared Grants
      </button>
    </div>

    <div v-if="loading" class="text-gray-400 text-sm">Loading...</div>
    <template v-else>
       <div v-if="featureFlaggedEntitlements.size > 0" class="mb-4 px-3 py-2 rounded bg-purple-900/20 border border-purple-700/50 text-xs text-purple-300">
         🚩 {{ featureFlaggedEntitlements.size }} entitlement bundle(s) are controlled by feature flags
       </div>
       <EntitlementBundleList v-if="activeTab === 'bundles'" :bundles="bundles" @refresh="loadData" />
       <TenantOverridePanel v-else-if="activeTab === 'overrides'" :overrides="overrides" @refresh="loadData" />
       <UserGrantPanel v-else-if="activeTab === 'grants'" :grants="grants" @refresh="loadData" />
       <SharedGrantsAdminPage v-else-if="activeTab === 'shared'" />
     </template>
  </div>
</template>
