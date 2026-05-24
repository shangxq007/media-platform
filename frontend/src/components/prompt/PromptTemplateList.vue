<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { PromptAPI } from '@/api/prompt'
import type { PromptTemplate } from '@/types'

const templates = ref<PromptTemplate[]>([])
const loading = ref(false)
const error = ref<string | null>(null)
const searchQuery = ref('')
const statusFilter = ref<string>('')

const filteredTemplates = computed(() => {
  let result = templates.value
  if (statusFilter.value) {
    result = result.filter(t => t.status === statusFilter.value)
  }
  if (searchQuery.value) {
    const q = searchQuery.value.toLowerCase()
    result = result.filter(t =>
      t.name?.toLowerCase().includes(q) ||
      t.category?.toLowerCase().includes(q) ||
      t.tags?.some(tag => tag.toLowerCase().includes(q))
    )
  }
  return result
})

const statusColors: Record<string, string> = {
  DRAFT: 'bg-surface-4',
  ACTIVE: 'bg-green-600',
  DEPRECATED: 'bg-yellow-600',
  ARCHIVED: 'bg-red-600'
}

onMounted(loadTemplates)

async function loadTemplates() {
  loading.value = true
  error.value = null
  try {
    templates.value = await PromptAPI.listTemplates(statusFilter.value || undefined)
  } catch (e: any) {
    error.value = e.message || 'Failed to load templates'
  } finally {
    loading.value = false
  }
}

</script>

<template>
  <div class="flex flex-col h-full">
    <!-- Header -->
    <div class="flex items-center justify-between p-3 border-b border-border-subtle">
      <h2 class="text-lg font-semibold text-white">Prompt Templates</h2>
      <button class="px-3 py-1 bg-blue-600 hover:bg-blue-500 text-white text-sm rounded"
        @click="$emit('create')">
        + New Template
      </button>
    </div>

    <!-- Filters -->
    <div class="flex gap-2 p-3 border-b border-border-subtle">
      <input v-model="searchQuery" placeholder="Search templates..."
        class="flex-1 bg-surface-2 border border-border-default rounded px-2 py-1 text-sm text-white" />
      <select v-model="statusFilter" @change="loadTemplates"
        class="bg-surface-2 border border-border-default rounded px-2 py-1 text-sm text-white">
        <option value="">All Status</option>
        <option value="DRAFT">Draft</option>
        <option value="ACTIVE">Active</option>
        <option value="DEPRECATED">Deprecated</option>
        <option value="ARCHIVED">Archived</option>
      </select>
    </div>

    <!-- Error -->
    <div v-if="error" class="p-3 text-danger text-sm">{{ error }}</div>

    <!-- Loading -->
    <div v-if="loading" class="p-3 text-text-secondary text-sm">Loading...</div>

    <!-- List -->
    <div class="flex-1 overflow-y-auto">
      <div v-if="!loading && filteredTemplates.length === 0" class="p-3 text-text-tertiary text-sm">
        No templates found
      </div>
      <div v-for="template in filteredTemplates" :key="template.templateId"
        class="p-3 border-b border-border-subtle hover:bg-surface-2/50 cursor-pointer"
        @click="$emit('select', template)">
        <div class="flex items-center justify-between">
          <div class="flex items-center gap-2">
            <span class="text-white font-medium">{{ template.name }}</span>
            <span class="px-1.5 py-0.5 rounded text-[10px] font-medium"
              :class="statusColors[template.status] || 'bg-surface-4'">
              {{ template.status }}
            </span>
          </div>
          <span class="text-text-tertiary text-xs">v{{ template.currentPromptVersion || '0.0.0' }}</span>
        </div>
        <div class="text-text-secondary text-xs mt-1">{{ template.description }}</div>
        <div class="flex gap-1 mt-1">
          <span v-for="tag in (template.tags || []).slice(0, 3)" :key="tag"
            class="px-1 py-0.5 rounded text-[10px] bg-surface-3 text-text-primary">
            {{ tag }}
          </span>
        </div>
      </div>
    </div>
  </div>
</template>
