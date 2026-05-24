<script setup lang="ts">
import { computed } from 'vue'
import type { Artifact } from '@/types'

const props = defineProps<{
  open: boolean
  artifact: Artifact | null
}>()

defineEmits<{
  close: []
}>()

const isVideo = computed(() =>
  props.artifact ? ['mp4', 'webm', 'mov', 'mkv'].includes(props.artifact.outputFormat) : false,
)

const isAudio = computed(() =>
  props.artifact ? ['mp3', 'wav', 'aac', 'ogg'].includes(props.artifact.outputFormat) : false,
)

const isImage = computed(() =>
  props.artifact ? ['png', 'jpg', 'jpeg', 'webp'].includes(props.artifact.outputFormat) : false,
)
</script>

<template>
  <Teleport to="body">
    <Transition name="fade">
      <div
        v-if="open && artifact"
        class="fixed inset-0 z-50 flex items-center justify-center bg-black/80"
        @click.self="$emit('close')"
      >
        <div class="bg-surface-0 border border-border-subtle rounded-lg shadow-xl max-w-3xl w-full max-h-[90vh] flex flex-col overflow-hidden">
          <div class="flex items-center justify-between px-4 py-3 border-b border-border-subtle">
            <h3 class="text-sm font-medium text-white truncate">{{ artifact.name }}</h3>
            <button
              class="text-text-secondary hover:text-white text-lg leading-none"
              aria-label="Close preview"
              @click="$emit('close')"
            >
              ✕
            </button>
          </div>

          <div class="flex-1 flex items-center justify-center p-4 overflow-auto bg-black/40">
            <div v-if="isVideo && artifact.outputUrl" class="w-full">
              <video
                :src="artifact.outputUrl"
                controls
                class="w-full max-h-[60vh] rounded"
                :aria-label="`Video preview: ${artifact.name}`"
              >
                Your browser does not support the video tag.
              </video>
            </div>

            <div v-else-if="isAudio && artifact.outputUrl" class="w-full flex flex-col items-center gap-4">
              <div class="w-24 h-24 rounded-full bg-surface-2 flex items-center justify-center">
                <span class="text-4xl">🎵</span>
              </div>
              <audio
                :src="artifact.outputUrl"
                controls
                class="w-full max-w-md"
                :aria-label="`Audio preview: ${artifact.name}`"
              >
                Your browser does not support the audio tag.
              </audio>
            </div>

            <div v-else-if="isImage && (artifact.outputUrl || artifact.thumbnailUrl)" class="w-full flex justify-center">
              <img
                :src="artifact.outputUrl || artifact.thumbnailUrl"
                :alt="artifact.name"
                class="max-w-full max-h-[60vh] rounded object-contain"
              />
            </div>

            <div v-else class="text-center">
              <div class="w-20 h-20 mx-auto mb-4 rounded-lg bg-surface-2 flex items-center justify-center">
                <span class="text-3xl">📄</span>
              </div>
              <p class="text-sm text-text-secondary mb-2">Preview not available for {{ artifact.outputFormat.toUpperCase() }} format</p>
              <p class="text-xs text-text-tertiary">Backend render is still stub — no actual media file generated</p>
              <a
                v-if="artifact.outputUrl"
                :href="artifact.outputUrl"
                target="_blank"
                class="inline-block mt-3 px-3 py-1.5 text-xs bg-info-muted text-info rounded hover:bg-blue-600/30"
              >
                Open file externally
              </a>
            </div>
          </div>

          <div class="px-4 py-3 border-t border-border-subtle flex items-center justify-between">
            <div class="text-[10px] text-text-tertiary">
              {{ artifact.outputFormat.toUpperCase() }} · {{ artifact.width }}×{{ artifact.height }}
            </div>
            <button
              class="px-3 py-1 text-xs bg-surface-3 text-text-primary rounded hover:bg-surface-4"
              @click="$emit('close')"
            >
              Close
            </button>
          </div>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<style scoped>
.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.2s ease;
}
.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}
</style>
