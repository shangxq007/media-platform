import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createRouter, createWebHistory } from 'vue-router'
import AnalyticsAssistantPage from './AnalyticsAssistantPage.vue'

vi.mock('@/api/analytics', () => ({
  NlqAPI: {
    preview: vi.fn().mockResolvedValue({
      previewId: 'prev-1',
      question: 'Show render jobs',
      intent: 'DETAIL',
      datasets: ['render_jobs_report_view'],
      sqlDraft: 'SELECT job_id FROM render_jobs_report_view LIMIT 100',
      sqlExplanation: 'Lists render jobs',
      parameters: {},
      safety: {
        safe: true,
        violations: [],
        riskLevel: 'LOW',
        requiresReview: false,
      },
      accessDecision: 'ALLOWED',
      riskLevel: 'LOW',
      requiresConfirmation: false,
      chartSuggestions: ['table'],
      warnings: [],
    }),
    execute: vi.fn().mockResolvedValue({
      queryId: 'qry-1',
      columns: ['job_id', 'status'],
      rows: [
        { job_id: 'job-1', status: 'completed' },
        { job_id: 'job-2', status: 'failed' },
      ],
      rowCount: 2,
      truncated: false,
      durationMs: 45,
      summary: 'Query returned 2 rows.',
      chartSuggestions: [{ chartType: 'table', title: 'Table view', reason: 'Default' }],
      warnings: [],
    }),
    explain: vi.fn().mockResolvedValue({
      explanation: 'This query lists all render jobs.',
    }),
    listDatasets: vi.fn().mockResolvedValue({
      datasets: [
        {
          datasetKey: 'render_jobs_report_view',
          name: 'Render Jobs Report',
          description: 'Render job execution history',
          viewName: 'render_jobs_report_view',
          module: 'render',
          enabled: true,
          tenantScoped: true,
          workspaceScoped: true,
          userScoped: false,
          maxRows: 1000,
          maxLookbackDays: 90,
          sensitivityLevel: 'low',
        },
      ],
      total: 1,
    }),
  },
}))

describe('AnalyticsAssistantPage', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('renders the page with query input', () => {
    const router = createRouter({ history: createWebHistory(), routes: [] })
    const wrapper = mount(AnalyticsAssistantPage, {
      global: { plugins: [router] },
    })
    expect(wrapper.text()).toContain('Analytics Assistant')
    expect(wrapper.find('input[type="text"]').exists()).toBe(true)
  })

  it('renders Preview and Explain buttons', () => {
    const router = createRouter({ history: createWebHistory(), routes: [] })
    const wrapper = mount(AnalyticsAssistantPage, {
      global: { plugins: [router] },
    })
    const buttons = wrapper.findAll('button')
    const buttonTexts = buttons.map(b => b.text())
    expect(buttonTexts.some(t => t.includes('Preview'))).toBe(true)
    expect(buttonTexts.some(t => t.includes('Explain'))).toBe(true)
  })

  it('does not render Execute button when no preview', () => {
    const router = createRouter({ history: createWebHistory(), routes: [] })
    const wrapper = mount(AnalyticsAssistantPage, {
      global: { plugins: [router] },
    })
    const buttons = wrapper.findAll('button')
    const executeBtn = buttons.find(b => b.text().includes('Execute'))
    expect(executeBtn).toBeUndefined()
  })

  it('renders available datasets section', () => {
    const router = createRouter({ history: createWebHistory(), routes: [] })
    const wrapper = mount(AnalyticsAssistantPage, {
      global: { plugins: [router] },
    })
    expect(wrapper.text()).toContain('Available Datasets')
  })

  it('renders query input placeholder text', () => {
    const router = createRouter({ history: createWebHistory(), routes: [] })
    const wrapper = mount(AnalyticsAssistantPage, {
      global: { plugins: [router] },
    })
    const input = wrapper.find('input[type="text"]')
    expect(input.attributes('placeholder')).toContain('render job')
  })
})
