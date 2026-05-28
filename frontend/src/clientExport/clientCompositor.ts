import { activeVideoClipsAt, parseEditorTimeline } from './timelineParser'
import type { ClientExportProgress, ClientExportResult } from './types'
import { detectClientExportCapabilities } from './clientExportCapabilities'

function normalizeSourceUrl(url: string): string {
  if (url.startsWith('file://')) {
    return url
  }
  return url
}

async function loadVideo(url: string): Promise<HTMLVideoElement> {
  const video = document.createElement('video')
  video.crossOrigin = 'anonymous'
  video.muted = true
  video.playsInline = true
  video.preload = 'auto'
  video.src = normalizeSourceUrl(url)
  await new Promise<void>((resolve, reject) => {
    video.onloadeddata = () => resolve()
    video.onerror = () => reject(new Error(`Failed to load video: ${url}`))
  })
  return video
}

async function loadAudio(url: string): Promise<HTMLAudioElement> {
  const audio = document.createElement('audio')
  audio.crossOrigin = 'anonymous'
  audio.preload = 'auto'
  audio.src = normalizeSourceUrl(url)
  await new Promise<void>((resolve, reject) => {
    audio.onloadeddata = () => resolve()
    audio.onerror = () => reject(new Error(`Failed to load audio: ${url}`))
  })
  return audio
}

function drawWatermark(ctx: CanvasRenderingContext2D, width: number, height: number) {
  const text = 'Media Platform — Free'
  ctx.save()
  ctx.globalAlpha = 0.55
  ctx.fillStyle = '#ffffff'
  ctx.font = `bold ${Math.round(height * 0.04)}px sans-serif`
  ctx.textAlign = 'right'
  ctx.textBaseline = 'bottom'
  ctx.fillText(text, width - 16, height - 16)
  ctx.restore()
}

async function seekVideo(video: HTMLVideoElement, timeSec: number): Promise<void> {
  if (Math.abs(video.currentTime - timeSec) < 0.05) {
    return
  }
  video.currentTime = Math.max(0, timeSec)
  await new Promise<void>((resolve) => {
    const onSeeked = () => {
      video.removeEventListener('seeked', onSeeked)
      resolve()
    }
    video.addEventListener('seeked', onSeeked)
  })
}

export type ProgressCallback = (p: ClientExportProgress) => void

