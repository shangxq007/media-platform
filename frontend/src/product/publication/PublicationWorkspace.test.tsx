import { StrictMode, useLayoutEffect } from 'react'
import { act, fireEvent, render, screen, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { SelectionProvider, useInteractionStore } from '../../interaction/SelectionContext'
import { LocalizationProvider } from '../../localization'
import type { InteractionStore } from '../../interaction/model'
import { PublicationWorkspace } from './PublicationWorkspace'
import type { PublicationPostList, PublicationReadSource, PublicationRequest } from './types'
import { fixtureAccount, fixtureList, fixturePost, source } from './testing'

function Probe({ capture }: { capture?: (store: InteractionStore) => void }) {
  const store = useInteractionStore(); useLayoutEffect(() => { capture?.(store) }, [capture, store]); return null
}
function host(value?: PublicationReadSource, owner = 'owner', capture?: (store: InteractionStore) => void) {
  return <StrictMode><LocalizationProvider initialLocale="en"><SelectionProvider key={owner} scope={{ surfaceId: 'publication', workspaceId: 'w', projectId: 'p' }}><Probe capture={capture} /><PublicationWorkspace workspaceId="w" projectId="p" tenantId="tenant" source={value} now={() => new Date('2026-09-10T12:00:00Z')} /></SelectionProvider></LocalizationProvider></StrictMode>
}
const button = (name: string) => screen.getByRole('button', { name })
async function ready() { await screen.findByRole('button', { name: 'Opening story' }) }

describe('authorized Publication workspace', () => {
  it('fails closed without an explicit source and exposes no fixture or write controls', () => {
    render(host())
    expect(screen.getByText('Publication source not connected')).toBeTruthy()
    expect(document.body.textContent).not.toMatch(/Opening story|fixture verification data/i)
    expect(screen.queryByRole('button', { name: /publish|schedule|send|cancel|retry send/i })).toBeNull()
  })

  it('queries secured Project accounts, then lists the exact selected account and UTC half-open month', async () => {
    const value = source()
    render(host(value)); await ready()
    expect(value.getAccounts).toHaveBeenCalledWith('p', expect.any(AbortSignal))
    expect(value.getPosts).toHaveBeenCalledWith({ projectId: 'p', connectedAccountId: 'a', bindingVersion: 4, start: '2026-09-01T00:00:00.000Z', end: '2026-10-01T00:00:00.000Z', limit: 200 }, expect.any(AbortSignal))
    expect(screen.getAllByText(/Planned publish time/).length).toBeGreaterThan(0)
    expect(document.body.textContent).not.toMatch(/published at|actual publication|attempt/i)
  })

  it('selects only backend-returned accounts and retires the prior exact-account list', async () => {
    const value = source({ accounts: [fixtureAccount(), fixtureAccount({ id: 'b', displayName: undefined, displayNameAvailability: 'NOT_PROVIDED', bindingVersion: 9 })] })
    render(host(value)); await ready()
    fireEvent.change(screen.getByLabelText('Account'), { target: { value: 'b' } })
    await screen.findByText('Content not provided')
    expect(value.getPosts).toHaveBeenLastCalledWith(expect.objectContaining({ projectId: 'p', connectedAccountId: 'b' }), expect.any(AbortSignal))
    expect(screen.getAllByText(/Account name not provided/).length).toBeGreaterThan(0)
  })

  it('loads corrected endpoint detail and represents absent content, version, Artifact, plan and outcomes without inventing a graph', async () => {
    const value = source({ detail: fixturePost({
      contentText: undefined,
      contentAvailability: 'NOT_PROVIDED',
      artifactId: undefined,
      artifactRelationState: 'NOT_PROVIDED',
      scheduledAt: undefined,
      timeMeaning: 'NOT_PROVIDED',
      timePrecision: 'UNKNOWN',
    }) })
    render(host(value)); await ready(); fireEvent.click(button('Opening story'))
    const dialog = await screen.findByRole('dialog')
    expect(value.getPost).toHaveBeenCalledWith({ id: 'one', projectId: 'p', connectedAccountId: 'a', bindingVersion: 4 }, expect.any(AbortSignal))
    expect(dialog.textContent).toContain('Content not provided')
    expect(dialog.textContent).toContain('Planned time not provided')
    expect(dialog.textContent).toContain('Not provided by the authoritative SocialPost record')
    expect(dialog.textContent).toContain('ArtifactNot provided')
    expect(dialog.textContent).toContain('Attempts and outcomes: NOT_PROVIDED / unknown')
    expect(dialog.textContent).not.toMatch(/published|failed|provider url|retry count/i)
  })

  it('renders authoritative Artifact presence and independent state-only content/Artifact restrictions', async () => {
    const positive = source()
    const view = render(host(positive)); await ready(); fireEvent.click(button('Opening story'))
    expect((await screen.findByRole('dialog')).textContent).toContain('Artifactartifact-one')
    fireEvent.click(button('Close publication details'))

    const denied = source({
      list: fixtureList({ items: [fixturePost({
        contentText: undefined,
        contentAvailability: 'RESTRICTED',
        contentVersionRelationState: 'RESTRICTED',
        artifactId: undefined,
        artifactRelationState: 'RESTRICTED',
      })] }),
      detail: fixturePost({
        contentText: undefined,
        contentAvailability: 'RESTRICTED',
        contentVersionRelationState: 'RESTRICTED',
        artifactId: undefined,
        artifactRelationState: 'RESTRICTED',
      }),
    })
    view.rerender(host(denied, 'denied-owner')); await screen.findByRole('button', { name: 'Content restricted' })
    fireEvent.click(button('Content restricted'))
    const dialog = await screen.findByRole('dialog')
    expect(dialog.textContent).toContain('ContentContent restricted')
    expect(dialog.textContent).toContain('Content version relationRestricted')
    expect(dialog.textContent).toContain('ArtifactRestricted')
    expect(dialog.textContent).not.toMatch(/artifact-one|Opening story/)
  })

  it('discloses bounded-partial empty and the detail-only unscheduled limitation without claiming complete emptiness', async () => {
    render(host(source({ list: fixtureList({ items: [] }) })))
    await screen.findByText('No planned publications in this bounded window')
    expect(screen.getByText(/Unscheduled records are outside every ranged list/)).toBeTruthy()
    expect(document.body.textContent).not.toMatch(/complete.*empty|no publications in this project/i)
  })

  it('retains valid same-source data during refresh and after a transient failure', async () => {
    let reject!: (reason: unknown) => void
    const value = source()
    render(host(value)); await ready()
    value.getPosts.mockImplementationOnce(() => new Promise((_resolve, fail) => { reject = fail }))
    fireEvent.click(button('Refresh publications'))
    expect(button('Opening story')).toBeTruthy()
    await act(async () => { await vi.waitFor(() => expect(reject).toBeTypeOf('function')) })
    await act(async () => reject(new Error('SECRET_TRANSPORT')))
    await screen.findByText('Refresh failed; showing the last verified bounded result.')
    expect(button('Opening story')).toBeTruthy()
    expect(document.body.innerHTML).not.toContain('SECRET_TRANSPORT')
  })

  it('retires snapshot, detail and Selection when the same account ID has a new binding revision', async () => {
    let store!: InteractionStore
    const value = source()
    render(host(value, 'owner', current => { store = current })); await ready()
    fireEvent.click(button('Opening story')); fireEvent.click(button('Close publication details'))
    expect(store.getSnapshot().selectedObjects).toHaveLength(1)
    value.getAccounts.mockResolvedValueOnce([fixtureAccount({ bindingVersion: 5 })])
    value.getPosts.mockResolvedValueOnce(fixtureList({ items: [fixturePost({
      id: 'new-binding',
      bindingVersion: 5,
      contentText: 'New binding bytes',
    })] }))
    fireEvent.click(button('Refresh publications'))
    await screen.findByRole('button', { name: 'New binding bytes' })
    expect(screen.queryByRole('button', { name: 'Opening story' })).toBeNull()
    expect(store.getSnapshot().selectedObjects).toEqual([])
    expect(value.getPosts).toHaveBeenLastCalledWith(expect.objectContaining({ connectedAccountId: 'a', bindingVersion: 5 }), expect.any(AbortSignal))
  })

  it('does not retain old bytes after a transient post error across same-ID binding replacement', async () => {
    const value = source()
    render(host(value)); await ready()
    value.getAccounts.mockResolvedValueOnce([fixtureAccount({ bindingVersion: 5 })])
    value.getPosts.mockRejectedValueOnce(new Error('TRANSIENT_AFTER_REBIND'))
    fireEvent.click(button('Refresh publications'))
    await screen.findByText('Could not load publications')
    expect(screen.queryByRole('button', { name: 'Opening story' })).toBeNull()
    expect(screen.queryByText('Refresh failed; showing the last verified bounded result.')).toBeNull()
    expect(document.body.textContent).not.toContain('TRANSIENT_AFTER_REBIND')
  })

  it('retires the verified snapshot and Selection when a refresh reports revoked authorization', async () => {
    let store!: InteractionStore
    const value = source()
    render(host(value, 'owner', current => { store = current })); await ready()
    fireEvent.click(button('Opening story')); fireEvent.click(button('Close publication details'))
    expect(store.getSnapshot().selectedObjects).toHaveLength(1)
    value.getPosts.mockRejectedValueOnce({ response: { status: 403 } })
    fireEvent.click(button('Refresh publications'))
    await screen.findByText('Publication access unavailable')
    expect(screen.queryByRole('button', { name: 'Opening story' })).toBeNull()
    expect(screen.queryByRole('dialog')).toBeNull()
    expect(store.getSnapshot().selectedObjects).toEqual([])
  })

  it('preserves search, list/calendar, timezone and selected-day behavior over planned instants', async () => {
    render(host(source())); await ready()
    fireEvent.change(screen.getByLabelText('Search supplied publications'), { target: { value: 'Opening' } })
    fireEvent.click(button('Calendar'))
    fireEvent.change(screen.getByLabelText('Display timezone'), { target: { value: 'America/Los_Angeles' } })
    expect(within(screen.getByRole('region', { name: 'Selected day agenda' })).getByRole('button', { name: 'Opening story' })).toBeTruthy()
    fireEvent.click(button('List'))
    expect((screen.getByLabelText('Search supplied publications') as HTMLInputElement).value).toBe('Opening')
    expect((screen.getByLabelText('Display timezone') as HTMLSelectElement).value).toBe('America/Los_Angeles')
  })

  it('aborts an in-flight list on source owner replacement and rejects late data', async () => {
    const pending: { signal: AbortSignal; resolve: (value: ReturnType<typeof fixtureList>) => void }[] = []
    const value = source()
    value.getPosts.mockImplementation((_request: PublicationRequest, signal: AbortSignal) => new Promise<PublicationPostList>(resolve => pending.push({ signal, resolve })))
    const view = render(host(value)); await screen.findByText('Loading publications…')
    view.rerender(host(source(), 'replacement')); await ready()
    expect(pending.every(entry => entry.signal.aborted)).toBe(true)
    await act(async () => pending.forEach(entry => entry.resolve(fixtureList({ items: [fixturePost({ contentText: 'LATE_SECRET' })] }))))
    expect(document.body.textContent).not.toContain('LATE_SECRET')
  })

  it('uses fixture verification only when explicitly injected and keeps endpoint authorization separate from unknown global access', async () => {
    render(host(source({ origin: 'fixture-verification' }))); await ready()
    expect(screen.getByText(/Explicit fixture verification data/)).toBeTruthy()
    expect(screen.getByText(/global EffectiveAccess remains UNKNOWN_FAIL_CLOSED/)).toBeTruthy()
  })

  it('returns focus to the launching row and rejects a retained dismissal from an older same-ID detail', async () => {
    render(host(source())); await ready()
    const row = button('Opening story'); row.focus(); fireEvent.click(row)
    await screen.findByText(/Post ID/)
    const oldClose = button('Close publication details')
    const propsKey = Object.keys(oldClose).find(key => key.startsWith('__reactProps$'))!
    const retainedDismiss = (oldClose as unknown as Record<string, { onClick: () => void }>)[propsKey].onClick
    fireEvent.click(oldClose)
    expect(document.activeElement).toBe(row)
    fireEvent.click(row); await screen.findByText(/Post ID/)
    act(retainedDismiss)
    expect(screen.getByRole('dialog')).toBeTruthy()
  })

  it('clears disappeared Selection on verified refresh and retires content on Selection-owner change', async () => {
    let store!: InteractionStore
    const value = source()
    render(host(value, 'owner', current => { store = current })); await ready()
    fireEvent.click(button('Opening story')); fireEvent.click(button('Close publication details'))
    value.getPosts.mockResolvedValueOnce(fixtureList({ items: [] }))
    fireEvent.click(button('Refresh publications'))
    await screen.findByText('No planned publications in this bounded window')
    expect(store.getSnapshot().selectedObjects).toEqual([])
    expect(document.activeElement).toBe(screen.getByRole('region', { name: 'Publication workspace' }))
    act(() => store.retireSelectionOwner('document'))
    expect(screen.queryByRole('button', { name: 'Opening story' })).toBeNull()
  })

  it('uses the supplied Chinese search translation in an explicitly localized host', async () => {
    render(<LocalizationProvider initialLocale="zh-CN"><SelectionProvider scope={{ surfaceId: 'publication', workspaceId: 'w', projectId: 'p' }}><PublicationWorkspace workspaceId="w" projectId="p" tenantId="tenant" source={source()} now={() => new Date('2026-09-10T12:00:00Z')} /></SelectionProvider></LocalizationProvider>)
    await screen.findByRole('button', { name: 'Opening story' })
    expect(screen.getByRole('searchbox', { name: '搜索已提供的发布内容' }).getAttribute('placeholder')).toBe('搜索已提供的发布内容')
  })
})


describe('Publication read recovery and available-field browsing', () => {
  it('retries initial account reads and refreshes an empty account response', async () => {
    const value = source(); value.getAccounts.mockRejectedValue(new Error('PRIVATE_BACKEND_ERROR'))
    render(host(value)); await screen.findByText('Could not load publications')
    expect(document.body.textContent).not.toContain('PRIVATE_BACKEND_ERROR')
    value.getAccounts.mockResolvedValue([])
    fireEvent.click(button('Retry account read')); await screen.findByRole('button', { name: 'Refresh accounts' })
    value.getAccounts.mockResolvedValue([fixtureAccount()])
    fireEvent.click(button('Refresh accounts')); await ready()
  })

  it('composes availability filters with search and resets them without sending invented server filters', async () => {
    const value = source({ list: fixtureList({ items: [fixturePost(), fixturePost({ id: 'absent', contentText: undefined, contentAvailability: 'NOT_PROVIDED', artifactId: undefined, artifactRelationState: 'NOT_PROVIDED' })] }) })
    render(host(value)); await ready()
    fireEvent.change(screen.getByLabelText('Content availability'), { target: { value: 'NOT_PROVIDED' } })
    fireEvent.change(screen.getByLabelText('Artifact availability'), { target: { value: 'AVAILABLE' } })
    expect(screen.getByText('No publications match these filters')).toBeTruthy()
    fireEvent.click(button('Reset filters'))
    expect(button('Opening story')).toBeTruthy()
    expect(value.getPosts.mock.calls.every(([request]) => !('status' in request) && !('query' in request))).toBe(true)
  })

  it('keeps month navigation available with no rows and after a recoverable month read failure', async () => {
    const value = source({ list: fixtureList({ items: [] }) })
    render(host(value)); await screen.findByText('No planned publications in this bounded window')
    value.getPosts.mockRejectedValueOnce(new Error('offline'))
    fireEvent.click(button('Next month')); await screen.findByText('Could not load publications')
    expect(button('Previous month')).toBeTruthy()
    fireEvent.click(button('Previous month')); await screen.findByText('No planned publications in this bounded window')
    expect(value.getPosts).toHaveBeenLastCalledWith(expect.objectContaining({ start: '2026-09-01T00:00:00.000Z' }), expect.any(AbortSignal))
  })

  it('retries only a failed detail read, retaining browse conditions and explicitly absent lifecycle facts', async () => {
    const value = source(); value.getPost.mockRejectedValueOnce(new Error('PRIVATE_DIAGNOSTICS'))
    render(host(value)); await ready()
    const row = button('Opening story'); row.focus(); fireEvent.click(row)
    await screen.findByRole('button', { name: 'Retry detail read' })
    expect(document.body.textContent).not.toContain('PRIVATE_DIAGNOSTICS')
    fireEvent.click(button('Retry detail read')); await screen.findByText('Post ID')
    expect(value.getPost).toHaveBeenCalledTimes(2)
    expect(screen.getByRole('dialog').textContent).toContain('Publication statusNot provided by this read contract')
    fireEvent.keyDown(screen.getByRole('dialog'), { key: 'Escape' })
    expect(document.activeElement).toBe(row)
  })

  it.each([401, 403, 404])('clears all account/list/detail/selection data on a detail %s without disclosing errors', async code => {
    const value = source(); value.getPost.mockRejectedValue({ response: { status: code, data: 'SECRET' } })
    let store!: InteractionStore
    render(host(value, 'owner', current => { store = current })); await ready(); fireEvent.click(button('Opening story'))
    await screen.findByText('Access or the current account binding is unavailable. Reopen the workspace with current access.')
    expect(screen.queryByRole('dialog')).toBeNull()
    expect(screen.queryByRole('button', { name: 'Opening story' })).toBeNull()
    expect(screen.queryByLabelText('Account')).toBeNull()
    expect(store.getSnapshot().selectedObjects).toEqual([])
    expect(document.body.textContent).not.toContain('SECRET')
  })

  it('makes timezone spillover records reachable without silently changing the UTC source query', async () => {
    const value = source({ list: fixtureList({ items: [fixturePost({ scheduledAt: '2026-09-01T00:30:00Z' })] }) })
    render(host(value)); await ready(); fireEvent.click(button('Calendar'))
    fireEvent.change(screen.getByLabelText('Display timezone'), { target: { value: 'America/Los_Angeles' } })
    fireEvent.click(button('View 2026-08-31'))
    expect(within(screen.getByRole('region', { name: 'Selected day agenda' })).getByRole('button', { name: 'Opening story' })).toBeTruthy()
    expect(value.getPosts.mock.calls.every(([request]) => request.start === '2026-09-01T00:00:00.000Z')).toBe(true)
  })
})
