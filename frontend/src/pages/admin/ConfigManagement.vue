<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ConfigAPI } from '@/api/admin/config'
import type { ConfigEntry } from '@/api/admin/config'

const loading = ref(true)
const namespace = ref('platform')
const configs = ref<ConfigEntry[]>([])
const showForm = ref(false)
const formKey = ref('')
const formValue = ref('')

onMounted(loadData)

async function loadData() {
  loading.value = true
  try {
    configs.value = await ConfigAPI.list(namespace.value)
  } catch { /* backend may not be running */ }
  loading.value = false
}

function openCreate() {
  formKey.value = ''
  formValue.value = ''
  showForm.value = true
}

async function upsertConfig() {
  if (!formKey.value.trim()) return
  await ConfigAPI.upsert(namespace.value, formKey.value.trim(), formValue.value)
  showForm.value = false
  await loadData()
}
</script>

<template>
  <div class="flex-1 overflow-y-auto p-6">
    <div class="flex items-center justify-between mb-6">
      <h1 class="text-xl font-bold">Configuration</h1>
      <div class="flex items-center gap-3">
        <input
          v-model="namespace"
          type="text"
          class="bg-surface-2 border border-border-default rounded px-2 py-1.5 text-sm text-white w-48"
          placeholder="Namespace"
        />
        <button class="px-3 py-1.5 bg-surface-3 hover:bg-surface-4 text-sm rounded" @click="loadData">Refresh</button>
        <button class="px-3 py-1.5 bg-blue-600 hover:bg-blue-500 text-sm rounded" @click="openCreate">+ Add Config</button>
      </div>
    </div>

    <!-- Create Form -->
    <div v-if="showForm" class="bg-surface-2 border border-border-subtle rounded-lg p-4 mb-6 max-w-lg">
      <h3 class="text-xs font-semibold text-text-primary mb-3">Add Configuration</h3>
      <div class="space-y-3">
        <div>
          <label class="text-xs text-text-secondary block mb-1">Key</label>
          <input v-model="formKey" type="text" class="w-full bg-surface-3 border border-border-default rounded px-2 py-1.5 text-sm text-white" placeholder="config.key" />
        </div>
        <div>
          <label class="text-xs text-text-secondary block mb-1">Value</label>
          <textarea v-model="formValue" rows="3" class="w-full bg-surface-3 border border-border-default rounded px-2 py-1.5 text-sm text-white font-mono resize-none" placeholder="value" />
        </div>
        <div class="flex gap-2">
          <button class="px-3 py-1.5 bg-blue-600 hover:bg-blue-500 text-sm rounded" @click="upsertConfig">Save</button>
          <button class="px-3 py-1.5 bg-surface-3 hover:bg-surface-4 text-sm rounded" @click="showForm = false">Cancel</button>
        </div>
      </div>
    </div>

    <div v-if="loading" class="text-text-secondary text-sm">Loading...</div>
    <div v-else-if="configs.length === 0" class="text-text-tertiary text-sm">No configurations in namespace "{{ namespace }}"</div>

    <div v-else class="bg-surface-2 border border-border-subtle rounded-lg overflow-hidden">
      <table class="w-full text-sm">
        <thead>
          <tr class="border-b border-border-subtle text-xs text-text-secondary">
            <th class="text-left px-3 py-2">Key</th>
            <th class="text-left px-3 py-2">Value</th>
            <th class="text-left px-3 py-2">Updated</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="c in configs" :key="c.key" class="border-b border-border-subtle/50 hover:bg-surface-3/30">
            <td class="px-3 py-2 text-xs font-mono text-info">{{ c.key }}</td>
            <td class="px-3 py-2 text-xs text-text-primary font-mono max-w-md truncate">{{ c.value }}</td>
            <td class="px-3 py-2 text-xs text-text-tertiary">{{ c.updatedAt || '—' }}</td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>
