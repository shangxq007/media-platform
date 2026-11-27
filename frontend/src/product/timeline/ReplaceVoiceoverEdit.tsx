import { useState } from 'react'
import type { SemanticBatch } from '../../contracts/semantic-batch.generated'
import { ConfirmedClipEdit, type ConfirmedEditProps } from './ConfirmedClipEdit'
import { SourceBrowser } from './SourceBrowser'
import type { AssetSourcePin } from './gateways'
import type { ProjectedTrack, ProjectedTransition } from './timeline-projection'
import { add, compare, formatRational, parseRational, subtract, ZERO, type Rational } from './trim'

function missingDuration(required: Rational, available: Rational): string {
  const missing = subtract(required, available)
  const amount = missing.denominator === 1n ? String(missing.numerator) : formatRational(missing)
  return amount + (compare(missing, { numerator: 1n, denominator: 1n }) === 0 ? ' second' : ' seconds')
}

export function ReplaceVoiceoverEdit({ project, tracks, transitions, onPreview, ...props }: ConfirmedEditProps & {
  project: string
  tracks: readonly ProjectedTrack[]
  transitions: readonly ProjectedTransition[]
  onPreview: (batch: SemanticBatch | null) => void
}) {
  const [targetId, selectTarget] = useState('')
  const [source, selectSource] = useState<AssetSourcePin | null>(null)
  const [streamId, selectStream] = useState('')
  const [sourceIn, setSourceIn] = useState('0')
  const clips = tracks.filter(track => track.type === 'AUDIO').flatMap(track => track.clips)
  const target = clips.find(clip => clip.clipId === targetId)
  const streams = source?.streams.filter(stream => stream.kind === 'AUDIO') ?? []
  const stream = streams.find(candidate => candidate.streamId === streamId)
  const start = parseRational(sourceIn)
  const end = target && start ? add(start, subtract(target.end, target.start)) : null
  const tail = transitions.find(t => t.audioCrossfade && t.outgoingClipId === targetId)?.duration ?? ZERO
  const available = stream && parseRational(stream.duration)
  const required = end && add(end, tail)
  const reason = target && (target.direction !== 'FORWARD' || target.rate.numerator !== target.rate.denominator)
    ? 'Replace voiceover requires forward playback at normal speed.'
    : source && streams.length !== 1 ? 'This editor and renderer require a source with one supported audio stream.'
    : !start || compare(start, ZERO) < 0 ? 'Enter a nonnegative exact source start.'
    : available && end && compare(end, available) > 0
      ? `The replacement is missing ${missingDuration(end, available)} for the visible clip range.`
    : available && required && compare(required, available) > 0
      ? `The replacement covers the visible clip range but is missing ${missingDuration(required, available)} for its outgoing crossfade handle.`
    : null
  return <ConfirmedClipEdit {...props} edit="replace voiceover" onPreview={onPreview}
    inputIdentity={JSON.stringify([targetId, source?.artifactId, streamId, sourceIn])}
    draft={target && source && stream && start && end && required && available && !reason ? () => ({
      step: { definitionId: 'timeline.clip.voiceover.replace', target: { clipId: target.clipId },
        parameters: { artifactId: source.artifactId, contentDigest: source.contentDigest,
          mediaStreamId: stream.streamId, sourceStart: formatRational(start) } },
      ranges: [
        `Audition ${source.name} on ${target.clipId} without moving the clip.`,
        `Replacement source [${formatRational(start)}, ${formatRational(end)}) seconds.`,
        ...(tail.numerator > 0n ? [`The outgoing transition uses the source through ${formatRational(required)} seconds.`] : []),
      ],
    }) : null}>
    <label>Voiceover clip <select aria-label="Voiceover clip" value={targetId} onChange={event => selectTarget(event.target.value)}>
      <option value="">Choose an audio clip</option>
      {clips.map(clip => <option key={clip.clipId} value={clip.clipId}>{clip.clipId} · {clip.trackId}</option>)}
    </select></label>
    {target && <SourceBrowser tenantId={props.tenant} project={project} existingAudioOnly
      onSelect={pin => { selectSource(pin); selectStream('') }} />}
    {target && source && <>
      <label>Replacement audio stream <select aria-label="Replacement audio stream" value={streamId} onChange={event => selectStream(event.target.value)}>
        <option value="">Choose an audio stream</option>
        {streams.map(candidate => <option key={candidate.streamId} value={candidate.streamId}>{candidate.streamId} · {candidate.duration} s</option>)}
      </select></label>
      <label>Replacement source start (seconds) <input value={sourceIn} maxLength={64}
        onChange={event => setSourceIn(event.target.value)} /></label>
      {start && end && <p>Selected source range [{formatRational(start)}, {formatRational(end)}) seconds.</p>}
    </>}
    {reason && <p role="status">{reason}</p>}
  </ConfirmedClipEdit>
}
