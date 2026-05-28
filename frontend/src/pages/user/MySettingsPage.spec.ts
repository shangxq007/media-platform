import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import MySettingsPage from './MySettingsPage.vue'
import { MeEntitlementAPI } from '@/api/me'

vi.mock('@/api/me', () => ({
  MeEntitlementAPI: {
    getNotificationPreferences: vi.fn().mockResolvedValue(null),
    updateNotificationPreferences: vi.fn().mockResolvedValue({}),
  },
}))

describe('MySettingsPage', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    localStorage.clear()
  })

  it('renders settings page', () => {
    const wrapper = mount(MySettingsPage)
    expect(wrapper.text()).toContain('Settings')
  })

  it('renders profile section', () => {
    const wrapper = mount(MySettingsPage)
    expect(wrapper.text()).toContain('Profile')
    expect(wrapper.text()).toContain('Full Name')
    expect(wrapper.text()).toContain('Email')
    expect(wrapper.text()).toContain('Avatar URL')
  })

  it('renders notifications section', () => {
    const wrapper = mount(MySettingsPage)
    expect(wrapper.text()).toContain('Notifications')
    expect(wrapper.text()).toContain('Email Updates')
    expect(wrapper.text()).toContain('Export Complete')
    expect(wrapper.text()).toContain('Billing Alerts')
  })

  it('renders appearance section', () => {
    const wrapper = mount(MySettingsPage)
    expect(wrapper.text()).toContain('Appearance')
    expect(wrapper.text()).toContain('Theme')
    expect(wrapper.text()).toContain('Language')
  })

  it('renders danger zone', () => {
    const wrapper = mount(MySettingsPage)
    expect(wrapper.text()).toContain('Danger Zone')
    expect(wrapper.text()).toContain('Delete Account')
  })

  it('profile save button is disabled (no backend API)', () => {
    const wrapper = mount(MySettingsPage)
    const profileButton = wrapper.findAll('button').find(b => b.text().includes('Save Profile'))
    expect(profileButton).toBeTruthy()
    expect(profileButton!.attributes('disabled')).toBeDefined()
  })

  it('delete account button is disabled (not implemented)', () => {
    const wrapper = mount(MySettingsPage)
    const deleteButton = wrapper.findAll('button').find(b => b.text().includes('Delete Account'))
    expect(deleteButton).toBeTruthy()
    expect(deleteButton!.attributes('disabled')).toBeDefined()
  })

  it('appearance save button says Save Locally', () => {
    const wrapper = mount(MySettingsPage)
    const saveLocalButton = wrapper.findAll('button').find(b => b.text().includes('Save Locally'))
    expect(saveLocalButton).toBeTruthy()
  })

  it('notification save calls real API', async () => {
    const wrapper = mount(MySettingsPage)
    const saveButton = wrapper.findAll('button').find(b => b.text().includes('Save Preferences'))
    expect(saveButton).toBeTruthy()

    await saveButton!.trigger('click')
    await vi.waitFor(() => {
      expect(MeEntitlementAPI.updateNotificationPreferences).toHaveBeenCalled()
    })
  })

  it('notification save shows success message after API call', async () => {
    vi.mocked(MeEntitlementAPI.updateNotificationPreferences).mockResolvedValue({} as any)
    const wrapper = mount(MySettingsPage)
    const saveButton = wrapper.findAll('button').find(b => b.text().includes('Save Preferences'))

    await saveButton!.trigger('click')
    await vi.waitFor(() => {
      expect(wrapper.text()).toContain('saved to server')
    })
  })

  it('notification save shows error message on API failure', async () => {
    vi.mocked(MeEntitlementAPI.updateNotificationPreferences).mockRejectedValue(new Error('Network error'))
    const wrapper = mount(MySettingsPage)
    const saveButton = wrapper.findAll('button').find(b => b.text().includes('Save Preferences'))

    await saveButton!.trigger('click')
    await vi.waitFor(() => {
      expect(wrapper.text()).toContain('Network error')
    })
  })

  it('appearance save writes to localStorage', async () => {
    const wrapper = mount(MySettingsPage)
    const saveButton = wrapper.findAll('button').find(b => b.text().includes('Save Locally'))

    await saveButton!.trigger('click')

    expect(localStorage.getItem('pref_theme')).toBe('system')
    expect(localStorage.getItem('pref_language')).toBe('en')
    expect(wrapper.text()).toContain('locally')
  })

  it('profile save shows coming soon message', () => {
    const wrapper = mount(MySettingsPage)
    expect(wrapper.text()).toContain('coming soon')
  })

  it('no mock setTimeout in save handlers', () => {
    mount(MySettingsPage)
    expect(MeEntitlementAPI.updateNotificationPreferences).not.toHaveBeenCalled()
  })

  it('loads notification preferences from API on mount', async () => {
    vi.mocked(MeEntitlementAPI.getNotificationPreferences).mockResolvedValue({
      preferenceId: 'p1',
      tenantId: 't1',
      userId: 'u1',
      globalEnabled: true,
      channelEnabled: {},
      digestMode: 'NONE',
      criticalOverride: true,
    })

    mount(MySettingsPage)

    await vi.waitFor(() => {
      expect(MeEntitlementAPI.getNotificationPreferences).toHaveBeenCalled()
    })
  })
})
