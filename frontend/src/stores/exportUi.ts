import { defineStore } from 'pinia'
import { ref } from 'vue'

/** 导出侧栏 UI 状态（跨页面：我的导出 → 编辑器增量改稿） */
export const useExportUiStore = defineStore('exportUi', () => {
  const mode = ref<'legacy' | 'incremental'>('incremental')
  const baseJobId = ref('')
  const openExportTab = ref(false)
  const pendingProjectId = ref<string | null>(null)

  function requestIncrementalEdit(projectId: string, jobId?: string) {
    mode.value = 'incremental'
    baseJobId.value = jobId ?? ''
    pendingProjectId.value = projectId
    openExportTab.value = true
  }

  function consumeOpenExportTab(): boolean {
    if (!openExportTab.value) return false
    openExportTab.value = false
    return true
  }

  function clearPendingProject() {
    pendingProjectId.value = null
  }

  return {
    mode,
    baseJobId,
    openExportTab,
    pendingProjectId,
    requestIncrementalEdit,
    consumeOpenExportTab,
    clearPendingProject,
  }
})
