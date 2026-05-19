import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createRouter, createWebHistory } from 'vue-router'
import UserSidebar from './UserSidebar.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', name: 'editor', component: { template: '<div>Editor</div>' } },
    { path: '/me', name: 'me', component: { template: '<div>Dashboard</div>' } },
    { path: '/me/projects', name: 'me-projects', component: { template: '<div>Projects</div>' } },
    { path: '/me/capabilities', name: 'me-capabilities', component: { template: '<div>Capabilities</div>' } },
    { path: '/me/usage', name: 'me-usage', component: { template: '<div>Usage</div>' } },
    { path: '/me/billing', name: 'me-billing', component: { template: '<div>Billing</div>' } },
    { path: '/me/credits', name: 'me-credits', component: { template: '<div>Credits</div>' } },
    { path: '/me/feedback', name: 'me-feedback', component: { template: '<div>Feedback</div>' } },
    { path: '/me/settings', name: 'me-settings', component: { template: '<div>Settings</div>' } },
    { path: '/prompts', name: 'prompts', component: { template: '<div>Prompts</div>' } },
    { path: '/effect-packs', name: 'effect-packs', component: { template: '<div>Effect Packs</div>' } },
    { path: '/admin', name: 'admin', component: { template: '<div>Admin</div>' } },
  ],
})

describe('UserSidebar', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('renders all navigation items', () => {
    const wrapper = mount(UserSidebar, { global: { plugins: [router] } })
    expect(wrapper.text()).toContain('Dashboard')
    expect(wrapper.text()).toContain('Projects')
    expect(wrapper.text()).toContain('Editor')
    expect(wrapper.text()).toContain('Prompts')
    expect(wrapper.text()).toContain('Effect Packs')
    expect(wrapper.text()).toContain('Capabilities')
    expect(wrapper.text()).toContain('Usage')
    expect(wrapper.text()).toContain('Billing')
    expect(wrapper.text()).toContain('Credits')
    expect(wrapper.text()).toContain('Feedback')
    expect(wrapper.text()).toContain('Settings')
  })

  it('renders admin link', () => {
    const wrapper = mount(UserSidebar, { global: { plugins: [router] } })
    expect(wrapper.text()).toContain('Admin')
  })

  it('has router-link elements', async () => {
    router.push('/me')
    await router.isReady()
    const wrapper = mount(UserSidebar, { global: { plugins: [router] } })
    const links = wrapper.findAll('a')
    expect(links.length).toBeGreaterThan(0)
  })
})
