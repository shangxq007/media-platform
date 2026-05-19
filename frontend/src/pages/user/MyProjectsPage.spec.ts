import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createRouter, createWebHistory } from 'vue-router'
import MyProjectsPage from './MyProjectsPage.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', name: 'editor', component: { template: '<div/>' } },
    { path: '/me/projects', name: 'me-projects', component: MyProjectsPage },
    { path: '/project/:id', name: 'project', component: { template: '<div/>' } },
    { path: '/project/new', name: 'new-project', component: { template: '<div/>' } },
  ],
})

describe('MyProjectsPage', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('renders loading state initially', () => {
    const wrapper = mount(MyProjectsPage, { global: { plugins: [router] } })
    expect(wrapper.text()).toContain('Loading projects...')
  })

  it('renders empty state when no projects', async () => {
    const wrapper = mount(MyProjectsPage, { global: { plugins: [router] } })
    await new Promise(r => setTimeout(r, 10))
    await wrapper.vm.$nextTick()
    expect(wrapper.text()).toContain('My Projects')
    expect(wrapper.text()).toContain('No projects found')
  })

  it('renders create project button', async () => {
    const wrapper = mount(MyProjectsPage, { global: { plugins: [router] } })
    await new Promise(r => setTimeout(r, 10))
    await wrapper.vm.$nextTick()
    expect(wrapper.text()).toContain('New Project')
  })

  it('renders search bar', async () => {
    const wrapper = mount(MyProjectsPage, { global: { plugins: [router] } })
    await new Promise(r => setTimeout(r, 10))
    await wrapper.vm.$nextTick()
    const input = wrapper.find('input[type="text"]')
    expect(input.exists()).toBe(true)
  })

  it('renders refresh button', async () => {
    const wrapper = mount(MyProjectsPage, { global: { plugins: [router] } })
    await new Promise(r => setTimeout(r, 10))
    await wrapper.vm.$nextTick()
    expect(wrapper.text()).toContain('Refresh')
  })
})
