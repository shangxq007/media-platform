import type { MediaProbeResult } from '@/types'

function probeVideo(file: File): Promise<MediaProbeResult> {
  return new Promise((resolve) => {
    const url = URL.createObjectURL(file)
    const video = document.createElement('video')
    video.preload = 'metadata'
    video.muted = true

    const cleanup = () => {
      video.removeAttribute('src')
      video.load()
      URL.revokeObjectURL(url)
    }

    video.onloadedmetadata = () => {
      cleanup()
      resolve({
        duration: video.duration,
        width: video.videoWidth,
        height: video.videoHeight,
      })
    }

    video.onerror = () => {
      cleanup()
      resolve({ error: 'Failed to probe video metadata' })
    }

    video.src = url

    setTimeout(() => {
      if (!video.readyState) {
        cleanup()
        resolve({ error: 'Video probe timed out' })
      }
    }, 10000)
  })
}

function probeAudio(file: File): Promise<MediaProbeResult> {
  return new Promise((resolve) => {
    const url = URL.createObjectURL(file)
    const audio = document.createElement('audio')
    audio.preload = 'metadata'

    const cleanup = () => {
      audio.removeAttribute('src')
      audio.load()
      URL.revokeObjectURL(url)
    }

    audio.onloadedmetadata = () => {
      cleanup()
      resolve({ duration: audio.duration })
    }

    audio.onerror = () => {
      cleanup()
      resolve({ error: 'Failed to probe audio metadata' })
    }

    audio.src = url

    setTimeout(() => {
      if (!audio.readyState) {
        cleanup()
        resolve({ error: 'Audio probe timed out' })
      }
    }, 10000)
  })
}

function probeImage(file: File): Promise<MediaProbeResult> {
  return new Promise((resolve) => {
    const url = URL.createObjectURL(file)
    const img = new Image()

    img.onload = () => {
      URL.revokeObjectURL(url)
      resolve({ width: img.naturalWidth, height: img.naturalHeight })
    }

    img.onerror = () => {
      URL.revokeObjectURL(url)
      resolve({ error: 'Failed to probe image metadata' })
    }

    img.src = url

    setTimeout(() => {
      if (!img.complete) {
        URL.revokeObjectURL(url)
        resolve({ error: 'Image probe timed out' })
      }
    }, 10000)
  })
}

function probeSubtitle(file: File): Promise<MediaProbeResult> {
  return new Promise((resolve) => {
    const reader = new FileReader()
    reader.onload = () => {
      const content = reader.result as string
      const ext = file.name.split('.').pop()?.toLowerCase()
      let cueCount = 0
      let estimatedDuration = 0

      if (ext === 'srt') {
        const blocks = content.trim().split(/\n\n+/)
        for (const block of blocks) {
          const lines = block.trim().split('\n')
          const timeLine = lines.find(l => l.includes(' --> '))
          if (timeLine) {
            cueCount++
            const parts = timeLine.split(' --> ')
            if (parts.length === 2) {
              const end = parseTimeStr(parts[1].trim())
              if (end > estimatedDuration) estimatedDuration = end
            }
          }
        }
      } else if (ext === 'vtt') {
        const withoutHeader = content.replace(/^WEBVTT[^\n]*\n*/i, '')
        const blocks = withoutHeader.trim().split(/\n\n+/)
        for (const block of blocks) {
          const lines = block.trim().split('\n')
          const timeLine = lines.find(l => l.includes(' --> '))
          if (timeLine) {
            cueCount++
            const parts = timeLine.split(' --> ')
            if (parts.length === 2) {
              const end = parseTimeStr(parts[1].trim())
              if (end > estimatedDuration) estimatedDuration = end
            }
          }
        }
      } else if (ext === 'ass') {
        const lines = content.split('\n')
        for (const line of lines) {
          if (line.startsWith('Dialogue:')) {
            cueCount++
            const parts = line.substring('Dialogue:'.length).split(',')
            if (parts.length >= 3) {
              const end = parseASSTime(parts[2]?.trim() || '')
              if (end > estimatedDuration) estimatedDuration = end
            }
          }
        }
      }

      resolve({ cueCount, duration: estimatedDuration || undefined })
    }

    reader.onerror = () => {
      resolve({ error: 'Failed to read subtitle file' })
    }

    reader.readAsText(file)
  })
}

function parseTimeStr(timeStr: string): number {
  const cleaned = timeStr.trim().replace(',', '.')
  const parts = cleaned.split(':')
  if (parts.length === 3) {
    return parseFloat(parts[0]) * 3600 + parseFloat(parts[1]) * 60 + parseFloat(parts[2])
  }
  return 0
}

function parseASSTime(timeStr: string): number {
  const parts = timeStr.split(':')
  if (parts.length === 3) {
    return parseFloat(parts[0]) * 3600 + parseFloat(parts[1]) * 60 + parseFloat(parts[2])
  }
  return 0
}

export async function probeMediaFile(file: File): Promise<MediaProbeResult> {
  if (file.type.startsWith('video/')) {
    return probeVideo(file)
  }
  if (file.type.startsWith('audio/')) {
    return probeAudio(file)
  }
  if (file.type.startsWith('image/')) {
    return probeImage(file)
  }

  const ext = file.name.split('.').pop()?.toLowerCase()
  if (ext === 'srt' || ext === 'ass' || ext === 'vtt') {
    return probeSubtitle(file)
  }

  return {}
}

export function getFileType(file: File): 'video' | 'audio' | 'image' | 'subtitle' | 'text' {
  if (file.type.startsWith('video/')) return 'video'
  if (file.type.startsWith('audio/')) return 'audio'
  if (file.type.startsWith('image/')) return 'image'
  const ext = file.name.split('.').pop()?.toLowerCase()
  if (ext === 'srt' || ext === 'ass' || ext === 'vtt') return 'subtitle'
  if (ext === 'json') return 'text'
  return 'text'
}
