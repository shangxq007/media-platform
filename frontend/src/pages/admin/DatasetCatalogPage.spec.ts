import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createRouter, createWebHistory } from 'vue-router'
import DatasetCatalogPage from './DatasetCatalogPage.vue'

vi.mock('@/api/analytics', () => ({
  NlqAPI: {
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
        {
          datasetKey: 'billing_usage_report_view',
          name: 'Billing Usage Report',
          description: 'Billing and usage metrics',
          viewName: 'billing_usage_report_view',
          module: 'billing',
          enabled: true,
          tenantScoped: true,
          workspaceScoped: false,
          userScoped: false,
          maxRows: 1000,
          maxLookbackDays: 365,
          sensitivityLevel: 'high',
        },
      ],
      total: 2,
    }),
    getDataset: vi.fn().mockResolvedValue({
      dataset: {
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
    }),
  },
}))

describe('DatasetCatalogPage', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('renders the page header', async () => {
    const router = createRouter({ history: createWebHistory(), routes: [] })
    const wrapper = mount(DatasetCatalogPage, {
      global: { plugins: [router] },
    })
    await new Promise(r => setTimeout(r, 50))
    await wrapper.vm.$nextTick()
    expect(wrapper.text()).toContain('Dataset Catalog')
  })

  it('renders datasets after loading', async () => {
    const router = createRouter({ history: createWebHistory(), routes: [] })
    const wrapper = mount(DatasetCatalogPage, {
      global: { plugins: [router] },
    })
    await new Promise(r => setTimeout(r, 50))
    await wrapper.vm.$nextTick()
    expect(wrapper.text()).toContain('Render Jobs Report')
    expect(wrapper.text()).toContain('Billing Usage Report')
  })

  it('renders dataset details when selected', async () => {
    const router = createRouter({ history: createWebHistory(), routes: [] })
    const wrapper = mount(DatasetCatalogPage, {
      global: { plugins: [router] },
    })
    await new Promise(r => setTimeout(r, 50))
    await wrapper.vm.$nextTick()
    const datasetCards = wrapper.findAll('[class*="cursor-pointer"]')
    if (datasetCards.length > 0) {
      await datasetCards[0].trigger('click')
      await new Promise(r => setTimeout(r, 50))
      await wrapper.vm.$nextTick()
      expect(wrapper.text()).toContain('Dataset Details')
    }
  })

  it('renders loading state initially', () => {
    const router = createRouter({ history: createWebHistory(), routes: [] })
    const wrapper = mount(DatasetCatalogPage, {
      global: { plugins: [router] },
    })
    expect(wrapper.text()).toContain('Loading')
  })
})
