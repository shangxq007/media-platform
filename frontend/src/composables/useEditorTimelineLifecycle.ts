import { onMounted, onUnmounted } from 'vue'

export interface EditorTimelineLifecycleOptions {
  isDirty: () => boolean
  hasPendingConflict: () => boolean
  onReconnect: () => void | Promise<void>
}

/**
 * Warns on tab close when dirty; re-checks server timeline when browser goes online.
 */
export function useEditorTimelineLifecycle(options: EditorTimelineLifecycleOptions) {
  function onBeforeUnload(e: BeforeUnloadEvent) {
    if (options.isDirty() || options.hasPendingConflict()) {
      e.preventDefault()
      e.returnValue = ''
    }
  }

  function onOnline() {
    void options.onReconnect()
  }

  onMounted(() => {
    window.addEventListener('beforeunload', onBeforeUnload)
    window.addEventListener('online', onOnline)
  })

  onUnmounted(() => {
    window.removeEventListener('beforeunload', onBeforeUnload)
    window.removeEventListener('online', onOnline)
  })
}
