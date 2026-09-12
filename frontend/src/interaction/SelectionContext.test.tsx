import { cleanup, fireEvent, render, screen, act } from '@testing-library/react'
import { afterEach, describe, expect, it } from 'vitest'
import { SelectionProvider, useInteractionStore, useSelection, useSurfaceAdapter } from './SelectionContext'
import { type InteractionStore } from './model'

let owned: InteractionStore
function Consumer() {
  owned = useInteractionStore()
  const state = useSelection()
  useSurfaceAdapter({ objects: () => [{ id: 'same-id', kind: 'NODE', title: 'Local' }], supports: [], handle: () => false })
  return <button onClick={() => owned.dispatch({ category: 'LOCAL_EPHEMERAL', type: 'select', ids: ['same-id'] })}>{state.selectedRefs.length} selected</button>
}
afterEach(cleanup)
describe('SelectionProvider lifetime regression', () => {
  it('rejects prior scope requests even when presentation IDs match', () => {
    const content = (projectId: string) => <SelectionProvider scope={{ workspaceId: 'w', projectId, surfaceId: 'canvas' }}><Consumer /></SelectionProvider>
    const view = render(content('p1'))
    fireEvent.click(screen.getByRole('button')); const previousStore = owned, previous = owned.getSnapshot()
    view.rerender(content('p2'))
    expect(screen.getByText('0 selected')).toBeTruthy()
    expect(owned).not.toBe(previousStore)
    expect(owned.select({ mode: 'replace', refs: previous.selectedRefs, lifetime: previous.lifetime, revision: previous.revision }).ok).toBe(false)
  })
  it('retires selection synchronously on pagehide and starts empty after pageshow', () => {
    render(<SelectionProvider scope={{ workspaceId: 'w', projectId: 'p', surfaceId: 'canvas' }}><Consumer /></SelectionProvider>)
    fireEvent.click(screen.getByRole('button')); const previous = owned.getSnapshot()
    act(() => window.dispatchEvent(new Event('pagehide')))
    expect(owned.getSnapshot().selectedRefs).toHaveLength(0)
    expect(owned.select({ mode: 'replace', refs: previous.selectedRefs, lifetime: previous.lifetime, revision: previous.revision }).ok).toBe(false)
    act(() => window.dispatchEvent(new Event('pageshow')))
    expect(screen.getByText('0 selected')).toBeTruthy()
    fireEvent.click(screen.getByRole('button')); expect(screen.getByText('1 selected')).toBeTruthy()
  })
})
