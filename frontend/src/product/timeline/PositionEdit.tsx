import { useState } from 'react'
import type { SemanticBatch } from '../../contracts/semantic-batch.generated'
import { ConfirmedClipEdit, type ConfirmedEditProps } from './ConfirmedClipEdit'
import { scaleMultiplierBounds, type ProjectedTrack } from './timeline-projection'
import { add, compare, formatRational, parseRational, subtract } from './trim'

type Row = { id: string; time: string; x: string; y: string }
const initial = (): Row[] => [
  { id: 'first', time: '', x: '0', y: '0' },
  { id: 'last', time: '', x: '0', y: '0' },
]

function micros(percent: string) {
  const match = /^([+-]?)(\d+)(?:\.(\d{1,4}))?$/.exec(percent.trim())
  if (!match) return null
  const exact = BigInt(match[2]) * 10000n + BigInt((match[3] ?? '').padEnd(4, '0'))
  return exact <= 16000000n ? Number(match[1] === '-' ? -exact : exact) : null
}

export function PositionEdit({ tracks, onPreview, ...props }: ConfirmedEditProps & {
  tracks: readonly ProjectedTrack[]
  onPreview: (batch: SemanticBatch | null) => void
}) {
  const clips = tracks.filter(track => track.type === 'VIDEO').flatMap(track => track.clips)
  const [selected, select] = useState('')
  const [form, update] = useState<{ scope: string; rows: Row[]; clear: boolean } | null>(null)
  const clip = clips.find(c => c.clipId === selected)
  const scope = JSON.stringify([props.head?.revisionId, props.head?.contentHash, selected])
  const saved = clip?.positionKeys
  const rows = form?.scope === scope ? form.rows : saved && clip
    ? saved.map((key, i) => ({ id: 'saved-' + i, time: formatRational(subtract(key.time, clip.start)),
      x: String(key.xMicros / 10000), y: String(key.yMicros / 10000) }))
    : initial()
  const clear = !!saved && form?.scope === scope && form.clear
  const keys = rows.flatMap(row => {
    const time = parseRational(row.time)
    const xMicros = micros(row.x), yMicros = micros(row.y)
    return time && time.numerator >= 0n && xMicros != null && yMicros != null
      ? [{ time, xMicros, yMicros }] : []
  })
  const valid = clip && (clear || keys.length === rows.length && keys.length >= 2
    && keys.every((key, i) => (i === 0 || compare(keys[i - 1].time, key.time) < 0)
      && compare(add(clip.start, key.time), clip.end) < 0))
  const setRows = (next: Row[]) => update({ scope, rows: next, clear: false })
  return <ConfirmedClipEdit {...props} edit="position" onPreview={onPreview}
    inputIdentity={JSON.stringify([scope, rows, clear])}
    draft={valid ? () => ({
      step: { definitionId: 'timeline.clip.position.keyframes.set', target: { clipId: selected },
        parameters: { keys: clear ? [] : keys.map(k => ({ ...k, time: formatRational(add(clip.start, k.time)) })) } },
      ranges: clear ? ['Restore the clip’s static position.'] : rows.map(k => k.time + ' s · X ' + k.x + '% · Y ' + k.y + '%'),
    }) : null}>
    <label>Position clip <select value={clip?.clipId ?? ''} onChange={e => { select(e.target.value); update(null) }}>
      <option value="">Choose a video clip</option>{clips.map(c => <option key={c.clipId}>{c.clipId}</option>)}
    </select></label>
    {clip && <>
      <p>Offsets use the canvas width and height. Key times are relative to the clip start.</p>
      {rows.map((row, index) => <div key={row.id}>
        {([['time', 'time (clip seconds)'], ['x', 'X (%)'], ['y', 'Y (%)']] as const).map(([field, label]) =>
          <label key={field}>Key {index + 1} {label} <input disabled={clear} value={row[field]}
            onChange={e => setRows(rows.map(r => r.id === row.id ? { ...r, [field]: e.target.value } : r))} /></label>)}
        {rows.length > 2 && <button type="button" disabled={clear} onClick={() => setRows(rows.filter(r => r.id !== row.id))}>
          Remove key {index + 1}</button>}
      </div>)}
      <button type="button" disabled={clear || rows.length >= 16}
        onClick={() => setRows([...rows, { id: crypto.randomUUID(), time: '', x: '0', y: '0' }])}>Add position key</button>
      {saved && <label><input type="checkbox" checked={clear}
        onChange={e => update({ scope, rows, clear: e.target.checked })} />Clear position keys</label>}
      {rows.every(row => row.time) && !valid && <p role="alert">Use increasing key times inside the clip and offsets from −1600% to 1600%.</p>}
    </>}
  </ConfirmedClipEdit>
}

