import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { SubtitleTrack, SubtitleCue, SubtitleFont } from '@/types'
import { parseSubtitleFile } from '@/utils/subtitleParser'

export const useSubtitleStore = defineStore('subtitle', () => {
  const tracks = ref<SubtitleTrack[]>([])
  const fonts = ref<SubtitleFont[]>([])
  const activeTrackId = ref<string | null>(null)
  const loading = ref(false)
  const error = ref<string | null>(null)

  const activeTrack = computed(() => tracks.value.find(t => t.id === activeTrackId.value) || null)

  async function uploadSubtitleFile(file: File, language: string, burnIn: boolean) {
    loading.value = true
    error.value = null
    try {
      const content = await file.text()
      const ext = file.name.split('.').pop()?.toLowerCase() as 'srt' | 'ass' | 'vtt'
      const format = ext === 'vtt' ? 'vtt' : ext === 'ass' ? 'ass' : 'srt'
      const cues = parseSubtitleFile(content, format)

      const track: SubtitleTrack = {
        id: `sub_${Date.now()}`,
        language,
        label: `${language} (${format.toUpperCase()})`,
        format,
        cues,
        fallbackFontIds: [],
        burnIn,
        externalFileUrl: burnIn ? undefined : URL.createObjectURL(file)
      }
      tracks.value.push(track)
      activeTrackId.value = track.id
    } catch (err: unknown) {
      error.value = `Failed to parse subtitle: ${err instanceof Error ? err.message : String(err)}`
    } finally {
      loading.value = false
    }
  }

  function uploadFont(file: File) {
    const font: SubtitleFont = {
      fontId: `font_${Date.now()}`,
      family: file.name.replace(/\.(ttf|otf)$/i, ''),
      format: file.name.endsWith('.otf') ? 'otf' : 'ttf',
      uploadedBy: 'current-user',
      uploadedAt: new Date().toISOString(),
      glyphCoverage: [],
      fallbackFontIds: [],
      fileSize: file.size,
      checksum: ''
    }
    fonts.value.push(font)
    return font
  }

  function updateCue(trackId: string, cueId: string, updates: Partial<SubtitleCue>) {
    const track = tracks.value.find(t => t.id === trackId)
    if (!track) return
    const cue = track.cues.find(c => c.id === cueId)
    if (cue) Object.assign(cue, updates)
  }

  function removeTrack(trackId: string) {
    const idx = tracks.value.findIndex(t => t.id === trackId)
    if (idx >= 0) tracks.value.splice(idx, 1)
    if (activeTrackId.value === trackId) activeTrackId.value = tracks.value[0]?.id || null
  }

  function setTrackFont(trackId: string, fontId: string) {
    const track = tracks.value.find(t => t.id === trackId)
    if (track) track.fontId = fontId
  }

  function setTrackBurnIn(trackId: string, burnIn: boolean) {
    const track = tracks.value.find(t => t.id === trackId)
    if (track) track.burnIn = burnIn
  }

  function addCue(trackId: string, cue: SubtitleCue) {
    const track = tracks.value.find(t => t.id === trackId)
    if (track) track.cues.push(cue)
  }

  function removeCue(trackId: string, cueId: string) {
    const track = tracks.value.find(t => t.id === trackId)
    if (!track) return
    const idx = track.cues.findIndex(c => c.id === cueId)
    if (idx >= 0) {
      track.cues.splice(idx, 1)
      track.cues.forEach((c, i) => { c.index = i + 1 })
    }
  }

  function setActiveTrack(id: string | null) {
    activeTrackId.value = id
  }

  return { tracks, fonts, activeTrack, activeTrackId, loading, error,
    uploadSubtitleFile, uploadFont, updateCue, removeTrack, setTrackFont, setTrackBurnIn,
    addCue, removeCue, setActiveTrack }
})
