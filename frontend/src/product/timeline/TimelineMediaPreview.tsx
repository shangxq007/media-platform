import { DEFAULT_COMPOSITION_ASPECT } from '../../contracts/app/composition-aspect'
import { previewRaster } from './composition-raster'
import { useEffect, useRef, useState } from 'react'
import { Button } from '../../components/design-system'
import { FormalRenderAPI } from '../../api/formal-render'
import { assetGateway } from '../../api/app/asset.gateway'
import { parseRational, compare, subtract, toSeconds } from './trim'
import { activeClipsAt, sourceTimeAt, timelineDuration, transitionForIncoming } from './timeline-preview'
import { audioRouteFor, projectSnapshotJson, type ProjectedVideoEffect, type ProjectedClip, type TimelineProjection } from './timeline-projection'
import { projectId as validateProjectId } from './types'

type PreviewStatus = 'IDLE' | 'PREPARING' | 'READY' | 'PLAYING' | 'PAUSED' | 'FAILED'
interface AudioClip { clip: ProjectedClip; buffer: AudioBuffer; gain: number; mute: boolean; pan: number }
interface PreviewSession {
  controller: AbortController
  urls: string[]
  videos: Map<string, HTMLVideoElement>
  audioClips: AudioClip[]
  scheduled: AudioBufferSourceNode[]
  audio?: AudioContext
  mixer?: GainNode
  analyser?: AnalyserNode
  projection?: TimelineProjection
  colorSurface?: HTMLCanvasElement
  duration: number
  seekInputEnd: number
  cursor: number
  origin: number
  playing: boolean
  starting: boolean
  frame?: number
}

const clock = (session: PreviewSession) => session.audio?.currentTime ?? performance.now() / 1000
const rationalTime = (seconds: number) => ({ numerator: BigInt(Math.floor(seconds * 1_000_000)), denominator: 1_000_000n })
const seekInputMax = (duration: number) => Math.max(0.01, duration)

function nativeSeekInputEnd(duration: number) {
  // A range control can serialize its maximum with fewer digits than its max attribute.
  // Measure that serialization without touching the live slider or snapping nearby positions.
  const input = document.createElement('input')
  input.type = 'range'
  input.min = '0'
  input.max = String(seekInputMax(duration))
  input.step = 'any'
  input.value = input.max
  return input.valueAsNumber
}

function stopPlayback(session: PreviewSession) {
  session.playing = false
  if (session.frame !== undefined) cancelAnimationFrame(session.frame)
  for (const media of session.videos.values()) media.pause()
  for (const source of session.scheduled) {
    source.stop()
    source.disconnect()
  }
  session.scheduled = []
}

function loadVideo(media: HTMLVideoElement, url: string, signal: AbortSignal) {
  return new Promise<void>((resolve, reject) => {
    media.addEventListener('loadeddata', () => resolve(), { once: true, signal })
    media.addEventListener('error', () => reject(new Error('A video source could not be decoded.')), { once: true, signal })
    signal.addEventListener('abort', () => reject(signal.reason), { once: true })
    media.src = url
    media.load()
  })
}

/** Playback derived from the exact canonical revision. */
/** RGB code values, brightness then contrast, followed by BT.601 luma saturation. */
function applyBasicVideoColor(samples: Uint8ClampedArray, effect: ProjectedVideoEffect) {
  const gain = 1 + effect.brightness
  const saturation = effect.grayscale ? 0 : effect.saturation
  const adjust = (channel: number) =>
    Math.min(255, Math.max(0, Math.round((channel * gain - 127.5) * effect.contrast + 127.5)))
  for (let offset = 0; offset < samples.length; offset += 4) {
    const red = adjust(samples[offset])
    const green = adjust(samples[offset + 1])
    const blue = adjust(samples[offset + 2])
    const luma = 0.299 * red + 0.587 * green + 0.114 * blue
    samples[offset] = luma + saturation * (red - luma)
    samples[offset + 1] = luma + saturation * (green - luma)
    samples[offset + 2] = luma + saturation * (blue - luma)
  }
}

