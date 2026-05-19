<script setup lang="ts">
import { ref } from 'vue'
import { exportToOTIO, importFromOTIO } from '@/utils/otio'
import { useTimelineStore } from '@/stores/timeline'

const store = useTimelineStore()
const importing = ref(false)

function exportTimeline() {
  const otio = exportToOTIO(store.state)
  const blob = new Blob([JSON.stringify(otio, null, 2)], { type: 'application/json' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `timeline-${Date.now()}.json`
  a.click()
  URL.revokeObjectURL(url)
}

async function importTimeline(event: Event) {
  const input = event.target as HTMLInputElement
  if (!input.files?.length) return

  importing.value = true
  try {
    const file = input.files[0]
    const text = await file.text()
    const otioData = JSON.parse(text)
    importFromOTIO(otioData, store)
  } catch (err) {
    alert(`Failed to import timeline: ${(err as Error).message}`)
  } finally {
    importing.value = false
    input.value = ''
  }
}
</script>

<template>
  <div class="p-2 border-t border-gray-700 space-y-2">
    <h4 class="text-xs font-medium text-white">OTIO Timeline</h4>
    <div class="flex gap-2">
      <button
        class="px-3 py-1.5 bg-clip-video/20 text-clip-video text-xs rounded hover:bg-clip-video/30"
        @click="exportTimeline"
      >
        📤 Export JSON
      </button>
      <label class="px-3 py-1.5 bg-gray-700 text-white text-xs rounded cursor-pointer hover:bg-gray-600">
        📥 Import JSON
        <input
          type="file"
          accept=".json"
          class="hidden"
          @change="importTimeline"
          :disabled="importing"
        />
      </label>
    </div>
    <div v-if="importing" class="text-xs text-yellow-400">Importing...</div>
  </div>
</template>