<script setup lang="ts">
import { ref, computed } from 'vue'
import type { SubtitleCue, SubtitleTrack } from '@/types'

const props = defineProps<{
  track: SubtitleTrack
  duration: number
}>()

const emit = defineEmits<{
  'edit-cue': [cue: SubtitleCue]
  'delete-cue': [cueId: string]
  'update-cue': [cueId: string, updates: Partial<SubtitleCue>]
  'add-cue': []
  'reorder-cues': [cues: SubtitleCue[]]
}>()

const selectedCueId = ref<string | null>(null)
const editingCueId = ref<string | null>(null)
const editingField = ref<'text' | 'startTime' | 'endTime' | null>(null)
const editValue = ref('')
const dragIndex = ref<number | null>(null)
const dropIndex = ref<number | null>(null)

const sortedCues = computed(() => [...props.track.cues].sort((a, b) => a.startTime - b.startTime))

function formatTime(seconds: number): string {
  const h = Math.floor(seconds / 3600)
  const m = Math.floor((seconds % 3600) / 60)
  const s = Math.floor(seconds % 60)
  const ms = Math.floor((seconds % 1) * 100)
  if (h > 0) {
    return `${String(h).padStart(2, '0')}:${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}.${String(ms).padStart(2, '0')}`
  }
  return `${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}.${String(ms).padStart(2, '0')}`
}

function parseTimeStr(str: string): number {
  const parts = str.split(':')
  if (parts.length === 3) {
    return parseFloat(parts[0]) * 3600 + parseFloat(parts[1]) * 60 + parseFloat(parts[2])
  }
  return 0
}

function selectCue(cueId: string) {
  selectedCueId.value = selectedCueId.value === cueId ? null : cueId
}

function startEditText(cue: SubtitleCue) {
  editingCueId.value = cue.id
  editingField.value = 'text'
  editValue.value = cue.text
}

function startEdit(cue: SubtitleCue, field: 'text' | 'startTime' | 'endTime') {
  editingCueId.value = cue.id
  editingField.value = field
  if (field === 'text') {
    editValue.value = cue.text
  } else if (field === 'startTime') {
    editValue.value = formatTime(cue.startTime)
  } else {
    editValue.value = formatTime(cue.endTime)
  }
}

function commitEdit(cue: SubtitleCue) {
  if (editingField.value === 'text') {
    emit('update-cue', cue.id, { text: editValue.value })
  } else if (editingField.value === 'startTime') {
    const time = parseTimeStr(editValue.value)
    if (time >= 0 && time < cue.endTime) {
      emit('update-cue', cue.id, { startTime: time })
    }
  } else if (editingField.value === 'endTime') {
    const time = parseTimeStr(editValue.value)
    if (time > cue.startTime && time <= props.duration) {
      emit('update-cue', cue.id, { endTime: time })
    }
  }
  editingCueId.value = null
  editingField.value = null
}

function cancelEdit() {
  editingCueId.value = null
  editingField.value = null
  editValue.value = ''
}

function handleDelete(cueId: string) {
  emit('delete-cue', cueId)
  if (selectedCueId.value === cueId) selectedCueId.value = null
}

function handleDragStart(e: DragEvent, index: number) {
  dragIndex.value = index
  if (e.dataTransfer) {
    e.dataTransfer.effectAllowed = 'move'
    e.dataTransfer.setData('text/plain', String(index))
  }
}

function handleDragOver(e: DragEvent, index: number) {
  e.preventDefault()
  dropIndex.value = index
}

function handleDragEnd() {
  if (dragIndex.value !== null && dropIndex.value !== null && dragIndex.value !== dropIndex.value) {
    const cues = [...sortedCues.value]
    const [moved] = cues.splice(dragIndex.value, 1)
    cues.splice(dropIndex.value, 0, moved)
    cues.forEach((c, i) => { c.index = i + 1 })
    emit('reorder-cues', cues)
  }
  dragIndex.value = null
  dropIndex.value = null
}

