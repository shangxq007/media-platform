import { useState } from 'react'
import type { SemanticBatch, SemanticStep } from '../../contracts/semantic-batch.generated'
import { ConfirmedClipEdit, type ConfirmedEditProps } from './ConfirmedClipEdit'
import { resolveSubtitleFont, SUBTITLE_FONTS, type SubtitleCue, type SubtitleFontProfile } from './subtitle-cues'
import { compare, formatRational, parseRational, subtract } from './trim'

type Row = { id: string; time: string; x: string; y: string; opacity: string }
type Form = { scope: string; text: string; start: string; end: string; width: string; fontSize: string; fontProfile: SubtitleFontProfile; keys: Row[]; remove: boolean }
const percent = (input: string, min: number, max: number) => {
  const exact = /^([+-]?)(\d+)(?:\.(\d{1,4}))?$/.exec(input.trim())
  if (!exact) return null
  const magnitude = BigInt(exact[2]) * 10000n + BigInt((exact[3] ?? '').padEnd(4, '0'))
  const signed = exact[1] === '-' ? -magnitude : magnitude
  return signed < BigInt(min) || signed > BigInt(max) ? null : Number(signed)
}
const integer = (value: string, min: number, max: number) => /^\d{1,3}$/.test(value) && +value >= min && +value <= max

export function SubtitleEdit({ cues, onPreview, ...props }: ConfirmedEditProps & {
  cues: readonly SubtitleCue[]
  onPreview: (batch: SemanticBatch | null) => void
}) {
  const [selected, select] = useState('')
  const [draft, update] = useState<Form | null>(null)
  const saved = cues.find(c => c.cueId === selected)
  const scope = JSON.stringify([props.head?.revisionId, props.head?.contentHash, selected])
  const form = draft?.scope === scope ? draft : {
    scope, text: saved?.text ?? '', start: saved?.start ?? '', end: saved?.end ?? '',
    width: String(saved?.width ?? 240), fontSize: String(saved?.fontSize ?? 24), fontProfile: resolveSubtitleFont(saved?.fontProfile), remove: false,
    keys: saved?.keys.map((k,i) => ({ id: String(i), time: k.time, x: String(k.xMicros / 10000),
      y: String(k.yMicros / 10000), opacity: String(k.opacityMicros / 10000) })) ?? [],
  }
  const change = (values: Partial<Form>) => update({ ...form, ...values })
  const start = parseRational(form.start), end = parseRational(form.end)
  const interval = !!start && !!end && start.numerator >= 0n && compare(start, end) < 0
  const keys = form.keys.flatMap(row => {
    const time = parseRational(row.time)
    const xMicros = percent(row.x, -16000000, 16000000)
    const yMicros = percent(row.y, -16000000, 16000000)
    const opacityMicros = percent(row.opacity, 0, 1000000)
    return time && time.numerator >= 0n && xMicros != null && yMicros != null && opacityMicros != null
      ? [{ time, xMicros, yMicros, opacityMicros }] : []
  })
  const valid = interval && form.text.trim().length > 0 && form.text.length <= 384
    && integer(form.width, 40, 304) && integer(form.fontSize, 12, 44)
    && keys.length === form.keys.length && keys.every((k, i) => compare(k.time, subtract(end, start)) < 0
      && (!i || compare(keys[i - 1].time, k.time) < 0))
  const remove = form.remove && !!saved
  return <ConfirmedClipEdit {...props} edit="subtitle" inputIdentity={JSON.stringify([scope, form])} onPreview={onPreview}
    draft={remove || valid ? () => {
      const cueId = saved?.cueId ?? crypto.randomUUID()
      const step: SemanticStep = remove ? { definitionId: 'timeline.subtitle.cue.remove', parameters: { cueId } }
        : { definitionId: 'timeline.subtitle.cue.set', parameters: {
          cueId, text: form.text, width: Number(form.width), fontSize: Number(form.fontSize),
          start: formatRational(start!), end: formatRational(end!), fontProfile: form.fontProfile,
          keys: keys.map(key => ({ ...key, time: formatRational(key.time) })),
        } }
      return { step, ranges: [remove ? 'Remove the selected subtitle.' : `Subtitle [${formatRational(start!)}, ${formatRational(end!)}) s`] }
    } : null}>
    <label>Subtitle cue <select aria-label="Subtitle cue" value={saved?.cueId ?? ''} onChange={e => { select(e.target.value); update(null) }}>
      <option value="">New subtitle</option>
      {cues.map(c => <option key={c.cueId} value={c.cueId}>{c.text.split(/\r?\n/, 1)[0]} · {c.start} s</option>)}
    </select></label>
    {saved && <label><input type="checkbox" checked={remove} onChange={e => change({ remove: e.target.checked })} />Remove saved subtitle</label>}
    <fieldset disabled={remove}>
      <label>Subtitle text <textarea aria-label="Subtitle text" maxLength={384} value={form.text} onChange={e => change({ text: e.target.value })} /></label>
      {([['start', 'Subtitle start (timeline seconds)'], ['end', 'Subtitle end (timeline seconds)'],
        ['width', 'Subtitle width (at 320 px)'], ['fontSize', 'Subtitle font size (at 320 px)']] as const)
        .map(([key,label]) => <label key={key}>{label} <input value={form[key]} onChange={e => change({ [key]: e.target.value })} /></label>)}
      <label>Subtitle font <select value={form.fontProfile} onChange={e => change({ fontProfile: resolveSubtitleFont(e.target.value) })}>
        {Object.entries(SUBTITLE_FONTS).map(([id, label]) => <option key={id} value={id}>{label}</option>)}
      </select></label>
      <label><input type="checkbox" checked={form.keys.length > 0}
        onChange={e => change({ keys: e.target.checked
          ? [{ id: 'first', time: '0', x: '10', y: '55', opacity: '100' },
            { id: 'last', time: '1', x: '25', y: '62.5', opacity: '60' }] : [] })} />Animate whole subtitle</label>
      {form.keys.map((row, index) => <div key={row.id}>
        {([['time', 'time (cue seconds)'], ['x', 'X (%)'], ['y', 'Y (%)'], ['opacity', 'opacity (%)']] as const)
          .map(([field,label]) => <label key={field}>Subtitle key {index + 1} {label}
            <input value={row[field]} onChange={e => change({ keys: form.keys.map(key =>
              key.id === row.id ? { ...key, [field]: e.target.value } : key) })} /></label>)}
        {form.keys.length > 2 && <button type="button" onClick={() => change({ keys: form.keys.filter(key => key.id !== row.id) })}>
          Remove subtitle key {index + 1}</button>}
      </div>)}
      {form.keys.length > 0 && form.keys.length < 16 && <button type="button" onClick={() => change({
        keys: [...form.keys, { id: crypto.randomUUID(), time: '', x: '25', y: '62.5', opacity: '60' }],
      })}>Add subtitle key</button>}
      {form.text && form.start && form.end && !valid && <p role="alert">Use increasing cue times and valid motion keys inside the cue.</p>}
    </fieldset>
  </ConfirmedClipEdit>
}
