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
          class="bg-gray-800 border border-gray-600 rounded px-2 py-1.5 text-sm text-white w-48"
          placeholder="Namespace"
        />
        <button class="px-3 py-1.5 bg-gray-700 hover:bg-gray-600 text-sm rounded" @click="loadData">Refresh</button>
        <button class="px-3 py-1.5 bg-blue-600 hover:bg-blue-500 text-sm rounded" @click="openCreate">+ Add Config</button>
      </div>
    </div>

    <!-- Create Form -->
    <div v-if="showForm" class="bg-gray-800 border border-gray-700 rounded-lg p-4 mb-6 max-w-lg">
      <h3 class="text-xs font-semibold text-gray-300 mb-3">Add Configuration</h3>
      <div class="space-y-3">
        <div>
          <label class="text-xs text-gray-400 block mb-1">Key</label>
          <input v-model="formKey" type="text" class="w-full bg-gray-700 border border-gray-600 rounded px-2 py-1.5 text-sm text-white" placeholder="config.key" />
        </div>
        <div>
          <label class="text-xs text-gray-400 block mb-1">Value</label>
          <textarea v-model="formValue" rows="3" class="w-full bg-gray-700 border border-gray-600 rounded px-2 py-1.5 text-sm text-white font-mono resize-none" placeholder="value" />
        </div>
        <div class="flex gap-2">
          <button class="px-3 py-1.5 bg-blue-600 hover:bg-blue-500 text-sm rounded" @click="upsertConfig">Save</button>
          <button class="px-3 py-1.5 bg-gray-700 hover:bg-gray-600 text-sm rounded" @click="showForm = false">Cancel</button>
        </div>
      </div>
    </div>

    <div v-if="loading" class="text-gray-400 text-sm">Loading...</div>
    <div v-else-if="configs.length === 0" class="text-gray-500 text-sm">No configurations in namespace "{{ namespace }}"</div>

    <div v-else class="bg-gray-800 border border-gray-700 rounded-lg overflow-hidden">
      <table class="w-full text-sm">
        <thead>
          <tr class="border-b border-gray-700 text-xs text-gray-400">
            <th class="text-left px-3 py-2">Key</th>
            <th class="text-left px-3 py-2">Value</th>
            <th class="text-left px-3 py-2">Updated</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="c in configs" :key="c.key" class="border-b border-gray-700/50 hover:bg-gray-700/30">
            <td class="px-3 py-2 text-xs font-mono text-blue-300">{{ c.key }}</td>
            <td class="px-3 py-2 text-xs text-gray-300 font-mono max-w-md truncate">{{ c.value }}</td>
            <td class="px-3 py-2 text-xs text-gray-500">{{ c.updatedAt || '—' }}</td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>
