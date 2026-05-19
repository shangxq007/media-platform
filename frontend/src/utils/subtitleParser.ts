export interface SubtitleCue {
  id: string
  index: number
  startTime: number
  endTime: number
  text: string
}

/**
 * Subtitle file parser for SRT, ASS/SSA, and VTT formats.
 */
export function parseSRT(content: string): SubtitleCue[] {
  const cues: SubtitleCue[] = []
  const blocks = content.trim().split(/\n\n+/)

  for (let i = 0; i < blocks.length; i++) {
    const lines = blocks[i].trim().split('\n')
    if (lines.length < 2) continue

    const timeLine = lines.find(l => l.includes(' --> '))
    if (!timeLine) continue

    const [start, end] = timeLine.split(' --> ').map(parseTimeStr)
    const text = lines.filter(l => !l.includes(' --> ') && !/^\d+$/.test(l.trim())).join('\n')

    cues.push({
      id: `cue_${i}`,
      index: i + 1,
      startTime: start,
      endTime: end,
      text: text.trim()
    })
  }
  return cues
}

export function parseVTT(content: string): SubtitleCue[] {
  const cues: SubtitleCue[] = []
  const withoutHeader = content.replace(/^WEBVTT[^\n]*\n*/i, '')
  const blocks = withoutHeader.trim().split(/\n\n+/)

  for (let i = 0; i < blocks.length; i++) {
    const lines = blocks[i].trim().split('\n')
    if (lines.length < 2) continue

    const timeLine = lines.find(l => l.includes(' --> '))
    if (!timeLine) continue

    const [start, end] = timeLine.split(' --> ').map(parseTimeStr)
    const text = lines.filter(l => !l.includes(' --> ') && !/^NOTE/i.test(l.trim())).join('\n')

    cues.push({
      id: `cue_${i}`,
      index: i + 1,
      startTime: start,
      endTime: end,
      text: text.trim()
    })
  }
  return cues
}

export function parseASS(content: string): SubtitleCue[] {
  const cues: SubtitleCue[] = []
  const lines = content.split('\n')
  let inEvents = false
  let formatFields: string[] = []

  for (const line of lines) {
    if (line.startsWith('[Events]')) {
      inEvents = true
      continue
    }
    if (line.startsWith('[') && inEvents) break
    if (!inEvents) continue

    if (line.toLowerCase().startsWith('format:')) {
      formatFields = line.substring('Format:'.length).split(',').map(f => f.trim())
      continue
    }

    if (!line.startsWith('Dialogue:') || formatFields.length === 0) continue

    const textIdx = formatFields.indexOf('Text')
    const startIdx = formatFields.indexOf('Start')
    const endIdx = formatFields.indexOf('End')
    if (textIdx === -1 || startIdx === -1 || endIdx === -1) continue

    const parts = line.substring('Dialogue:'.length).split(',')
    if (parts.length <= Math.max(textIdx, startIdx, endIdx)) continue

    const startTime = parseASSTime(parts[startIdx]?.trim())
    const endTime = parseASSTime(parts[endIdx]?.trim())
    let text = parts.slice(textIdx).join(',').trim()
    // Remove ASS override tags
    text = text.replace(/\{[^}]*\}/g, '').replace(/\\N/g, '\n')

    cues.push({
      id: `cue_${cues.length}`,
      index: cues.length + 1,
      startTime,
      endTime,
      text
    })
  }
  return cues
}

export function parseSubtitleFile(content: string, format: 'srt' | 'ass' | 'vtt'): SubtitleCue[] {
  switch (format) {
    case 'srt': return parseSRT(content)
    case 'vtt': return parseVTT(content)
    case 'ass': return parseASS(content)
    default: return []
  }
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

export function cuesToSRT(cues: SubtitleCue[]): string {
  return cues.map((cue, i) => {
    return `${i + 1}\n${formatSRTTime(cue.startTime)} --> ${formatSRTTime(cue.endTime)}\n${cue.text}\n`
  }).join('\n')
}

function formatSRTTime(seconds: number): string {
  const h = Math.floor(seconds / 3600)
  const m = Math.floor((seconds % 3600) / 60)
  const s = seconds % 60
  return `${String(h).padStart(2, '0')}:${String(m).padStart(2, '0')}:${s.toFixed(3).padStart(6, '0').replace('.', ',')}`
}
