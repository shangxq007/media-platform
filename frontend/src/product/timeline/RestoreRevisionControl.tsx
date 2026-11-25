import { useEffect, useRef, useState } from 'react'
import { FormalRenderAPI } from '../../api/formal-render'
import { Button, PropertyRow } from '../../components/design-system'
import type { SemanticBatch } from '../../contracts/semantic-batch.generated'
import type { CanonicalHeadReference } from './gateways'

interface RestorePreview {
  body: SemanticBatch
  digest: string
  candidateHash: string
  noOp: boolean
}

export function RestoreRevisionControl({ head, source, tenantId, onApplying, onRefresh }: {
  head: CanonicalHeadReference
  source: string
  tenantId: string
  onApplying: (busy: boolean) => void
  onRefresh: (message: string) => Promise<void>
}) {
  const [plan, setPlan] = useState<RestorePreview | null>(null)
  const [phase, setPhase] = useState<'idle' | 'previewing' | 'applying' | 'retry'>('idle')
  const [message, setMessage] = useState('')
  const request = useRef<AbortController | null>(null)
  useEffect(() => () => request.current?.abort(), [])

  async function preview() {
    if (request.current || phase !== 'idle') return
    const controller = new AbortController()
    request.current = controller
    setPhase('previewing')
    setMessage('')
    setPlan(null)
    const body: SemanticBatch = {
      batchId: crypto.randomUUID(), baseRevisionId: head.revisionId, baseContentHash: head.contentHash,
      operations: [{ definitionId: 'timeline.revision.restore', version: '1.0', parameters: { sourceRevisionId: source } }],
    }
    try {
      const result = await FormalRenderAPI.previewRestore(tenantId, head.projectId, body, controller.signal)
      if (!controller.signal.aborted) setPlan({
        body, digest: result.batchDigest, candidateHash: result.finalCandidateHash, noOp: result.noOp,
      })
    } catch {
      if (!controller.signal.aborted) setMessage('Restore preview unavailable.')
    } finally {
      if (!controller.signal.aborted) setPhase('idle')
      request.current = null
    }
  }

  async function apply() {
    if (!plan || request.current) return
    const controller = new AbortController()
    request.current = controller
    setPhase('applying')
    setMessage('')
    onApplying(true)
    try {
      const accepted = await FormalRenderAPI.commitBatch(tenantId, head.projectId, plan.body,
        plan.body.baseRevisionId, plan.digest, controller.signal)
      if (controller.signal.aborted) return
      setPlan(null)
      await onRefresh(plan.noOp ? 'The editing head already has this content. No revision was created.'
        : `Restore accepted as revision ${accepted.revisionId}.`)
    } catch (error) {
      if (controller.signal.aborted) return
      const status = (error as { response?: { status?: number } })?.response?.status
      if (status === 409 || status === 412) {
        setPlan(null)
        await onRefresh('The restore confirmation is stale. Select the source and preview again.')
      } else if (status === undefined || status >= 500 || status === 429) {
        setPhase('retry')
        setMessage('Restore could not be confirmed. Retry sends the original confirmed request.')
        return
      } else {
        setPlan(null)
        setMessage(`Restore was rejected (HTTP ${status}).`)
      }
    } finally {
      request.current = null
      onApplying(false)
    }
    if (!controller.signal.aborted) setPhase('idle')
  }

  return <section aria-label="Restore historical revision">
    {!plan && <Button disabled={phase !== 'idle'} onClick={() => void preview()}>Preview restore</Button>}
    {plan && <>
      <PropertyRow label="Restore source"><code>{source}</code></PropertyRow>
      <PropertyRow label="Expected editing head"><code>{plan.body.baseRevisionId}</code></PropertyRow>
      <PropertyRow label="Restored content hash"><code>{plan.candidateHash}</code></PropertyRow>
      <p>{plan.noOp ? 'The content already matches the editing head.' : 'Confirm to append this content as a new revision.'}</p>
      <Button variant="primary" disabled={phase === 'applying'} onClick={() => void apply()}>
        {phase === 'retry' ? 'Retry confirmed restore' : 'Confirm restore'}
      </Button>
      <Button disabled={phase === 'applying'} onClick={() => { setPlan(null); setPhase('idle'); setMessage('') }}>Discard restore</Button>
    </>}
    {message && <p role="alert">{message}</p>}
  </section>
}
