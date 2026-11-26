import { useEffect, useLayoutEffect, useMemo, useRef, useState } from 'react'
import { FormalRenderAPI } from '../../api/formal-render'
import { Button } from '../../components/design-system'
import type { SemanticBatch, SemanticStep } from '../../contracts/semantic-batch.generated'
import type { CanonicalHeadReference, EditorCommandAccess } from './gateways'
import type { ProjectedTrack } from './timeline-projection'
import { add, compare, formatRational as f, parseRational, subtract, type Rational } from './trim'

interface RangePlan {
  tenant: string
  project: string
  context: string
  body: SemanticBatch
  digest: string
  ranges: string[]
}

interface Choice {
  key: string
  label: string
  draft: (value: Rational) => { step: SemanticStep; ranges: string[] }
}

function rollChoices(tracks: readonly ProjectedTrack[]): Choice[] {
  return tracks.flatMap(track => {
    const ordered = [...track.clips].sort((a, b) => compare(a.start, b.start))
    return ordered.flatMap((left, index) => {
      const right = ordered[index + 1]
      if (!right || compare(left.end, right.start) !== 0) return []
      return [{ key: JSON.stringify([track.trackId, left.clipId, right.clipId]),
        label: left.clipId + ' / ' + right.clipId + ' at ' + f(left.end) + 's',
        draft: (cut: Rational) => {
          const shift = subtract(cut, left.end)
          return { step: { definitionId: 'timeline.clip.roll' as const, version: '1.0' as const,
            parameters: { trackId: track.trackId, leftClipId: left.clipId, rightClipId: right.clipId, cutTime: f(cut) } },
            ranges: ['Shared cut ' + f(left.end) + 's → ' + f(cut) + 's',
              left.clipId + ' timeline [' + f(left.start) + ', ' + f(cut) + '), source [' + f(left.sourceStart) + ', ' + f(add(left.sourceEnd, shift)) + ')',
              right.clipId + ' timeline [' + f(cut) + ', ' + f(right.end) + '), source [' + f(add(right.sourceStart, shift)) + ', ' + f(right.sourceEnd) + ')'] }
        } }]
    })
  })
}

function slipChoices(tracks: readonly ProjectedTrack[]): Choice[] {
  return tracks.flatMap(track => track.clips.map(clip => ({
    key: clip.clipId, label: clip.clipId + ' on ' + track.name,
    draft: (offset: Rational) => ({
      step: { definitionId: 'timeline.clip.slip' as const, version: '1.0' as const, target: { clipId: clip.clipId },
        parameters: { offset: f(offset) } },
      ranges: [clip.clipId + ' timeline [' + f(clip.start) + ', ' + f(clip.end) + ')',
        'Source [' + f(clip.sourceStart) + ', ' + f(clip.sourceEnd) + ') → [' + f(add(clip.sourceStart, offset)) + ', ' + f(add(clip.sourceEnd, offset)) + ')',
        'Source offset ' + (offset.numerator > 0n ? '+' : '') + f(offset) + 's'],
    }),
  })))
}

function slideChoices(tracks: readonly ProjectedTrack[]): Choice[] {
  return tracks.flatMap(track => {
    const ordered = [...track.clips].sort((a, b) => compare(a.start, b.start))
    const choices: Choice[] = []
    let earlierEnd: Rational | undefined
    for (let i = 0; i + 2 < ordered.length; i++) {
      const left = ordered[i], middle = ordered[i + 1], right = ordered[i + 2]
      const overlapsEarlier = earlierEnd && compare(earlierEnd, left.start) > 0
      if (!earlierEnd || compare(left.end, earlierEnd) > 0) earlierEnd = left.end
      if (overlapsEarlier || compare(left.end, middle.start) !== 0 || compare(middle.end, right.start) !== 0
          || (ordered[i + 3] && compare(ordered[i + 3].start, right.end) < 0)) continue
      const ids = { trackId: track.trackId, leftClipId: left.clipId, middleClipId: middle.clipId, rightClipId: right.clipId }
      choices.push({
        key: JSON.stringify([track.trackId, left.clipId, middle.clipId, right.clipId]),
        label: middle.clipId + ' between ' + left.clipId + ' and ' + right.clipId,
        draft(offset) {
          const first = f(add(middle.start, offset)), second = f(add(middle.end, offset))
          return { step: { definitionId: 'timeline.clip.slide', version: '1.0', parameters: { ...ids, offset: f(offset) } },
            ranges: ['Cuts ' + f(middle.start) + 's → ' + first + 's and ' + f(middle.end) + 's → ' + second + 's',
              left.clipId + ' timeline [' + f(left.start) + ', ' + first + '), source [' + f(left.sourceStart) + ', ' + f(add(left.sourceEnd, offset)) + ')',
              middle.clipId + ' timeline [' + first + ', ' + second + '), source [' + f(middle.sourceStart) + ', ' + f(middle.sourceEnd) + ')',
              right.clipId + ' timeline [' + second + ', ' + f(right.end) + '), source [' + f(add(right.sourceStart, offset)) + ', ' + f(right.sourceEnd) + ')'] }
        },
      })
    }
    return choices
  })
}

