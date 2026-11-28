import { useEffect, useLayoutEffect, useRef, useState } from 'react'
import { listEditingBranches, readEditingBranch } from '../../api/app/timeline-query.gateway'
import { Button, Panel } from '../../components/design-system'
import type { RevisionListEntry } from './gateways'
import { ReviewComments } from '../review/ReviewComments'
import type { FrameCommentDraft, SavedFrameComment } from '../timeline-review/api'
import { TimelineMediaPreview, type PreviewTransport } from './TimelineMediaPreview'
import { decimalCursor } from './timeline-preview'
import { compare, formatRational, parseRational, toSeconds, ZERO, type Rational } from './trim'
import { projectId } from './types'

const sides = ['A', 'B'] as const
type Side = typeof sides[number]
interface Pin { branch: string; revision: string }
type Pair = Record<Side, Pin | null>
interface Mount { id: number; pin: Pin }
type Mounts = Record<Side, Mount | null>
type Preparation = { mount: Mount; state: 'loading' | 'failed' } | { mount: Mount; state: 'ready'; end: Rational }
type Preparations = Partial<Record<Side, Preparation>>
type Playback = 'paused' | 'pending' | 'playing'
interface Saved { open: boolean; selected: Side; pins: Pair; position: string }
const empty = (): Saved => ({ open: false, selected: 'A', pins: { A: null, B: null }, position: '0/1' })

export function readRevisionComparison(key: string): Saved {
  try {
    const raw = JSON.parse(sessionStorage.getItem(key) ?? 'null')
    if (typeof raw?.open !== 'boolean' || !sides.includes(raw.selected) || typeof raw.position !== 'string'
        || raw.position.length > 64 || !parseRational(raw.position)) return empty()
    for (const side of sides) {
      const pin = raw.pins?.[side]
      if (pin !== null && !(typeof pin?.branch === 'string' && typeof pin?.revision === 'string')) return empty()
    }
    return { open: raw.open, selected: raw.selected, pins: { A: raw.pins.A, B: raw.pins.B }, position: raw.position }
  } catch { return empty() }
}

export function saveRevisionComparison(key: string, value: Saved) {
  try { sessionStorage.setItem(key, JSON.stringify(value)) } catch { /* Comparison also works without storage. */ }
}

function RevisionPicker({ side, project, branches, initialBranch, pin, onPin }: {
  side: Side; project: string; branches: string[]; initialBranch: string; pin: Pin | null; onPin(pin: Pin): void
}) {
  const [branch, setBranch] = useState(pin?.branch ?? initialBranch)
  const [epoch, refresh] = useState(0)
  const [catalog, setCatalog] = useState<{ branch: string; history: readonly RevisionListEntry[]; error?: string } | null>(null)
  useEffect(() => {
    const request = new AbortController()
    void readEditingBranch(projectId(project), branch, request.signal).then(result => {
      if (request.signal.aborted) return
      setCatalog(result.ok ? { branch, history: result.value.history } : { branch, history: [], error: result.message })
    })
    return () => request.abort()
  }, [project, branch, epoch])
  const history = catalog?.branch === branch ? catalog.history : []
  const selected = pin?.branch === branch ? pin.revision : ''
  return <fieldset>
    <legend>Side {side}</legend>
    <p>{pin ? <><strong>{pin.branch}</strong> · <code data-pinned-side={side}>{pin.revision}</code></> : 'No revision pinned.'}</p>
    <label>Branch for {side} <select value={branch} onChange={event => { setCatalog(null); setBranch(event.target.value) }}>
      {Array.from(new Set([...branches, branch])).map(ref => <option key={ref}>{ref}</option>)}
    </select></label>{' '}
    <Button type="button" onClick={() => { setCatalog(null); refresh(value => value + 1) }}>Refresh revisions for {side}</Button>{' '}
    <label>Saved revision for {side} <select value={selected} onChange={event => {
      const revision = event.target.value
      if (history.some(candidate => candidate.id === revision)) onPin({ branch, revision })
    }}>
      <option value="" disabled>Choose a saved revision</option>
      {selected && !history.some(item => item.id === selected) && <option value={selected}>{selected} (pinned)</option>}
      {history.map((item, index) => <option key={item.id} value={item.id}>
        {index === 0 ? 'Head · ' : ''}r{item.revisionNumber} · {item.id}
      </option>)}
    </select></label>
    {catalog?.branch === branch && catalog.error && <p role="alert">Saved revisions could not be read for {branch}. {catalog.error}</p>}
  </fieldset>
}

