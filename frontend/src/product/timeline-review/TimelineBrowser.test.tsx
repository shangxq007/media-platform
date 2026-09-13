import { StrictMode } from 'react'
import { act, cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { WorkspaceSessionProvider } from '../../foundation/workspaceSession'
import { platformClient } from '../../foundation/platformClient'
import { TimelineBrowser } from './TimelineBrowser'
import { deferred, detail, revision, source } from './testing'
const retirement = vi.hoisted(() => new Set<() => void>())
vi.mock('../../auth/oidcClient', async original => ({ ...await original<typeof import('../../auth/oidcClient')>(), subscribeOidcSessionRetirement: (listener: () => void) => { retirement.add(listener); return () => retirement.delete(listener) } }))
const clients: QueryClient[] = []
function mount(reads = source()) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  clients.push(client)
  const tree = (visible = true, workspaceId = 'w', projectId?: string) => <StrictMode><QueryClientProvider client={client}><WorkspaceSessionProvider workspaceId={workspaceId} projectId={projectId}>{visible ? <main className="ff-center-workspace"><TimelineBrowser workspaceId={workspaceId} source={reads} /></main> : <p>Other route</p>}</WorkspaceSessionProvider></QueryClientProvider></StrictMode>
  const view = render(tree())
  return { reads, client, view, tree }
}
async function choose(id = 'p') { fireEvent.change(await screen.findByRole('combobox', { name: 'Project' }), { target: { value: id } }) }
beforeEach(() => { vi.spyOn(platformClient.workspace, 'getHome').mockImplementation(async id => ({ workspace: { id, name: 'Workspace' }, tenantId: 'tenant', recentProjects: [{ id: 'unauthorized-dashboard', name: 'Not discovery' }] })); })
afterEach(async () => { cleanup(); clients.splice(0).forEach(client => client.clear()); await new Promise(resolve => setTimeout(resolve, 0)); retirement.clear(); vi.restoreAllMocks() })
describe('authenticated bounded timeline browsing (explicit test source)', () => {
  it('requires explicit authorized discovery, reads canonical head and shows summaries and accessible detail', async () => {
    const { reads } = mount(); await screen.findByRole('combobox', { name: 'Project' })
    expect(reads.revisions).not.toHaveBeenCalled(); expect(screen.queryByText('Not discovery')).toBeNull()
    await choose(); fireEvent.click(await screen.findByRole('button', { name: 'Revision 1 · Opening cut' }))
    expect(screen.getByText('Current head')).toBeTruthy(); expect(screen.getByText('2 patch operations')).toBeTruthy()
    const launch = screen.getByRole('button', { name: 'Opening review' }); launch.focus(); fireEvent.click(launch)
    await screen.findByText(detail.comments[0].content)
    const dialog = screen.getByRole('dialog'); expect(dialog.contains(document.activeElement)).toBe(true)
    expect(screen.getByText('Guard blocks merge')).toBeTruthy(); expect(screen.getByText(/Request changes \(REQUEST_CHANGES\)/)).toBeTruthy()
    const last = screen.getByRole('button', { name: 'Refresh review detail' }); last.focus(); fireEvent.keyDown(dialog, { key: 'Tab' }); expect(document.activeElement).toBe(screen.getByRole('button', { name: 'Close Review details' }))
    fireEvent.keyDown(dialog, { key: 'Escape' }); expect(document.activeElement).toBe(launch)
    expect(reads.project).toHaveBeenCalledWith('tenant', 'p', expect.any(AbortSignal))
  })
  it('handles legitimate empty revisions/reviews and clears project filters/selections', async () => {
    mount(); await choose(); await screen.findByText('Current head')
    fireEvent.change(screen.getByRole('searchbox'), { target: { value: 'no match' } }); expect(screen.getByText('No returned reviews match these filters.')).toBeTruthy()
    await choose('empty'); await screen.findByText('No revisions were returned for this project.'); expect(screen.getByText('No reviews were returned for this project.')).toBeTruthy()
    expect((screen.getByRole('searchbox') as HTMLInputElement).value).toBe(''); expect(screen.queryByText('Current head')).toBeNull()
  })
  it('retries detail and refreshes without showing old private content during the request or failure', async () => {
    const reads = source(); vi.mocked(reads.detail).mockRejectedValue(new Error('offline')); mount(reads); await choose()
    fireEvent.click(await screen.findByRole('button', { name: 'Opening review' })); await screen.findByRole('button', { name: 'Retry review detail' }); vi.mocked(reads.detail).mockResolvedValue(detail); fireEvent.click(screen.getByRole('button', { name: 'Retry review detail' })); await screen.findByText(detail.comments[0].content)
    const pending = deferred<typeof detail>(); vi.mocked(reads.detail).mockReturnValueOnce(pending.promise)
    fireEvent.click(screen.getByRole('button', { name: 'Refresh review detail' })); await screen.findByText('Loading review detail…'); expect(screen.queryByText(detail.comments[0].content)).toBeNull()
    await act(async () => pending.resolve({ ...detail, comments: [] })); await screen.findByText('No comments.')
  })
  it.each([401, 403])('clears all inaccessible data and caches after HTTP %s', async status => {
    const reads = source(); const { client } = mount(reads); await choose(); fireEvent.click(await screen.findByRole('button', { name: 'Opening review' })); await screen.findByText(detail.comments[0].content)
    vi.mocked(reads.detail).mockRejectedValueOnce({ response: { status } }); fireEvent.click(screen.getByRole('button', { name: 'Refresh review detail' }))
    await screen.findByRole('alert'); expect(screen.queryByRole('dialog')).toBeNull(); expect(screen.queryByRole('combobox', { name: 'Project' })).toBeNull()
    expect(client.getQueryCache().findAll({ queryKey: ['platform', 'timeline-review'] }).every(query => query.state.data === undefined)).toBe(true)
  })
  it('distinguishes not found from session denial and never displays cached detail on 404', async () => {
    const reads = source(); mount(reads); await choose(); fireEvent.click(await screen.findByRole('button', { name: 'Opening review' })); await screen.findByText(detail.comments[0].content)
    vi.mocked(reads.detail).mockRejectedValueOnce({ response: { status: 404 } }); fireEvent.click(screen.getByRole('button', { name: 'Refresh review detail' })); await screen.findByText('This resource is no longer available in the current scope.')
    expect(screen.queryByText(detail.comments[0].content)).toBeNull(); expect(screen.getByRole('combobox', { name: 'Project' })).toBeTruthy()
  })
  it('aborts late list and detail responses on project switch and session retirement', async () => {
    const reads = source(), pending = deferred<typeof detail>(); vi.mocked(reads.detail).mockReturnValue(pending.promise)
    mount(reads); await choose(); fireEvent.click(await screen.findByRole('button', { name: 'Opening review' })); await screen.findByText('Loading review detail…')
    const signal = vi.mocked(reads.detail).mock.calls.slice(-1)[0][2]; await choose('empty'); await screen.findByText('No reviews were returned for this project.'); expect(signal.aborted).toBe(true)
    await act(async () => pending.resolve(detail)); expect(screen.queryByText(detail.comments[0].content)).toBeNull()
    const list = deferred<typeof revision[]>(); vi.mocked(reads.revisions).mockReturnValueOnce(list.promise); await choose(); await waitFor(() => expect(reads.revisions).toHaveBeenCalledTimes(3))
    const listSignal = vi.mocked(reads.revisions).mock.calls.slice(-1)[0][1]
    act(() => retirement.forEach(listener => listener())); expect(listSignal.aborted).toBe(true)
    await act(async () => list.resolve([revision])); expect(screen.queryByText('Current head')).toBeNull()
  })
  it('restores preferences and scroll only after route return revalidation, retires them on workspace/project change', async () => {
    const { reads, view, tree } = mount(); await choose(); fireEvent.click(await screen.findByRole('button', { name: 'Revision 1 · Opening cut' })); fireEvent.change(screen.getByRole('searchbox'), { target: { value: 'opening' } })
    screen.getByRole('main').scrollTop = 123; fireEvent.scroll(screen.getByRole('main'))
    view.rerender(tree(false)); const pending = deferred<Awaited<ReturnType<typeof reads.projects>>>(); vi.mocked(reads.projects).mockReturnValueOnce(pending.promise)
    view.rerender(tree()); await screen.findByText('Loading authorized projects…'); expect(screen.queryByText('Opening review')).toBeNull()
    await act(async () => pending.resolve([{ id: 'p', tenantId: 'tenant', name: 'Film', description: null, status: 'ACTIVE', createdAt: null }]))
    await screen.findByText('2 patch operations'); expect((screen.getByRole('searchbox') as HTMLInputElement).value).toBe('opening'); expect(screen.getByRole('main').scrollTop).toBe(123)
    view.rerender(tree(true, 'other')); await screen.findByRole('combobox', { name: 'Project' }); expect((screen.getByRole('combobox', { name: 'Project' }) as HTMLSelectElement).value).toBe('')
  })
  it('does not restore a project removed from authorized discovery', async () => {
    const { reads, view, tree } = mount(); await choose(); await screen.findByText('Current head'); view.rerender(tree(false)); vi.mocked(reads.projects).mockResolvedValue([]); view.rerender(tree())
    await screen.findByText('No authorized projects were returned.'); expect(screen.queryByText('Current head')).toBeNull()
  })
  it('does not infer head from list order and filters only returned review fields and statuses', async () => {
    const reads = source(); vi.mocked(reads.head).mockResolvedValue(null)
    vi.mocked(reads.reviews).mockResolvedValue([detail.review, { ...detail.review, reviewId: 'closed', title: 'Other review', status: 'CLOSED' }])
    mount(reads); await choose(); await screen.findByRole('button', { name: 'Opening review' }); expect(screen.queryByText('Current head')).toBeNull()
    fireEvent.change(screen.getByLabelText('Review status'), { target: { value: 'CLOSED' } }); expect(screen.queryByRole('button', { name: 'Opening review' })).toBeNull(); expect(screen.getByRole('button', { name: 'Other review' })).toBeTruthy()
    fireEvent.change(screen.getByRole('searchbox'), { target: { value: 'r1' } }); expect(screen.getByText('1 of 2 returned reviews')).toBeTruthy()
    fireEvent.click(screen.getByRole('button', { name: 'Clear review filters' })); expect(screen.getByText('2 of 2 returned reviews')).toBeTruthy()
  })
  it('retries discovery and history failures and clears stale private lists during refresh', async () => {
    const reads = source(); vi.mocked(reads.projects).mockRejectedValue(new Error('offline')); mount(reads)
    await screen.findByRole('button', { name: 'Retry project discovery' }); vi.mocked(reads.projects).mockResolvedValue([{ id: 'p', tenantId: 'tenant', name: 'Film', description: null, status: 'ACTIVE', createdAt: null }])
    fireEvent.click(screen.getByRole('button', { name: 'Retry project discovery' })); vi.mocked(reads.revisions).mockRejectedValue(new Error('offline')); await choose(); await screen.findByRole('button', { name: 'Retry history read' })
    expect(screen.queryByText('No revisions were returned for this project.')).toBeNull()
    vi.mocked(reads.revisions).mockResolvedValue([revision]); fireEvent.click(screen.getByRole('button', { name: 'Retry history read' })); await screen.findByText('Current head')
    const pending = deferred<typeof revision[]>(); vi.mocked(reads.revisions).mockReturnValue(pending.promise); fireEvent.click(screen.getByRole('button', { name: 'Refresh history and reviews' })); await screen.findByText('Loading revision history and reviews…'); expect(screen.queryByText('Current head')).toBeNull()
    await act(async () => pending.resolve([])); await screen.findByText('No revisions were returned for this project.')
  })

})
