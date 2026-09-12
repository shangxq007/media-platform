import { createContext, useContext, useLayoutEffect, useRef, useState, useSyncExternalStore, type ReactNode } from 'react'
import { flushSync } from 'react-dom'
import { createInteractionStore, type InteractionStore, type SelectionScope, type SurfaceAdapter } from './model'

const SelectionContext = createContext<InteractionStore | null>(null)
export function SelectionProvider({ scope, children }: { scope: SelectionScope; children: ReactNode }) {
  const key = JSON.stringify([scope.workspaceId, scope.projectId, scope.surfaceId])
  const [owned, setOwned] = useState(() => ({ key, store: createInteractionStore(scope) }))
  // Clear scope synchronously with a fresh lifetime; carry only the provider's revision forward.
  if (owned.key !== key) setOwned({ key, store: createInteractionStore(scope, owned.store.getSnapshot().revision + 1) })
  useLayoutEffect(() => {
    const store = owned.store
    let suspended = false
    const pagehide = () => {
      suspended = true
      // Both persisted and ordinary departures retire before the document can be suspended.
      // The store notification releases Canvas capture; flushSync also discards rendered preview.
      flushSync(() => store.retireSelectionOwner('document'))
    }
    const pageshow = () => {
      if (!suspended) return
      suspended = false
      const { workspaceId, projectId, surfaceId, revision } = store.getSnapshot()
      flushSync(() => setOwned({ key: owned.key, store: createInteractionStore({ workspaceId, projectId, surfaceId }, revision + 1) }))
    }
    window.addEventListener('pagehide', pagehide)
    window.addEventListener('pageshow', pageshow)
    return () => {
      window.removeEventListener('pagehide', pagehide)
      window.removeEventListener('pageshow', pageshow)
      store.retireSelectionOwner('owner')
    }
  }, [owned])
  return <SelectionContext.Provider value={owned.store}>{children}</SelectionContext.Provider>
}
export function useInteractionStore() {
  const store = useContext(SelectionContext)
  if (!store) throw new Error('Product interactions require the shell SelectionProvider.')
  return store
}
export function useSelection() {
  const store = useInteractionStore()
  return useSyncExternalStore(store.subscribe, store.getSnapshot, store.getSnapshot)
}
export function useSurfaceAdapter(adapter: SurfaceAdapter) {
  const store = useInteractionStore()
  const latest = useRef(adapter)
  useLayoutEffect(() => { latest.current = adapter })
  useLayoutEffect(() => store.register({ objects: () => latest.current.objects(), get supports() { return latest.current.supports }, onSelection: objects => latest.current.onSelection?.(objects), handle: action => latest.current.handle(action) }), [store])
  // Reconcile committed projections before paint. Equal projections preserve snapshot identity.
  useLayoutEffect(() => store.reconcile())
}
