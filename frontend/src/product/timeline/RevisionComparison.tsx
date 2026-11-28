import { useEffect, useLayoutEffect, useRef, useState } from 'react'
import { listEditingBranches, readEditingBranch } from '../../api/app/timeline-query.gateway'
import { Button, Panel } from '../../components/design-system'
import type { RevisionListEntry } from './gateways'
import { TimelineMediaPreview, type PreviewTransport } from './TimelineMediaPreview'
import { decimalCursor } from './timeline-preview'
import { compare, formatRational, parseRational, toSeconds, ZERO, type Rational } from './trim'
import { projectId } from './types'

const sides = ['A', 'B'] as const
type Side = typeof sides[number]
interface Pin { branch: string; revision: string }
type Pair = Record<Side, Pin | null>
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

export function RevisionComparison({ project, tenant, editingBranch, preferenceScope, onClose }: {
  project: string; tenant: string | null; editingBranch: string; preferenceScope: string; onClose(): void
}) {
  const [initial] = useState(() => readRevisionComparison(preferenceScope))
  const [pins, setPins] = useState(initial.pins)
  const pinned = useRef(pins)
  const [selected, setSelected] = useState(initial.selected)
  const active = useRef(selected)
  const [position, setPosition] = useState(() => parseRational(initial.position) ?? ZERO)
  const cursor = useRef(position)
  const [playing, setPlaying] = useState(false)
  const wantsPlayback = useRef(false)
  const running = useRef<{ side: Side; key: string } | null>(null)
  const [handoff, nextHandoff] = useState(0)
  const transports = useRef<Partial<Record<Side, PreviewTransport>>>({})
  const [prepared, setPrepared] = useState<Partial<Record<Side, { key: string; end: Rational | null }>>>({})
  const [exact, setExact] = useState('')
  const [branches, setBranches] = useState(['main', editingBranch])
  const [branchError, setBranchError] = useState(false)
  const audio = useRef<AudioContext | null>(null)
  const audioContext = () => audio.current ??= new AudioContext({ sampleRate: 48_000 })
  const latestKey = pinKey(pins[selected])
  const durationFor = (side: Side) => prepared[side]?.key === pinKey(pins[side]) ? prepared[side]?.end ?? null : null
  const duration = durationFor(selected)
  const ownsPlayback = (side: Side, key: string) => running.current?.side === side && running.current.key === key
  const start = (side: Side) => {
    const transport = transports.current[side]
    if (!transport) return
    running.current = { side, key: pinKey(pinned.current[side]) }
    transport.play()
  }
  const durationText = duration && formatRational(duration)
  const ends = sides.map(durationFor).filter((value): value is Rational => value !== null)
  const maximum = ends.reduce((longer, end) => compare(end, longer) > 0 ? end : longer, ZERO)
  const outOfRange = (end: Rational | null) => end !== null && compare(position, end) >= 0

  const persist = (open = true) => saveRevisionComparison(preferenceScope, {
    open, selected: active.current, pins: pinned.current, position: formatRational(cursor.current),
  })
  const move = (next: Rational) => { cursor.current = next; setPosition(next) }
  const intent = (next: boolean) => { wantsPlayback.current = next; setPlaying(next) }

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

  // Commit the new selection before its graph can play. Only a ready graph for the exact pin
  // may claim playback. This also restores the shared cursor after a late prepare or a reload.
  useLayoutEffect(() => {
    const end = parseRational(durationText)
    const transport = transports.current[selected]
    if (!end || !transport) return
    transport.seek(cursor.current)
    if (compare(cursor.current, end) >= 0) intent(false)
    else if (wantsPlayback.current) start(selected)
  }, [selected, latestKey, durationText, handoff])

  function stopActive() {
    const old = active.current
    const hadPlayback = ownsPlayback(old, pinKey(pinned.current[old]))
    running.current = null
    const held = transports.current[old]?.pause()
    if (held && hadPlayback) move(held)
  }
  function selectSide(side: Side) {
    if (side === active.current) return
    stopActive()
    active.current = side
    setSelected(side)
    nextHandoff(value => value + 1)
    persist()
  }
  function selectPin(side: Side, next: Pin) {
    if (pinKey(pinned.current[side]) === pinKey(next)) return
    if (side === active.current) {
      stopActive()
      nextHandoff(value => value + 1)
    }
    pinned.current = { ...pinned.current, [side]: next }
    setPins(pinned.current)
    persist()
  }
  function seek(next: Rational) {
    if (compare(next, ZERO) < 0 || compare(next, maximum) > 0) return
    const transport = transports.current[active.current]
    running.current = null
    transport?.pause()
    move(next)
    transport?.seek(next)
    if (wantsPlayback.current && duration && compare(next, duration) < 0) start(active.current)
    else intent(false)
    persist()
  }
  function toggle() {
    if (wantsPlayback.current) {
      stopActive()
      intent(false)
      persist()
    } else if (duration && !outOfRange(duration)) {
      intent(true)
      start(selected)
    }
  }
  const exactTarget = exact.length <= 64 ? parseRational(exact) ?? decimalCursor(exact) : null
  const canSeek = exactTarget && compare(exactTarget, ZERO) >= 0 && compare(exactTarget, maximum) <= 0 && ends.length > 0
  return <section aria-label="Revision comparison" data-review-side={selected}
    data-review-revision={pins[selected]?.revision} data-review-position={formatRational(position)}>
    <Panel title="Revision A/B comparison" actions={<Button type="button" onClick={() => {
      stopActive()
      persist(false)
      onClose()
    }}>Return to editing</Button>}>
      <p>Read-only review. Editing stays on <strong>{editingBranch}</strong>. Refreshing a list does not change either pin.</p>
      {branchError && <p role="alert">Could not list editing branches.</p>}
      {sides.map(side => <RevisionPicker key={side} side={side} project={project}
        branches={branches} initialBranch={side === 'A' ? editingBranch : 'main'}
        pin={pins[side]} onPin={next => selectPin(side, next)} />)}
      <div aria-label="Comparison transport">
        {sides.map(side => <Button key={side} type="button" aria-pressed={selected === side}
          onClick={() => selectSide(side)}>Show {side}</Button>)}
        <Button type="button" onClick={toggle} disabled={!playing && (!duration || outOfRange(duration))}>
          {playing ? 'Pause comparison' : 'Play comparison'}
        </Button>
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
      <p aria-live="polite">Viewing {selected}{pins[selected] && <> · {pins[selected]?.branch} · <code>{pins[selected]?.revision}</code></>}</p>
      {sides.map(side => outOfRange(durationFor(side)) && <p key={side}>Shared playhead is outside revision {side}.</p>)}
      {!pins[selected] && <p>Choose a saved revision for {selected} to preview it.</p>}
      {sides.map(side => {
        const pin = pins[side]
        if (!pin) return null
        const key = pinKey(pin)
        const ownsPin = () => pinKey(pinned.current[side]) === key
        return <div key={JSON.stringify([side, key])} hidden={selected !== side || outOfRange(durationFor(side))}>
          <TimelineMediaPreview revisionId={pin.revision} tenantId={tenant} projectId={project}
            transportRef={transport => {
              if (transport) transports.current[side] = transport
              else delete transports.current[side]
            }}
            comparison={{
              audioContext,
              onPrepared: end => {
                if (!ownsPin()) return
                setPrepared(previous => ({ ...previous, [side]: { key, end } }))
                if (end === null && active.current === side) { running.current = null; intent(false) }
              },
              onCursor: time => { if (ownsPin() && ownsPlayback(side, key)) move(time) },
              onEnd: () => {
                if (ownsPin() && ownsPlayback(side, key)) { running.current = null; intent(false); persist() }
              },
            }} />
        </div>
      })}
    </Panel>
  </section>
}
