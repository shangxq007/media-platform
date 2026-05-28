import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { defineComponent, nextTick } from 'vue'
import { useAdminTenantSelection } from './useAdminTenantSelection'
import type { AdminTenant } from '@/api/admin/identity'

// Mock the IdentityAPI
vi.mock('@/api/admin/identity', () => ({
  IdentityAPI: {
    listAllTenants: vi.fn(),
  },
}))

import { IdentityAPI } from '@/api/admin/identity'

function createTestComponent() {
  return defineComponent({
    setup() {
      const composable = useAdminTenantSelection()
      return { ...composable }
    },
    template: '<div></div>',
  })
}

describe('useAdminTenantSelection', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('loads tenants on mount', async () => {
    const mockTenants: AdminTenant[] = [
      { id: 't1', name: 'Tenant One', status: 'ACTIVE', createdAt: '2024-01-01T00:00:00Z' },
      { id: 't2', name: 'Tenant Two', status: 'ACTIVE', createdAt: '2024-01-02T00:00:00Z' },
    ]
    vi.mocked(IdentityAPI.listAllTenants).mockResolvedValue(mockTenants)

    const wrapper = mount(createTestComponent())
    await nextTick()
    await nextTick()

    expect(wrapper.vm.tenants).toHaveLength(2)
    expect(wrapper.vm.selectedTenantId).toBe('t1')
    expect(wrapper.vm.loading).toBe(false)
    expect(wrapper.vm.error).toBeNull()
  })

  it('does not default to tenant-1', async () => {
    vi.mocked(IdentityAPI.listAllTenants).mockResolvedValue([])

    const wrapper = mount(createTestComponent())
    await nextTick()
    await nextTick()

    expect(wrapper.vm.tenants).toHaveLength(0)
    expect(wrapper.vm.selectedTenantId).toBe('')
    expect(wrapper.vm.error).toBeNull()
  })

  it('handles API error gracefully', async () => {
    vi.mocked(IdentityAPI.listAllTenants).mockRejectedValue(new Error('Forbidden'))

    const wrapper = mount(createTestComponent())
    await nextTick()
    await nextTick()

    expect(wrapper.vm.tenants).toHaveLength(0)
    expect(wrapper.vm.selectedTenantId).toBe('')
    expect(wrapper.vm.error).toBe('Forbidden')
  })

  it('auto-selects first tenant when none selected', async () => {
    const mockTenants: AdminTenant[] = [
      { id: 'real-tenant', name: 'Real Tenant', status: 'ACTIVE', createdAt: '2024-01-01T00:00:00Z' },
    ]
    vi.mocked(IdentityAPI.listAllTenants).mockResolvedValue(mockTenants)

    const wrapper = mount(createTestComponent())
    await nextTick()
    await nextTick()

    expect(wrapper.vm.selectedTenantId).toBe('real-tenant')
  })

  it('does not overwrite existing selection', async () => {
    const mockTenants: AdminTenant[] = [
      { id: 't1', name: 'Tenant One', status: 'ACTIVE', createdAt: '2024-01-01T00:00:00Z' },
    ]
    vi.mocked(IdentityAPI.listAllTenants).mockResolvedValue(mockTenants)

    const wrapper = mount(createTestComponent())
    await nextTick()
    await nextTick()

    // Manually change selection
    wrapper.vm.selectedTenantId = 't1'

    // Reload — should not overwrite
    await wrapper.vm.loadTenants()
    await nextTick()

    expect(wrapper.vm.selectedTenantId).toBe('t1')
  })
})
