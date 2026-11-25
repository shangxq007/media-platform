import { useEffect, useRef, useState } from 'react'
import { FormalRenderAPI } from '../../api/formal-render'
import { Button, Input } from '../../components/design-system'
import type { EditorCommandAccess } from './gateways'

export function CreateEditingBranch({ projectId, source, disabled, access, onCreated }: {
  projectId: string
  source: string
  disabled: boolean
  access: EditorCommandAccess
  onCreated: (refId: string) => void
}) {
  const [name, setName] = useState('')
  const [busy, setBusy] = useState(false)
  const [attempt, setAttempt] = useState<{ refId: string; sourceRevisionId: string; commandId: string }>()
  const [message, setMessage] = useState('')
  const request = useRef<AbortController | null>(null)
  useEffect(() => () => request.current?.abort(), [])

  async function create() {
    if (disabled || request.current || !/^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$/.test(name) || name === 'main') return
    if (!access.claim()) return
    const command = attempt ?? { refId: name, sourceRevisionId: source, commandId: crypto.randomUUID() }
    const controller = new AbortController()
    request.current = controller
    setAttempt(command)
    setMessage('')
    setBusy(true)
    let keepCommand = false
    try {
      const created = await FormalRenderAPI.createEditingBranch(projectId, command, controller.signal)
      if (!controller.signal.aborted) {
        setAttempt(undefined)
        setName('')
        onCreated(created.refId)
      }
    } catch (error) {
      if (controller.signal.aborted) return
      const status = (error as { response?: { status?: number } })?.response?.status
      if (status === 409) {
        setAttempt(undefined)
        setMessage('That editing branch name or command is already in use.')
      } else if (status !== undefined && status < 500 && status !== 429) {
        setAttempt(undefined)
        setMessage(`Branch creation was rejected (HTTP ${status}).`)
      } else {
        keepCommand = true
        setMessage('Branch creation could not be confirmed. Retry to check the original request.')
      }
    } finally {
      request.current = null
      if (!controller.signal.aborted) {
        setBusy(false)
        if (!keepCommand) access.release()
      }
    }
  }

  return <form aria-label="Create editing branch" onSubmit={event => { event.preventDefault(); void create() }}>
    <label>Branch name <Input value={name} disabled={disabled || access.blocked || busy || attempt !== undefined}
      required pattern="[A-Za-z0-9][A-Za-z0-9._\-]{0,63}" maxLength={64}
      onChange={event => { setName(event.target.value); setMessage('') }} /></label>
    <Button type="submit" disabled={disabled || access.blocked || busy || !name || name === 'main'}>
      {attempt ? 'Retry branch creation' : 'Create branch from viewed revision'}
    </Button>
    {attempt && <Button type="button" disabled={disabled || access.blocked || busy}
      onClick={() => { setAttempt(undefined); setMessage(''); access.release() }}>Discard creation request</Button>}
    {message && <p role="alert">{message}</p>}
  </form>
}
