import { useEffect, useRef, useState } from 'react'
import { Button } from '../../components/design-system'
import { FormalRenderAPI } from '../../api/formal-render'
import { assetGateway } from '../../api/app/asset.gateway'
import { toSeconds } from './trim'
import { activeClipsAt, sourceTimeAt, timelineDuration, transitionForIncoming } from './timeline-preview'
import { audioRouteFor, projectSnapshotJson, type ProjectedClip, type TimelineProjection } from './timeline-projection'
import type { CanonicalHeadReference } from './gateways'

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
  duration: number
  cursor: number
  origin: number
  playing: boolean
  starting: boolean
  frame?: number
}

const clock = (session: PreviewSession) => session.audio?.currentTime ?? performance.now() / 1000
const rationalTime = (seconds: number) => ({ numerator: BigInt(Math.floor(seconds * 1_000_000)), denominator: 1_000_000n })

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
export function TimelineMediaPreview({ head, tenantId, projectId, onTime }: {
  head: CanonicalHeadReference | null
  tenantId: string | null
  projectId: string
  onTime?: (time: number) => void
}) {
  const canvas = useRef<HTMLCanvasElement>(null)
  const host = useRef<HTMLElement>(null)
  const current = useRef<PreviewSession | null>(null)
  const timeCallback = useRef(onTime)
  timeCallback.current = onTime
  const [status, setStatus] = useState<PreviewStatus>('IDLE')
  const [duration, setDuration] = useState(0)
  const [time, setTime] = useState(0)
  const [message, setMessage] = useState('')
  const [retry, setRetry] = useState(0)
  const revisionId = head?.revisionId

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
    const context = target?.getContext('2d')
    if (!target || !context) return
    const time = rationalTime(session.cursor)
    const clips = session.projection ? activeClipsAt(session.projection, time) : []
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
      const transition = session.projection
        ? transitionForIncoming(session.projection, clip.clipId) : null
      const transitionAlpha = transition
        ? Math.min(1, Math.max(0, (now - clipStart) / toSeconds(transition.duration))) : 1
      const t = clip.transform
      const sourceWidth = media.videoWidth || 320
      const sourceHeight = media.videoHeight || 240
      const sx = sourceWidth * t.cropLeft
      const sy = sourceHeight * t.cropTop
      const sw = Math.max(1, sourceWidth * (t.cropRight - t.cropLeft))
      const sh = Math.max(1, sourceHeight * (t.cropBottom - t.cropTop))
      const dw = target.width * t.scaleX
      const dh = target.height * t.scaleY
      const radians = t.rotationDegrees * Math.PI / 180
      const rotatedWidth = Math.abs(dw * Math.cos(radians)) + Math.abs(dh * Math.sin(radians))
      const rotatedHeight = Math.abs(dw * Math.sin(radians)) + Math.abs(dh * Math.cos(radians))
      context.save()
      context.globalAlpha = t.opacity * fadeIn * fadeOut * transitionAlpha
      if (effect && (effect.brightness !== 0 || effect.contrast !== 1
          || effect.saturation !== 1 || effect.grayscale)) {
        context.filter = `brightness(${1 + effect.brightness}) contrast(${effect.contrast}) `
          + `saturate(${effect.saturation})${effect.grayscale ? ' grayscale(1)' : ''}`
      }
      // Position is the top-left of the rotated output frame, matching BMF rotate+overlay.
      context.translate(t.positionX + rotatedWidth / 2, t.positionY + rotatedHeight / 2)
      context.rotate(radians)
      context.drawImage(media, sx, sy, sw, sh, -dw / 2, -dh / 2, dw, dh)
      context.restore()
    }
  }

  useEffect(() => {
    const session: PreviewSession = {
      controller: new AbortController(), urls: [], videos: new Map(), audioClips: [], scheduled: [],
      duration: 0, cursor: 0, origin: 0, playing: false, starting: false,
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
        const audioTracks = projection.tracks.filter(track => track.type === 'AUDIO')
        const videoTracks = projection.tracks.filter(track => track.type === 'VIDEO')
        const videoClips = videoTracks.flatMap(track => track.clips)
        // Embedded video audio participates only through an explicit canonical
        // AudioRoute. A second video track never implicitly adds its audio.
        const routedVideoClips = videoClips.filter(clip => audioRouteFor(projection, clip.trackId, clip.clipId) !== null)
        // Embedded video audio is opt-in through an explicit canonical AudioRoute.
        // No video-track-count heuristic silently adds or drops audio.
        const audioClips = [...audioTracks.flatMap(track => track.clips), ...routedVideoClips]
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
    session.cursor = Math.min(session.duration, Math.max(0, seconds))
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
    <canvas ref={canvas} width={320} height={240} aria-label="Timeline preview canvas"
      style={{ display: 'block', width: '100%', maxWidth: 480, background: '#000', borderRadius: 4 }} />
    <div style={{ display: 'flex', gap: 8, alignItems: 'center', marginTop: 8 }}>
      <Button type="button" disabled={!duration || !['READY', 'PAUSED', 'PLAYING'].includes(status)}
        onClick={() => void toggle()}>{status === 'PLAYING' ? 'Pause Timeline' : 'Play Timeline'}</Button>
      <input aria-label="Timeline preview seek" type="range" min={0} max={Math.max(0.01, duration)}
        step={0.01} value={time} onChange={event => seek(Number(event.target.value))} style={{ flex: 1, minWidth: 0 }} />
      <output aria-label="Timeline preview time">{time.toFixed(2)}s / {duration.toFixed(2)}s</output>
    </div>
    {status === 'FAILED' && <Button type="button" onClick={() => setRetry(value => value + 1)}>Retry preview</Button>}
  </section>
}
