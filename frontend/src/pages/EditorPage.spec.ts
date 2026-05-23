import { describe, it, expect, beforeEach, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { createRouter, createWebHistory } from 'vue-router'
import { setActivePinia, createPinia } from 'pinia'
import { ref } from 'vue'
import EditorPage from './EditorPage.vue'

vi.mock('@/components/timeline/TimelineEditor.vue', () => ({
  default: {
    props: ['currentTime'],
    emits: ['update:currentTime'],
    template: '<div class="timeline-editor">Timeline</div>',
  },
}))

vi.mock('@/components/clip-library/ClipLibrary.vue', () => ({
  default: { template: '<div class="clip-library">Clips</div>' },
}))

vi.mock('@/components/export/ExportPanel.vue', () => ({
  default: { template: '<div class="export-panel">Export</div>' },
}))

vi.mock('@/components/effects/EffectsPanel.vue', () => ({
  default: { template: '<div class="effects-panel">Effects</div>' },
}))

vi.mock('@/components/common/MigrationPanel.vue', () => ({
  default: { template: '<div class="migration-panel">Migration</div>' },
}))

vi.mock('@/components/common/SubtitleUpload.vue', () => ({
  default: { template: '<div class="subtitle-upload">Subtitles</div>' },
}))

vi.mock('@/components/editor/EmptyProjectGuide.vue', () => ({
  default: { template: '<div class="empty-project-guide">Empty Guide</div>' },
}))

vi.mock('@/composables/usePlayback', () => ({
  usePlayback: () => ({
    isPlaying: ref(false),
    currentTime: ref(0),
    togglePlayback: vi.fn(),
    stepForward: vi.fn(),
    stepBackward: vi.fn(),
    seek: vi.fn(),
    startPlayback: vi.fn(),
    stopPlayback: vi.fn(),
  }),
}))

vi.mock('@/composables/useSaveProject', () => ({
  useSaveProject: () => ({
    isSaving: ref(false),
    isDirty: ref(false),
    lastSavedAt: ref(null),
    saveError: ref(null),
    markDirty: vi.fn(),
    clearDirty: vi.fn(),
    saveProject: vi.fn(),
    getSaveStatusText: () => 'Unsaved changes',
  }),
}))

vi.mock('@/composables/useTimelineSync', () => ({
  useTimelineSync: () => ({
    isSyncing: ref(false),
    isPulling: ref(false),
    syncError: ref(null),
    fastForwardNotice: ref(null),
    pendingConflict: ref(null),
    pullTimeline: vi.fn(),
    syncTimeline: vi.fn(),
    resolveConflict: vi.fn(),
    dismissConflict: vi.fn(),
    persistOfflineDraft: vi.fn(),
    tryRestoreOfflineDraft: vi.fn(),
  }),
}))

vi.mock('@/composables/useEditorTimelineLifecycle', () => ({
  useEditorTimelineLifecycle: vi.fn(),
}))

vi.mock('@/components/timeline/TimelineConflictDialog.vue', () => ({
  default: { template: '<div />' },
}))

vi.mock('@/stores/timeline', () => ({
  useTimelineStore: () => ({
    state: {
      tracks: [{ id: 't1', name: 'Video 1', type: 'video', clips: [], muted: false, locked: false }],
      duration: 60,
      currentTime: 0,
      zoom: 1,
      playing: false,
    },
    clips: [],
    trackCount: 1,
    addTrack: vi.fn(),
    addClipToTrack: vi.fn(),
    toJSON: () => ({ schemaVersion: '2.0.0', tracks: [] }),
    selectedTrackClip: null,
    loadDemoProject: vi.fn(),
    loadFromJSON: vi.fn(),
    setCurrentTime: vi.fn(),
  }),
}))

vi.mock('@/stores/project', () => ({
  useProjectStore: () => ({
    currentProject: null,
    setTenant: vi.fn(),
    setProject: vi.fn(),
    currentTenant: '',
    saving: false,
  }),
}))

vi.mock('@/stores/subtitle', () => ({
  useSubtitleStore: () => ({
    tracks: [],
    activeTrackId: null,
  }),
}))

vi.mock('@/stores/history', () => ({
  useHistoryStore: () => ({
    undo: vi.fn(),
    redo: vi.fn(),
  }),
}))

vi.mock('@/utils/demoProjectFactory', () => ({
  createDemoProject: () => ({
    name: 'Demo Editing Project',
    clips: [],
    tracks: [],
    trackClips: [],
    subtitleTracks: [],
    subtitleCues: [],
    effects: [],
    transitions: [],
  }),
}))

vi.mock('@/composables/useFeatureFlag', () => ({
  useEditorFeatureFlags: () => ({
    isEnabled: () => false,
  }),
}))

describe('EditorPage', () => {
  let router: ReturnType<typeof createRouter>

  beforeEach(async () => {
    setActivePinia(createPinia())
    router = createRouter({
      history: createWebHistory(),
      routes: [
        { path: '/', name: 'editor', component: EditorPage },
        { path: '/project/:id', name: 'project', component: EditorPage },
      ],
    })
    await router.push('/')
    await router.isReady()
  })

  it('renders the editor page', () => {
    const wrapper = mount(EditorPage, {
      global: {
        plugins: [router],
      },
    })
    expect(wrapper.find('.h-full').exists()).toBe(true)
  })

  it('has left panel with clip library toggle', () => {
    const wrapper = mount(EditorPage, {
      global: {
        plugins: [router],
      },
    })
    const toggleBtn = wrapper.find('button')
    expect(toggleBtn.exists()).toBe(true)
  })

  it('has right panel with tabs', () => {
    const wrapper = mount(EditorPage, {
      global: {
        plugins: [router],
      },
    })
    const tabButtons = wrapper.findAll('button')
    const tabLabels = tabButtons.map(b => b.text())
    const hasEffects = tabLabels.some(t => t.includes('Effects'))
    const hasExport = tabLabels.some(t => t.includes('Export'))
    expect(hasEffects || hasExport).toBe(true)
  })

  it('has playback controls in EditorShell', () => {
    const wrapper = mount(EditorPage, {
      global: {
        plugins: [router],
      },
    })
    const text = wrapper.text()
    expect(text).toContain('↩️')
    expect(text).toContain('↪️')
  })

  it('has save and export buttons', () => {
    const wrapper = mount(EditorPage, {
      global: {
        plugins: [router],
      },
    })
    const text = wrapper.text()
    expect(text).toContain('Save')
    expect(text).toContain('Export')
  })

  it('renders EditorShell layout wrapper', () => {
    const wrapper = mount(EditorPage, {
      global: {
        plugins: [router],
      },
    })
    expect(wrapper.find('header').exists()).toBe(true)
    expect(wrapper.find('.h-full').exists()).toBe(true)
  })

  it('has undo/redo/save/export buttons from EditorShell', () => {
    const wrapper = mount(EditorPage, {
      global: {
        plugins: [router],
      },
    })
    const buttons = wrapper.findAll('button')
    const buttonTexts = buttons.map(b => b.text())
    const hasSave = buttonTexts.some(t => t.includes('Save'))
    const hasExport = buttonTexts.some(t => t.includes('Export'))
    expect(hasSave).toBe(true)
    expect(hasExport).toBe(true)
  })

  it('displays save status', () => {
    const wrapper = mount(EditorPage, {
      global: {
        plugins: [router],
      },
    })
    const text = wrapper.text()
    expect(text).toMatch(/Unsaved changes|All changes saved/)
  })
})
