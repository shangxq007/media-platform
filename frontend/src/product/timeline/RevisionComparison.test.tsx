import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { beforeEach, expect, it, vi } from 'vitest'
import { RevisionComparison, frameCommentFromHash, frameCommentLink, readRevisionComparison, saveRevisionComparison } from './RevisionComparison'
import { timelineCommentSource, type SavedFrameComment } from '../timeline-review/api'
import { formatRational, type Rational, ZERO } from './trim'

const mocks = vi.hoisted(() => ({
  branchRead: vi.fn(), events: [] as string[], pending: new Map<string, (end: Rational | null) => void>(),
}))

vi.mock('../../api/app/timeline-query.gateway', () => ({
  listEditingBranches: async () => ['main', 'translated'],
  readEditingBranch: mocks.branchRead,
}))

vi.mock('./TimelineMediaPreview', async () => {
  const { useEffect, useImperativeHandle, useRef } = await import('react')
  return { TimelineMediaPreview: ({ revisionId, comparison, transportRef }: {
    revisionId: string; comparison: { onPrepared(end: Rational | null): void; onStarted(): void };
    transportRef: import('react').Ref<import('./TimelineMediaPreview').PreviewTransport>
  }) => {
    const cursor = useRef(ZERO)
    const started = useRef(comparison.onStarted)
    started.current = comparison.onStarted
    useImperativeHandle(transportRef, () => ({
      pause: () => { mocks.events.push('pause ' + revisionId); return cursor.current },
      play: () => { mocks.events.push('play ' + revisionId); started.current() },
      seek: position => { cursor.current = position; mocks.events.push('seek ' + revisionId + ' ' + formatRational(position)) },
    }), [revisionId])
    const prepared = useRef(comparison.onPrepared)
    prepared.current = comparison.onPrepared
    useEffect(() => {
      mocks.pending.set(revisionId, end => prepared.current(end))
    }, [revisionId])
    return <p>Preview {revisionId}</p>
  } }
})

const preferenceScope = 'review:test'
const initial = {
  open: true, selected: 'A' as const,
  pins: { A: { branch: 'main', revision: 'main-1' }, B: { branch: 'translated', revision: 'translation-1' } },
  position: '71/24',
}
const renderReview = () => render(<RevisionComparison project="project1" tenant="tenant1" editingBranch="translated"
  preferenceScope={preferenceScope} onClose={() => {}} />)
const btn = (name: string) => screen.getByRole('button', {name})
const testEnd = { numerator: 8n, denominator: 1n }
const finish = (revision: string, end: Rational | null = testEnd) => act(() => mocks.pending.get(revision)?.(end))
const history = (...ids: string[]) => ({ ok: true, value: { history: ids.map((id, i) => ({ id, revisionNumber: ids.length - i })) } })

beforeEach(() => {
  sessionStorage.clear()
  mocks.branchRead.mockReset()
  mocks.branchRead.mockImplementation(async (_, branch) => branch === 'main'
    ? history('main-1') : history('translation-2', 'translation-1'))
  mocks.pending.clear()
  mocks.events.length = 0
  saveRevisionComparison(preferenceScope, initial)
})

it('repins only an explicitly selected side and ignores media finishing for its superseded pin', async () => {
  renderReview()
  await screen.findByRole('option', {name: /Head.*translation-2/})
  expect((screen.getByLabelText('Saved revision for B') as HTMLSelectElement).value).toBe('translation-1')
  finish('main-1')
  fireEvent.change(screen.getByLabelText('Saved revision for B'),{target:{value:'translation-2'}})
  fireEvent.click(btn('Show B'))
  expect(screen.getByRole('region',{name:'Revision comparison'}).getAttribute('data-review-position')).toBe('71/24')
  expect((btn('Play comparison') as HTMLButtonElement).disabled).toBe(true)
  finish('translation-1')
  expect((btn('Play comparison') as HTMLButtonElement).disabled).toBe(true)
  finish('translation-2')
  expect((btn('Play comparison') as HTMLButtonElement).disabled).toBe(false)
  expect(mocks.events).toContain('seek translation-2 71/24')
  expect(mocks.events.some(value => value.startsWith('play '))).toBe(false)
  expect(readRevisionComparison(preferenceScope)).toEqual({
    ...initial, selected:'B', pins:{...initial.pins,B:{branch:'translated',revision:'translation-2'}},
  })
})

