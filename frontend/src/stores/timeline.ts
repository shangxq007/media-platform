import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { Track, TrackClip, TimelineState, Clip } from '@/types'
import type { DemoProject } from '@/utils/demoProjectFactory'
import { createDemoTimeline } from '@/utils/demoTimelineFactory'

export const useTimelineStore = defineStore('timeline', () => {
  const state = ref<TimelineState>({
    tracks: [],
    duration: 60,
    currentTime: 0,
    zoom: 1,
    playing: false
  })

  const clips = ref<Clip[]>([])

  const trackCount = computed(() => state.value.tracks.length)

  function addTrack(name: string, type: 'video' | 'audio' | 'text' | 'image' | 'subtitle'): Track {
    const track: Track = {
      id: `track_${Date.now()}`,
      name,
      type,
      clips: [],
      muted: false,
      locked: false
    }
    state.value.tracks.push(track)
    return track
  }

  function removeTrack(trackId: string) {
    const idx = state.value.tracks.findIndex(t => t.id === trackId)
    if (idx >= 0) state.value.tracks.splice(idx, 1)
  }

  function addClipToTrack(trackId: string, clip: Clip, start: number): TrackClip | null {
    const track = state.value.tracks.find(t => t.id === trackId)
    if (!track || track.locked) return null

    const trackClip: TrackClip = {
      id: `tc_${Date.now()}`,
      clipId: clip.id,
      trackId,
      start,
      duration: clip.endTime - clip.startTime,
      clipStart: clip.startTime,
      clipEnd: clip.endTime
    }
    track.clips.push(trackClip)
    clips.value.push(clip)
    return trackClip
  }

  function removeClipFromTrack(trackId: string, trackClipId: string) {
    const track = state.value.tracks.find(t => t.id === trackId)
    if (!track) return
    const idx = track.clips.findIndex(c => c.id === trackClipId)
    if (idx >= 0) track.clips.splice(idx, 1)
  }

  function moveClip(trackId: string, clipId: string, newStart: number) {
    const track = state.value.tracks.find(t => t.id === trackId)
    if (!track || track.locked) return
    const tc = track.clips.find(c => c.id === clipId)
    if (tc) tc.start = Math.max(0, newStart)
  }

  function resizeClip(trackId: string, clipId: string, newDuration: number) {
    const track = state.value.tracks.find(t => t.id === trackId)
    if (!track || track.locked) return
    const tc = track.clips.find(c => c.id === clipId)
    if (tc) tc.duration = Math.max(0.1, newDuration)
  }

  function setCurrentTime(time: number) {
    state.value.currentTime = Math.max(0, Math.min(time, state.value.duration))
  }

  function togglePlayback() {
    state.value.playing = !state.value.playing
  }

  function setZoom(zoom: number) {
    state.value.zoom = Math.max(0.1, Math.min(10, zoom))
  }

  function loadFromJSON(json: TimelineState) {
    state.value = json
  }

  function toJSON(): any {
    const json = JSON.parse(JSON.stringify(state.value));
    // Add schema version
    json.schemaVersion = '2.0.0';
    // Add subtitle tracks from subtitle store
    // (subtitle tracks are managed separately but included in OTIO export)
    return json;
  }

  function loadDemoProject(project: DemoProject) {
    const timeline = createDemoTimeline(project)
    state.value.tracks = timeline.tracks
    state.value.duration = timeline.duration
    state.value.currentTime = 0
    state.value.playing = false
    clips.value = timeline.clips
  }

  function getOTIOExport(): any {
    const json = toJSON();
    for (const track of json.tracks || []) {
      for (const clip of track.children || []) {
        if (clip.effects && clip.effects.length > 0) {
          for (const effect of clip.effects) {
            if (!effect.effectKey && effect.effectId) {
              effect.effectKey = effect.effectId;
              delete effect.effectId;
            }
            if (!effect.providerPreference && effect.provider) {
              effect.providerPreference = [effect.provider];
              delete effect.provider;
            }
          }
        }
      }
    }
    return json;
  }

  const selectedClipId = ref<string | null>(null)
  const patchHighlightClipIds = ref<string[]>([])

  function setPatchHighlightClipIds(ids: string[]) {
    patchHighlightClipIds.value = [...new Set(ids.filter(Boolean))]
    patchHighlightIndex.value = 0
  }

  function clearPatchHighlightClipIds() {
    patchHighlightClipIds.value = []
  }

  function isClipPatchHighlighted(trackClipId: string): boolean {
    return patchHighlightClipIds.value.includes(trackClipId)
  }

  const scrollToTrackClipId = ref<string | null>(null)
  const patchHighlightIndex = ref(0)

  function focusTrackClip(trackClipId: string): boolean {
    const tc = findTrackClipInState(trackClipId)
    if (!tc) {
      return false
    }
    selectedClipId.value = trackClipId
    state.value.currentTime = Math.max(0, tc.start)
    scrollToTrackClipId.value = trackClipId
    return true
  }

  function focusFirstPatchHighlightClip(): boolean {
    patchHighlightIndex.value = 0
    const first = patchHighlightClipIds.value[0]
    if (!first) {
      return false
    }
    return focusTrackClip(first)
  }

  function focusHighlightAtIndex(index: number): boolean {
    const ids = patchHighlightClipIds.value
    if (!ids.length) {
      return false
    }
    const i = ((index % ids.length) + ids.length) % ids.length
    patchHighlightIndex.value = i
    return focusTrackClip(ids[i])
  }

  function nextPatchHighlight(): boolean {
    return focusHighlightAtIndex(patchHighlightIndex.value + 1)
  }

  function prevPatchHighlight(): boolean {
    return focusHighlightAtIndex(patchHighlightIndex.value - 1)
  }

  function findTrackClipInState(trackClipId: string) {
    for (const track of state.value.tracks) {
      const tc = track.clips.find(c => c.id === trackClipId)
      if (tc) {
        return tc
      }
    }
    return null
  }

  function clearScrollToTrackClipRequest() {
    scrollToTrackClipId.value = null
  }

  const selectedTrackClip = computed(() => {
    if (!selectedClipId.value) return null
    for (const track of state.value.tracks) {
      const tc = track.clips.find(c => c.id === selectedClipId.value)
      if (tc) return { trackClip: tc, track }
    }
    return null
  })

  const selectedClip = computed(() => {
    const sel = selectedTrackClip.value
    if (!sel) return null
    return clips.value.find(c => c.id === sel.trackClip.clipId) || null
  })

  function getTrackEnd(trackType: string): number {
    const track = state.value.tracks.find(t => t.type === trackType)
    if (!track || track.clips.length === 0) return 0
    return Math.max(...track.clips.map(tc => tc.start + tc.duration))
  }

  function createTrackIfNeeded(type: string): Track {
    const existing = state.value.tracks.find(t => t.type === type)
    if (existing) return existing
    const typeLabels: Record<string, string> = {
      video: 'Video',
      audio: 'Audio',
      text: 'Text',
      image: 'Image',
      subtitle: 'Subtitle',
    }
    const count = state.value.tracks.filter(t => t.type === type).length + 1
    return addTrack(`${typeLabels[type] || type} ${count}`, type as Track['type'])
  }

  function insertClipAtPlayhead(clip: Clip, trackType: string) {
    const track = createTrackIfNeeded(trackType)
    const tc = addClipToTrack(track.id, clip, state.value.currentTime)
    return tc
  }

  function appendClip(clip: Clip, trackType: string) {
    const track = createTrackIfNeeded(trackType)
    const end = getTrackEnd(trackType)
    const tc = addClipToTrack(track.id, clip, end)
    return tc
  }

  function selectClip(clipId: string) {
    selectedClipId.value = clipId
  }

  function deselectClip() {
    selectedClipId.value = null
  }

  function deleteSelectedClip() {
    if (!selectedClipId.value) return
    const sel = selectedTrackClip.value
    if (!sel) return
    removeClipFromTrack(sel.track.id, selectedClipId.value)
    const clip = clips.value.find(c => c.id === sel.trackClip.clipId)
    if (clip) {
      const idx = clips.value.findIndex(c => c.id === clip.id)
      if (idx >= 0) clips.value.splice(idx, 1)
    }
    selectedClipId.value = null
  }

  function duplicateSelectedClip() {
    if (!selectedClipId.value) return
    const sel = selectedTrackClip.value
    if (!sel) return
    const originalClip = clips.value.find(c => c.id === sel.trackClip.clipId)
    if (!originalClip) return

    const newClip: Clip = {
      ...originalClip,
      id: `clip_${Date.now()}_${Math.random().toString(36).slice(2, 8)}`,
      name: `${originalClip.name} (copy)`,
    }
    clips.value.push(newClip)

    const newTc: TrackClip = {
      ...sel.trackClip,
      id: `tc_${Date.now()}`,
      clipId: newClip.id,
      start: sel.trackClip.start + sel.trackClip.duration,
    }
    sel.track.clips.push(newTc)
    selectedClipId.value = newTc.id
  }

  function updateClipMetadata(clipId: string, updates: Partial<Clip>) {
    const clip = clips.value.find(c => c.id === clipId)
    if (clip) Object.assign(clip, updates)
  }

  function updateClipName(clipId: string, name: string) {
    const clip = clips.value.find(c => c.id === clipId)
    if (clip) clip.name = name
  }

  function updateTrackClipTime(trackClipId: string, trackId: string, start: number, duration: number) {
    const track = state.value.tracks.find(t => t.id === trackId)
    if (!track) return
    const tc = track.clips.find(c => c.id === trackClipId)
    if (tc) {
      tc.start = Math.max(0, start)
      tc.duration = Math.max(0.1, duration)
    }
  }

  function addEffectToClip(trackClipId: string, effect: import('@/types').ClipEffect) {
    for (const track of state.value.tracks) {
      const tc = track.clips.find(c => c.id === trackClipId)
      if (tc) {
        if (!tc.effects) tc.effects = []
        tc.effects.push(effect)
        return true
      }
    }
    return false
  }

  function removeEffectFromClip(trackClipId: string, effectId: string) {
    for (const track of state.value.tracks) {
      const tc = track.clips.find(c => c.id === trackClipId)
      if (tc && tc.effects) {
        const idx = tc.effects.findIndex(e => e.id === effectId)
        if (idx >= 0) {
          tc.effects.splice(idx, 1)
          return true
        }
      }
    }
    return false
  }

  function updateEffectParams(trackClipId: string, effectId: string, params: Record<string, unknown>) {
    for (const track of state.value.tracks) {
      const tc = track.clips.find(c => c.id === trackClipId)
      if (tc && tc.effects) {
        const ce = tc.effects.find(e => e.id === effectId)
        if (ce) {
          Object.assign(ce.parameters, params)
          return true
        }
      }
    }
    return false
  }

  function reorderEffects(trackClipId: string, effectIds: string[]) {
    for (const track of state.value.tracks) {
      const tc = track.clips.find(c => c.id === trackClipId)
      if (tc && tc.effects) {
        const reordered: import('@/types').ClipEffect[] = []
        for (const id of effectIds) {
          const found = tc.effects.find(e => e.id === id)
          if (found) reordered.push(found)
        }
        tc.effects = reordered
        return true
      }
    }
    return false
  }

  return {
    state, clips, trackCount, selectedClipId, selectedTrackClip, selectedClip,
    patchHighlightClipIds,
    setPatchHighlightClipIds,
    clearPatchHighlightClipIds,
    isClipPatchHighlighted,
    scrollToTrackClipId,
    focusTrackClip,
    focusFirstPatchHighlightClip,
    patchHighlightIndex,
    focusHighlightAtIndex,
    nextPatchHighlight,
    prevPatchHighlight,
    clearScrollToTrackClipRequest,
    addTrack, removeTrack, addClipToTrack, removeClipFromTrack,
    moveClip, resizeClip, setCurrentTime, togglePlayback, setZoom,
    loadFromJSON, toJSON, getOTIOExport, loadDemoProject,
    insertClipAtPlayhead, appendClip, createTrackIfNeeded,
    selectClip, deselectClip, deleteSelectedClip, duplicateSelectedClip,
    updateClipMetadata, updateClipName, updateTrackClipTime,
    addEffectToClip, removeEffectFromClip, updateEffectParams, reorderEffects
  }
})