export function TimelineMediaPreview({ revisionId, tenantId, projectId, onTime, opacityPreview, dissolvePlan, positionPreview }: {
  revisionId: string | undefined
  tenantId: string | null
  projectId: string
  dissolvePlan?: import('../../contracts/semantic-batch.generated').SemanticBatch | null
  opacityPreview?: { revisionId: string; clipId: string; keys: import('./timeline-projection').OpacityKeys } | null
  positionPreview?: { revisionId: string; clipId: string; keys: readonly import('./timeline-projection').PositionKey[] } | null
  onTime?: (time: number) => void
}) {
  const canvas = useRef<HTMLCanvasElement>(null)
  const host = useRef<HTMLElement>(null)
  const current = useRef<PreviewSession | null>(null)
  const dissolve = useRef(dissolvePlan)
  dissolve.current = dissolvePlan
  const transient = useRef(opacityPreview)
  transient.current = opacityPreview
  const positionDraft = useRef(positionPreview)
  positionDraft.current = positionPreview
  useEffect(() => { if (current.current?.projection) draw(current.current) }, [opacityPreview, dissolvePlan, positionPreview])
  const timeCallback = useRef(onTime)
  timeCallback.current = onTime
  const [status, setStatus] = useState<PreviewStatus>('IDLE')
  const [duration, setDuration] = useState(0)
  const [time, setTime] = useState(0)
  const [message, setMessage] = useState('')
  const [retry, setRetry] = useState(0)

  function publishTime(seconds: number) {
    setTime(seconds)
    timeCallback.current?.(seconds)
  }

  function fail(session: PreviewSession, error: unknown) {
    if (current.current !== session) return
    stopPlayback(session)
    setStatus('FAILED')
    setMessage(error instanceof Error ? error.message : 'Timeline media could not be played.')
  }

  function draw(session: PreviewSession, forceSeek = false) {
    const target = canvas.current
    if (!target) return
    const raster = previewRaster(session.projection?.compositionAspectRatio ?? DEFAULT_COMPOSITION_ASPECT)
    if (!raster) return
    const { width, height } = raster
    if (target.width !== width || target.height !== height) {
      target.width = width
      target.height = height
    }
    const context = target.getContext('2d')
    if (!context) return
    const time = rationalTime(session.cursor)
    let projection = session.projection
    const plan = dissolve.current
    const step = plan?.operations[0]
    if (projection && plan?.baseRevisionId === revisionId && step) {
      let transitions = projection.transitions
      if (step.definitionId === 'timeline.transition.cross-dissolve.create' && 'clipId' in step.target) {
        transitions = [...transitions, { ...step.parameters, outgoingClipId: step.target.clipId,
          outgoingHandle: true, duration: parseRational(step.parameters.duration)! }]
      } else if (step.definitionId === 'timeline.transition.cross-dissolve.update') {
        transitions = transitions.map(t => t.transitionId === step.target.transitionId
          ? { ...t, duration: parseRational(step.parameters.duration)! } : t)
      } else if (step.definitionId === 'timeline.transition.cross-dissolve.delete') {
        transitions = transitions.filter(t => t.transitionId !== step.target.transitionId)
      }
      projection = { ...projection, transitions }
    }
    const clips = projection ? activeClipsAt(projection, time) : []
    const top = clips.length ? clips[clips.length - 1] : null
    if (host.current) host.current.dataset.activeClip = top?.clipId ?? ''
    const activeIds = new Set(clips.map(clip => clip.clipId))
    for (const [clipId, media] of session.videos) {
      if (!activeIds.has(clipId) && !media.paused) media.pause()
    }
    context.fillStyle = '#000'
    context.fillRect(0, 0, target.width, target.height)
    for (const clip of clips) {
      const media = session.videos['get'](clip.clipId)
      if (!media) continue
      const offset = Math.max(0, toSeconds(sourceTimeAt(clip, time)))
      const rate = toSeconds(clip.rate)
      if (Math.abs(media.currentTime - offset) > (forceSeek || !session.playing ? 0.001 : 0.12)) {
        media.currentTime = offset
      }
      if (session.playing && clip.direction === 'FORWARD' && media.paused && !media.seeking) {
        media.playbackRate = rate
        void media.play().catch(error => {
          if (session.playing && error.name !== 'AbortError') fail(session, error)
        })
      }
      if (media.seeking || media.readyState < 2) continue
      const effect = clip.effect.enabled ? clip.effect : null
      const now = toSeconds(time)
      const clipStart = toSeconds(clip.start)
      const clipEnd = toSeconds(clip.end)
      const fadeIn = effect && effect.fadeInSeconds > 0
        ? Math.min(1, Math.max(0, (now - clipStart) / effect.fadeInSeconds)) : 1
      const fadeOut = effect && effect.fadeOutSeconds > 0
        ? Math.min(1, Math.max(0, (clipEnd - now) / effect.fadeOutSeconds)) : 1
      const transition = projection
        ? transitionForIncoming(projection, clip.clipId) : null
      const transitionAlpha = transition
        ? Math.min(1, Math.max(0, (now - clipStart) / toSeconds(transition.duration))) : 1
      const t = clip.transform
      const sourceWidth = media.videoWidth
      const sourceHeight = media.videoHeight
      if (!sourceWidth || !sourceHeight) {
        fail(session, new Error('Source video dimensions are unavailable.'))
        return
      }
      const sx = sourceWidth * t.cropLeft
      const sy = sourceHeight * t.cropTop
      const sw = Math.max(1, sourceWidth * (t.cropRight - t.cropLeft))
      const sh = Math.max(1, sourceHeight * (t.cropBottom - t.cropTop))
      const dw = target.width * t.scaleX
      const dh = target.height * t.scaleY
      const radians = t.rotationDegrees * Math.PI / 180
      const rotatedWidth = Math.abs(dw * Math.cos(radians)) + Math.abs(dh * Math.sin(radians))
      const rotatedHeight = Math.abs(dw * Math.sin(radians)) + Math.abs(dh * Math.cos(radians))
      let sample: CanvasImageSource = media
      if (effect && (effect.brightness !== 0 || effect.contrast !== 1
          || effect.saturation !== 1 || effect.grayscale)) {
        const surface = session.colorSurface ??= document.createElement('canvas')
        if (surface.width !== sourceWidth || surface.height !== sourceHeight) {
          surface.width = sourceWidth
          surface.height = sourceHeight
        }
        const colorContext = surface.getContext('2d', { willReadFrequently: true })
        if (!colorContext) { fail(session, new Error('Preview color surface unavailable.')); return }
        colorContext.clearRect(0, 0, sourceWidth, sourceHeight)
        colorContext.drawImage(media, 0, 0)
        const frame = colorContext.getImageData(0, 0, sourceWidth, sourceHeight)
        applyBasicVideoColor(frame.data, effect)
        colorContext.putImageData(frame, 0, 0)
        sample = surface
      }
      context.save()
      const override = transient.current
      const keys = override && override.revisionId === revisionId && override.clipId === clip.clipId ? override.keys : clip.opacityKeys
      let opacity = t.opacity
      if (keys) {
        const progress = compare(time, keys.firstTime) <= 0 ? 0 : compare(time, keys.lastTime) >= 0 ? 1
          : toSeconds(subtract(time, keys.firstTime)) / toSeconds(subtract(keys.lastTime, keys.firstTime))
        opacity = keys.firstValue + progress * (keys.lastValue - keys.firstValue)
      }
      context.globalAlpha = opacity * fadeIn * fadeOut * transitionAlpha
      const preview = positionDraft.current
      const positions = preview && preview.revisionId === revisionId && preview.clipId === clip.clipId ? preview.keys : clip.positionKeys
      let { positionX, positionY } = t
      if (positions?.length) {
        const index = positions.findIndex(key => compare(time, key.time) <= 0)
        const right = index < 0 ? positions[positions.length - 1] : positions[index]
        const left = index <= 0 ? right : positions[index - 1]
        const fraction = left === right ? 0 : toSeconds(subtract(time, left.time)) / toSeconds(subtract(right.time, left.time))
        positionX = (left.xMicros + fraction * (right.xMicros - left.xMicros)) / 1000000
        positionY = (left.yMicros + fraction * (right.yMicros - left.yMicros)) / 1000000
      }
      context.translate(positionX * target.width + rotatedWidth / 2,
        positionY * target.height + rotatedHeight / 2)
      context.rotate(radians)
      context.drawImage(sample, sx, sy, sw, sh, -dw / 2, -dh / 2, dw, dh)
      context.restore()
    }
  }

  useEffect(() => {
    const session: PreviewSession = {
      controller: new AbortController(), urls: [], videos: new Map(), audioClips: [], scheduled: [],
      duration: 0, seekInputEnd: 0, cursor: 0, origin: 0, playing: false, starting: false,
    }
    current.current = session
    const signal = session.controller.signal
    publishTime(0)
    setDuration(0)
    setMessage('')
    draw(session)
    if (host.current) host.current.dataset.audioRms = '0'

    async function prepare(revision: string, tenant: string) {
      setStatus('PREPARING')
      try {
        const snapshot = await FormalRenderAPI.snapshot(projectId, revision, signal)
        if (signal.aborted) return
        const projection = projectSnapshotJson(snapshot.canonicalTimelineJson)
        if (!previewRaster(projection.compositionAspectRatio)) {
          throw new Error('No supported preview raster exactly matches the authored composition aspect ratio.')
        }
        const audioTracks = projection.tracks.filter(track => track.type === 'AUDIO')
        const videoTracks = projection.tracks.filter(track => track.type === 'VIDEO')
        const videoClips = videoTracks.flatMap(track => track.clips)
        // Embedded video audio participates only through an explicit canonical
        // AudioRoute. A second video track never implicitly adds its audio.
        const routedVideoClips = videoClips.filter(clip => audioRouteFor(projection, clip.trackId, clip.clipId) !== null)
        // Embedded video audio is opt-in through an explicit canonical AudioRoute.
        // No video-track-count heuristic silently adds or drops audio.
        const audioClips = [...audioTracks.flatMap(track => track.clips), ...routedVideoClips]
        const scopedSources = projection.clips.length ? await assetGateway.listSourcePins(tenant, validateProjectId(projectId), signal) : null
        if (signal.aborted) return
        if (scopedSources && !scopedSources.ok) throw new Error(scopedSources.message)
        const verifiedSources = new Map(scopedSources?.value.map(source => [String(source.artifactId), source]))
        for (const track of projection.tracks) {
          for (const clip of track.clips) {
            const source = verifiedSources.get(clip.artifactId)
            const selected = source?.streams.filter(stream => stream.kind === track.type)
            if (!source || source.contentDigest !== clip.contentDigest || selected?.length !== 1
                || selected[0].streamId !== clip.mediaStreamId) {
              throw new Error('Preview does not support the selected source stream for ' + clip.clipId)
            }
          }
        }
        for (const clip of audioClips) {
          const streams = verifiedSources.get(clip.artifactId)?.streams.filter(stream => stream.kind === 'AUDIO')
          if (streams?.length !== 1 || clip.direction !== 'FORWARD'
              || clip.rate.numerator !== clip.rate.denominator) {
            throw new Error('Preview does not support the selected audio stream or mapping for ' + clip.clipId)
          }
        }
        const artifacts = new Set([...videoClips, ...audioClips].map(clip => clip.artifactId))
        const blobs = new Map<string, Blob>()
        for (const id of artifacts) {
          if (!id) throw new Error('A Timeline clip has no source Artifact.')
          const result = await assetGateway.readSourceContent(tenant, projectId, id, signal)
          if (signal.aborted) return
          if (!result.ok) throw new Error(result.message)
          blobs.set(id, result.value)
        }
        const videoUrls = new Map<string, string>()
        const videosReady = videoClips.map(clip => {
          let url = videoUrls['get'](clip.artifactId)
          if (!url) {
            url = URL.createObjectURL(blobs['get'](clip.artifactId)!)
            session.urls.push(url)
            videoUrls.set(clip.artifactId, url)
          }
          const media = document.createElement('video')
          media.preload = 'auto'
          media.playsInline = true
          media.muted = true
          media.addEventListener('seeked', () => { if (!session.playing) draw(session) }, { signal })
          media.addEventListener('error', () => fail(session, new Error('A video source became unavailable.')), { signal })
          session.videos.set(clip.clipId, media)
          return loadVideo(media, url, signal)
        })
        await Promise.all(videosReady)
        if (signal.aborted) return
        if (audioClips.length) {
          const context = new AudioContext({ sampleRate: 48_000 })
          session.audio = context
          const mixer = context.createGain()
          mixer.gain.value = 0.5
          mixer.channelCount = 2
          mixer.channelCountMode = 'explicit'
          const limiter = context.createWaveShaper()
          limiter.curve = new Float32Array([-1, 1])
          const analyser = context.createAnalyser()
          analyser.fftSize = 4096
          mixer.connect(limiter).connect(analyser).connect(context.destination)
          session.mixer = mixer
          session.analyser = analyser
          const buffers = new Map<string, AudioBuffer>()
          for (const clip of audioClips) {
            if (clip.direction !== 'FORWARD' || toSeconds(clip.rate) !== 1) {
              throw new Error('Audio track preview requires forward playback at normal speed.')
            }
            let buffer = buffers['get'](clip.artifactId)
            if (!buffer) {
              buffer = await context.decodeAudioData(await blobs['get'](clip.artifactId)!.arrayBuffer())
              if (signal.aborted) return
              buffers.set(clip.artifactId, buffer)
            }
            const route = audioRouteFor(projection, clip.trackId, clip.clipId)
            session.audioClips.push({ clip, buffer,
              gain: route?.gain ?? 1, mute: route?.mute ?? false, pan: route?.pan ?? 0 })
          }
        }
        session.projection = projection
        session.duration = toSeconds(timelineDuration(projection))
        session.seekInputEnd = nativeSeekInputEnd(session.duration)
        setDuration(session.duration)
        setStatus('READY')
        draw(session, true)
      } catch (error) {
        if (!signal.aborted) fail(session, error)
      }
    }

    if (revisionId && tenantId) void prepare(revisionId, tenantId)
    else setStatus('IDLE')
    return () => {
      session.controller.abort()
      stopPlayback(session)
      for (const media of session.videos.values()) {
        media.removeAttribute('src')
        media.load()
      }
      for (const url of session.urls) URL.revokeObjectURL(url)
      if (session.audio) void session.audio.close()
      current.current = null
    }
  }, [projectId, retry, revisionId, tenantId])

  function start(session: PreviewSession) {
    const now = clock(session)
    session.origin = now - session.cursor
    session.playing = true
    const audio = session.audio
    if (audio && session.mixer) {
      for (const { clip, buffer, gain, mute, pan } of session.audioClips) {
        if (mute) continue
        const clipStart = toSeconds(clip.start)
        const start = Math.max(session.cursor, clipStart)
        const duration = toSeconds(clip.end) - start
        if (duration <= 0) continue
        const source = audio.createBufferSource()
        source.buffer = buffer
        const level = audio.createGain()
        level.gain.value = gain
        const splitter = audio.createChannelSplitter(2)
        const merger = audio.createChannelMerger(2)
        const left = audio.createGain()
        const right = audio.createGain()
        left.gain.value = (1 - pan) / 2
        right.gain.value = (1 + pan) / 2
        source.connect(level).connect(splitter)
        // Mono sources feed both sides; stereo sources preserve their channels.
        splitter.connect(left, 0)
        splitter.connect(right, Math.min(1, buffer.numberOfChannels - 1))
        left.connect(merger, 0, 0)
        right.connect(merger, 0, 1)
        merger.connect(session.mixer)
        source.start(session.origin + start, toSeconds(clip.sourceStart) + start - clipStart, duration)
        session.scheduled.push(source)
      }
    }
    const samples = session.analyser ? new Float32Array(session.analyser.fftSize) : null
    const advance = () => {
      if (!session.playing) return
      session.cursor = Math.min(session.duration, Math.max(0, clock(session) - session.origin))
      publishTime(session.cursor)
      draw(session)
      if (samples && host.current && session.analyser) {
        session.analyser.getFloatTimeDomainData(samples)
        let energy = 0
        for (const sample of samples) energy += sample * sample
        host.current.dataset.audioRms = Math.sqrt(energy / samples.length).toFixed(4)
      }
      if (session.cursor >= session.duration) {
        stopPlayback(session)
        setStatus('PAUSED')
      } else {
        session.frame = requestAnimationFrame(advance)
      }
    }
    setStatus('PLAYING')
    advance()
  }

  async function toggle() {
    const session = current.current
    if (!session?.duration || session.starting) return
    if (session.playing) {
      stopPlayback(session)
      setStatus('PAUSED')
      return
    }
    session.starting = true
    try {
      if (session.audio) await session.audio.resume()
      if (current.current !== session) return
      if (session.cursor >= session.duration) session.cursor = 0
      start(session)
    } catch (error) {
      fail(session, error)
    } finally {
      session.starting = false
    }
  }

  function seek(seconds: number) {
    const session = current.current
    if (!session?.projection) return
    const resume = session.playing
    stopPlayback(session)
    session.cursor = seconds === session.seekInputEnd
      ? session.duration : Math.min(session.duration, Math.max(0, seconds))
    publishTime(session.cursor)
    draw(session, true)
    if (resume) start(session)
  }

  return <section ref={host} aria-label="Timeline media preview" data-preview-status={status}
    data-preview-revision={revisionId} data-preview-time={time.toFixed(3)} data-audio-rms="0">
    <p role="status">{
      status === 'PREPARING' ? 'Preparing Timeline media preview…'
        : status === 'READY' ? 'Preview ready.'
          : status === 'PLAYING' ? 'Playing the current Timeline revision.'
            : status === 'PAUSED' ? 'Paused.' : status === 'FAILED' ? message : 'No preview is available.'
    }</p>
    <canvas ref={canvas} width={320} height={240} hidden={status === 'FAILED'} aria-label="Timeline preview canvas"
      style={{ display: 'block', width: '100%', maxWidth: 480, background: '#000', borderRadius: 4 }} />
    <div style={{ display: 'flex', gap: 8, alignItems: 'center', marginTop: 8 }}>
      <Button type="button" disabled={!duration || !['READY', 'PAUSED', 'PLAYING'].includes(status)}
        onClick={() => void toggle()}>{status === 'PLAYING' ? 'Pause Timeline' : 'Play Timeline'}</Button>
      {/* The native input must be able to reach a canonical end that is off the centisecond grid. */}
      <input aria-label="Timeline preview seek" type="range" min={0} max={seekInputMax(duration)}
        step="any" value={time} onChange={event => seek(Number(event.target.value))} style={{ flex: 1, minWidth: 0 }} />
      <output aria-label="Timeline preview time">{time.toFixed(2)}s / {duration.toFixed(2)}s</output>
    </div>
    {status === 'FAILED' && <Button type="button" onClick={() => setRetry(value => value + 1)}>Retry preview</Button>}
  </section>
}
