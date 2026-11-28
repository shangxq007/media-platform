import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { afterEach, expect, it, vi } from 'vitest'
import { ReviewComments } from './ReviewComments'
import { timelineCommentSource, type ReviewComment } from '../timeline-review/api'

const legacy: ReviewComment = { commentId:'old', reviewId:'review',threadId:null,revisionId:'r1',entityRef:null,
  authorUserId:'author',content:'Legacy note',createdAt:null }

afterEach(() => vi.restoreAllMocks())

it('holds the captured frame across selection changes and retries the exact failed response', async () => {
  vi.spyOn(timelineCommentSource, 'list').mockResolvedValue([legacy])
  const post = vi.spyOn(timelineCommentSource,'submitFrame').mockRejectedValueOnce(new Error('response lost'))
    .mockImplementationOnce(async (_,body) => ({
      ...legacy, commentId:'new', content:body.content,revisionId:body.revisionId,frameAnchor:body.frameAnchor,
    }))
  const openFrame = vi.fn()
  let chosen = { revisionId:'r1',frameAnchor:{ side:'A' as const, branch:'main',position:'73/24' } }
  const capture = () => chosen
  const ui = render(<ReviewComments project="project" capture={capture} canCapture openFrame={openFrame} />)
  await screen.findByText('No frame anchor.')
  fireEvent.click(screen.getByRole('button',{name:'Comment on this frame'}))
  const form = screen.getByRole('form',{name:'Frame comment draft'})
  expect(within(form).getByLabelText('Comment draft time').textContent).toBe('73/24 s')
  chosen = { ...chosen, revisionId:'newer',frameAnchor:{...chosen.frameAnchor,position:'4/1'} }
  ui.rerender(<ReviewComments project="project" capture={capture} canCapture openFrame={openFrame} />)
  fireEvent.change(within(form).getByLabelText('Comment text'),{target:{value:'<b>plain text</b>'}})
  fireEvent.click(within(form).getByRole('button',{name:'Post comment'}))
  await screen.findByRole('alert')
  const first = post.mock.calls[0][1]
  expect(first).toEqual({revisionId:'r1',frameAnchor:{side:'A',branch:'main',position:'73/24'},
    content:'<b>plain text</b>',requestId:expect.any(String)})
  fireEvent.click(within(form).getByRole('button',{name:'Retry comment'}))
  await screen.findByText('Comment posted.')
  expect(post.mock.calls[1][1]).toEqual(first)
  expect(screen.getByText('<b>plain text</b>').tagName).toBe('P')
  fireEvent.click(screen.getByRole('button',{name:'Open saved frame'}))
  expect(openFrame).toHaveBeenCalledWith({revisionId:'r1',frameAnchor:first.frameAnchor})
})

it('does not write while browsing, opening a draft, or cancelling it', async () => {
  const list=vi.spyOn(timelineCommentSource,'list').mockResolvedValue([])
  const post=vi.spyOn(timelineCommentSource,'submitFrame')
  render(<ReviewComments project="project" canCapture capture={() => ({
    revisionId:'r2',frameAnchor:{side:'B',branch:'alternate',position:'3/1'}})} />)
  await screen.findByText('No comments yet.')
  fireEvent.click(screen.getByRole('button',{name:'Comment on this frame'}))
  fireEvent.click(screen.getByRole('button',{name:'Cancel comment'}))
  await waitFor(() => expect(screen.queryByRole('form',{name:'Frame comment draft'})).toBeNull())
  expect(list).toHaveBeenCalledTimes(1)
  expect(post).not.toHaveBeenCalled()
})