type ScaleRow = { id: string; time: string; multiplier: string }
const initialScale: ScaleRow[] = [{ id: 'first', time: '', multiplier: '1' },
  { id: 'last', time: '', multiplier: '1' }]

function multiplierMicros(value: string) {
  const match = /^(\d{1,8})(?:\.(\d{1,6}))?$/.exec(value.trim())
  if (!match) return null
  return Number(match[1]) * 1_000_000 + Number((match[2] ?? '').padEnd(6, '0'))
}

export function ScaleEdit({ tracks, onPreview, ...props }: ConfirmedEditProps & {
  tracks: readonly ProjectedTrack[]
  onPreview: (batch: SemanticBatch | null) => void
}) {
  const clips = tracks.filter(track => track.type === 'VIDEO').flatMap(track => track.clips)
  const [selected, select] = useState('')
  const [form, update] = useState<{ scope: string; rows: ScaleRow[]; clear: boolean } | null>(null)
  const clip = clips.find(c => c.clipId === selected)
  const scope = JSON.stringify([props.head?.revisionId, props.head?.contentHash, selected])
  const saved = clip?.scaleKeys
  const rows = form?.scope === scope ? form.rows : saved && clip
    ? saved.map((key, i) => ({ id: 'saved-' + i, time: formatRational(subtract(key.time, clip.start)),
      multiplier: String(Number((key.multiplierMicros / 1_000_000).toFixed(6))) }))
    : initialScale
  const clear = !!saved && form?.scope === scope && form.clear
  const keys = rows.flatMap(row => {
    const time = parseRational(row.time), value = multiplierMicros(row.multiplier)
    return time && time.numerator >= 0n && value !== null ? [{ time, multiplierMicros: value }] : []
  })
  const limits = clip ? scaleMultiplierBounds(clip.transform) : null
  const valid = clip && limits && (clear || keys.length === rows.length && keys.length >= 2
    && keys.every((key, index) => key.multiplierMicros >= limits.min && key.multiplierMicros <= limits.max
      && (index === 0 || compare(keys[index - 1].time, key.time) < 0)
      && compare(add(clip.start, key.time), clip.end) < 0))
  const setRows = (next: ScaleRow[]) => update({ scope, rows: next, clear: false })
  return <ConfirmedClipEdit {...props} edit="scale" onPreview={onPreview} inputIdentity={JSON.stringify([scope, rows, clear])}
    draft={valid ? () => ({
      step: { definitionId: 'timeline.clip.scale.keyframes.set', target: { clipId: selected },
        parameters: { keys: clear ? [] : keys.map(k => ({ multiplierMicros: k.multiplierMicros, time: formatRational(add(clip.start, k.time)) })) } },
      ranges: clear ? ['Restore the clip’s original static scale.'] : rows.map(k => k.time + ' s · ' + k.multiplier + '× static scale'),
    }) : null}>
    <label>Scale clip <select aria-label="Scale clip" value={clip?.clipId ?? ''} onChange={e => { select(e.target.value); update(null) }}>
      <option value="">Choose a video clip</option>{clips.map(c => <option key={c.clipId}>{c.clipId}</option>)}
    </select></label>
    {clip && <>
      <p>1× keeps the authored scale. Each multiplier scales both axes around the top-left of the rotated bounds.
        Times are relative to the clip start. Values interpolate linearly and hold at either end.</p>
      {rows.map((row, index) => <div key={row.id}>
        {([['time', 'time (clip seconds)'], ['multiplier', 'multiplier (×)']] as const).map(([field,label]) =>
          <label key={field}>Key {index+1} {label} <input disabled={clear} value={row[field]}
            onChange={e => setRows(rows.map(r => r.id === row.id ? { ...r, [field]: e.target.value } : r))} /></label>)}
        {rows.length > 2 && <button type="button" disabled={clear} onClick={() => setRows(rows.filter(r => r.id !== row.id))}>
          Remove scale key {index+1}</button>}
      </div>)}
      <button type="button" disabled={clear || rows.length >= 16}
        onClick={() => setRows([...rows,{ id: crypto.randomUUID(), time: '', multiplier: '1' }])}>Add scale key</button>
      {saved && <label><input type="checkbox" checked={clear}
        onChange={e => update({ scope, rows, clear: e.target.checked })} />Clear scale keys</label>}
      {rows.every(row => row.time) && !valid && <p role="alert">Use increasing key times inside the clip and keep both effective scale axes within 0.000001× to 10×.</p>}
    </>}
  </ConfirmedClipEdit>
}
