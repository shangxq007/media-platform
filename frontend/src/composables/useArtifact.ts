import { ref } from 'vue'
import type { Artifact } from '@/types'

export function useArtifact() {
  const artifact = ref<Artifact | null>(null)
  const isDownloading = ref(false)
  const downloadError = ref<string | null>(null)
  const previewOpen = ref(false)

  function setArtifact(data: Artifact) {
    artifact.value = data
  }

  async function downloadArtifact() {
    if (!artifact.value) return
    isDownloading.value = true
    downloadError.value = null

    try {
      const url = artifact.value.outputUrl
      if (url) {
        window.open(url, '_blank') ?? null
      } else {
        downloadError.value = 'COMMON-404-001: No download URL available'
      }
    } catch (err: any) {
      downloadError.value = err.message || 'Download failed'
    } finally {
      isDownloading.value = false
    }
  }

  function openPreview() {
    previewOpen.value = true
  }

  function closePreview() {
    previewOpen.value = false
  }

  return {
    artifact,
    isDownloading,
    downloadError,
    previewOpen,
    setArtifact,
    downloadArtifact,
    openPreview,
    closePreview,
  }
}