it('changes only the selected note and opens its saved frame in either state without touching the draft', async () => {
  const frame = { side: 'B' as const, branch: 'alternate', position: '73/24' }
  const first: ReviewComment = { ...legacy, commentId: 'first', content: 'First frame note', frameAnchor: frame,
    frameStatus: 'OPEN', frameStatusVersion: '0' }
  const second: ReviewComment = { ...first, commentId: 'second', content: 'Second frame note' }
  const list = vi.spyOn(timelineCommentSource, 'list').mockResolvedValue([first, second])
  const transition = vi.spyOn(timelineCommentSource, 'transitionFrame').mockImplementation(
    async (_, row, command) => ({ row: { ...row, threadId: 'thread',
      frameStatus: command.action === 'resolve' ? 'RESOLVED' : 'OPEN',
      frameStatusVersion: command.action === 'resolve' ? '1' : '2' }, stale: false }),
  )
  const openFrame = vi.fn()
  const capture = () => ({ revisionId: 'r1', frameAnchor: frame })
  const ui = render(<ReviewComments project="project" canCapture capture={capture} openFrame={openFrame} />)
  const note = within((await screen.findByText(first.content)).closest('li')!)
  const sibling = within(screen.getByText(second.content).closest('li')!)
  fireEvent.click(note.getByRole('button', { name: 'Open saved frame' }))
  expect(transition).not.toHaveBeenCalled()
  fireEvent.click(screen.getByRole('button', { name: 'Comment on this frame' }))
  fireEvent.change(screen.getByLabelText('Comment text'), { target: { value: 'Unsent draft' } })
  fireEvent.click(note.getByRole('button', { name: 'Resolve comment' }))
  await note.findByText('Resolved')
  expect(sibling.getByText('Open')).toBeDefined()
  expect((screen.getByLabelText('Comment text') as HTMLTextAreaElement).value).toBe('Unsent draft')
  fireEvent.click(note.getByRole('button', { name: 'Open saved frame' }))
  expect(openFrame).toHaveBeenNthCalledWith(2, capture())
  expect(transition).toHaveBeenCalledTimes(1)
  const saved = { ...first, threadId: 'thread', frameStatus: 'RESOLVED' as const, frameStatusVersion: '1' }
  list.mockResolvedValue([saved, second])
  ui.unmount()
  render(<ReviewComments project="project" openFrame={openFrame} />)
  const reloaded = within((await screen.findByText(first.content)).closest('li')!)
  expect(reloaded.getByText('Resolved')).toBeDefined()
  fireEvent.click(reloaded.getByRole('button', { name: 'Reopen comment' }))
  await reloaded.findByText('Open')
  expect(transition).toHaveBeenLastCalledWith('project', saved, { action: 'reopen', expectedVersion: '1' }, expect.any(AbortSignal))
})

it('refreshes uncertain status, retries the original version, then uses the current version for a new action', async () => {
  const open: ReviewComment = { ...legacy, frameAnchor: { side: 'B', branch: 'alternate', position: '73/24' },
    frameStatus: 'OPEN', frameStatusVersion: '0' }
  const resolved = { ...open, threadId: 'thread', frameStatus: 'RESOLVED' as const, frameStatusVersion: '1' }
  const reopened = { ...open, threadId: 'thread', frameStatusVersion: '2' }
  vi.spyOn(timelineCommentSource, 'list').mockResolvedValueOnce([open]).mockResolvedValueOnce([resolved])
  const transition = vi.spyOn(timelineCommentSource, 'transitionFrame')
    .mockRejectedValueOnce(new Error('response dropped'))
    .mockResolvedValueOnce({ row: reopened, stale: true })
    .mockResolvedValueOnce({ row: { ...resolved, frameStatusVersion: '3' }, stale: false })
  render(<ReviewComments project="project" openFrame={vi.fn()} />)
  fireEvent.click(await screen.findByRole('button', { name: 'Resolve comment' }))
  await screen.findByText('Resolved')
  fireEvent.click(screen.getByRole('button', { name: 'Retry status action' }))
  await screen.findByText('Open')
  expect(transition.mock.calls[0][2]).toEqual({ action: 'resolve', expectedVersion: '0' })
  expect(transition.mock.calls[1][2]).toEqual(transition.mock.calls[0][2])
  fireEvent.click(screen.getByRole('button', { name: 'Resolve comment' }))
  await screen.findByText('Resolved')
  expect(transition.mock.calls[2][2]).toEqual({ action: 'resolve', expectedVersion: '2' })
  expect(screen.queryByRole('button', { name: 'Retry status action' })).toBeNull()
})

function frameNote(commentId: string, revisionId: string, side: 'A' | 'B', position: string): ReviewComment {
  return { ...legacy, commentId, revisionId, content: commentId,
    frameAnchor: { side, branch: side === 'A' ? 'main' : 'alternate', position },
    frameStatus: 'OPEN', frameStatusVersion: '0' }
}

