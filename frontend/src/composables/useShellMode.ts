import { computed } from 'vue'
import { useRoute } from 'vue-router'

export type ShellMode = 'admin' | 'portal' | 'editor' | 'system'

const SYSTEM_PREFIXES = ['/forbidden', '/route-disabled', '/upgrade-required']

export function useShellMode() {
  const route = useRoute()

  const mode = computed<ShellMode>(() => {
    const path = route.path
    if (SYSTEM_PREFIXES.some(p => path.startsWith(p))) return 'system'
    if (path.startsWith('/admin')) return 'admin'
    if (path === '/' || path.startsWith('/project/')) return 'editor'
    return 'portal'
  })

  const showUserSidebar = computed(() => mode.value === 'portal' || mode.value === 'editor')

  const homePath = computed(() => (mode.value === 'admin' ? '/admin' : '/me'))

  return { mode, showUserSidebar, homePath }
}
