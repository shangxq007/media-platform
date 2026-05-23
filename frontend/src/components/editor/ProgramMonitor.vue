<script setup lang="ts">
import { ref, watch, onMounted, onUnmounted } from 'vue'
import { ClientCompositor } from '@/clientExport/clientCompositor'

const props = defineProps<{
  timelineJson?: string
  currentTime: number
  isPlaying: boolean
  watermark?: boolean
}>()

const canvasRef = ref<HTMLCanvasElement | null>(null)
const compositor = new ClientCompositor()
let rafId = 0
const previewError = ref<string | null>(null)

async function paintFrame() {
  const canvas = canvasRef.value
  if (!canvas || !props.timelineJson) {
    return
  }
  try {
    previewError.value = null
    await compositor.drawPreviewFrame(
      props.timelineJson,
      canvas,
      props.currentTime,
      props.watermark !== false
    )
  } catch (e) {
    previewError.value = e instanceof Error ? e.message : 'Preview unavailable'
  }
}

function schedulePaint() {
  cancelAnimationFrame(rafId)
  rafId = requestAnimationFrame(() => {
    void paintFrame()
  })
}

watch(
  () => [props.timelineJson, props.currentTime, props.watermark],
  () => schedulePaint(),
  { immediate: true }
)

watch(
  () => props.isPlaying,
  (playing) => {
    if (playing) {
      schedulePaint()
    }
  }
)

onMounted(() => schedulePaint())
onUnmounted(() => cancelAnimationFrame(rafId))

defineExpose({ refresh: paintFrame })
</script>

<template>
  <div class="relative w-full h-full flex items-center justify-center bg-black">
    <canvas
      ref="canvasRef"
      class="max-w-full max-h-full w-full h-full object-contain"
      aria-label="Program monitor preview"
    />
    <p
      v-if="previewError"
      class="absolute bottom-2 left-2 right-2 text-[10px] text-amber-400 bg-black/70 px-2 py-1 rounded text-center"
    >
      {{ previewError }}
    </p>
  </div>
</template>
