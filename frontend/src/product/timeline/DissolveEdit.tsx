import { useState } from 'react'
import type { SemanticBatch, SemanticStep } from '../../contracts/semantic-batch.generated'
import { ConfirmedClipEdit, type ConfirmedEditProps } from './ConfirmedClipEdit'
import type { ProjectedClip, ProjectedTrack, ProjectedTransition } from './timeline-projection'
import { add, compare, formatRational as exact, parseRational } from './trim'

const forward = (clip: ProjectedClip) => clip.direction === 'FORWARD' && clip.rate.numerator === clip.rate.denominator

export function DissolveEdit({ tracks, transitions, onPreview, audio = false, ...props }: ConfirmedEditProps & {
  audio?: boolean
  tracks: readonly ProjectedTrack[]
  transitions: readonly ProjectedTransition[]
  onPreview: (batch: SemanticBatch | null) => void
}) {
  const cuts = tracks.filter(track => track.type === (audio ? 'AUDIO' : 'VIDEO')).flatMap(track => {
    const clips = [...track.clips].sort((a, b) => compare(a.start, b.start))
    return clips.flatMap((left, i) => {
      const right = clips[i + 1]
      return right && forward(left) && forward(right) && compare(left.end, right.start) === 0
        ? [{ left, right, key: JSON.stringify([track.trackId, left.clipId, right.clipId]) }] : []
    })
  })
  const [selection, select] = useState('')
  const cut = cuts.find(c => c.key === selection)
  const saved = cut && transitions.find(t => t.outgoingClipId === cut.left.clipId && t.incomingClipId === cut.right.clipId && t.outgoingHandle && Boolean(t.audioCrossfade) === audio)
  const scope = JSON.stringify([props.head?.revisionId, selection])
  const [kind, setKind] = useState<'dissolve' | 'wipe'>('dissolve')
  const wipe = !audio && (saved ? saved.wipeLeftToRight === true : kind === 'wipe')
  const [input, setInput] = useState<{scope: string; duration: string; remove: boolean} | null>(null)
  const value = input?.scope === scope ? input.duration : saved ? exact(saved.duration) : '1/1'
  const remove = !!saved && input?.scope === scope && input.remove
  const duration = remove && saved ? saved.duration : parseRational(value)
  return <ConfirmedClipEdit {...props} edit={audio ? 'audio crossfade' : wipe ? 'wipe' : 'dissolve'} onPreview={onPreview}
    inputIdentity={JSON.stringify([scope, saved?.transitionId, value, remove, wipe])}
    draft={cut && duration && duration.numerator > 0n ? () => {
      const id = saved?.transitionId ?? crypto.randomUUID()
      const step: SemanticStep = remove
        ? { definitionId: audio ? 'timeline.transition.audio-crossfade.delete' : wipe ? 'timeline.transition.wipe-left-to-right.delete' : 'timeline.transition.cross-dissolve.delete', target: { transitionId: id } }
        : saved ? { definitionId: audio ? 'timeline.transition.audio-crossfade.update' : wipe ? 'timeline.transition.wipe-left-to-right.update' : 'timeline.transition.cross-dissolve.update', target: { transitionId: id }, parameters: { duration: exact(duration) } }
        : { definitionId: audio ? 'timeline.transition.audio-crossfade.create' : wipe ? 'timeline.transition.wipe-left-to-right.create' : 'timeline.transition.cross-dissolve.create', target: { clipId: cut.left.clipId },
          parameters: { transitionId: id, incomingClipId: cut.right.clipId, duration: exact(duration) } }
      return { step, ranges: [cut.left.clipId + ' → ' + cut.right.clipId + ' · cut ' + exact(cut.left.end) + ' s',
        (remove ? 'Remove ' : '') + (wipe ? 'Left-to-right wipe' : 'Blend') + ' [' + exact(cut.left.end) + ', ' + exact(add(cut.left.end, duration)) + ') s · duration ' + exact(duration) + ' s'] }
    } : null}>
    <label>{audio ? 'Shared audio cut' : 'Shared video cut'} <select aria-label={audio ? 'Shared audio cut' : undefined} value={cut?.key ?? ''} onChange={e => { select(e.target.value); setInput(null) }}>
      <option value="">Choose a cut</option>{cuts.map(c => <option key={c.key} value={c.key}>
        {c.left.clipId} → {c.right.clipId} at {exact(c.left.end)} s</option>)}
    </select></label>
    {audio && cut && (cut.left.audioFades?.fadeOut.numerator || cut.right.audioFades?.fadeIn.numerator)
      ? <p role="status">Remove the outgoing fade-out or incoming fade-in before using an audio crossfade.</p> : null}
    {!audio && cut && <label>Video transition <select aria-label="Video transition" value={wipe ? 'wipe' : 'dissolve'}
      disabled={!!saved} onChange={e => { setKind(e.target.value === 'wipe' ? 'wipe' : 'dissolve'); setInput(null) }}>
      <option value="dissolve">Cross-dissolve</option><option value="wipe">Left-to-right wipe</option>
    </select></label>}
    {cut && <label>{audio ? 'Crossfade duration (seconds)' : wipe ? 'Wipe duration (seconds)' : 'Dissolve duration (seconds)'} <input value={value} disabled={remove}
      onChange={e => setInput({ scope, duration: e.target.value, remove: false })} /></label>}
    {saved && <label><input type="checkbox" checked={remove}
      onChange={e => setInput({ scope, duration: value, remove: e.target.checked })} />{audio ? 'Remove saved crossfade' : wipe ? 'Remove saved wipe' : 'Remove saved dissolve'}</label>}
  </ConfirmedClipEdit>
}
