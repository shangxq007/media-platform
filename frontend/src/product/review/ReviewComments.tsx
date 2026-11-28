import { useEffect, useMemo, useRef, useState } from 'react'
import { Badge, Button, Panel } from '../../components/design-system'
import { compare, parseRational, type Rational } from '../timeline/trim'
import { timelineCommentSource, type FrameCommentDraft, type FrameCommentWrite, type FrameStatusAction, type ReviewComment, type SavedFrameComment } from '../timeline-review/api'

type Side = FrameCommentDraft['frameAnchor']['side']
type View = 'all' | Side
type LocatedComment = { row: ReviewComment; target: FrameCommentDraft; time: Rational }

function compareComments(a: LocatedComment, b: LocatedComment) {
  const timeOrder = compare(a.time, b.time)
  if (timeOrder !== 0) return timeOrder
  // Equal rational positions still have a deterministic, individually selectable order.
  return a.row.commentId < b.row.commentId ? -1 : a.row.commentId > b.row.commentId ? 1 : 0
}

function newestStatus(previous: ReviewComment | undefined, incoming: ReviewComment) {
  const before = previous?.frameStatusVersion
  const next = incoming.frameStatusVersion
  // A delayed list or link read must not undo a newer status receipt.
  return previous && before !== undefined && (next === undefined || BigInt(before) > BigInt(next)) ? previous : incoming
}

