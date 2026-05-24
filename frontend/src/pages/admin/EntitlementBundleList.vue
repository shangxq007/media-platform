<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { EntitlementAdminAPI } from '@/api/admin/entitlement-admin'
import type { EntitlementBundle } from '@/types'
import EntitlementBundleEditor from './EntitlementBundleEditor.vue'

const bundles = ref<EntitlementBundle[]>([])
const loading = ref(true)
const showEditor = ref(false)
const editingBundle = ref<EntitlementBundle | null>(null)

onMounted(loadData)

async function loadData() {
  loading.value = true
  try {
    const result = await EntitlementAdminAPI.getBundles()
    bundles.value = result
  } catch { /* backend may not be running */ }
  loading.value = false
}

function newBundle() {
  editingBundle.value = null
  showEditor.value = true
}

function editBundle(bundle: EntitlementBundle) {
  editingBundle.value = bundle
  showEditor.value = true
}

async function archiveBundle(bundleId: string) {
  await EntitlementAdminAPI.archiveBundle(bundleId)
  await loadData()
}

function statusClass(status: string): string {
  switch (status) {
    case 'ACTIVE': return 'bg-success-muted text-success'
    case 'DRAFT': return 'bg-yellow-600/20 text-warning'
    case 'ARCHIVED': return 'bg-surface-4/20 text-text-secondary'
    default: return 'bg-surface-4/20 text-text-secondary'
  }
}
</script>

<template>
  <div class="flex-1 overflow-y-auto p-6 space-y-6">
    <div class="flex items-center justify-between">
      <h1 class="text-xl font-bold text-white">Entitlement Bundles</h1>
      <div class="flex gap-2">
        <button class="px-3 py-1.5 bg-blue-600 hover:bg-blue-500 text-sm rounded text-white" @click="newBundle">+ New Bundle</button>
        <button class="px-3 py-1.5 bg-surface-3 hover:bg-surface-4 text-sm rounded text-white" @click="loadData">Refresh</button>
      </div>
    </div>

    <div v-if="loading" class="text-text-secondary text-sm">Loading...</div>
    <div v-else-if="bundles.length === 0" class="text-text-tertiary text-sm">No bundles defined</div>
    <div v-else class="grid grid-cols-2 gap-4">
      <div v-for="bundle in bundles" :key="bundle.bundleId" class="bg-surface-2 border border-border-subtle rounded-lg p-4">
        <div class="flex items-center justify-between mb-2">
          <div>
            <h3 class="text-sm font-semibold text-white">{{ bundle.name }}</h3>
            <span class="text-[10px] text-text-tertiary">{{ bundle.bundleId }}</span>
          </div>
          <span class="px-1.5 py-0.5 rounded text-[10px]" :class="statusClass(bundle.status)">{{ bundle.status }}</span>
        </div>
        <p class="text-xs text-text-secondary mb-2">{{ bundle.description }}</p>
        <div class="flex items-center gap-2 mb-2">
          <span class="px-1.5 py-0.5 rounded bg-surface-3 text-[10px] text-text-primary">{{ bundle.tier }}</span>
          <span class="text-[10px] text-text-tertiary">{{ bundle.features.length }} features</span>
        </div>
        <div class="flex flex-wrap gap-1 mb-3">
          <span v-for="feat in bundle.features.slice(0, 5)" :key="feat" class="px-1 py-0.5 rounded bg-surface-3 text-[10px] text-text-secondary">{{ feat }}</span>
          <span v-if="bundle.features.length > 5" class="text-[10px] text-text-tertiary">+{{ bundle.features.length - 5 }} more</span>
        </div>
        <div class="flex gap-2">
          <button class="text-[10px] text-info hover:text-info" @click="editBundle(bundle)">Edit</button>
          <button v-if="bundle.status !== 'ARCHIVED'" class="text-[10px] text-warning hover:text-warning" @click="archiveBundle(bundle.bundleId)">Archive</button>
        </div>
      </div>
    </div>

    <EntitlementBundleEditor
      v-if="showEditor"
      :bundle="editingBundle"
      @close="showEditor = false"
      @saved="showEditor = false; loadData()" />
  </div>
</template>
