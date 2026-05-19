import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import SubtitleCueEditor from './SubtitleCueEditor.vue'
import type { SubtitleCue } from '@/types'

function makeCue(overrides: Partial<SubtitleCue> = {}): SubtitleCue {
  return {
    id: 'cue_1',
    index: 1,
    startTime: 1.5,
    endTime: 3.5,
    text: 'Hello world',
    ...overrides
  }
}

describe('SubtitleCueEditor', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('renders with cue data', () => {
    const cue = makeCue({ text: 'Test subtitle' })
    const wrapper = mount(SubtitleCueEditor, {
      props: { cue, duration: 60 }
    })
    const textarea = wrapper.find('textarea')
    expect((textarea.element as HTMLTextAreaElement).value).toBe('Test subtitle')
  })

  it('shows cue index', () => {
    const cue = makeCue({ index: 5 })
    const wrapper = mount(SubtitleCueEditor, {
      props: { cue, duration: 60 }
    })
    expect(wrapper.text()).toContain('Edit Cue #5')
  })

  it('emits cancel on cancel button click', async () => {
    const cue = makeCue()
    const wrapper = mount(SubtitleCueEditor, {
      props: { cue, duration: 60 }
    })
    const buttons = wrapper.findAll('buttons')
    const cancelBtn = buttons.find(b => b.text() === 'Cancel')
    await cancelBtn!.trigger('click')
    expect(wrapper.emitted('cancel')).toBeTruthy()
  })

  it('emits cancel on Escape key', async () => {
    const cue = makeCue()
    const wrapper = mount(SubtitleCueEditor, {
      props: { cue, duration: 60 }
    })
    await wrapper.trigger('keydown', { key: 'Escape' })
    expect(wrapper.emitted('cancel')).toBeTruthy()
  })

  it('emits save with updates on save button click', async () => {
    const cue = makeCue({ text: 'Original', startTime: 1, endTime: 3 })
    const wrapper = mount(SubtitleCueEditor, {
      props: { cue, duration: 60 }
    })
    const textarea = wrapper.find('textarea')
    await textarea.setValue('Updated text')
    const buttons = wrapper.findAll('buttons')
    const saveBtn = buttons.find(b => b.text() === 'Save')
    await saveBtn!.trigger('click')
    expect(wrapper.emitted('save')).toBeTruthy()
    expect(wrapper.emitted('save')![0][0]).toMatchObject({ text: 'Updated text' })
  })

  it('emits save on Ctrl+Enter', async () => {
    const cue = makeCue()
    const wrapper = mount(SubtitleCueEditor, {
      props: { cue, duration: 60 }
    })
    await wrapper.trigger('keydown', { key: 'Enter', ctrlKey: true })
    expect(wrapper.emitted('save')).toBeTruthy()
  })

  it('disables save when text is empty', async () => {
    const cue = makeCue({ text: '' })
    const wrapper = mount(SubtitleCueEditor, {
      props: { cue, duration: 60 }
    })
    const buttons = wrapper.findAll('buttons')
    const saveBtn = buttons.find(b => b.text() === 'Save')
    expect(saveBtn!.attributes('disabled')).toBeDefined()
  })

  it('disables save when end time before start time', async () => {
    const cue = makeCue({ startTime: 5, endTime: 2 })
    const wrapper = mount(SubtitleCueEditor, {
      props: { cue, duration: 60 }
    })
    const buttons = wrapper.findAll('buttons')
    const saveBtn = buttons.find(b => b.text() === 'Save')
    expect(saveBtn!.attributes('disabled')).toBeDefined()
  })

  it('shows timeline duration', () => {
    const cue = makeCue()
    const wrapper = mount(SubtitleCueEditor, {
      props: { cue, duration: 120 }
    })
    expect(wrapper.text()).toContain('120.0s')
  })

  it('validates end time within timeline duration', async () => {
    const cue = makeCue({ startTime: 0, endTime: 3 })
    const wrapper = mount(SubtitleCueEditor, {
      props: { cue, duration: 60 }
    })
    const timeInputs = wrapper.findAll('input[type="text"]')
    const endTimeInput = timeInputs[1]
    await endTimeInput.setValue('00:01:00.000')
    await endTimeInput.trigger('input')
    const buttons = wrapper.findAll('buttons')
    const saveBtn = buttons.find(b => b.text() === 'Save')
    await saveBtn!.trigger('click')
    // Should still emit save since 60s <= 60s
    expect(wrapper.emitted('save')).toBeTruthy()
  })
})