const rangeEdits = {
  roll: { label: 'Roll', selectLabel: 'Shared cut', placeholder: 'Choose adjacent clips', timeLabel: 'New cut time (seconds)', choices: rollChoices },
  slip: { label: 'Slip', selectLabel: 'Slip clip', placeholder: 'Choose a clip', timeLabel: 'Source offset (seconds)', choices: slipChoices },
  slide: { label: 'Slide', selectLabel: 'Middle clip', placeholder: 'Choose a middle clip', timeLabel: 'Timeline offset (seconds)', choices: slideChoices },
}

export function SourceRangeEdit({ edit, head, tenant, tracks, disabled, access, readback, onAccepted }: {
  edit: keyof typeof rangeEdits
  head: CanonicalHeadReference | null
  tenant: string | null
  tracks: readonly ProjectedTrack[]
  disabled: boolean
  access: EditorCommandAccess
  readback: () => Promise<CanonicalHeadReference | undefined>
  onAccepted: (base: string, hash: string, receipt: { revisionId: string; contentHash: string }) => void
}) {
  const { label, selectLabel, placeholder, timeLabel } = rangeEdits[edit]
  const signed = edit !== 'roll'
  const choices = useMemo(() => rangeEdits[edit].choices(tracks), [edit, tracks])
  const [selected, select] = useState('')
  const [input, setInput] = useState('')
  const [plan, setPlan] = useState<RangePlan | null>(null)
  const [retry, setRetry] = useState<RangePlan | null>(null)
  const [busy, setBusy] = useState(false)
  const [message, setMessage] = useState('')
  const [failed, setFailed] = useState(false)
  const reading = useRef<AbortController | null>(null)
  const writing = useRef<AbortController | null>(null)
  const choice = choices.find(p => p.key === selected)
  const value = parseRational(input)
  const locked = disabled || access.blocked
  const context = !locked && tenant && head && choice && value && (signed || value.numerator >= 0n)
    ? JSON.stringify([tenant, head.projectId, head.revisionId, head.contentHash, selected, input]) : null
  useLayoutEffect(() => {
    setPlan(null)
    return () => reading.current?.abort()
  }, [context])
  useEffect(() => () => writing.current?.abort(), [])
  const visible = plan && plan.context === context ? plan : null

  async function preview() {
    if (!context || !tenant || !head || !choice || !value || reading.current || writing.current || retry || !access.canStart()) return
    const request = new AbortController()
    reading.current = request
    const { step, ranges } = choice.draft(value)
    const body: SemanticBatch = { batchId: crypto.randomUUID(), baseRevisionId: head.revisionId,
      baseContentHash: head.contentHash, operations: [step] }
    setBusy(true); setPlan(null); setMessage(''); setFailed(false)
    try {
      const result = await FormalRenderAPI.previewBatch(tenant, head.projectId, body, request.signal)
      if (request.signal.aborted) return
      setPlan({ tenant, project: head.projectId, context, body, digest: result.batchDigest, ranges })
    } catch (error) {
      if (!request.signal.aborted) {
        setFailed(true)
        setMessage((error as {response?: {data?: {detail?: string}}}).response?.data?.detail ?? label + ' preview unavailable.')
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
      setMessage((receipt.revisionId === attempt.body.baseRevisionId ? label + ' created no revision at ' : label + ' accepted at revision ')
        + receipt.revisionId + '.' + (current ? (current.revisionId === receipt.revisionId && current.contentHash === receipt.contentHash
          ? '' : ' Current HEAD is ' + current.revisionId + '.') : ' Reload HEAD to refresh the editor.'))
    } catch (error) {
      if (request.signal.aborted) return
      const response = (error as {response?: {status: number; data?: {detail?: string}}}).response
      setFailed(true); setPlan(null)
      if (!response || response.status >= 500 || response.status === 429) {
        retain = true
        setRetry(attempt)
        setMessage(label + ' outcome unknown. Retry sends the original confirmed request.')
      } else {
        setRetry(null)
        setMessage(response.status === 409 || response.status === 412 ? label + ' conflict. Reload HEAD and preview again.'
          : response.data?.detail ?? label + ' rejected.')
      }
    } finally {
      writing.current = null
      if (!retain) access.release()
      setBusy(false)
    }
  }

  return <section aria-label={label + ' edit'}>
    <fieldset disabled={locked || !!retry || !!writing.current}>
      <legend>{label} edit</legend>
      <label>{selectLabel} <select value={choice?.key ?? ''} onChange={e => { select(e.target.value); setInput('') }}>
        <option value="">{placeholder}</option>
        {choices.map(p => <option key={p.key} value={p.key}>{p.label}</option>)}
      </select></label>
      <label>{timeLabel} <input value={input} onChange={e => setInput(e.target.value)} /></label>
      {input && (!value || (!signed && value.numerator < 0n)) && <p role="alert">{signed ? 'Enter a signed integer or fraction.' : 'Enter a nonnegative integer or fraction.'}</p>}
      <Button disabled={busy || !context} onClick={() => void preview()}>Preview {edit}</Button>
      {visible && <div aria-label={label + ' preview'}>{visible.ranges.map(line => <p key={line}>{line}</p>)}
        <Button disabled={busy} onClick={() => void confirm()}>Confirm {edit}</Button>
        <Button disabled={busy} onClick={() => setPlan(null)}>Cancel {edit}</Button>
      </div>}
    </fieldset>
    {retry && <Button disabled={busy || access.blocked} onClick={() => void confirm()}>Retry confirmed {edit}</Button>}
    {message && <p role={failed ? 'alert' : 'status'}>{message}</p>}
  </section>
}
