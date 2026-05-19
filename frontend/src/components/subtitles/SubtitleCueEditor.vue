<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import type { SubtitleCue } from '@/types'

const props = defineProps<{
  cue: SubtitleCue
  duration: number
}>()

const emit = defineEmits<{
  'save': [updates: Partial<SubtitleCue>]
  'cancel': []
}>()

const text = ref('')
const startTime = ref('')
const endTime = ref('')
const errors = ref<Record<string, string>>({})

function formatTime(seconds: number): string {
  const h = Math.floor(seconds / 3600)
  const m = Math.floor((seconds % 3600) / 60)
  const s = seconds % 60
  return `${String(h).padStart(2, '0')}:${String(m).padStart(2, '0')}:${s.toFixed(3).padStart(6, '0')}`
}

function parseTimeStr(str: string): number {
  const cleaned = str.trim().replace(',', '.')
  const parts = cleaned.split(':')
  if (parts.length === 3) {
    return parseFloat(parts[0]) * 3600 + parseFloat(parts[1]) * 60 + parseFloat(parts[2])
  }
  return NaN
}

watch(() => props.cue, (cue) => {
  text.value = cue.text
  startTime.value = formatTime(cue.startTime)
  endTime.value = formatTime(cue.endTime)
}, { immediate: true })

const isValid = computed(() => {
  const start = parseTimeStr(startTime.value)
  const end = parseTimeStr(endTime.value)
  return !isNaN(start) && !isNaN(end) && start >= 0 && end > start && end <= props.duration && text.value.trim().length > 0
})

function validate(): boolean {
  errors.value = {}
  const start = parseTimeStr(startTime.value)
  const end = parseTimeStr(endTime.value)

  if (isNaN(start) || start < 0) {
    errors.value.startTime = 'Invalid start time'
  }
  if (isNaN(end)) {
    errors.value.endTime = 'Invalid end time'
  } else if (!isNaN(start) && end <= start) {
    errors.value.endTime = 'End time must be after start time'
  } else if (end > props.duration) {
    errors.value.endTime = `End time must be within timeline (${props.duration.toFixed(1)}s)`
  }
  if (!text.value.trim()) {
    errors.value.text = 'Subtitle text is required'
  }

  return Object.keys(errors.value).length === 0
}

function handleSave() {
  if (!validate()) return
  emit('save', {
    text: text.value.trim(),
    startTime: parseTimeStr(startTime.value),
    endTime: parseTimeStr(endTime.value)
  })
}

function handleKeydown(e: KeyboardEvent) {
  if (e.key === 'Escape') emit('cancel')
  if (e.key === 'Enter' && e.ctrlKey) handleSave()
}
</script>

<template>
  <div class="p-3 border border-gray-700 rounded-lg bg-gray-800/60 space-y-3" @keydown="handleKeydown">
    <div class="flex items-center justify-between">
      <span class="text-xs font-medium text-white">Edit Cue #{{ cue.index }}</span>
      <span class="text-[10px] text-gray-500">Ctrl+Enter to save · Esc to cancel</span>
    </div>

    <div>
      <label class="text-[10px] text-gray-500 block mb-1">Subtitle Text</label>
      <textarea
        v-model="text"
        rows="2"
        class="w-full bg-gray-800 border rounded px-2 py-1 text-xs text-white focus:outline-none focus:border-primary-400 resize-none"
        :class="errors.text ? 'border-danger-500' : 'border-gray-600'"
        placeholder="Enter subtitle text..."
      />
      <span v-if="errors.text" class="text-[10px] text-danger-500">{{ errors.text }}</span>
    </div>

    <div class="grid grid-cols-2 gap-2">
      <div>
        <label class="text-[10px] text-gray-500 block mb-1">Start Time</label>
        <input
          v-model="startTime"
          type="text"
          class="w-full bg-gray-800 border rounded px-2 py-1 text-xs text-white font-mono focus:outline-none focus:border-primary-400"
          :class="errors.startTime ? 'border-danger-500' : 'border-gray-600'"
          placeholder="00:00:00.000"
        />
        <span v-if="errors.startTime" class="text-[10px] text-danger-500">{{ errors.startTime }}</span>
      </div>
      <div>
        <label class="text-[10px] text-gray-500 block mb-1">End Time</label>
        <input
          v-model="endTime"
          type="text"
          class="w-full bg-gray-800 border rounded px-2 py-1 text-xs text-white font-mono focus:outline-none focus:border-primary-400"
          :class="errors.endTime ? 'border-danger-500' : 'border-gray-600'"
          placeholder="00:00:00.000"
        />
        <span v-if="errors.endTime" class="text-[10px] text-danger-500">{{ errors.endTime }}</span>
      </div>
    </div>

    <div class="text-[10px] text-gray-500">
      Timeline duration: {{ duration.toFixed(1) }}s
    </div>

    <div class="flex gap-2 pt-1">
      <button
        class="flex-1 px-2 py-1.5 text-xs rounded transition-colors disabled:opacity-40 disabled:cursor-not-allowed"
        :class="isValid ? 'bg-primary-500/20 text-primary-400 hover:bg-primary-500/30' : 'bg-gray-700 text-gray-500'"
        :disabled="!isValid"
        @click="handleSave"
      >
        Save
      </button>
      <button
        class="flex-1 px-2 py-1.5 text-xs bg-gray-700 text-gray-400 rounded hover:bg-gray-600 transition-colors"
        @click="emit('cancel')"
      >
        Cancel
      </button>
    </div>
  </div>
</template>
