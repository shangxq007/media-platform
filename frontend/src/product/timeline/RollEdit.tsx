import { useEffect, useLayoutEffect, useRef, useState } from 'react'
import { FormalRenderAPI } from '../../api/formal-render'
import { Button } from '../../components/design-system'
import type { SemanticBatch } from '../../contracts/semantic-batch.generated'
import type { CanonicalHeadReference, EditorCommandAccess } from './gateways'
import type { ProjectedTrack } from './timeline-projection'
import { add, compare, formatRational, parseRational, subtract } from './trim'

interface RollPlan {
  tenant: string
  project: string
  context: string
  body: SemanticBatch
  digest: string
  ranges: string[]
}

export function RollEdit({ head, tenant, tracks, disabled, access, readback, onAccepted }: {
  head: CanonicalHeadReference | null
  tenant: string | null
  tracks: readonly ProjectedTrack[]
  disabled: boolean
  access: EditorCommandAccess
  readback: () => Promise<CanonicalHeadReference | undefined>
  onAccepted: (base: string, hash: string, receipt: { revisionId: string; contentHash: string }) => void
}) {
  const pairs = tracks.flatMap(track => {
    const ordered = [...track.clips].sort((a,b) => compare(a.start, b.start))
    return ordered.flatMap((left, index) => {
      const right = ordered[index + 1]
      return right && compare(left.end, right.start) === 0
        ? [{ key: JSON.stringify([track.trackId, left.clipId, right.clipId]), left, right }] : []
    })
  })
  const [selected, select] = useState('')
  const [input, setInput] = useState('')
  const [plan, setPlan] = useState<RollPlan | null>(null)
  const [retry, setRetry] = useState<RollPlan | null>(null)
  const [busy, setBusy] = useState(false)
  const [message, setMessage] = useState('')
  const [failed, setFailed] = useState(false)
  const reading = useRef<AbortController | null>(null)
  const writing = useRef<AbortController | null>(null)
  const pair = pairs.find(p => p.key === selected)
  const cut = parseRational(input)
  const locked = disabled || access.blocked
  const context = !locked && tenant && head && pair && cut && cut.numerator >= 0n
    ? JSON.stringify([tenant, head.projectId, head.revisionId, head.contentHash, selected, input]) : null
  useLayoutEffect(() => {
    setPlan(null)
    return () => reading.current?.abort()
  }, [context])
  useEffect(() => () => writing.current?.abort(), [])
  const visible = plan && plan.context === context ? plan : null

  async function preview() {
    if (!context || !tenant || !head || !pair || !cut || reading.current || writing.current || retry || !access.canStart()) return
    const request = new AbortController()
    reading.current = request
    const { left, right } = pair
    const time = formatRational(cut)
    const body: SemanticBatch = { batchId: crypto.randomUUID(), baseRevisionId: head.revisionId, baseContentHash: head.contentHash,
      operations: [{ definitionId: 'timeline.clip.roll', version: '1.0', parameters: {
        trackId: left.trackId, leftClipId: left.clipId, rightClipId: right.clipId, cutTime: time } }] }
    setBusy(true); setPlan(null); setMessage(''); setFailed(false)
    try {
      const result = await FormalRenderAPI.previewBatch(tenant, head.projectId, body, request.signal)
      if (request.signal.aborted) return
      const shift = subtract(cut, left.end)
      const f = formatRational
      setPlan({ tenant, project: head.projectId, context, body, digest: result.batchDigest, ranges: [
        'Shared cut ' + f(left.end) + 's → ' + time + 's',
        left.clipId + ' timeline [' + f(left.start) + ', ' + time + '), source [' + f(left.sourceStart) + ', ' + f(add(left.sourceEnd, shift)) + ')',
        right.clipId + ' timeline [' + time + ', ' + f(right.end) + '), source [' + f(add(right.sourceStart, shift)) + ', ' + f(right.sourceEnd) + ')',
      ] })
    } catch (error) {
      if (!request.signal.aborted) {
        setFailed(true)
        setMessage((error as {response?: {data?: {detail?: string}}}).response?.data?.detail ?? 'Roll preview unavailable.')
      }
    } finally {
      reading.current = null
      setBusy(false)
    }
  }

  async function confirm() {
    const attempt = retry ?? visible
    if (!attempt || writing.current || reading.current || (!retry && locked) || !access.claim()) return
    const request = new AbortController()
    writing.current = request
    setBusy(true); setMessage(''); setFailed(false)
    let retain = false
    try {
      const receipt = await FormalRenderAPI.commitBatch(attempt.tenant, attempt.project, attempt.body,
        attempt.body.baseRevisionId, attempt.digest, request.signal)
      if (request.signal.aborted) return
      setPlan(null); setRetry(null)
      onAccepted(attempt.body.baseRevisionId, attempt.body.baseContentHash, receipt)
      const current = await readback().catch(() => undefined)
      setMessage((receipt.revisionId === attempt.body.baseRevisionId ? 'Roll created no revision at ' : 'Roll accepted at revision ')
        + receipt.revisionId + '.' + (current ? (current.revisionId === receipt.revisionId && current.contentHash === receipt.contentHash
          ? '' : ' Current HEAD is ' + current.revisionId + '.') : ' Reload HEAD to refresh the editor.'))
    } catch (error) {
      if (request.signal.aborted) return
      const response = (error as {response?: {status: number; data?: {detail?: string}}}).response
      setFailed(true); setPlan(null)
      if (!response || response.status >= 500 || response.status === 429) {
        retain = true
        setRetry(attempt)
        setMessage('Roll outcome unknown. Retry sends the original confirmed request.')
      } else {
        setRetry(null)
        setMessage(response.status === 409 || response.status === 412 ? 'Roll conflict. Reload HEAD and preview again.'
          : response.data?.detail ?? 'Roll rejected.')
      }
    } finally {
      writing.current = null
      if (!retain) access.release()
      setBusy(false)
    }
  }

  return <section aria-label="Roll edit">
    <fieldset disabled={locked || !!retry || !!writing.current}>
      <legend>Roll edit</legend>
      <label>Shared cut <select value={pair?.key ?? ''} onChange={e => { select(e.target.value); setInput('') }}>
        <option value="">Choose adjacent clips</option>
        {pairs.map(p => <option key={p.key} value={p.key}>{p.left.clipId} / {p.right.clipId} at {formatRational(p.left.end)}s</option>)}
      </select></label>
      <label>New cut time (seconds) <input value={input} onChange={e => setInput(e.target.value)} /></label>
      {input && (!cut || cut.numerator < 0n) && <p role="alert">Enter a nonnegative integer or fraction.</p>}
      <Button disabled={busy || !context} onClick={() => void preview()}>Preview roll</Button>
      {visible && <div aria-label="Roll preview">{visible.ranges.map(line => <p key={line}>{line}</p>)}
        <Button disabled={busy} onClick={() => void confirm()}>Confirm roll</Button>
        <Button disabled={busy} onClick={() => setPlan(null)}>Cancel roll</Button>
      </div>}
    </fieldset>
    {retry && <Button disabled={busy || access.blocked} onClick={() => void confirm()}>Retry confirmed roll</Button>}
    {message && <p role={failed ? 'alert' : 'status'}>{message}</p>}
  </section>
}
