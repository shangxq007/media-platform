import { describe, it, expect, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { createRouter, createWebHistory } from 'vue-router'
import EditorShell from './EditorShell.vue'

describe('EditorShell', () => {
  let router: ReturnType<typeof createRouter>

  beforeEach(async () => {
    router = createRouter({
      history: createWebHistory(),
      routes: [
        { path: '/', name: 'home', component: { template: '<div>Home</div>' } },
      ],
    })
    await router.push('/')
    await router.isReady()
  })

  it('renders the shell with header', () => {
    const wrapper = mount(EditorShell, {
      global: {
        plugins: [router],
      },
    })
    expect(wrapper.find('header').exists()).toBe(true)
  })

  it('displays project name', () => {
    const wrapper = mount(EditorShell, {
      global: {
        plugins: [router],
      },
    })
    expect(wrapper.text()).toContain('Untitled Project')
  })

  it('displays save status', () => {
    const wrapper = mount(EditorShell, {
      global: {
        plugins: [router],
      },
    })
    expect(wrapper.text()).toContain('Unsaved changes')
  })

  it('displays custom save status from prop', () => {
    const wrapper = mount(EditorShell, {
      props: { saveStatus: 'All changes saved' },
      global: {
        plugins: [router],
      },
    })
    expect(wrapper.text()).toContain('All changes saved')
  })

  it('renders status indicators', () => {
    const wrapper = mount(EditorShell, {
      global: {
        plugins: [router],
      },
    })
    expect(wrapper.text()).toContain('CPU')
    expect(wrapper.text()).toContain('Worker')
    expect(wrapper.text()).toContain('Connected')
  })

  it('emits undo event', async () => {
    const wrapper = mount(EditorShell, {
      global: {
        plugins: [router],
      },
    })
    const buttons = wrapper.findAll('button')
    const undoBtn = buttons.find(b => b.attributes('title') === 'Undo')
    expect(undoBtn).toBeTruthy()
    await undoBtn!.trigger('click')
    expect(wrapper.emitted('undo')).toBeTruthy()
  })

  it('emits redo event', async () => {
    const wrapper = mount(EditorShell, {
      global: {
        plugins: [router],
      },
    })
    const buttons = wrapper.findAll('button')
    const redoBtn = buttons.find(b => b.attributes('title') === 'Redo')
    expect(redoBtn).toBeTruthy()
    await redoBtn!.trigger('click')
    expect(wrapper.emitted('redo')).toBeTruthy()
  })

  it('emits save event', async () => {
    const wrapper = mount(EditorShell, {
      global: {
        plugins: [router],
      },
    })
    const buttons = wrapper.findAll('button')
    const saveBtn = buttons.find(b => b.text().includes('Save'))
    expect(saveBtn).toBeTruthy()
    await saveBtn!.trigger('click')
    expect(wrapper.emitted('save')).toBeTruthy()
  })

  it('emits export event', async () => {
    const wrapper = mount(EditorShell, {
      global: {
        plugins: [router],
      },
    })
    const buttons = wrapper.findAll('button')
    const exportBtn = buttons.find(b => b.text().includes('Export'))
    expect(exportBtn).toBeTruthy()
    await exportBtn!.trigger('click')
    expect(wrapper.emitted('export')).toBeTruthy()
  })

  it('renders RouterView for content', () => {
    const wrapper = mount(EditorShell, {
      global: {
        plugins: [router],
      },
    })
    expect(wrapper.findComponent({ name: 'RouterView' }).exists()).toBe(true)
  })

  it('shows GPU status when gpuAvailable is true', () => {
    const wrapper = mount(EditorShell, {
      props: { gpuAvailable: true },
      global: {
        plugins: [router],
      },
    })
    expect(wrapper.text()).toContain('GPU')
  })

  it('shows worker connected status', () => {
    const wrapper = mount(EditorShell, {
      props: { workerConnected: true },
      global: {
        plugins: [router],
      },
    })
    const indicators = wrapper.findAll('.rounded-full')
    expect(indicators.length).toBeGreaterThan(0)
  })

  it('has a back to home link', () => {
    const wrapper = mount(EditorShell, {
      global: {
        plugins: [router],
      },
    })
    const link = wrapper.find('a')
    expect(link.exists()).toBe(true)
  })
})
