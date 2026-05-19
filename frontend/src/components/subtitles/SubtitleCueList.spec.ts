import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import SubtitleCueList from './SubtitleCueList.vue'
import type { SubtitleTrack, SubtitleCue } from '@/types'

function makeTrack(cues: SubtitleCue[] = []): SubtitleTrack {
  return {
    id: 'track_1',
    language: 'en',
    label: 'English (SRT)',
    format: 'srt',
    cues,
    fallbackFontIds: [],
    burnIn: true
  }
}

function makeCue(overrides: Partial<SubtitleCue> = {}): SubtitleCue {
  return {
    id: `cue_${Math.random().toString(36).slice(2, 8)}`,
    index: 1,
    startTime: 1,
    endTime: 3,
    text: 'Hello world',
    ...overrides
  }
}

describe('SubtitleCueList', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('renders empty state when no cues', () => {
    const wrapper = mount(SubtitleCueList, {
      props: { track: makeTrack(), duration: 60 }
    })
    expect(wrapper.text()).toContain('No cues yet')
  })

  it('renders cue list with text and timecodes', () => {
    const cues = [
      makeCue({ id: 'c1', index: 1, startTime: 0, endTime: 2, text: 'First cue' }),
      makeCue({ id: 'c2', index: 2, startTime: 3, endTime: 5, text: 'Second cue' }),
    ]
    const wrapper = mount(SubtitleCueList, {
      props: { track: makeTrack(cues), duration: 60 }
    })
    expect(wrapper.text()).toContain('First cue')
    expect(wrapper.text()).toContain('Second cue')
  })

  it('emits add-cue when add button clicked', async () => {
    const wrapper = mount(SubtitleCueList, {
      props: { track: makeTrack(), duration: 60 }
    })
    await wrapper.find('button').trigger('click')
    expect(wrapper.emitted('add-cue')).toBeTruthy()
  })

  it('emits delete-cue when delete button clicked', async () => {
    const cues = [makeCue({ id: 'c1' })]
    const wrapper = mount(SubtitleCueList, {
      props: { track: makeTrack(cues), duration: 60 }
    })
    const deleteBtn = wrapper.find('button[title="Delete cue"]')
    await deleteBtn.trigger('click')
    expect(wrapper.emitted('delete-cue')).toBeTruthy()
    expect(wrapper.emitted('delete-cue')![0]).toEqual(['c1'])
  })

  it('selects cue on click', async () => {
    const cues = [makeCue({ id: 'c1' })]
    const wrapper = mount(SubtitleCueList, {
      props: { track: makeTrack(cues), duration: 60 }
    })
    const cueEl = wrapper.find('[draggable="true"]')
    await cueEl.trigger('click')
    expect(cueEl.classes()).toContain('bg-primary-500/10')
  })

  it('toggles selection on second click', async () => {
    const cues = [makeCue({ id: 'c1' })]
    const wrapper = mount(SubtitleCueList, {
      props: { track: makeTrack(cues), duration: 60 }
    })
    const cueEl = wrapper.find('[draggable="true"]')
    await cueEl.trigger('click')
    await cueEl.trigger('click')
    expect(cueEl.classes()).not.toContain('bg-primary-500/10')
  })

  it('enters edit mode when text is clicked', async () => {
    const cues = [makeCue({ id: 'c1', text: 'Editable text' })]
    const wrapper = mount(SubtitleCueList, {
      props: { track: makeTrack(cues), duration: 60 }
    })
    const textSpan = wrapper.find('.cursor-text')
    await textSpan.trigger('click')
    const input = wrapper.find('input')
    expect(input.exists()).toBe(true)
    expect((input.element as HTMLInputElement).value).toBe('Editable text')
  })

  it('emits update-cue on text edit commit', async () => {
    const cues = [makeCue({ id: 'c1', text: 'Original' })]
    const wrapper = mount(SubtitleCueList, {
      props: { track: makeTrack(cues), duration: 60 }
    })
    const textSpan = wrapper.find('.cursor-text')
    await textSpan.trigger('click')
    const input = wrapper.find('input')
    ;(input.element as HTMLInputElement).value = 'Updated'
    await input.setValue('Updated')
    await input.trigger('keydown.enter')
    expect(wrapper.emitted('update-cue')).toBeTruthy()
  })

  it('cancels edit on escape', async () => {
    const cues = [makeCue({ id: 'c1', text: 'Test' })]
    const wrapper = mount(SubtitleCueList, {
      props: { track: makeTrack(cues), duration: 60 }
    })
    const textSpan = wrapper.find('.cursor-text')
    await textSpan.trigger('click')
    const input = wrapper.find('input')
    await input.trigger('keydown.escape')
    expect(wrapper.find('input').exists()).toBe(false)
  })

  it('shows cue count in header', () => {
    const cues = [
      makeCue({ id: 'c1' }),
      makeCue({ id: 'c2' }),
      makeCue({ id: 'c3' }),
    ]
    const wrapper = mount(SubtitleCueList, {
      props: { track: makeTrack(cues), duration: 60 }
    })
    expect(wrapper.text()).toContain('3 cue(s)')
  })

  it('displays track language', () => {
    const wrapper = mount(SubtitleCueList, {
      props: { track: makeTrack(), duration: 60 }
    })
    expect(wrapper.text()).toContain('en')
  })
})
