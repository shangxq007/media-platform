import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import MySettingsPage from './MySettingsPage.vue'

describe('MySettingsPage', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
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

  it('renders save buttons for each section', () => {
    const wrapper = mount(MySettingsPage)
    const buttons = wrapper.findAll('button')
    const saveButtons = buttons.filter(b => b.text().includes('Save'))
    expect(saveButtons.length).toBe(3)
  })
})
