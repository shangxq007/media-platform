import { describe, it, expect, beforeEach, vi } from 'vitest'
import { useNavigation } from './useNavigation'

vi.mock('@/api/navigation', () => ({
  NavigationClient: {
    getNavigation: vi.fn(),
    getRoutes: vi.fn(),
    previewNavigation: vi.fn()
  }
}))

import { NavigationClient } from '@/api/navigation'

describe('useNavigation', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.spyOn(NavigationClient, 'getNavigation').mockResolvedValue({ routes: [], menuGroups: {} })
  })

  it('starts with empty state', () => {
    const nav = useNavigation()
    expect(nav.profile.value).toBeNull()
    expect(nav.loading.value).toBe(false)
    expect(nav.routes.value).toEqual([])
    expect(nav.menuGroups.value).toEqual({})
  })

  it('fetches navigation on mount', async () => {
    const mockProfile = {
      routes: [
        { routeKey: 'editor', path: '/', title: 'Editor', visible: true, enabled: true }
      ],
      menuGroups: {
        main: [
          { routeKey: 'editor', path: '/', title: 'Editor', visible: true, enabled: true }
        ]
      }
    }
    vi.spyOn(NavigationClient, 'getNavigation').mockResolvedValue(mockProfile)

    const nav = useNavigation()
    await nav.fetchNavigation()

    expect(nav.profile.value).not.toBeNull()
    expect(nav.routes.value.length).toBeGreaterThan(0)
    expect(nav.isUsingFallback.value).toBe(false)
  })

  it('returns visible routes only', async () => {
    vi.spyOn(NavigationClient, 'getNavigation').mockResolvedValue({
      routes: [
        { routeKey: 'r1', path: '/a', title: 'A', visible: true, enabled: true },
        { routeKey: 'r2', path: '/b', title: 'B', visible: false, enabled: true },
        { routeKey: 'r3', path: '/c', title: 'C', visible: true, enabled: false }
      ],
      menuGroups: { main: [] }
    })

    const nav = useNavigation()
    await nav.fetchNavigation()

    expect(nav.visibleRoutes.value).toHaveLength(2)
    expect(nav.enabledRoutes.value).toHaveLength(1)
    expect(nav.disabledRoutes.value).toHaveLength(1)
  })

  it('checks route visibility', async () => {
    vi.spyOn(NavigationClient, 'getNavigation').mockResolvedValue({
      routes: [
        { routeKey: 'visible', path: '/v', title: 'V', visible: true, enabled: true },
        { routeKey: 'hidden', path: '/h', title: 'H', visible: false, enabled: true }
      ],
      menuGroups: {}
    })

    const nav = useNavigation()
    await nav.fetchNavigation()

    expect(nav.isRouteVisible('visible')).toBe(true)
    expect(nav.isRouteVisible('hidden')).toBe(false)
    expect(nav.isRouteVisible('nonexistent')).toBe(true)
  })

  it('checks route enabled state', async () => {
    vi.spyOn(NavigationClient, 'getNavigation').mockResolvedValue({
      routes: [
        { routeKey: 'on', path: '/on', title: 'On', visible: true, enabled: true },
        { routeKey: 'off', path: '/off', title: 'Off', visible: true, enabled: false }
      ],
      menuGroups: {}
    })

    const nav = useNavigation()
    await nav.fetchNavigation()

    expect(nav.isRouteEnabled('on')).toBe(true)
    expect(nav.isRouteEnabled('off')).toBe(false)
    expect(nav.isRouteEnabled('nonexistent')).toBe(true)
  })

  it('returns route decision by key', async () => {
    const decision = { routeKey: 'editor', path: '/', title: 'Editor', visible: true, enabled: true }
    vi.spyOn(NavigationClient, 'getNavigation').mockResolvedValue({
      routes: [decision],
      menuGroups: {}
    })

    const nav = useNavigation()
    await nav.fetchNavigation()

    expect(nav.getRouteDecision('editor')).toEqual(decision)
    expect(nav.getRouteDecision('nonexistent')).toBeUndefined()
  })

  it('returns upgrade suggestions for disabled routes', async () => {
    vi.spyOn(NavigationClient, 'getNavigation').mockResolvedValue({
      routes: [
        { routeKey: 'free', path: '/f', title: 'Free', visible: true, enabled: true },
        { routeKey: 'pro', path: '/p', title: 'Pro', visible: true, enabled: false, requiredUpgrade: 'PROFESSIONAL' }
      ],
      menuGroups: {}
    })

    const nav = useNavigation()
    await nav.fetchNavigation()

    expect(nav.upgradeSuggestions.value).toHaveLength(1)
    expect(nav.upgradeSuggestions.value[0].routeKey).toBe('pro')
  })

  it('handles fetch error gracefully', async () => {
    vi.spyOn(NavigationClient, 'getNavigation').mockRejectedValue(new Error('Network error'))

    const nav = useNavigation()
    await nav.fetchNavigation()

    expect(nav.isUsingFallback.value).toBe(true)
    expect(nav.routes.value.length).toBeGreaterThan(0)
  })

  it('clears cache', async () => {
    vi.spyOn(NavigationClient, 'getNavigation').mockResolvedValue({
      routes: [{ routeKey: 'r1', path: '/a', title: 'A', visible: true, enabled: true }],
      menuGroups: {}
    })

    const nav = useNavigation()
    await nav.fetchNavigation()
    expect(nav.routes.value.length).toBeGreaterThan(0)

    nav.clearCache()
    expect(nav.profile.value).toBeNull()
    expect(nav.error.value).toBeNull()
  })
})
