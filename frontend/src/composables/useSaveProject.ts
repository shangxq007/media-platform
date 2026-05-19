import { ref } from 'vue'
import { useProjectStore } from '@/stores/project'
import { ProjectAPI } from '@/api'
import type { Project } from '@/types'
import { getErrorMessage } from '@/utils/i18n'

export function useSaveProject() {
  const projectStore = useProjectStore()

  const isSaving = ref(false)
  const isDirty = ref(false)
  const lastSavedAt = ref<Date | null>(null)
  const saveError = ref<string | null>(null)

  function markDirty() {
    isDirty.value = true
  }

  function clearDirty() {
    isDirty.value = false
    saveError.value = null
  }

  async function saveProject() {
    if (isSaving.value) return

    isSaving.value = true
    saveError.value = null

    try {
      const current = projectStore.currentProject
      if (!current) {
        saveError.value = 'PROJECT-400-001: No project to save'
        return
      }

      const saved: Project = await ProjectAPI.create(current.name, current.description)
      projectStore.setProject(saved)
      isDirty.value = false
      saveError.value = null
      lastSavedAt.value = new Date()
    } catch (err: any) {
      const errorCode = err.response?.data?.errorCode || 'COMMON-500-001'
      const message = getErrorMessage(errorCode)
      saveError.value = `${errorCode}: ${message}`
    } finally {
      isSaving.value = false
    }
  }

  function getSaveStatusText(): string {
    if (isSaving.value) return 'Saving...'
    if (saveError.value) return `Save failed: ${saveError.value}`
    if (isDirty.value) return 'Unsaved changes'
    if (lastSavedAt.value) {
      const elapsed = Date.now() - lastSavedAt.value.getTime()
      if (elapsed < 5000) return 'All changes saved'
      if (elapsed < 60000) return `Saved ${Math.floor(elapsed / 1000)}s ago`
      return `Saved at ${lastSavedAt.value.toLocaleTimeString()}`
    }
    return 'All changes saved'
  }

  return {
    isSaving,
    isDirty,
    lastSavedAt,
    saveError,
    markDirty,
    clearDirty,
    saveProject,
    getSaveStatusText,
  }
}
