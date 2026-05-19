import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import MyFeedbackPage from './MyFeedbackPage.vue'

describe('MyFeedbackPage', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('renders loading state initially', () => {
    const wrapper = mount(MyFeedbackPage)
    expect(wrapper.text()).toContain('Loading feedback...')
  })

  it('renders feedback page after loading', async () => {
    const wrapper = mount(MyFeedbackPage)
    await new Promise(r => setTimeout(r, 10))
    await wrapper.vm.$nextTick()
    expect(wrapper.text()).toContain('Feedback')
  })

  it('renders empty state when no feedback', async () => {
    const wrapper = mount(MyFeedbackPage)
    await new Promise(r => setTimeout(r, 10))
    await wrapper.vm.$nextTick()
    expect(wrapper.text()).toContain('No feedback submitted')
  })

  it('renders submit feedback button', async () => {
    const wrapper = mount(MyFeedbackPage)
    await new Promise(r => setTimeout(r, 10))
    await wrapper.vm.$nextTick()
    expect(wrapper.text()).toContain('Submit Feedback')
  })

  it('opens submit dialog when button clicked', async () => {
    const wrapper = mount(MyFeedbackPage)
    await new Promise(r => setTimeout(r, 10))
    await wrapper.vm.$nextTick()
    const buttons = wrapper.findAll('button')
    const submitBtn = buttons.find(b => b.text().includes('Submit Feedback'))
    expect(submitBtn).toBeTruthy()
  })

  it('renders page header with title', async () => {
    const wrapper = mount(MyFeedbackPage)
    await new Promise(r => setTimeout(r, 10))
    await wrapper.vm.$nextTick()
    expect(wrapper.text()).toContain('Feedback')
  })

  it('renders refresh button', async () => {
    const wrapper = mount(MyFeedbackPage)
    await new Promise(r => setTimeout(r, 10))
    await wrapper.vm.$nextTick()
    const buttons = wrapper.findAll('button')
    const refreshBtn = buttons.find(b => b.text().includes('Refresh'))
    expect(refreshBtn).toBeTruthy()
  })
})
