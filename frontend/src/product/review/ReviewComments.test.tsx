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
  const first: ReviewComment = { ...legacy, commentId: 'first', content: 'First frame note', frameAnchor: frame, frameStatus: 'OPEN' }
  const second: ReviewComment = { ...first, commentId: 'second', content: 'Second frame note' }
  const list = vi.spyOn(timelineCommentSource, 'list').mockResolvedValue([first, second])
  const transition = vi.spyOn(timelineCommentSource, 'transitionFrame').mockImplementation(
    async (_, row, action) => ({ ...row, threadId: 'thread', frameStatus: action === 'resolve' ? 'RESOLVED' : 'OPEN' }),
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
  const saved = { ...first, threadId: 'thread', frameStatus: 'RESOLVED' as const }
  list.mockResolvedValue([saved, second])
  ui.unmount()
  render(<ReviewComments project="project" openFrame={openFrame} />)
  const reloaded = within((await screen.findByText(first.content)).closest('li')!)
  expect(reloaded.getByText('Resolved')).toBeDefined()
  fireEvent.click(reloaded.getByRole('button', { name: 'Reopen comment' }))
  await reloaded.findByText('Open')
  expect(transition).toHaveBeenLastCalledWith('project', saved, 'reopen', expect.any(AbortSignal))
})