function handleAddCue() {
  emit('add-cue')
}
</script>

<template>
  <div class="flex flex-col">
    <div class="flex items-center justify-between px-2 py-1 border-b border-border-subtle/50 bg-surface-2/30">
      <span class="text-[10px] text-text-secondary">{{ track.cues.length }} cue(s) · {{ track.language }}</span>
      <button
        class="px-1.5 py-0.5 text-[10px] rounded bg-primary-500/20 text-primary-400 hover:bg-primary-500/30 transition-colors"
        @click="handleAddCue"
      >
        + Add Cue
      </button>
    </div>

    <div v-if="!track.cues.length" class="p-3 text-center text-xs text-text-tertiary">
      No cues yet. Upload a subtitle file or add cues manually.
    </div>

    <div v-else class="max-h-60 overflow-y-auto theme-scrollbar">
      <div
        v-for="(cue, index) in sortedCues"
        :key="cue.id"
        class="flex items-center gap-1.5 px-2 py-1.5 border-b border-border-subtle/20 text-xs transition-colors cursor-pointer"
        :class="[
          selectedCueId === cue.id ? 'bg-primary-500/10 border-l-2 border-l-primary-400' : 'hover:bg-surface-2/40 border-l-2 border-l-transparent',
          dragIndex === index ? 'opacity-50' : '',
          dropIndex === index ? 'border-t border-t-primary-400' : ''
        ]"
        draggable="true"
        @click="selectCue(cue.id)"
        @dragstart="handleDragStart($event, index)"
        @dragover="handleDragOver($event, index)"
        @dragend="handleDragEnd"
      >
        <span class="text-text-tertiary w-5 text-right shrink-0 select-none">{{ cue.index }}</span>

        <div class="flex-1 min-w-0 flex items-center gap-1">
          <template v-if="editingCueId === cue.id && editingField === 'startTime'">
            <input
              v-model="editValue"
              class="w-20 bg-surface-2 border border-primary-400 rounded px-1 py-0.5 text-[10px] text-white focus:outline-none"
              @keydown.enter="commitEdit(cue)"
              @keydown.escape="cancelEdit"
              @click.stop
            />
          </template>
          <template v-else>
            <span
              class="text-[10px] text-text-secondary font-mono hover:text-white shrink-0"
              @click.stop="startEdit(cue, 'startTime')"
            >
              {{ formatTime(cue.startTime) }}
            </span>
          </template>

          <span class="text-text-tertiary shrink-0">→</span>

          <template v-if="editingCueId === cue.id && editingField === 'endTime'">
            <input
              v-model="editValue"
              class="w-20 bg-surface-2 border border-primary-400 rounded px-1 py-0.5 text-[10px] text-white focus:outline-none"
              @keydown.enter="commitEdit(cue)"
              @keydown.escape="cancelEdit"
              @click.stop
            />
          </template>
          <template v-else>
            <span
              class="text-[10px] text-text-secondary font-mono hover:text-white shrink-0"
              @click.stop="startEdit(cue, 'endTime')"
            >
              {{ formatTime(cue.endTime) }}
            </span>
          </template>

          <template v-if="editingCueId === cue.id && editingField === 'text'">
            <input
              v-model="editValue"
              class="flex-1 min-w-0 bg-surface-2 border border-primary-400 rounded px-1 py-0.5 text-[10px] text-white focus:outline-none"
              @keydown.enter="commitEdit(cue)"
              @keydown.escape="cancelEdit"
              @click.stop
            />
          </template>
          <span
            v-else
            class="flex-1 min-w-0 text-[10px] text-white truncate cursor-text"
            @click.stop="startEditText(cue)"
          >
            {{ cue.text || '—' }}
          </span>
        </div>

        <button
          class="shrink-0 text-text-tertiary hover:text-danger-500 text-[10px] px-1 transition-colors"
          title="Delete cue"
          @click="handleDelete(cue.id)"
        >
          ✕
        </button>
      </div>
    </div>
  </div>
</template>