it('stops the old side before playing the new side and retains a seek beyond a shorter pin', async () => {
  renderReview()
  await screen.findByRole('option', {name:/Head.*translation-2/})
  finish('main-1')
  finish('translation-1', {numerator:3n,denominator:1n})
  fireEvent.click(btn('Play comparison'))
  fireEvent.click(btn('Show B'))
  expect(mocks.events.slice(-4)).toEqual([
    'play main-1', 'pause main-1', 'seek translation-1 71/24', 'play translation-1',
  ])
  fireEvent.click(btn('Pause comparison'))
  fireEvent.change(screen.getByLabelText('Exact comparison time (seconds)'),{target:{value:'4'}})
  fireEvent.click(btn('Seek comparison'))
  expect(screen.getByText('Shared playhead is outside revision B.')).toBeTruthy()
  expect((btn('Play comparison') as HTMLButtonElement).disabled).toBe(true)
  fireEvent.click(btn('Show A'))
  const panel=screen.getByRole('region',{name:'Revision comparison'})
  expect(within(panel).getByText('Preview main-1').closest('[hidden]')).toBeNull()
  expect(panel.getAttribute('data-review-position')).toBe('4/1')
  await waitFor(() => expect(mocks.events[mocks.events.length-1]).toBe('seek main-1 4/1'))
})

it('builds a same-editor URL with the comment ID and drops unrelated query data', () => {
  const url = new URL('https://example.test/w/ws/projects/project1/edit?branch=translated&token=hidden&position=44#old')
  const link = frameCommentLink(url, 'tfc_saved')
  expect(link).toBe('https://example.test/w/ws/projects/project1/edit#frame-comment=tfc_saved')
  expect(frameCommentFromHash(new URL(link).hash)).toBe('tfc_saved')
  expect(frameCommentFromHash('#frame-comment=a&frame-comment=b')).toBe('')
  expect(frameCommentFromHash('#frame-comment=%2F')).toBe('')
  expect(frameCommentFromHash('#other-fragment')).toBeNull()
})

it('opens a linked resolved frame exactly without requiring any previous pins', async () => {
  sessionStorage.clear()
  const linked: SavedFrameComment = { commentId:'tfc_saved', reviewId:'review', threadId:'thread',
    revisionId:'translation-1', entityRef:null, authorUserId:null, content:'Second note', createdAt:null,
    frameAnchor:{side:'B',branch:'translated',position:'73/24'}, frameStatus:'RESOLVED', frameStatusVersion:'1' }
  const list = vi.spyOn(timelineCommentSource, 'list').mockResolvedValue([])
  try {
    render(<RevisionComparison project="project1" tenant="tenant1" editingBranch="main"
      preferenceScope={preferenceScope} onClose={() => {}} linkedComment={linked} />)
    const panel = screen.getByRole('region', {name:'Revision comparison'})
    await within(panel).findByText('Preview translation-1')
    finish('translation-1')
    expect(panel.getAttribute('data-review-side')).toBe('B')
    expect(panel.getAttribute('data-review-position')).toBe('73/24')
    expect(mocks.events).toContain('seek translation-1 73/24')
    expect(mocks.events.some(event => event.startsWith('play '))).toBe(false)
    expect(readRevisionComparison(preferenceScope).pins.A).toBeNull()
    expect(screen.getByText('Second note').closest('li')?.getAttribute('aria-current')).toBe('true')
    expect(screen.getByText('Resolved')).toBeDefined()
  } finally { list.mockRestore() }
})
