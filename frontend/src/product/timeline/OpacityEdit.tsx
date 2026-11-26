import { useState } from 'react'
import type { SemanticBatch } from '../../contracts/semantic-batch.generated'
import { ConfirmedClipEdit, type ConfirmedEditProps } from './ConfirmedClipEdit'
import type { ProjectedTrack } from './timeline-projection'
import { add, compare, formatRational, parseRational, subtract } from './trim'

const labels = ['First key time (clip seconds)', 'First opacity (%)', 'Second key time (clip seconds)', 'Second opacity (%)']

export function OpacityEdit({ tracks, onPreview, ...props }: ConfirmedEditProps & {
  tracks: readonly ProjectedTrack[]
  onPreview: (batch: SemanticBatch | null) => void
}) {
  const clips = tracks.filter(track => track.type === 'VIDEO').flatMap(track => track.clips)
  const [selected, select] = useState('')
  const [draft, update] = useState<{ scope: string; values: string[] } | null>(null)
  const clip = clips.find(c => c.clipId === selected)
  const scope = JSON.stringify([props.head?.revisionId, props.head?.contentHash, selected])
  const saved = clip?.opacityKeys
  const values = draft?.scope === scope ? draft.values : saved && clip
    ? [formatRational(subtract(saved.firstTime, clip.start)), String(saved.firstValue * 100),
      formatRational(subtract(saved.lastTime, clip.start)), String(saved.lastValue * 100)]
    : ['', '0', '', '100']
  const first = parseRational(values[0]), last = parseRational(values[2])
  const validPercent = (v: string) => /^\d+(?:\.\d+)?$/.test(v) && Number(v) <= 100
  const valid = clip && first && last && first.numerator >= 0n && compare(first, last) < 0
    && compare(add(clip.start, last), clip.end) < 0 && validPercent(values[1]) && validPercent(values[3])
  return <ConfirmedClipEdit {...props} edit="opacity" onPreview={onPreview} inputIdentity={JSON.stringify([selected, ...values])}
    draft={valid ? () => {
      const firstTime = formatRational(add(clip.start, first)), lastTime = formatRational(add(clip.start, last))
      return { step: { definitionId: 'timeline.clip.opacity.keyframes.set', target: { clipId: selected },
        parameters: { firstTime, lastTime, firstValue: Number(values[1]) / 100, lastValue: Number(values[3]) / 100 } },
        ranges: ['Timeline ' + firstTime + ' s at ' + values[1] + '% → ' + lastTime + ' s at ' + values[3] + '%'] }
    } : null}>
    <label>Opacity clip <select value={clip?.clipId ?? ''} onChange={e => { select(e.target.value); update(null) }}>
      <option value="">Choose a video clip</option>{clips.map(c => <option key={c.clipId}>{c.clipId}</option>)}
    </select></label>
    {clip && <><p>Opacity changes linearly between keys and holds at either end. The curve replaces static opacity.</p>
      {labels.map((label, index) => <label key={label}>{label} <input value={values[index]}
        onChange={e => update({ scope, values: values.map((v,i) => i === index ? e.target.value : v) })} /></label>)}
      {values[0] && values[2] && !valid && <p role="alert">Use increasing times inside the clip and opacity from 0 to 100%.</p>}
    </>}
  </ConfirmedClipEdit>
}
