import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createRouter, createWebHistory } from 'vue-router'
import MyReportsPage from './MyReportsPage.vue'

vi.mock('@/api/analytics', () => ({
  ReportAPI: {
    listReports: vi.fn().mockResolvedValue({
      reports: [
        {
          reportId: 'rpt-1',
          tenantId: 't1',
          workspaceId: 'ws1',
          name: 'Weekly Render Summary',
          description: 'Weekly summary of render jobs',
          createdBy: 'admin',
          visibility: 'workspace',
          createdAt: '2026-01-15T10:00:00Z',
          updatedAt: '2026-01-15T10:00:00Z',
          archived: false,
        },
      ],
      total: 1,
    }),
    executeReport: vi.fn().mockResolvedValue({
      executionId: 'rpx-1',
      reportId: 'rpt-1',
      status: 'SUCCESS',
      rowCount: 25,
      durationMs: 150,
      errorCode: null,
      createdAt: '2026-01-16T10:00:00Z',
    }),
    archiveReport: vi.fn().mockResolvedValue({ reportId: 'rpt-1', archived: true }),
  },
}))

describe('MyReportsPage', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('renders the page header', async () => {
    const router = createRouter({ history: createWebHistory(), routes: [] })
    const wrapper = mount(MyReportsPage, {
      global: { plugins: [router] },
    })
    await new Promise(r => setTimeout(r, 50))
    await wrapper.vm.$nextTick()
    expect(wrapper.text()).toContain('My Reports')
  })

  it('renders saved reports after loading', async () => {
    const router = createRouter({ history: createWebHistory(), routes: [] })
    const wrapper = mount(MyReportsPage, {
      global: { plugins: [router] },
    })
    await new Promise(r => setTimeout(r, 50))
    await wrapper.vm.$nextTick()
    expect(wrapper.text()).toContain('Weekly Render Summary')
  })

  it('renders Execute and Archive buttons for each report', async () => {
    const router = createRouter({ history: createWebHistory(), routes: [] })
    const wrapper = mount(MyReportsPage, {
      global: { plugins: [router] },
    })
    await new Promise(r => setTimeout(r, 50))
    await wrapper.vm.$nextTick()
    const buttons = wrapper.findAll('button')
    const buttonTexts = buttons.map(b => b.text())
    expect(buttonTexts.some(t => t.includes('Execute'))).toBe(true)
    expect(buttonTexts.some(t => t.includes('Archive'))).toBe(true)
  })

  it('renders loading state initially', () => {
    const router = createRouter({ history: createWebHistory(), routes: [] })
    const wrapper = mount(MyReportsPage, {
      global: { plugins: [router] },
    })
    expect(wrapper.text()).toContain('Loading')
  })
})
