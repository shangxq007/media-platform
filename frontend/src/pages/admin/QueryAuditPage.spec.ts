import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createRouter, createWebHistory } from 'vue-router'
import QueryAuditPage from './QueryAuditPage.vue'

describe('QueryAuditPage', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('renders the page header', async () => {
    const router = createRouter({ history: createWebHistory(), routes: [] })
    const wrapper = mount(QueryAuditPage, {
      global: { plugins: [router] },
    })
    await new Promise(r => setTimeout(r, 50))
    await wrapper.vm.$nextTick()
    expect(wrapper.text()).toContain('Query Audit Logs')
  })

  it('renders empty state when no audit entries', async () => {
    const router = createRouter({ history: createWebHistory(), routes: [] })
    const wrapper = mount(QueryAuditPage, {
      global: { plugins: [router] },
    })
    await new Promise(r => setTimeout(r, 50))
    await wrapper.vm.$nextTick()
    expect(wrapper.text()).toContain('No audit entries')
  })

  it('renders loading state initially', () => {
    const router = createRouter({ history: createWebHistory(), routes: [] })
    const wrapper = mount(QueryAuditPage, {
      global: { plugins: [router] },
    })
    expect(wrapper.text()).toContain('Loading')
  })

  it('renders Refresh button', async () => {
    const router = createRouter({ history: createWebHistory(), routes: [] })
    const wrapper = mount(QueryAuditPage, {
      global: { plugins: [router] },
    })
    await new Promise(r => setTimeout(r, 50))
    await wrapper.vm.$nextTick()
    const buttons = wrapper.findAll('button')
    const buttonTexts = buttons.map(b => b.text())
    expect(buttonTexts.some(t => t.includes('Refresh'))).toBe(true)
  })
})