export class ClientCompositor {
  async exportTimeline(
    timelineJson: string,
    options: { watermark?: boolean; onProgress?: ProgressCallback; signal?: AbortSignal }
  ): Promise<ClientExportResult> {
    const caps = detectClientExportCapabilities()
    if (!caps.supported) {
      throw new Error(`Browser export not supported: ${caps.reasons.join(', ')}`)
    }

    const timeline = parseEditorTimeline(timelineJson)
    const { width, height, fps, duration } = timeline
    const canvas = document.createElement('canvas')
    canvas.width = width
    canvas.height = height
    const ctx = canvas.getContext('2d')
    if (!ctx) {
      throw new Error('Canvas 2D not available')
    }

    options.onProgress?.({ phase: 'preparing', progress: 0, message: 'Loading media' })

    const videoCache = new Map<string, HTMLVideoElement>()
    const audioElements: HTMLAudioElement[] = []

    for (const clip of timeline.clips) {
      if (!clip.sourceUrl || videoCache.has(clip.id)) {
        continue
      }
      try {
        videoCache.set(clip.id, await loadVideo(clip.sourceUrl))
      } catch (e) {
        console.warn('Skip clip', clip.id, e)
      }
    }

    for (const track of timeline.tracks) {
      if (track.type !== 'audio') continue
      for (const clip of track.clips ?? []) {
        const clipWithUrl = clip as typeof clip & { sourceUrl?: string }
        if (!clipWithUrl.sourceUrl) continue
        try {
          const audio = await loadAudio(clipWithUrl.sourceUrl)
          audio.currentTime = clip.clipStart ?? 0
          audioElements.push(audio)
        } catch (e) {
          console.warn('Skip audio clip', clip.clipId, e)
        }
      }
    }

    const totalFrames = Math.max(1, Math.ceil(duration * fps))
    const canvasStream = canvas.captureStream(fps)

    let combinedStream: MediaStream
    if (audioElements.length > 0) {
      const audioCtx = new AudioContext()
      const destination = audioCtx.createMediaStreamDestination()
      for (const audio of audioElements) {
        const source = audioCtx.createMediaElementSource(audio)
        source.connect(destination)
        source.connect(audioCtx.destination)
      }
      const audioTrack = destination.stream.getAudioTracks()[0]
      combinedStream = new MediaStream([
        ...canvasStream.getVideoTracks(),
        ...(audioTrack ? [audioTrack] : []),
      ])
    } else {
      combinedStream = canvasStream
    }

    const mimeType = MediaRecorder.isTypeSupported('video/webm;codecs=vp9,opus')
      ? 'video/webm;codecs=vp9,opus'
      : MediaRecorder.isTypeSupported('video/webm;codecs=vp9')
        ? 'video/webm;codecs=vp9'
        : 'video/webm'
    const recorder = new MediaRecorder(combinedStream, { mimeType, videoBitsPerSecond: 4_000_000 })
    const chunks: Blob[] = []

    const recorded = new Promise<Blob>((resolve, reject) => {
      recorder.ondataavailable = (ev) => {
        if (ev.data.size > 0) {
          chunks.push(ev.data)
        }
      }
      recorder.onerror = () => reject(new Error('MediaRecorder failed'))
      recorder.onstop = () => resolve(new Blob(chunks, { type: mimeType }))
    })

    recorder.start(100)

    for (const audio of audioElements) {
      audio.currentTime = 0
      audio.play().catch(() => {})
    }

    for (let frame = 0; frame < totalFrames; frame++) {
      if (options.signal?.aborted) {
        recorder.stop()
        for (const audio of audioElements) audio.pause()
        throw new DOMException('Export cancelled', 'AbortError')
      }
      const t = frame / fps
      ctx.fillStyle = '#000'
      ctx.fillRect(0, 0, width, height)

      const active = activeVideoClipsAt(timeline, t)
      for (const { clip, trackClip } of active) {
        const video = videoCache.get(clip.id)
        if (!video) {
          continue
        }
        const localT = t - (timeline.tracks.flatMap((tr) => tr.clips ?? []).find((tc) => tc.clipId === clip.id)?.start ?? 0) + trackClip.clipStart
        await seekVideo(video, Math.max(0, localT))
        const vw = video.videoWidth || width
        const vh = video.videoHeight || height
        const scale = Math.min(width / vw, height / vh)
        const dw = vw * scale
        const dh = vh * scale
        const dx = (width - dw) / 2
        const dy = (height - dh) / 2
        ctx.drawImage(video, dx, dy, dw, dh)
      }

      if (options.watermark !== false) {
        drawWatermark(ctx, width, height)
      }

      const pct = Math.round((frame / totalFrames) * 100)
      options.onProgress?.({
        phase: 'rendering',
        progress: pct,
        message: `Frame ${frame + 1}/${totalFrames}`,
      })

      await new Promise((r) => setTimeout(r, 1000 / fps))
    }

    options.onProgress?.({ phase: 'encoding', progress: 99, message: 'Finalizing' })
    recorder.stop()
    for (const audio of audioElements) audio.pause()
    const blob = await recorded
    options.onProgress?.({ phase: 'done', progress: 100 })

    return {
      blob,
      mimeType,
      durationSeconds: duration,
    }
  }

  /** Draw a single preview frame (for Program Monitor). */
  async drawPreviewFrame(
    timelineJson: string,
    canvas: HTMLCanvasElement,
    timeSec: number,
    watermark = true
  ): Promise<void> {
    const timeline = parseEditorTimeline(timelineJson)
    const ctx = canvas.getContext('2d')
    if (!ctx) {
      return
    }
    const { width, height } = timeline
    canvas.width = width
    canvas.height = height
    ctx.fillStyle = '#000'
    ctx.fillRect(0, 0, width, height)

    const active = activeVideoClipsAt(timeline, timeSec)
    for (const { clip, trackClip } of active) {
      if (!clip.sourceUrl) {
        continue
      }
      const video = await loadVideo(clip.sourceUrl)
      const trackStart =
        timeline.tracks
          .flatMap((tr) => tr.clips ?? [])
          .find((tc) => tc.clipId === clip.id)?.start ?? 0
      await seekVideo(video, Math.max(0, timeSec - trackStart + trackClip.clipStart))
      const vw = video.videoWidth || width
      const vh = video.videoHeight || height
      const scale = Math.min(width / vw, height / vh)
      const dw = vw * scale
      const dh = vh * scale
      ctx.drawImage(video, (width - dw) / 2, (height - dh) / 2, dw, dh)
    }
    if (watermark) {
      drawWatermark(ctx, width, height)
    }
  }
}
