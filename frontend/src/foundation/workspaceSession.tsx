import { createContext, useContext, useLayoutEffect, useState, useSyncExternalStore, type ReactNode } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { flushSync } from 'react-dom'
import { subscribeOidcSessionRetirement } from '../auth/oidcClient'
import { defaultProjectBrowsing, type ProjectBrowsing } from '../product/projects/model'

let nextBinding = 0
function createBinding(workspaceId: string) {
  let retired = false
  const listeners = new Set<() => void>()
  return {
    workspaceId,
    id: ++nextBinding,
    browsing: defaultProjectBrowsing() as ProjectBrowsing,
    canvasView: null as { projectId: string; zoom: number; viewportX: number; viewportY: number } | null,
    projectId: undefined as string | undefined,
    getSnapshot: () => retired,
    subscribe: (listener: () => void) => { listeners.add(listener); return () => { listeners.delete(listener) } },
    retire() {
      retired = true
      this.browsing = defaultProjectBrowsing()
      this.canvasView = null
      listeners.forEach(listener => listener())
    },
  }
}
export type WorkspaceBinding = ReturnType<typeof createBinding>
const Context = createContext<WorkspaceBinding | null>(null)

// Route-lifetime memory only. A Workspace switch discards the previous binding;
// leaving for a non-Workspace route keeps it until return or session retirement.
export function WorkspaceSessionProvider({ workspaceId, projectId, children }: { workspaceId?: string; projectId?: string; children: ReactNode }) {
  const client = useQueryClient()
  const [binding, setBinding] = useState(() => createBinding(workspaceId ?? ''))
  const [sessionRetired, setSessionRetired] = useState(false)
  if (workspaceId && binding.workspaceId !== workspaceId) {
    const next = createBinding(workspaceId)
    if (sessionRetired) next.retire()
    setBinding(next)
  }
  if (projectId && binding.projectId !== projectId) {
    binding.projectId = projectId
    binding.canvasView = null
  }
  useLayoutEffect(() => {
    const discard = () => {
      void client.cancelQueries({ queryKey: ['platform', 'workspace', binding.workspaceId, 'home', binding.id] })
      client.removeQueries({ queryKey: ['platform', 'workspace', binding.workspaceId, 'home', binding.id] })
    }
    const unsubscribe = binding.subscribe(discard)
    if (sessionRetired) binding.retire()
    return () => { unsubscribe(); discard() }
  }, [binding, client, sessionRetired])
  useLayoutEffect(() => {
    const retire = () => {
      flushSync(() => { binding.retire(); setSessionRetired(true) })
      void client.cancelQueries({ queryKey: ['platform', 'workspace'] })
      client.removeQueries({ queryKey: ['platform', 'workspace'] })
    }
    const unsubscribe = subscribeOidcSessionRetirement(retire)
    window.addEventListener('pagehide', retire)
    return () => { unsubscribe(); window.removeEventListener('pagehide', retire) }
  }, [binding, client])
  return <Context.Provider value={binding}>{children}</Context.Provider>
}

export function useWorkspaceBinding() {
  const binding = useContext(Context)
  if (!binding) throw new Error('Workspace reads and presentation require WorkspaceSessionProvider.')
  const retired = useSyncExternalStore(binding.subscribe, binding.getSnapshot, binding.getSnapshot)
  return { binding, retired }
}