it('filters by exact pinned revision and orders rational times that compare equal as numbers', async () => {
  const rounded = '3602879701896397/36028797018963968'
  expect(3602879701896397 / 36028797018963968).toBe(1 / 10)
  const greater = frameNote('a-later', 'r1', 'A', rounded)
  const earlier = frameNote('z-earlier', 'r1', 'B', '1/10')
  const other = frameNote('other', 'r2', 'A', '1/100')
  vi.spyOn(timelineCommentSource, 'list').mockResolvedValue([other, greater, legacy, earlier])
  const post = vi.spyOn(timelineCommentSource, 'submitFrame')
  const change = vi.spyOn(timelineCommentSource, 'transitionFrame')
  const openFrame = vi.fn()
  const ui = render(<ReviewComments project="project" pinnedRevisions={{ A: 'r1', B: 'r2' }} openFrame={openFrame} />)
  await screen.findByText('Legacy note')
  const filter = screen.getByRole('combobox', { name: 'Review comments view' })
  fireEvent.change(filter, { target: { value: 'A' } })
  expect(Array.from(ui.container.querySelectorAll('[data-comment-id]'), n => n.getAttribute('data-comment-id')))
    .toEqual(['z-earlier', 'a-later'])
  expect(screen.queryByText('Legacy note')).toBeNull()
  expect(openFrame).not.toHaveBeenCalled()
  fireEvent.click(screen.getByRole('button', { name: 'Next open comment' }))
  expect(openFrame).toHaveBeenLastCalledWith({ revisionId: 'r1', frameAnchor: greater.frameAnchor })
  fireEvent.click(screen.getByRole('button', { name: 'Previous open comment' }))
  expect(openFrame).toHaveBeenLastCalledWith({ revisionId: 'r1', frameAnchor: earlier.frameAnchor })
  fireEvent.change(filter, { target: { value: 'B' } })
  expect(screen.getByText('other')).toBeDefined()
  fireEvent.change(filter, { target: { value: 'all' } })
  expect(screen.getByText('Legacy note')).toBeDefined()
  expect(post).not.toHaveBeenCalled()
  expect(change).not.toHaveBeenCalled()
})

it('navigates distinct same-frame IDs and recovers selection after a saved or stale resolution', async () => {
  const adjacent = frameNote('adjacent', 'rB', 'B', '3/1')
  const first = frameNote('same-first', 'rB', 'B', '73/24')
  const second = frameNote('same-second', 'rB', 'B', '73/24')
  vi.spyOn(timelineCommentSource, 'list').mockResolvedValue([second, first, adjacent])
  const transition = vi.spyOn(timelineCommentSource, 'transitionFrame').mockImplementation(
    async (_, row) => ({ row: { ...row, frameStatus: 'RESOLVED', frameStatusVersion: '1' },
      stale: row.commentId === second.commentId }),
  )
  const openFrame = vi.fn()
  const ui = render(<ReviewComments project="project" pinnedRevisions={{ A: null, B: 'rB' }} openFrame={openFrame} />)
  await screen.findByText('same-first')
  const filter = screen.getByRole('combobox', { name: 'Review comments view' })
  fireEvent.change(filter, { target: { value: 'B' } })
  const active = () => ui.container.querySelector('[aria-current="true"][data-comment-id]')?.getAttribute('data-comment-id')
  const next = screen.getByRole('button', { name: 'Next open comment' })
  const previous = screen.getByRole('button', { name: 'Previous open comment' })
  expect(active()).toBe(adjacent.commentId)
  fireEvent.click(next)
  expect(active()).toBe(first.commentId)
  fireEvent.click(next)
  expect(active()).toBe(second.commentId)
  expect(openFrame.mock.calls[0]).toEqual(openFrame.mock.calls[1])
  fireEvent.click(previous)
  expect(active()).toBe(first.commentId)
  expect(transition).not.toHaveBeenCalled()
  const resolve = () => fireEvent.click(within(ui.container.querySelector('[aria-current="true"][data-comment-id]')!)
    .getByRole('button', { name: 'Resolve comment' }))
  resolve()
  await waitFor(() => expect(active()).toBe(second.commentId))
  expect(screen.queryByText('same-first')).toBeNull()
  resolve()
  await waitFor(() => expect(active()).toBe(adjacent.commentId))
  expect(openFrame).toHaveBeenLastCalledWith({ revisionId: 'rB', frameAnchor: adjacent.frameAnchor })
  resolve()
  await screen.findByText('No open frame comments for this revision.')
  expect(active()).toBeUndefined()
  expect((next as HTMLButtonElement).disabled).toBe(true)
  expect((previous as HTMLButtonElement).disabled).toBe(true)
  expect(transition.mock.calls.map(call => call[1].commentId)).toEqual([first.commentId, second.commentId, adjacent.commentId])
  fireEvent.change(filter, { target: { value: 'all' } })
  expect(screen.getAllByText('Resolved')).toHaveLength(3)
})
