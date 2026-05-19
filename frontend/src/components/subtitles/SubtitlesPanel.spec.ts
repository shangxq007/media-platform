import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import SubtitlesPanel from './SubtitlesPanel.vue'

const mockStore = {
  tracks: [],
  fonts: [],
  activeTrack: null,
  activeTrackId: null,
  loading: false,
  error: null as string | null,
  uploadSubtitleFile: vi.fn().mockResolvedValue(undefined),
  uploadFont: vi.fn(),
  updateCue: vi.fn(),
  removeTrack: vi.fn(),
  setTrackFont: vi.fn(),
  setTrackBurnIn: vi.fn(),
  addCue: vi.fn(),
  removeCue: vi.fn(),
  setActiveTrack: vi.fn()
}

vi.mock('@/stores/subtitle', () => ({
  useSubtitleStore: () => mockStore
}))

vi.mock('@/stores/timeline', () => ({
  useTimelineStore: () => ({
    state: { duration: 60, tracks: [] },
    clips: []
  })
}))

describe('SubtitlesPanel', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    mockStore.error = null
    mockStore.tracks = []
    mockStore.fonts = []
    mockStore.activeTrack = null
    mockStore.activeTrackId = null
  })

  it('renders upload section', () => {
    const wrapper = mount(SubtitlesPanel)
    expect(wrapper.text()).toContain('Upload Subtitles')
    expect(wrapper.text()).toContain('+ Upload SRT/ASS/VTT')
    expect(wrapper.text()).toContain('+ Upload Font (TTF/OTF)')
  })

  it('shows empty state when no tracks', () => {
    const wrapper = mount(SubtitlesPanel)
    expect(wrapper.text()).toContain('No subtitle tracks')
    expect(wrapper.text()).toContain('Upload a file to get started')
  })

  it('renders language selector', () => {
    const wrapper = mount(SubtitlesPanel)
    const select = wrapper.find('select')
    expect(select.exists()).toBe(true)
  })

  it('renders burn-in/external selector', () => {
    const wrapper = mount(SubtitlesPanel)
    const selects = wrapper.findAll('select')
    expect(selects.length).toBeGreaterThanOrEqual(2)
  })

  it('shows error when subtitle store has error', () => {
    mockStore.error = 'Parse failed'
    const wrapper = mount(SubtitlesPanel)
    expect(wrapper.text()).toContain('Parse failed')
  })
})
