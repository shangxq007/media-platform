import { useCallback, useEffect, useRef, useState } from 'react'
import { FormalRenderAPI } from '../../api/formal-render'
import { versionlessApi } from '../../api/app/versionless-api'
import { formatRational, toSeconds, type Rational } from './trim'
import { activeClipAt, clampTimelineTime, sourceTimeAt, timelineDuration } from './timeline-preview'
import type { ProjectedClip, TimelineProjection } from './timeline-projection'
import { projectSnapshotJson } from './timeline-projection'
import type { CanonicalHeadReference } from './gateways'

type PreviewStatus = 'IDLE' | 'PREPARING' | 'READY' | 'PLAYING' | 'PAUSED' | 'FAILED'
interface SourceMedia { readonly video: HTMLVideoElement; readonly url: string }

const ZERO: Rational = { numerator: 0n, denominator: 1n }

/** Real Timeline media preview: source artifacts are sequenced on a canvas by canonical Timeline time. */
export function TimelineMediaPreview({ head, tenantId, projectId }: {
  head: CanonicalHeadReference | null
  tenantId: string | null
  projectId: string
}) {
  const canvas = useRef<HTMLCanvasElement | null>(null)
  const host = useRef<HTMLElement | null>(null)
  const sources = useRef(new Map<string, SourceMedia>())
  const audio = useRef<{ context: AudioContext; analyser: AnalyserNode } | null>(null)
  const connected = useRef(new WeakSet<HTMLVideoElement>())
  const generation = useRef(0)
  const raf = useRef<number | null>(null)
  const startedAt = useRef(0)
  const [status, setStatus] = useState<PreviewStatus>('IDLE')
  const [projection, setProjection] = useState<TimelineProjection | null>(null)
  const [duration, setDuration] = useState<number>(0)
  const [time, setTime] = useState(0)
  const [message, setMessage] = useState('')
  const playingRef = useRef(false)
  const playing = status === 'PLAYING'

  const stopVideos = useCallback(() => { for (const source of sources.current.values()) source.video.pause() }, [])

  const draw = useCallback((seconds: number) => {
    const target = canvas.current
    const current = projection
    if (!target || !current) return
    const context = target.getContext('2d')
    if (!context) return
    context.fillStyle = '#000'
    context.fillRect(0, 0, target.width, target.height)
    const timelineTime = { numerator: BigInt(Math.round(seconds * 1000)), denominator: 1000n }
    const clip = activeClipAt(current, timelineTime)
    if (host.current) host.current.dataset.activeClip = clip?.clipId ?? ''
    if (!clip) return
    const media = sources.current.get(clip.artifactId)
    if (!media || media.video.readyState < 2) return
    const sourceSeconds = toSeconds(sourceTimeAt(clip, timelineTime))
    if (Math.abs(media.video.currentTime - sourceSeconds) > 0.08) {
      try { media.video.currentTime = Math.max(0, sourceSeconds) } catch { /* seeking is retried next frame */ }
    }
    context.drawImage(media.video, 0, 0, target.width, target.height)
    if (playingRef.current) {
      const promise = media.video.play()
      if (promise) void promise.catch(() => undefined)
    }
    if (audio.current && !connected.current.has(media.video)) {
      try {
        const node = audio.current.context.createMediaElementSource(media.video)
        node.connect(audio.current.analyser)
        connected.current.add(media.video)
      } catch { /* element already connected or audio unavailable */ }
    }
  }, [projection])

  const loadRevision = useCallback(async () => {
    const current = head
    if (!current || !tenantId) { setStatus('IDLE'); setProjection(null); return }
    const owned = ++generation.current
    if (raf.current !== null) cancelAnimationFrame(raf.current)
    raf.current = null
    stopVideos()
    setStatus('PREPARING'); setMessage(''); setTime(0)
    try {
      const snapshot = await FormalRenderAPI.snapshot(projectId, current.revisionId)
      if (owned !== generation.current) return
      const nextProjection = projectSnapshotJson(snapshot.canonicalTimelineJson)
      setProjection(nextProjection)
      const seconds = toSeconds(timelineDuration(nextProjection))
      setDuration(seconds)
      const artifacts = [...new Set(nextProjection.clips.map(clip => clip.artifactId).filter(Boolean))]
      for (const artifactId of artifacts) {
        if (owned !== generation.current) return
        if (sources.current.has(artifactId)) continue
        const response = await versionlessApi.get(
          `/tenants/${encodeURIComponent(tenantId)}/projects/${encodeURIComponent(projectId)}/source-artifacts/${encodeURIComponent(artifactId)}/content`,
          { responseType: 'blob' })
        if (owned !== generation.current) return
        const url = URL.createObjectURL(response.data as Blob)
        const video = document.createElement('video')
        video.preload = 'auto'; video.playsInline = true; video.muted = false
        video.src = url
        await new Promise<void>((resolve, reject) => {
          video.onloadedmetadata = () => resolve()
          video.onerror = () => reject(new Error(`Source media ${artifactId} could not be decoded.`))
        })
        if (owned !== generation.current) { URL.revokeObjectURL(url); return }
        sources.current.set(artifactId, { video, url })
      }
      if (owned !== generation.current) return
      setStatus('READY')
      draw(0)
    } catch (error) {
      if (owned !== generation.current) return
      setStatus('FAILED')
      setMessage(error instanceof Error ? error.message : 'Timeline preview could not be prepared.')
    }
  }, [draw, head, projectId, stopVideos, tenantId])

  useEffect(() => {
    void loadRevision()
    return () => { generation.current += 1; if (raf.current !== null) cancelAnimationFrame(raf.current) }
  }, [loadRevision])

  useEffect(() => () => {
    for (const source of sources.current.values()) { source.video.pause(); URL.revokeObjectURL(source.url) }
    sources.current.clear()
    if (audio.current) void audio.current.context.close()
  }, [])

  const pause = useCallback(() => {
    if (raf.current !== null) cancelAnimationFrame(raf.current)
    raf.current = null
    stopVideos()
    playingRef.current = false
    setStatus(current => current === 'PLAYING' ? 'PAUSED' : current)
  }, [stopVideos])

  const loop = useCallback(() => {
    const t = Math.min(duration, (performance.now() - startedAt.current) / 1000)
    setTime(t)
    draw(t)
    if (host.current && audio.current) {
      const buffer = new Uint8Array(audio.current.analyser.fftSize)
      audio.current.analyser.getByteTimeDomainData(buffer)
      let sum = 0
      for (const value of buffer) { const sample = (value - 128) / 128; sum += sample * sample }
      host.current.dataset.audioRms = Math.sqrt(sum / buffer.length).toFixed(4)
    }
    if (t >= duration) { pause(); setTime(duration); return }
    raf.current = requestAnimationFrame(loop)
  }, [draw, duration, pause])

  const play = useCallback(async () => {
    if (status !== 'READY' && status !== 'PAUSED') return
    if (!audio.current) {
      try {
        const context = new AudioContext()
        const analyser = context.createAnalyser()
        analyser.fftSize = 256
        analyser.connect(context.destination)
        audio.current = { context, analyser }
        await context.resume()
      } catch { audio.current = null }
    } else if (audio.current.context.state === 'suspended') {
      await audio.current.context.resume()
    }
    const start = time >= duration ? 0 : time
    if (start === 0) setTime(0)
    startedAt.current = performance.now() - start * 1000
    playingRef.current = true
    setStatus('PLAYING')
    draw(start)
    raf.current = requestAnimationFrame(loop)
  }, [draw, duration, loop, status, time])

  const seek = useCallback((seconds: number) => {
    const clamped = Math.min(duration, Math.max(0, seconds))
    setTime(clamped)
    if (playing) startedAt.current = performance.now() - clamped * 1000
    draw(clamped)
  }, [draw, duration, playing])

  const toggle = () => { if (playing) pause(); else void play() }
  const revision = head?.revisionId ?? ''

  return <section ref={host} aria-label="Timeline media preview" data-preview-status={status}
    data-preview-revision={revision} data-preview-time={time.toFixed(3)} data-audio-rms="0">
    <h3>Timeline media preview</h3>
    <p role="status">{
      status === 'PREPARING' ? 'Preparing Timeline media preview…'
        : status === 'READY' ? `Preview ready for revision ${revision}.`
          : status === 'PLAYING' ? 'Playing the current Timeline revision.'
            : status === 'PAUSED' ? 'Paused.' : status === 'FAILED' ? message
              : 'Preview has not been prepared.'
    }</p>
    <canvas ref={canvas} width={320} height={240} aria-label="Timeline preview canvas"
      style={{ display: 'block', width: '100%', maxWidth: 480, background: '#000', borderRadius: 4 }} />
    <div style={{ display: 'flex', gap: 8, alignItems: 'center', marginTop: 8 }}>
      <button type="button" disabled={status !== 'READY' && status !== 'PAUSED' && status !== 'PLAYING'}
        onClick={toggle}>{playing ? 'Pause Timeline' : 'Play Timeline'}</button>
      <input aria-label="Timeline preview seek" type="range" min={0} max={Math.max(0.01, duration)}
        step={0.01} value={time} onChange={event => seek(Number(event.target.value))} style={{ flex: 1 }} />
      <output aria-label="Timeline preview time">{time.toFixed(2)}s / {duration.toFixed(2)}s</output>
    </div>
    {status === 'FAILED' ? <button type="button" onClick={() => void loadRevision()}>Retry preview</button> : null}
    <p style={{ fontSize: 12, color: '#9ca3af' }}>
      Preview is derived from revision {revision || '—'} using the canonical snapshot and the
      project's source artifacts. It does not write the Timeline.
    </p>
  </section>
}
