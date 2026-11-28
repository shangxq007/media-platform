import { useEffect, useLayoutEffect, useRef, useState } from 'react'
import { FormalRenderAPI } from '../../api/formal-render'
import { Button } from '../../components/design-system'
import type { SemanticBatch, SemanticStep } from '../../contracts/semantic-batch.generated'
import type { CanonicalHeadReference, EditorCommandAccess } from './gateways'
import type { ReactNode } from 'react'

interface RangePlan {
  tenant: string
  project: string
  context: string
  body: SemanticBatch
  display: SemanticBatch
  digest: string
  ranges: string[]
}

export interface ConfirmedEditProps {
  head: CanonicalHeadReference | null
  tenant: string | null
  disabled: boolean
  access: EditorCommandAccess
  readback: () => Promise<CanonicalHeadReference | undefined>
  onAccepted: (base: string, hash: string, receipt: { revisionId: string; contentHash: string }, step: SemanticStep) => void
}

export function ConfirmedClipEdit({ edit, head, tenant, refId, disabled, access, readback, onAccepted, inputIdentity, draft, onPreview, children }: ConfirmedEditProps & {
  edit: string
  refId?: string
  inputIdentity: string
  draft: (() => { step: SemanticStep; ranges: string[] }) | null
  onPreview?: (batch: SemanticBatch | null) => void
  children: ReactNode
}) {
  const label = edit[0].toUpperCase() + edit.slice(1)
  const locked = disabled || access.blocked
  const context = !locked && tenant && head && draft
    ? JSON.stringify([tenant, head.projectId, refId, head.revisionId, head.contentHash, inputIdentity]) : null
  const [plan, setPlan] = useState<RangePlan | null>(null)
  const [retry, setRetry] = useState<RangePlan | null>(null)
  const [busy, setBusy] = useState(false)
  const [message, setMessage] = useState('')
  const [failed, setFailed] = useState(false)
  const reading = useRef<AbortController | null>(null)
  const writing = useRef<AbortController | null>(null)
  useLayoutEffect(() => {
    setPlan(null)
    return () => reading.current?.abort()
  }, [context])
  useEffect(() => () => writing.current?.abort(), [])
  const visible = plan && plan.context === context ? plan : null

  useLayoutEffect(() => {
    if (!onPreview) return
    onPreview(visible?.display ?? null)
    return () => onPreview(null)
  }, [visible, onPreview])

  async function preview() {
    if (!context || !tenant || !head || !draft || reading.current || writing.current || retry || !access.canStart()) return
    const request = new AbortController()
    reading.current = request
    const { step, ranges } = draft()
    const body: SemanticBatch = { batchId: crypto.randomUUID(), baseRevisionId: head.revisionId,
      baseContentHash: head.contentHash, ...(refId ? { refId } : {}), operations: [step] }
    setBusy(true); setPlan(null); setMessage(''); setFailed(false)
    try {
      const result = await FormalRenderAPI.previewBatch(tenant, head.projectId, body, request.signal)
      if (request.signal.aborted) return
      let display = body
      if (step.definitionId === 'timeline.subtitle.cue.set') {
        const font = result.resolvedSubtitleFont
        if (!font) throw new Error('Subtitle preview did not resolve a font')
        // Keep the submitted body (including omitted/default intent) intact for Apply/replay.
        display = { ...body, operations: [{ ...step, parameters: { ...step.parameters, fontProfile: font.id } }] }
        ranges.push(`Preview font: ${font.label} (${font.id})`)
      }
      setPlan({ tenant, project: head.projectId, context, body, display, digest: result.batchDigest, ranges })
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
      onAccepted(attempt.body.baseRevisionId, attempt.body.baseContentHash, receipt, attempt.body.operations[0])
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
      {children}
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
