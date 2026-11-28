import { useEffect, useRef, useState } from 'react'
import { FormalRenderAPI } from '../../api/formal-render'
import type { SemanticBatch } from '../../contracts/semantic-batch.generated'
import type { CanonicalHeadReference, EditorCommandAccess } from './gateways'

type Direction = 'Undo' | 'Redo'
type EditKind = 'trim' | 'ripple delete' | 'ripple insert' | 'roll' | 'slip' | 'slide' | 'opacity' | 'position' | 'scale' | 'gain' | 'audio fades' | 'subtitle' | 'dissolve' | 'wipe' | 'audio crossfade' | 'replace voiceover'
interface History {
  scope: string
  head: string
  hash: string
  verified: boolean
  revisions: readonly string[]
  edits: readonly EditKind[]
  cursor: number
}
interface Attempt {
  history: History
  direction: Direction
  cursor: number
  body: SemanticBatch
  digest: string
}

export function useTrimHistory(scope: string, head: CanonicalHeadReference | null, tenant: string | null | undefined,
  access: EditorCommandAccess, disabled: boolean, readback: () => Promise<CanonicalHeadReference | undefined>,
  refId?: string) {
  const [history, setHistory] = useState<History | null>(null)
  const [retry, setRetry] = useState<Attempt | null>(null)
  const [busy, setBusy] = useState(false)
  const [message, setMessage] = useState('')
  const [failed, setFailed] = useState(false)
  const inFlight = useRef<AbortController | null>(null)
  useEffect(() => () => inFlight.current?.abort(), [])
  const active = history?.verified && history.scope === scope && history.head === head?.revisionId
    && history.hash === head.contentHash
    ? history : null

  function recordEdit(base: string, baseHash: string, accepted: { revisionId: string; contentHash: string }, kind: EditKind) {
    if (base === accepted.revisionId) return
    setHistory(previous => {
      const prior = previous?.scope === scope && previous.head === base && previous.hash === baseHash ? previous : null
      const revisions = prior ? prior.revisions.slice(0, prior.cursor + 1) : [base]
      const edits = prior ? prior.edits.slice(0, prior.cursor) : []
      return { scope, head: accepted.revisionId, hash: accepted.contentHash,
        verified: false, revisions: [...revisions, accepted.revisionId], edits: [...edits, kind], cursor: revisions.length }
    })
    setMessage('')
  }

  function observeHead(current: CanonicalHeadReference) {
    // Only call on a successful authoritative read, never on a cached or failed projection.
    setHistory(previous => {
      if (!previous || previous.scope !== scope) return previous
      if (previous.head !== current.revisionId || previous.hash !== current.contentHash) return null
      return previous.verified ? previous : { ...previous, verified: true }
    })
  }

  function can(direction: Direction) {
    return !!active && (direction === 'Undo' ? active.cursor > 0 : active.cursor < active.revisions.length - 1)
  }

  function kindFor(direction: Direction, attempt?: Attempt): EditKind {
    const stack = attempt?.history ?? active
    return (stack && stack.edits[stack.cursor - (direction === 'Undo' ? 1 : 0)]) || (refId ? 'replace voiceover' : 'trim')
  }

  async function execute(direction: Direction, attempt?: Attempt) {
    if (inFlight.current || !tenant || !head || (!attempt && (disabled || !can(direction)))
        || (attempt && attempt.history.scope !== scope) || !access.claim()) return
    const request = new AbortController()
    inFlight.current = request
    setBusy(true); setMessage(''); setFailed(false)
    let retain = false
    try {
      if (!attempt && active) {
        const cursor = active.cursor + (direction === 'Undo' ? -1 : 1)
        const body: SemanticBatch = {
          batchId: crypto.randomUUID(), baseRevisionId: head.revisionId, baseContentHash: head.contentHash,
          ...(refId ? { refId } : {}),
          operations: [{ definitionId: 'timeline.revision.restore', version: '1.0',
            parameters: { sourceRevisionId: active.revisions[cursor] } }],
        }
        const plan = await FormalRenderAPI.previewRestore(tenant, head.projectId, body, request.signal)
        if (request.signal.aborted) return
        if (plan.noOp) {
          setHistory(null)
          setMessage(direction + ' did not change the timeline.')
          return
        }
        attempt = { history: active, direction, cursor, body, digest: plan.batchDigest }
      }
      if (!attempt) return
      const accepted = await FormalRenderAPI.commitBatch(tenant, head.projectId, attempt.body,
        attempt.body.baseRevisionId, attempt.digest, request.signal)
      if (request.signal.aborted) return
      setRetry(null)
      const changed = accepted.revisionId !== attempt.body.baseRevisionId
      setHistory(changed ? { ...attempt.history, head: accepted.revisionId, hash: accepted.contentHash,
        verified: false, cursor: attempt.cursor } : null)
      // A failed read cannot turn an accepted receipt into an uncertain write.
      const current = await readback().catch(() => undefined)
      if (changed && current?.revisionId === accepted.revisionId && current.contentHash === accepted.contentHash) {
        setMessage(direction + ' applied at revision ' + accepted.revisionId + '.')
      } else {
        setMessage(direction + ' accepted at revision ' + accepted.revisionId + '. '
          + (current ? 'The current HEAD is ' + current.revisionId + '.' : 'Reload HEAD to refresh the editor.'))
      }
    } catch (error) {
      if (request.signal.aborted) return
      const status = (error as { response?: { status?: number } })?.response?.status
      setFailed(true)
      if (status === 409 || status === 412) {
        setRetry(null); setHistory(null)
        setMessage(direction + ' conflict (HTTP ' + status + '). The editing head changed. Reload HEAD to continue.')
      } else if (attempt && (status === undefined || status >= 500 || status === 429)) {
        retain = true
        setRetry(attempt)
        setMessage(direction + ' could not be confirmed. Retry sends the original confirmed request.')
      } else {
        setRetry(null)
        setMessage(direction + ' was not applied' + (status ? ' (HTTP ' + status + ').' : '. Preview unavailable.'))
      }
    } finally {
      inFlight.current = null
      if (!retain) access.release()
      setBusy(false)
    }
  }
  return { recordEdit, observeHead, retry, busy, message, failed, execute, can, kindFor }
}
