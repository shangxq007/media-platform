import { useMemo, useState } from 'react'
import { ConfirmedClipEdit, type ConfirmedEditProps } from './ConfirmedClipEdit'
import type { SemanticStep } from '../../contracts/semantic-batch.generated'
import type { ProjectedTrack } from './timeline-projection'
import { add, compare, formatRational as f, parseRational, subtract, type Rational } from './trim'

interface Choice {
  key: string
  label: string
  draft: (value: Rational) => { step: SemanticStep; ranges: string[] }
}

function rollChoices(tracks: readonly ProjectedTrack[]): Choice[] {
  return tracks.flatMap(track => {
    const ordered = [...track.clips].sort((a, b) => compare(a.start, b.start))
    return ordered.flatMap((left, index) => {
      const right = ordered[index + 1]
      if (!right || compare(left.end, right.start) !== 0) return []
      return [{ key: JSON.stringify([track.trackId, left.clipId, right.clipId]),
        label: left.clipId + ' / ' + right.clipId + ' at ' + f(left.end) + 's',
        draft: (cut: Rational) => {
          const shift = subtract(cut, left.end)
          return { step: { definitionId: 'timeline.clip.roll' as const, version: '1.0' as const,
            parameters: { trackId: track.trackId, leftClipId: left.clipId, rightClipId: right.clipId, cutTime: f(cut) } },
            ranges: ['Shared cut ' + f(left.end) + 's → ' + f(cut) + 's',
              left.clipId + ' timeline [' + f(left.start) + ', ' + f(cut) + '), source [' + f(left.sourceStart) + ', ' + f(add(left.sourceEnd, shift)) + ')',
              right.clipId + ' timeline [' + f(cut) + ', ' + f(right.end) + '), source [' + f(add(right.sourceStart, shift)) + ', ' + f(right.sourceEnd) + ')'] }
        } }]
    })
  })
}

function slipChoices(tracks: readonly ProjectedTrack[]): Choice[] {
  return tracks.flatMap(track => track.clips.map(clip => ({
    key: clip.clipId, label: clip.clipId + ' on ' + track.name,
    draft: (offset: Rational) => ({
      step: { definitionId: 'timeline.clip.slip' as const, version: '1.0' as const, target: { clipId: clip.clipId },
        parameters: { offset: f(offset) } },
      ranges: [clip.clipId + ' timeline [' + f(clip.start) + ', ' + f(clip.end) + ')',
        'Source [' + f(clip.sourceStart) + ', ' + f(clip.sourceEnd) + ') → [' + f(add(clip.sourceStart, offset)) + ', ' + f(add(clip.sourceEnd, offset)) + ')',
        'Source offset ' + (offset.numerator > 0n ? '+' : '') + f(offset) + 's'],
    }),
  })))
}

function slideChoices(tracks: readonly ProjectedTrack[]): Choice[] {
  return tracks.flatMap(track => {
    const ordered = [...track.clips].sort((a, b) => compare(a.start, b.start))
    const choices: Choice[] = []
    let earlierEnd: Rational | undefined
    for (let i = 0; i + 2 < ordered.length; i++) {
      const left = ordered[i], middle = ordered[i + 1], right = ordered[i + 2]
      const overlapsEarlier = earlierEnd && compare(earlierEnd, left.start) > 0
      if (!earlierEnd || compare(left.end, earlierEnd) > 0) earlierEnd = left.end
      if (overlapsEarlier || compare(left.end, middle.start) !== 0 || compare(middle.end, right.start) !== 0
          || (ordered[i + 3] && compare(ordered[i + 3].start, right.end) < 0)) continue
      const ids = { trackId: track.trackId, leftClipId: left.clipId, middleClipId: middle.clipId, rightClipId: right.clipId }
      choices.push({
        key: JSON.stringify([track.trackId, left.clipId, middle.clipId, right.clipId]),
        label: middle.clipId + ' between ' + left.clipId + ' and ' + right.clipId,
        draft(offset) {
          const first = f(add(middle.start, offset)), second = f(add(middle.end, offset))
          return { step: { definitionId: 'timeline.clip.slide', version: '1.0', parameters: { ...ids, offset: f(offset) } },
            ranges: ['Cuts ' + f(middle.start) + 's → ' + first + 's and ' + f(middle.end) + 's → ' + second + 's',
              left.clipId + ' timeline [' + f(left.start) + ', ' + first + '), source [' + f(left.sourceStart) + ', ' + f(add(left.sourceEnd, offset)) + ')',
              middle.clipId + ' timeline [' + first + ', ' + second + '), source [' + f(middle.sourceStart) + ', ' + f(middle.sourceEnd) + ')',
              right.clipId + ' timeline [' + second + ', ' + f(right.end) + '), source [' + f(add(right.sourceStart, offset)) + ', ' + f(right.sourceEnd) + ')'] }
        },
      })
    }
    return choices
  })
}

const rangeEdits = {
  roll: { label: 'Roll', selectLabel: 'Shared cut', placeholder: 'Choose adjacent clips', timeLabel: 'New cut time (seconds)', choices: rollChoices },
  slip: { label: 'Slip', selectLabel: 'Slip clip', placeholder: 'Choose a clip', timeLabel: 'Source offset (seconds)', choices: slipChoices },
  slide: { label: 'Slide', selectLabel: 'Middle clip', placeholder: 'Choose a middle clip', timeLabel: 'Timeline offset (seconds)', choices: slideChoices },
}

export function SourceRangeEdit({ edit, tracks, ...props }: ConfirmedEditProps & {
  edit: keyof typeof rangeEdits
  tracks: readonly ProjectedTrack[]
}) {
  const { selectLabel, placeholder, timeLabel } = rangeEdits[edit]
  const signed = edit !== 'roll'
  const choices = useMemo(() => rangeEdits[edit].choices(tracks), [edit, tracks])
  const [selected, select] = useState('')
  const [input, setInput] = useState('')
  const choice = choices.find(p => p.key === selected)
  const value = parseRational(input)
  return <ConfirmedClipEdit {...props} edit={edit} inputIdentity={JSON.stringify([selected, input])}
    draft={choice && value && (signed || value.numerator >= 0n) ? () => choice.draft(value) : null}>
      <label>{selectLabel} <select value={choice?.key ?? ''} onChange={e => { select(e.target.value); setInput('') }}>
        <option value="">{placeholder}</option>
        {choices.map(p => <option key={p.key} value={p.key}>{p.label}</option>)}
      </select></label>
      <label>{timeLabel} <input value={input} onChange={e => setInput(e.target.value)} /></label>
      {input && (!value || (!signed && value.numerator < 0n)) && <p role="alert">{signed ? 'Enter a signed integer or fraction.' : 'Enter a nonnegative integer or fraction.'}</p>}
  </ConfirmedClipEdit>
}