const pinKey = (pin: Pin | null) => pin ? JSON.stringify([pin.branch, pin.revision]) : ''

const commentIdPattern = /^[A-Za-z0-9._:-]{1,64}$/
const branchPattern = /^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$/

export function frameCommentFromHash(hash: string): string | null {
  const values = new URLSearchParams(hash.slice(1)).getAll('frame-comment')
  if (!values.length) return null
  return values.length === 1 && commentIdPattern.test(values[0]) ? values[0] : ''
}

export function frameCommentLink(location: Pick<Location, 'origin' | 'pathname' | 'search'>,
                                 editingBranch: string, commentId: string): string {
  const url = new URL(location.origin)
  url.pathname = location.pathname
  // Retain only the existing editor branch selector, not comment anchor data
  // or arbitrary query parameters. This keeps same-editor links in the document.
  if (branchPattern.test(editingBranch) && new URLSearchParams(location.search).get('branch') === editingBranch) {
    url.searchParams.set('branch', editingBranch)
  }
  url.hash = new URLSearchParams({ 'frame-comment': commentId }).toString()
  return url.href
}

export function RevisionComparison({ project, tenant, editingBranch, preferenceScope, onClose, linkedComment }: {
  project: string; tenant: string | null; editingBranch: string; preferenceScope: string; onClose(): void
  linkedComment?: SavedFrameComment | null
}) {
  const [initial] = useState(() => readRevisionComparison(preferenceScope))
  const [mounts, setMounts] = useState<Mounts>(() => ({
    A: initial.pins.A && { id: 0, pin: initial.pins.A },
    B: initial.pins.B && { id: 1, pin: initial.pins.B },
  }))
  const mounted = useRef(mounts)
  const nextMount = useRef(2)
  const [selected, setSelected] = useState(initial.selected)
  const active = useRef(selected)
  const [position, setPosition] = useState(() => parseRational(initial.position) ?? ZERO)
  const cursor = useRef(position)
  const appliedCommentLink = useRef<SavedFrameComment | null>(null)
  const [playback, setPlayback] = useState<Playback>('paused')
  const wantsPlayback = useRef(false)
  const running = useRef<Mount | null>(null)
  const [handoff, nextHandoff] = useState(0)
  const transports = useRef<Partial<Record<Side, { mount: Mount; transport: PreviewTransport }>>>({})
  const [prepared, setPrepared] = useState<Preparations>({})
  const reported = useRef<Preparations>({})
  const [exact, setExact] = useState('')
  const [branches, setBranches] = useState(['main', editingBranch])
  const [branchError, setBranchError] = useState(false)
  const audio = useRef<AudioContext | null>(null)
  const audioContext = () => audio.current ??= new AudioContext({ sampleRate: 48_000 })

  const preparation = (side: Side, pair: Mounts, records: Preparations) => {
    const record = records[side]
    return record?.mount === pair[side] ? record : undefined
  }
  const durationFor = (side: Side) => {
    const record = preparation(side, mounts, prepared)
    return record?.state === 'ready' ? record.end : null
  }
  const transportFor = (side: Side) => {
    const bound = transports.current[side]
    return bound?.mount === mounted.current[side] ? bound.transport : undefined
  }
  const currentMount = mounts[selected]
  const duration = durationFor(selected)
  const ends = sides.map(durationFor).filter((value): value is Rational => value !== null)
  const maximum = ends.reduce((longer, end) => compare(end, longer) > 0 ? end : longer, ZERO)
  const outOfRange = (end: Rational | null) => end !== null && compare(position, end) >= 0

  const persist = (open = true) => saveRevisionComparison(preferenceScope, {
    open, selected: active.current,
    pins: { A: mounted.current.A?.pin ?? null, B: mounted.current.B?.pin ?? null },
    position: formatRational(cursor.current),
  })
  const move = (next: Rational) => { cursor.current = next; setPosition(next) }
  const intent = (next: boolean) => {
    wantsPlayback.current = next
    setPlayback(next ? 'pending' : 'paused')
  }
  const canResume = (side: Side) => {
    if (!tenant || !mounted.current[side]) return false
    const report = preparation(side, mounted.current, reported.current)
    return report?.state !== 'failed' && (report?.state !== 'ready' || compare(cursor.current, report.end) < 0)
  }
  const start = (side: Side) => {
    const mount = mounted.current[side]
    const transport = transportFor(side)
    if (!mount || !transport || active.current !== side || !wantsPlayback.current) return
    running.current = mount
    transport.play()
  }

  useEffect(() => {
    const request = new AbortController()
    void listEditingBranches(projectId(project), request.signal).then(value => {
      if (!request.signal.aborted) setBranches(Array.from(new Set(['main', editingBranch, ...value])))
    }, () => { if (!request.signal.aborted) setBranchError(true) })
    return () => request.abort()
  }, [project, editingBranch])
  useEffect(() => {
    const save = () => persist()
    window.addEventListener('pagehide', save)
    return () => {
      window.removeEventListener('pagehide', save)
      if (audio.current) void audio.current.close()
      audio.current = null
    }
  }, [preferenceScope])

  // A saved pin can be mounted more than once. Only this mount's ready report
  // authorizes a seek, and the seek must install the shared cursor before play.
  useLayoutEffect(() => {
    const transport = transportFor(selected)
    if (!duration || !transport) return
    transport.seek(cursor.current)
    if (compare(cursor.current, duration) >= 0) intent(false)
    else start(selected)
  }, [selected, currentMount, duration, handoff])

  function stopActive() {
    const side = active.current
    const hadPlayback = running.current !== null && running.current === mounted.current[side]
    running.current = null
    const held = transportFor(side)?.pause()
    if (held && hadPlayback) move(held)
  }
  function selectSide(side: Side) {
    if (side === active.current) return
    stopActive()
    active.current = side
    intent(wantsPlayback.current && canResume(side))
    setSelected(side)
    nextHandoff(value => value + 1)
    persist()
  }
  function selectPin(side: Side, next: Pin) {
    if (pinKey(mounted.current[side]?.pin ?? null) === pinKey(next)) return
    const replacingActive = side === active.current
    if (replacingActive) stopActive()
    mounted.current = { ...mounted.current, [side]: { id: nextMount.current++, pin: next } }
    if (replacingActive) intent(wantsPlayback.current && canResume(side))
    setMounts(mounted.current)
    persist()
  }
  function seek(next: Rational) {
    if (compare(next, ZERO) < 0 || compare(next, maximum) > 0) return
    const side = active.current
    stopActive()
    move(next)
    const report = preparation(side, mounted.current, reported.current)
    if (report?.state === 'ready') transportFor(side)?.seek(next)
    intent(wantsPlayback.current && canResume(side))
    if (report?.state === 'ready') start(side)
    persist()
  }
  function toggle() {
    if (playback === 'playing') {
      stopActive()
      intent(false)
      persist()
    } else if (playback === 'paused' && duration && !outOfRange(duration)) {
      intent(true)
      start(selected)
    }
  }
  function captureFrame(): FrameCommentDraft | null {
    const side = active.current
    const mount = mounted.current[side]
    const ready = preparation(side, mounted.current, reported.current)
    const held = cursor.current
    if (!mount || ready?.state !== 'ready' || compare(held, ZERO) < 0 || compare(held, ready.end) >= 0) return null
    stopActive()
    intent(false)
    move(held)
    transportFor(side)?.seek(held)
    persist()
    return { revisionId: mount.pin.revision, frameAnchor: { side, branch: mount.pin.branch, position: formatRational(held) } }
  }

  function openFrame(target: FrameCommentDraft) {
    const { side, branch, position: value } = target.frameAnchor
    const time = parseRational(value)
    if (!time) return
    stopActive()
    intent(false)
    active.current = side
    setSelected(side)
    selectPin(side, { branch, revision: target.revisionId })
    move(time)
    // Seeking also has to run when the comment targets the currently mounted revision.
    nextHandoff(previous => previous + 1)
    persist()
  }

  useEffect(() => {
    if (!linkedComment || appliedCommentLink.current === linkedComment) return
    appliedCommentLink.current = linkedComment
    openFrame(linkedComment)
  }, [linkedComment, openFrame])

  const exactTarget = exact.length <= 64 ? parseRational(exact) ?? decimalCursor(exact) : null
  const canSeek = exactTarget && compare(exactTarget, ZERO) >= 0 && compare(exactTarget, maximum) <= 0 && ends.length > 0
  return <section aria-label="Revision comparison" data-review-side={selected}
    data-review-revision={currentMount?.pin.revision} data-review-position={formatRational(position)}>
    <Panel title="Revision A/B comparison" actions={<Button type="button" onClick={() => {
      stopActive()
      persist(false)
      onClose()
    }}>Return to editing</Button>}>
      <p>Review does not change the timeline. Editing stays on <strong>{editingBranch}</strong>. Refreshing a list does not change either pin.</p>
      {branchError && <p role="alert">Could not list editing branches.</p>}
      {sides.map(side => <RevisionPicker key={side} side={side} project={project}
        branches={branches} initialBranch={side === 'A' ? editingBranch : 'main'}
        pin={mounts[side]?.pin ?? null} onPin={next => selectPin(side, next)} />)}
      <div aria-label="Comparison transport">
        {sides.map(side => <Button key={side} type="button" aria-pressed={selected === side}
          onClick={() => selectSide(side)}>Show {side}</Button>)}
        <Button type="button" onClick={toggle}
          disabled={playback !== 'playing' && (playback === 'pending' || !duration || outOfRange(duration))}>
          {playback === 'playing' ? 'Pause comparison' : 'Play comparison'}
        </Button>
        {playback === 'pending' && <span role="status">Playback pending for {selected}.</span>}
        <label>Shared playhead <input type="range" min={0} max={toSeconds(maximum)} step="any"
          value={toSeconds(position)} disabled={!ends.length} onChange={event => {
            const next = decimalCursor(event.target.value)
            if (next) seek(event.target.valueAsNumber === toSeconds(maximum) ? maximum : next)
          }} /></label>
        <output aria-label="Comparison time">{toSeconds(position).toFixed(3)} s</output>
        <form onSubmit={event => { event.preventDefault(); if (canSeek) seek(exactTarget) }}>
          <label>Exact comparison time (seconds) <input value={exact} maxLength={64} placeholder="71/24"
            onChange={event => setExact(event.target.value)} /></label>{' '}
          <Button type="submit" disabled={!canSeek}>Seek comparison</Button>
        </form>
      </div>
      <p aria-live="polite">Viewing {selected}{currentMount && <> · {currentMount.pin.branch} · <code>{currentMount.pin.revision}</code></>}</p>
      {sides.map(side => outOfRange(durationFor(side)) && <p key={side}>Shared playhead is outside revision {side}.</p>)}
      {!currentMount && <p>Choose a saved revision for {selected} to preview it.</p>}
      {sides.map(side => {
        const mount = mounts[side]
        if (!mount) return null
        const ownsMount = () => mounted.current[side] === mount
        const ownsPlayback = () => ownsMount() && active.current === side && running.current === mount
        const report = (value: Preparation) => {
          if (!ownsMount()) return
          reported.current = { ...reported.current, [side]: value }
          setPrepared(reported.current)
          if (value.state === 'failed' && active.current === side) {
            running.current = null
            intent(false)
          }
        }
        return <div key={mount.id} hidden={selected !== side || outOfRange(durationFor(side))}>
          <TimelineMediaPreview revisionId={mount.pin.revision} tenantId={tenant} projectId={project}
            transportRef={transport => {
              if (!ownsMount()) return
              if (transport) transports.current[side] = { mount, transport }
              else delete transports.current[side]
            }}
            comparison={{
              audioContext,
              onLoading: () => report({ mount, state: 'loading' }),
              onPrepared: end => report(end === null ? { mount, state: 'failed' } : { mount, state: 'ready', end }),
              onStarted: () => { if (ownsPlayback()) setPlayback('playing') },
              onCursor: time => { if (ownsPlayback()) move(time) },
              onEnd: () => {
                if (ownsPlayback()) { running.current = null; intent(false); persist() }
              },
            }} />
        </div>
      })}
    </Panel>
    <ReviewComments key={project} project={project} capture={captureFrame}
      canCapture={!!duration && !outOfRange(duration)} openFrame={openFrame}
      linkedComment={linkedComment}
      copyLink={async commentId => navigator.clipboard.writeText(frameCommentLink(window.location, editingBranch, commentId))}
      pinnedRevisions={{ A: mounts.A?.pin.revision ?? null, B: mounts.B?.pin.revision ?? null }} />
  </section>
}