export function ReviewComments({ project, capture, openFrame, canCapture, pinnedRevisions, linkedComment, copyLink }: {
  project: string
  capture?: () => FrameCommentDraft | null
  openFrame?: (target: FrameCommentDraft) => void
  canCapture?: boolean
  pinnedRevisions?: Record<Side, string | null>
  linkedComment?: SavedFrameComment | null
  copyLink?: (id: string) => Promise<void>
}) {
  const lifetime = useRef<AbortController | null>(null)
  const inFlight = useRef(false)
  const transitioning = useRef(new Set<string>())
  const [pending, setPending] = useState<string[]>([])
  const [actionProblems, setActionProblems] = useState<Record<string, string>>({})
  const [uncertainActions, setUncertainActions] = useState<Record<string, FrameStatusAction | undefined>>({})
  const [comments, setComments] = useState<ReviewComment[]>(() => linkedComment ? [linkedComment] : [])
  const [problem, setProblem] = useState('')
  const [loading, setLoading] = useState(true)
  const [draft, setDraft] = useState<(FrameCommentWrite & { issued: boolean }) | null>(null)
  const [sending, setSending] = useState(false)
  const [postProblem, setPostProblem] = useState('')
  const [acknowledged, setAcknowledged] = useState('')
  const [reload, setReload] = useState(0)
  const [view, setView] = useState<View>('all')
  const [selectedId, setSelectedId] = useState<string | null>(() => linkedComment?.commentId ?? null)
  const linkedRow = useRef<HTMLLIElement | null>(null)
  const copyAttempt = useRef(0)
  const [copyFeedback, setCopyFeedback] = useState<{ ok: boolean; text: string } | null>(null)
  const revisionId = view === 'all' ? null : pinnedRevisions?.[view] ?? null
  const located = useMemo(() => comments.flatMap(row => {
    if (!revisionId || row.revisionId !== revisionId || !row.frameAnchor) return []
    const time = parseRational(row.frameAnchor.position)
    return time ? [{ row, time, target: { revisionId, frameAnchor: row.frameAnchor } }] : []
  }).sort(compareComments), [comments, revisionId])
  const outstanding = located.filter(item => item.row.frameStatus === 'OPEN')
  const remembered = located.find(item => item.row.commentId === selectedId)
  const selected = remembered?.row.frameStatus === 'OPEN' ? remembered
    : remembered ? outstanding.find(item => compareComments(item, remembered) > 0) ?? outstanding[outstanding.length - 1]
    : outstanding[0]
  const currentId = view === 'all' ? selectedId : selected?.row.commentId ?? null
  const index = selected ? outstanding.indexOf(selected) : -1
  const visible = view === 'all' ? comments : outstanding.map(item => item.row)

  useEffect(() => {
    if (view === 'all' || currentId === selectedId) return
    setSelectedId(currentId)
    // A status receipt (including a stale one) can remove the active note.
    // Pick its successor, or the preceding note if it was the last one.
    if (remembered?.row.frameStatus === 'RESOLVED' && selected) openFrame?.(selected.target)
  }, [view, currentId, selectedId, remembered, selected, openFrame])

  function open(row: ReviewComment) {
    if (!row.revisionId || !row.frameAnchor) return
    setSelectedId(row.commentId)
    openFrame?.({ revisionId: row.revisionId, frameAnchor: row.frameAnchor })
  }

  useEffect(() => {
    if (!linkedComment) return
    setComments(previous => {
      const existing = previous.find(row => row.commentId === linkedComment.commentId)
      if (!existing) return [linkedComment, ...previous]
      return previous.map(row => row === existing ? newestStatus(row, linkedComment) : row)
    })
    setView('all')
    setSelectedId(linkedComment.commentId)
  }, [linkedComment])

  useEffect(() => {
    if (linkedComment && view === 'all' && selectedId === linkedComment.commentId) {
      linkedRow.current?.scrollIntoView?.({ block: 'nearest' })
    }
  }, [linkedComment, selectedId, view])

  async function copy(id: string) {
    if (!copyLink) return
    const attempt = ++copyAttempt.current
    setCopyFeedback(null)
    try {
      await copyLink(id)
      if (copyAttempt.current === attempt) setCopyFeedback({ ok: true, text: 'Comment link copied.' })
    } catch {
      if (copyAttempt.current === attempt) setCopyFeedback({ ok: false, text: 'Could not copy the comment link.' })
    }
  }

  useEffect(() => {
    const signal = new AbortController()
    lifetime.current = signal
    setLoading(true)
    setProblem('')
    void timelineCommentSource.list(project, signal.signal).then(rows => {
      if (!signal.signal.aborted) setComments(previous => {
        const listedIds = new Set(rows.map(row => row.commentId))
        const known = Object.fromEntries(previous.map(row => [row.commentId, row]))
        // A list initiated before a confirmed Post must not erase that receipt.
        return [
          ...rows.map(listed => {
            const current = Object.prototype.hasOwnProperty.call(known, listed.commentId) ? known[listed.commentId] : undefined
            return current ? newestStatus(current, listed) : listed
          }),
          ...previous.filter(row => !listedIds.has(row.commentId)),
        ]
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

  async function changeStatus(row: ReviewComment, retry?: FrameStatusAction) {
    const signal = lifetime.current?.signal
    const id = row.commentId
    if (!signal || signal.aborted || !row.frameStatus || row.frameStatusVersion === undefined
      || transitioning.current.has(id)) return
    const command: FrameStatusAction = retry ?? {
      action: row.frameStatus === 'RESOLVED' ? 'reopen' : 'resolve', expectedVersion: row.frameStatusVersion,
    }
    transitioning.current.add(id)
    setPending([...transitioning.current])
    setActionProblems(previous => ({ ...previous, [id]: '' }))
    const showSaved = (saved: ReviewComment) =>
      setComments(previous => previous.map(item => item.commentId === id ? saved : item))
    try {
      const result = await timelineCommentSource.transitionFrame(project, row, command, signal)
      if (signal.aborted) return
      showSaved(result.row)
      setUncertainActions(previous => ({ ...previous, [id]: undefined }))
      if (result.stale) setActionProblems(previous => ({ ...previous, [id]: 'That action is out of date. Showing the saved status.' }))
    } catch {
      if (signal.aborted) return
      setUncertainActions(previous => ({ ...previous, [id]: command }))
      try {
        const saved = await timelineCommentSource.refreshFrame(project, row, signal)
        if (signal.aborted) return
        showSaved(saved)
        setActionProblems(previous => ({ ...previous, [id]: 'The action was not confirmed. Showing the saved status.' }))
      } catch {
        if (!signal.aborted) setActionProblems(previous => ({ ...previous, [id]: 'The saved status could not be confirmed.' }))
      }
    } finally {
      transitioning.current = new Set([...transitioning.current].filter(commentId => commentId !== id))
      if (!signal.aborted) setPending([...transitioning.current])
    }
  }

  return <section aria-label="Review comments"><Panel title="Review comments">
    {pinnedRevisions && <div className="ff-review-compare-controls"><label>Review comments view <select value={view} onChange={event => {
      setSelectedId(null)
      setView(event.target.value as View)
    }}>
      <option value="all">All comments</option>
      <option value="A" disabled={!pinnedRevisions.A}>Open comments for side A</option>
      <option value="B" disabled={!pinnedRevisions.B}>Open comments for side B</option>
    </select></label></div>}
    {view !== 'all' && <div role="group" aria-label="Open frame comment navigation">
      {revisionId && <p>Side {view} pinned revision <code aria-label="Filtered pinned revision">{revisionId}</code></p>}
      <Button type="button" disabled={index <= 0}
        onClick={() => { if (index > 0) open(outstanding[index - 1].row) }}>Previous open comment</Button>{' '}
      <Button type="button" disabled={index < 0 || index >= outstanding.length - 1}
        onClick={() => { if (index >= 0 && index + 1 < outstanding.length) open(outstanding[index + 1].row) }}>Next open comment</Button>
      {index >= 0 && <p role="status" aria-label="Open comment position">{index + 1} of {outstanding.length}</p>}
    </div>}
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
    {copyFeedback && <p role={copyFeedback.ok ? 'status' : 'alert'}>{copyFeedback.text}</p>}
    {loading && <p role="status">Loading review comments…</p>}
    {problem && <p role="alert">{problem} <Button type="button" disabled={sending || pending.length > 0}
      onClick={() => setReload(value => value + 1)}>Retry loading comments</Button></p>}
    {!loading && !problem && visible.length === 0 && (view === 'all' ? <p>No comments yet.</p>
      : <p role="status">{revisionId ? 'No open frame comments for this revision.'
        : `Pin a saved revision on side ${view} to see its open comments.`}</p>)}
    {visible.length > 0 && <ul>{visible.map(row => <li key={row.commentId} data-comment-id={row.commentId}
      ref={row.commentId === linkedComment?.commentId ? linkedRow : undefined}
      aria-current={row.commentId === currentId ? true : undefined}>
      <p style={{ whiteSpace: 'pre-wrap', overflowWrap: 'anywhere' }}>{row.content}</p>
      {row.commentId === currentId && <p><Badge tone="info">Selected comment</Badge></p>}
      {row.frameAnchor && row.revisionId ? <>
        <p>Side {row.frameAnchor.side} · {row.frameAnchor.branch} · <code>{row.revisionId}</code> · {row.frameAnchor.position} s</p>
        {row.frameStatus && <p role="status" aria-label="Frame comment status">{row.frameStatus === 'RESOLVED' ? 'Resolved' : 'Open'}</p>}
        {openFrame && <Button type="button" onClick={() => open(row)}>Open saved frame</Button>}{' '}
        {copyLink && <Button type="button" onClick={() => void copy(row.commentId)}>Copy comment link</Button>}{' '}
        {openFrame && row.frameStatus && row.frameStatusVersion !== undefined
          && <Button type="button" disabled={loading || pending.includes(row.commentId)}
          onClick={() => void changeStatus(row)}>{row.frameStatus === 'RESOLVED' ? 'Reopen comment' : 'Resolve comment'}</Button>}
        {openFrame && uncertainActions[row.commentId] && <Button type="button" disabled={pending.includes(row.commentId)}
          onClick={() => void changeStatus(row, uncertainActions[row.commentId])}>Retry status action</Button>}
        {actionProblems[row.commentId] && <p role="alert">{actionProblems[row.commentId]}</p>}
      </> : <p>No frame anchor.</p>}
    </li>)}</ul>}
  </Panel></section>
}
