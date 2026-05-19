<script setup lang="ts">
import { ref, computed } from 'vue'
import { RouterView } from 'vue-router'

const projectName = ref('Untitled Project')

const props = withDefaults(defineProps<{
  gpuAvailable?: boolean
  workerConnected?: boolean
  saveStatus?: string
  saveStatusColor?: string
}>(), {
  gpuAvailable: false,
  workerConnected: false,
  saveStatus: '',
  saveStatusColor: '',
})

const displaySaveStatus = computed(() => {
  if (props.saveStatus) return props.saveStatus
  return 'Unsaved changes'
})

const displaySaveColor = computed(() => {
  if (props.saveStatusColor) return props.saveStatusColor
  return 'text-text-muted'
})

defineEmits<{
  undo: []
  redo: []
  save: []
  export: []
}>()
</script>

<template>
  <div class="h-full flex flex-col bg-bg-base">
    <header class="h-12 flex items-center justify-between px-md border-b border-default bg-bg-surface flex-shrink-0">
      <div class="flex items-center gap-md">
        <router-link to="/" class="text-text-secondary hover:text-text-primary transition-colors">
          <span class="text-sm">←</span>
        </router-link>
        <span class="text-sm font-medium text-text-primary truncate-text max-w-48">{{ projectName }}</span>
        <span class="text-xs" :class="displaySaveColor">{{ displaySaveStatus }}</span>
      </div>

      <div class="flex items-center gap-sm">
        <div class="flex items-center gap-xs mr-md">
          <span class="flex items-center gap-xs text-xs text-text-muted">
            <span class="w-2 h-2 rounded-full" :class="gpuAvailable ? 'bg-success-500' : 'bg-danger-500'"></span>
            {{ gpuAvailable ? 'GPU' : 'CPU' }}
          </span>
          <span class="flex items-center gap-xs text-xs text-text-muted">
            <span class="w-2 h-2 rounded-full" :class="workerConnected ? 'bg-success-500' : 'bg-text-muted'"></span>
            Worker
          </span>
          <span class="flex items-center gap-xs text-xs text-text-muted">
            <span class="w-2 h-2 rounded-full bg-success-500"></span>
            Connected
          </span>
        </div>

        <button
          class="theme-btn theme-btn-ghost theme-btn-sm"
          title="Undo"
          @click="$emit('undo')"
        >
          ↩️
        </button>
        <button
          class="theme-btn theme-btn-ghost theme-btn-sm"
          title="Redo"
          @click="$emit('redo')"
        >
          ↪️
        </button>
        <button
          class="theme-btn theme-btn-secondary theme-btn-sm"
          @click="$emit('save')"
        >
          💾 Save
        </button>
        <button
          class="theme-btn theme-btn-primary theme-btn-sm"
          @click="$emit('export')"
        >
          📤 Export
        </button>
      </div>
    </header>

    <div class="flex-1 overflow-hidden">
      <RouterView />
    </div>
  </div>
</template>
