<script setup lang="ts">
import { ref } from 'vue'
import type { ClipEffect } from '@/types'

const props = defineProps<{
  effects: ClipEffect[]
}>()

const emit = defineEmits<{
  remove: [effectId: string]
  edit: [effect: ClipEffect]
  reorder: [effects: ClipEffect[]]
}>()

const dragIndex = ref<number | null>(null)
const dropIndex = ref<number | null>(null)

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
    const reordered = [...props.effects]
    const [moved] = reordered.splice(dragIndex.value, 1)
    reordered.splice(dropIndex.value, 0, moved)
    emit('reorder', reordered)
  }
  dragIndex.value = null
  dropIndex.value = null
}
</script>

<template>
  <div class="flex flex-col">
    <div class="flex items-center justify-between px-2 py-1 border-b border-gray-700/50">
      <span class="text-[10px] text-gray-400 uppercase tracking-wider">Effect Chain</span>
      <span class="text-[10px] text-gray-500">{{ effects.length }} effect(s)</span>
    </div>

    <div v-if="!effects.length" class="p-4 text-center">
      <div class="text-lg mb-1">✨</div>
      <p class="text-[10px] text-gray-500">No effects applied</p>
      <p class="text-[10px] text-gray-600">Select a clip and apply effects</p>
    </div>

    <div v-else class="space-y-0">
      <div
        v-for="(effect, index) in effects"
        :key="effect.id"
        class="flex items-center gap-2 px-2 py-1.5 border-b border-gray-700/20 transition-colors group"
        :class="[
          dragIndex === index ? 'opacity-50' : '',
          dropIndex === index ? 'border-t border-t-primary-400' : ''
        ]"
        draggable="true"
        @dragstart="handleDragStart($event, index)"
        @dragover="handleDragOver($event, index)"
        @dragend="handleDragEnd"
      >
        <span class="text-gray-600 cursor-grab hover:text-gray-400 shrink-0 select-none" title="Drag to reorder">⠿</span>

        <div class="flex-1 min-w-0">
          <div class="text-[10px] text-white truncate">{{ effect.effectKey }}</div>
          <div v-if="Object.keys(effect.parameters).length" class="text-[9px] text-gray-500 truncate">
            {{ Object.entries(effect.parameters).map(([k, v]) => `${k}: ${v}`).join(' · ') }}
          </div>
        </div>

        <div class="flex items-center gap-0.5 opacity-0 group-hover:opacity-100 transition-opacity">
          <button
            class="px-1 py-0.5 text-[10px] text-gray-500 hover:text-primary-400 transition-colors"
            title="Edit parameters"
            @click="emit('edit', effect)"
          >
            ✎
          </button>
          <button
            class="px-1 py-0.5 text-[10px] text-gray-500 hover:text-danger-500 transition-colors"
            title="Remove effect"
            @click="emit('remove', effect.id)"
          >
            ✕
          </button>
        </div>
      </div>
    </div>
  </div>
</template>
