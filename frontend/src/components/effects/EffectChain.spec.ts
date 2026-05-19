import { describe, it, expect, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import EffectChain from './EffectChain.vue'
import type { ClipEffect } from '@/types'

function makeEffect(overrides: Partial<ClipEffect> = {}): ClipEffect {
  return {
    id: `ce_${Math.random().toString(36).slice(2, 8)}`,
    effectKey: 'video.fade_in',
    providerPreference: ['javacv'],
    parameters: { duration: 1.0 },
    ...overrides
  }
}

describe('EffectChain', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('renders empty state when no effects', () => {
    const wrapper = mount(EffectChain, {
      props: { effects: [] }
    })
    expect(wrapper.text()).toContain('No effects applied')
  })

  it('renders effect list', () => {
    const effects = [
      makeEffect({ id: 'e1', effectKey: 'video.fade_in' }),
      makeEffect({ id: 'e2', effectKey: 'video.blur' }),
    ]
    const wrapper = mount(EffectChain, {
      props: { effects }
    })
    expect(wrapper.text()).toContain('video.fade_in')
    expect(wrapper.text()).toContain('video.blur')
  })

  it('shows effect count in header', () => {
    const effects = [
      makeEffect({ id: 'e1' }),
      makeEffect({ id: 'e2' }),
      makeEffect({ id: 'e3' }),
    ]
    const wrapper = mount(EffectChain, {
      props: { effects }
    })
    expect(wrapper.text()).toContain('3 effect(s)')
  })

  it('emits remove on remove button click', async () => {
    const effects = [makeEffect({ id: 'e1', effectKey: 'video.fade_in' })]
    const wrapper = mount(EffectChain, {
      props: { effects }
    })
    const removeBtn = wrapper.find('button[title="Remove effect"]')
    await removeBtn.trigger('click')
    expect(wrapper.emitted('remove')).toBeTruthy()
    expect(wrapper.emitted('remove')![0]).toEqual(['e1'])
  })

  it('emits edit on edit button click', async () => {
    const effects = [makeEffect({ id: 'e1', effectKey: 'video.fade_in' })]
    const wrapper = mount(EffectChain, {
      props: { effects }
    })
    const editBtn = wrapper.find('button[title="Edit parameters"]')
    await editBtn.trigger('click')
    expect(wrapper.emitted('edit')).toBeTruthy()
    expect((wrapper.emitted('edit')![0][0] as ClipEffect).id).toBe('e1')
  })

  it('shows effect parameters summary', () => {
    const effects = [
      makeEffect({ id: 'e1', effectKey: 'video.fade_in', parameters: { duration: 1.5 } }),
    ]
    const wrapper = mount(EffectChain, {
      props: { effects }
    })
    expect(wrapper.text()).toContain('duration: 1.5')
  })

  it('effects are draggable', () => {
    const effects = [makeEffect({ id: 'e1' })]
    const wrapper = mount(EffectChain, {
      props: { effects }
    })
    const draggable = wrapper.find('[draggable="true"]')
    expect(draggable.exists()).toBe(true)
  })
})
