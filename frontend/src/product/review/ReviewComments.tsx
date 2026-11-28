import { useEffect, useRef, useState } from 'react'
import { Button, Panel } from '../../components/design-system'
import { timelineCommentSource, type FrameCommentDraft, type FrameCommentWrite, type ReviewComment } from '../timeline-review/api'

export function ReviewComments({ project, capture, openFrame, canCapture }: {
  project: string
  capture?: () => FrameCommentDraft | null
  openFrame?: (target: FrameCommentDraft) => void
  canCapture?: boolean
}) {
  const lifetime = useRef<AbortController | null>(null)
  const inFlight = useRef(false)
  const transitioning = useRef(new Set<string>())
  const [pending, setPending] = useState<string[]>([])
  const [actionProblems, setActionProblems] = useState<Record<string, string>>({})
  const [comments, setComments] = useState<ReviewComment[]>([])
  const [problem, setProblem] = useState('')
  const [loading, setLoading] = useState(true)
  const [draft, setDraft] = useState<(FrameCommentWrite & { issued: boolean }) | null>(null)
  const [sending, setSending] = useState(false)
  const [postProblem, setPostProblem] = useState('')
  const [acknowledged, setAcknowledged] = useState('')
  const [reload, setReload] = useState(0)

  useEffect(() => {
    const signal = new AbortController()
    lifetime.current = signal
    setLoading(true)
    setProblem('')
    void timelineCommentSource.list(project, signal.signal).then(rows => {
      if (!signal.signal.aborted) setComments(previous => {
        const indexed = new Map(rows.map(row => [row.commentId, row]))
        // A list initiated before a confirmed Post must not erase that receipt.
        for (const row of previous) if (!indexed.has(row.commentId)) indexed.set(row.commentId, row)
        return [...indexed.values()]
      })
    }, () => { if (!signal.signal.aborted) setProblem('Review comments could not be read.') })
      .finally(() => { if (!signal.signal.aborted) setLoading(false) })
    return () => signal.abort()
  }, [project, reload])

  function begin() {
    const target = capture?.()
    if (!target) return
    setAcknowledged('')
    setPostProblem('')
    setDraft({ ...target, requestId: crypto.randomUUID(), content: '', issued: false })
  }

  async function post() {
    const signal = lifetime.current?.signal
    if (!signal || signal.aborted || !draft || !draft.content.trim() || inFlight.current) return
    const { revisionId, frameAnchor, content, requestId } = draft
    inFlight.current = true
    setDraft({ ...draft, issued: true })
    setSending(true)
    setPostProblem('')
    try {
      const row = await timelineCommentSource.submitFrame(project, { revisionId, frameAnchor, content, requestId }, signal)
      setComments(previous => [row, ...previous.filter(comment => comment.commentId !== row.commentId)])
      setDraft(null)
      setAcknowledged('Comment posted.')
    } catch {
      if (!signal.aborted) setPostProblem('The comment response was not confirmed. Retry the same comment.')
    } finally {
      inFlight.current = false
      if (!signal.aborted) setSending(false)
    }
  }

  async function changeStatus(row: ReviewComment) {
    const signal = lifetime.current?.signal
    const id = row.commentId
    if (!signal || signal.aborted || !row.frameStatus || transitioning.current.has(id)) return
    const action = row.frameStatus === 'RESOLVED' ? 'reopen' : 'resolve'
    transitioning.current.add(id)
    setPending([...transitioning.current])
    setActionProblems(previous => ({ ...previous, [id]: '' }))
    try {
      const saved = await timelineCommentSource.transitionFrame(project, row, action, signal)
      if (!signal.aborted) setComments(previous => previous.map(item => item.commentId === id ? saved : item))
    } catch {
      if (!signal.aborted) setActionProblems(previous => ({ ...previous, [id]: `Could not ${action} this comment.` }))
    } finally {
      transitioning.current.delete(id)
      if (!signal.aborted) setPending([...transitioning.current])
    }
  }

  return <section aria-label="Review comments"><Panel title="Review comments">
    {capture && <Button type="button" disabled={!canCapture || !!draft} onClick={begin}>Comment on this frame</Button>}
    {draft && <form aria-label="Frame comment draft" onSubmit={event => { event.preventDefault(); void post() }}>
      <p>Side {draft.frameAnchor.side} · {draft.frameAnchor.branch} · Revision <code>{draft.revisionId}</code></p>
      <p>Exact time <output aria-label="Comment draft time">{draft.frameAnchor.position} s</output></p>
      <label>Comment text <textarea aria-label="Comment text" value={draft.content} maxLength={4000} readOnly={draft.issued}
        onChange={event => setDraft({ ...draft, content: event.target.value })} /></label>{' '}
      <Button type="submit" disabled={sending || !draft.content.trim()}>{draft.issued ? 'Retry comment' : 'Post comment'}</Button>{' '}
      <Button type="button" disabled={sending} onClick={() => { setDraft(null); setPostProblem('') }}>Cancel comment</Button>
      {postProblem && <p role="alert">{postProblem}</p>}
    </form>}
    {acknowledged && <p role="status">{acknowledged}</p>}
    {loading && <p role="status">Loading review comments…</p>}
    {problem && <p role="alert">{problem} <Button type="button" disabled={sending || pending.length > 0}
      onClick={() => setReload(value => value + 1)}>Retry loading comments</Button></p>}
    {!loading && !problem && !comments.length && <p>No comments yet.</p>}
    {comments.length > 0 && <ul>{comments.map(row => <li key={row.commentId} data-comment-id={row.commentId}>
      <p style={{ whiteSpace: 'pre-wrap', overflowWrap: 'anywhere' }}>{row.content}</p>
      {row.frameAnchor && row.revisionId ? <>
        <p>Side {row.frameAnchor.side} · {row.frameAnchor.branch} · <code>{row.revisionId}</code> · {row.frameAnchor.position} s</p>
        {row.frameStatus && <p role="status" aria-label="Frame comment status">{row.frameStatus === 'RESOLVED' ? 'Resolved' : 'Open'}</p>}
        {openFrame && <Button type="button" onClick={() => {
          if (row.revisionId && row.frameAnchor) openFrame({ revisionId: row.revisionId, frameAnchor: row.frameAnchor })
        }}>Open saved frame</Button>}{' '}
        {openFrame && row.frameStatus && <Button type="button" disabled={loading || pending.includes(row.commentId)}
          onClick={() => void changeStatus(row)}>{row.frameStatus === 'RESOLVED' ? 'Reopen comment' : 'Resolve comment'}</Button>}
        {actionProblems[row.commentId] && <p role="alert">{actionProblems[row.commentId]}</p>}
      </> : <p>No frame anchor.</p>}
    </li>)}</ul>}
  </Panel></section>
}
