import { editRangeFonts, setRangeFont, subtitleSelection, textareaText, type FontRun, type TextInputSpan } from './subtitle-font-runs'
import { useEffect, useRef, useState } from 'react'
import { FormalRenderAPI, type SubtitleDecorationCatalog, type SubtitleFontCatalog } from '../../api/formal-render'
import type { SemanticBatch, SemanticStep } from '../../contracts/semantic-batch.generated'
import { ConfirmedClipEdit, type ConfirmedEditProps } from './ConfirmedClipEdit'
import { savedSubtitleFont, type SelectedTextFade, type SubtitleCue, type SubtitleDecoration } from './subtitle-cues'
import { add, compare, formatRational, parseRational, subtract } from './trim'

type Row = { id: string; time: string; x: string; y: string; opacity: string }
type Form = { scope: string; text: string; start: string; end: string; width: string; fontSize: string; fontProfile: string; fontRuns: FontRun[]; selectedTextFade?: SelectedTextFade; decoration?: SubtitleDecoration; keys: Row[]; remove: boolean }
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
  const [selection, setSelection] = useState<{ scope: string; text: string; from: number; to: number } | null>(null)
  const [attempt, reload] = useState(0)
  const project = props.head?.projectId
  const catalogScope = project && props.tenant ? JSON.stringify([project, props.tenant, attempt]) : null
  const [catalogState, setCatalog] = useState<{ scope: string; fonts: SubtitleFontCatalog | null } | null>(null)
  useEffect(() => {
    if (!catalogScope || !project || !props.tenant) return
    const cancel = new AbortController()
    void FormalRenderAPI.listSubtitleFonts(props.tenant, project, cancel.signal).then(
      fonts => { if (!cancel.signal.aborted) setCatalog({ scope: catalogScope, fonts }) },
      () => { if (!cancel.signal.aborted) setCatalog({ scope: catalogScope, fonts: null }) },
    )
    return () => cancel.abort()
  }, [catalogScope, project, props.tenant])
  const catalog = catalogState?.scope === catalogScope ? catalogState.fonts : undefined
  const [decorationCatalogState, setDecorationCatalog] =
    useState<{ scope: string; assets: SubtitleDecorationCatalog | null } | null>(null)
  useEffect(() => {
    if (!catalogScope || !project || !props.tenant) return
    const cancel = new AbortController()
    void FormalRenderAPI.listSubtitleDecorations(props.tenant, project, cancel.signal).then(
      assets => { if (!cancel.signal.aborted) setDecorationCatalog({ scope: catalogScope, assets }) },
      () => { if (!cancel.signal.aborted) setDecorationCatalog({ scope: catalogScope, assets: null }) },
    )
    return () => cancel.abort()
  }, [catalogScope, project, props.tenant])
  const decorations = decorationCatalogState?.scope === catalogScope ? decorationCatalogState.assets : undefined
  const saved = cues.find(c => c.cueId === selected)
  const savedProfile = saved ? savedSubtitleFont(saved.fontProfile) : ''
  const scope = JSON.stringify([project, props.tenant, props.head?.revisionId, props.head?.contentHash, selected])
  const form = draft?.scope === scope ? draft : {
    scope, text: saved?.text ?? '', start: saved?.start ?? '', end: saved?.end ?? '',
    width: String(saved?.width ?? 240), fontSize: String(saved?.fontSize ?? 24), fontProfile: savedProfile, fontRuns: saved?.fontRuns ?? [], selectedTextFade: saved?.selectedTextFade ?? undefined,
    decoration: saved?.decoration ?? undefined, remove: false,
    keys: saved?.keys.map((k,i) => ({ id: String(i), time: k.time, x: String(k.xMicros / 10000),
      y: String(k.yMicros / 10000), opacity: String(k.opacityMicros / 10000) })) ?? [],
  }
  const textarea = useRef<HTMLTextAreaElement>(null)
  const pendingInput = useRef<(TextInputSpan & { scope: string; value: string }) | null>(null)
  const clearInput = () => { pendingInput.current = null }
  useEffect(() => {
    const target = textarea.current
    if (!target) return
    const capture = (event: InputEvent) => {
      pendingInput.current = {
        scope, start: target.selectionStart, end: target.selectionEnd, inputType: event.inputType, value: target.value,
      }
    }
    // React's synthesized beforeinput omits native edits such as paste and deletion.
    target.addEventListener('beforeinput', capture)
    return () => {
      pendingInput.current = null
      target.removeEventListener('beforeinput', capture)
    }
  }, [scope])
  const change = (values: Partial<Form>) => update({ ...form, ...values })
  const range = selection && selection.scope === scope && selection.text === form.text
    ? subtitleSelection(form.text, selection.from, selection.to) : null
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
  const fade = form.selectedTextFade
  const fadeTime = parseRational(fade?.time), fadeLength = parseRational(fade?.duration)
  const validFade = !fade || (!!fadeTime && !!fadeLength && interval && fadeTime.numerator >= 0n
    && fadeLength.numerator > 0n && compare(add(fadeTime, fadeLength), subtract(end, start)) <= 0)
  const decoration = form.decoration
  const decorationStart = parseRational(decoration?.start), decorationEnd = parseRational(decoration?.end)
  const validDecoration = !decoration || (interval && !!decorationStart && !!decorationEnd
    && decorationStart.numerator >= 0n && compare(decorationStart, decorationEnd) < 0
    && compare(decorationEnd, subtract(end, start)) <= 0)
  const valid = interval && validFade && validDecoration && form.fontRuns.length <= 64 && form.text.trim().length > 0 && form.text.length <= 384
    && integer(form.width, 40, 304) && integer(form.fontSize, 12, 44)
    && keys.length === form.keys.length && keys.every((k, i) => compare(k.time, subtract(end, start)) < 0
      && (!i || compare(keys[i - 1].time, k.time) < 0))
  const remove = form.remove && !!saved
  const eligible = !!catalog && (!form.fontProfile || form.fontProfile === savedProfile
    || catalog.some(font => font.id === form.fontProfile))
  return <ConfirmedClipEdit {...props} edit="subtitle" inputIdentity={JSON.stringify([scope, attempt, form])} onPreview={onPreview}
    draft={remove || (valid && eligible) ? () => {
      const cueId = saved?.cueId ?? crypto.randomUUID()
      const step: SemanticStep = remove ? { definitionId: 'timeline.subtitle.cue.remove', parameters: { cueId } }
        : { definitionId: 'timeline.subtitle.cue.set', parameters: {
          cueId, text: form.text, width: Number(form.width), fontSize: Number(form.fontSize),
          start: formatRational(start!), end: formatRational(end!),
          ...(form.fontProfile && form.fontProfile !== savedProfile ? { fontProfile: form.fontProfile } : {}),
          ...(form.fontRuns.length || saved?.fontRuns?.length ? { fontRuns: form.fontRuns } : {}),
          ...(fade ? { selectedTextFade: { ...fade, time: formatRational(fadeTime!), duration: formatRational(fadeLength!) } }
            : saved?.selectedTextFade ? { selectedTextFade: null } : {}),
          ...(decoration ? { decoration: { ...decoration,
            start: formatRational(decorationStart!), end: formatRational(decorationEnd!) } }
            : saved?.decoration ? { decoration: null } : {}),
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
      <label>Subtitle text <textarea aria-label="Subtitle text" maxLength={384} ref={textarea} value={textareaText(form.text)} onBlur={clearInput}
        onCompositionStart={clearInput} onCompositionEnd={clearInput}
        onSelect={e => setSelection({ scope, text: form.text, from: e.currentTarget.selectionStart, to: e.currentTarget.selectionEnd })}
        onInput={e => {
          const pending = pendingInput.current
          clearInput()
          const { value, selectionEnd } = e.currentTarget
          const input = pending?.scope === scope && pending.value === textareaText(form.text) && pending.inputType === (e.nativeEvent as InputEvent).inputType
            ? pending : null
          setSelection(null)
          const edited = editRangeFonts(form.text, value, form.fontRuns, selectionEnd, input, form.selectedTextFade)
          change({ ...edited, selectedTextFade: edited.selectedTextFade })
        }} /></label>
      {range && <label>Font for selected text <select aria-label="Font for selected text" value="" disabled={!catalog}
        onChange={e => change({ fontRuns: setRangeFont(form.fontRuns, range, e.target.value === 'inherit' ? '' : e.target.value) })}>
        <option value="" disabled>Choose font</option>
        <option value="inherit">Use cue font</option>
        {catalog?.map(font => <option key={font.id} value={font.id}>{font.label}</option>)}
      </select></label>}
      <button type="button" disabled={!range} onClick={() => range && change({
        selectedTextFade: { time: fade?.time ?? '0', duration: fade?.duration ?? '1/1', ...range },
      })}>Fade selected text</button>
      {fade && <fieldset>
        <legend>Selected-text fade</legend>
        <p>{Array.from(form.text).slice(fade.start, fade.end).join('')}</p>
        <label>Fade start (cue seconds) <input value={fade.time}
          onChange={e => change({ selectedTextFade: { ...fade, time: e.target.value } })} /></label>
        <label>Fade duration (seconds) <input value={fade.duration}
          onChange={e => change({ selectedTextFade: { ...fade, duration: e.target.value } })} /></label>
        <button type="button" onClick={() => change({ selectedTextFade: undefined })}>Remove selected-text fade</button>
        {interval && !validFade && <p role="alert">Use a nonnegative start and positive duration that fit inside the cue.</p>}
      </fieldset>}
      {selection?.scope === scope && selection.text === form.text && selection.from !== selection.to && !range &&
        <p role="alert">Select whole characters, including combining marks and emoji sequences.</p>}
      {form.fontRuns.length > 64 && <p role="alert">A subtitle supports up to 64 font ranges.</p>}
      {!!form.fontRuns.length && <ul aria-label="Selected text fonts">{form.fontRuns.map(run =>
        <li key={run.start}>{Array.from(form.text).slice(run.start, run.end).join('')} · {catalog?.find(f => f.id === run.fontProfile)?.label ?? run.fontProfile}</li>)}</ul>}
      {([['start', 'Subtitle start (timeline seconds)'], ['end', 'Subtitle end (timeline seconds)'],
        ['width', 'Subtitle width (at 320 px)'], ['fontSize', 'Subtitle font size (at 320 px)']] as const)
        .map(([key,label]) => <label key={key}>{label} <input value={form[key]} onChange={e => change({ [key]: e.target.value })} /></label>)}
      <label>Subtitle font <select aria-label="Subtitle font" value={form.fontProfile} disabled={!catalog}
        onChange={e => change({ fontProfile: e.target.value })}>
        {!saved && <option value="">Use project default</option>}
        {savedProfile && !catalog?.some(font => font.id === savedProfile)
          && <option value={savedProfile}>Keep saved font ({savedProfile})</option>}
        {catalog?.map(font => <option key={font.id} value={font.id}>{font.label}</option>)}
      </select></label>
      {catalog === undefined && catalogScope && <p role="status">Loading subtitle fonts…</p>}
      {catalog === null && <><p role="alert">Subtitle font catalog unavailable. No font was substituted.</p>
        <button type="button" onClick={() => reload(previous => previous + 1)}>Retry subtitle fonts</button></>}
      <fieldset>
        <legend>Subtitle decoration</legend>
        <label>Decoration <select aria-label="Decoration" value={decoration?.assetId ?? ''} disabled={!decorations} onChange={e => {
          const asset = decorations?.find(item => item.id === e.target.value)
          if (asset) change({ decoration: { assetId: asset.id, placement: asset.placement,
            enabled: true, start: '0', end: interval ? formatRational(subtract(end, start)) : '1/1' } })
        }}>
          <option value="" disabled>Choose a decoration</option>
          {decoration && !decorations?.some(asset => asset.id === decoration.assetId)
            && <option value={decoration.assetId}>{decoration.assetId}</option>}
          {decorations?.map(asset => <option key={asset.id} value={asset.id}>{asset.label}</option>)}
        </select></label>
        {decorations === null && <p role="status">Decoration catalog unavailable.</p>}
        {decoration && <>
          <p>Right of the subtitle box</p>
          <label><input type="checkbox" checked={decoration.enabled}
            onChange={e => change({ decoration: { ...decoration, enabled: e.target.checked } })} />Enable decoration</label>
          <label>Decoration start (cue seconds) <input value={decoration.start}
            onChange={e => change({ decoration: { ...decoration, start: e.target.value } })} /></label>
          <label>Decoration end (cue seconds) <input value={decoration.end}
            onChange={e => change({ decoration: { ...decoration, end: e.target.value } })} /></label>
          <button type="button" onClick={() => change({ decoration: undefined })}>Remove decoration</button>
          {interval && !validDecoration && <p role="alert">Set an increasing decoration interval inside the cue.</p>}
        </>}
      </fieldset>
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
