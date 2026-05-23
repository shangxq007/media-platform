import { ref, computed } from 'vue'
import { NavigationClient } from '@/api/navigation'
import type { NavigationProfile, RouteVisibilityDecision } from '@/types/routing'
import { getFallbackNavigation, mergeWithFallback } from '@/navigation/navigationFallback'

const profile = ref<NavigationProfile | null>(null)
const loading = ref(false)
const error = ref<string | null>(null)
const isUsingFallback = ref(false)
let fetchPromise: Promise<NavigationProfile> | null = null

export function useNavigation() {
  const routes = computed(() => profile.value?.routes ?? [])
  const menuGroups = computed(() => profile.value?.menuGroups ?? {})

  const visibleRoutes = computed(() =>
    routes.value.filter(r => r.visible)
  )

  const enabledRoutes = computed(() =>
    routes.value.filter(r => r.visible && r.enabled)
  )

  const disabledRoutes = computed(() =>
    routes.value.filter(r => r.visible && !r.enabled)
  )

  const upgradeSuggestions = computed(() =>
    disabledRoutes.value.filter(r => r.requiredUpgrade)
  )

  function isRouteVisible(routeKey: string): boolean {
    const route = routes.value.find(r => r.routeKey === routeKey)
    return route?.visible ?? true
  }

  function isRouteEnabled(routeKey: string): boolean {
    const route = routes.value.find(r => r.routeKey === routeKey)
    return route?.enabled ?? true
  }

  function getRouteDecision(routeKey: string): RouteVisibilityDecision | undefined {
    return routes.value.find(r => r.routeKey === routeKey)
  }

  async function fetchNavigation(): Promise<NavigationProfile> {
    if (fetchPromise) return fetchPromise

    loading.value = true
    error.value = null
    isUsingFallback.value = false

    fetchPromise = NavigationClient.getNavigation()
      .then(result => {
        if (!result || !result.routes || result.routes.length === 0) {
          console.warn('[useNavigation] Backend returned empty navigation, using fallback')
          const fallback = getFallbackNavigation()
          profile.value = fallback
          isUsingFallback.value = true
          return fallback
        }
        const merged = mergeWithFallback(result)
        profile.value = merged
        return merged
      })
      .catch(err => {
        const msg = err instanceof Error ? err.message : String(err)
        error.value = msg
        console.warn('[useNavigation] Failed to fetch navigation, using fallback:', err)
        const fallback = getFallbackNavigation()
        profile.value = fallback
        isUsingFallback.value = true
        return fallback
      })
      .finally(() => {
        loading.value = false
        fetchPromise = null
      })

    return fetchPromise
  }

  function clearCache() {
    profile.value = null
    error.value = null
    isUsingFallback.value = false
  }

  return {
    profile,
    loading,
    error,
    isUsingFallback,
    routes,
    menuGroups,
    visibleRoutes,
    enabledRoutes,
    disabledRoutes,
    upgradeSuggestions,
    isRouteVisible,
    isRouteEnabled,
    getRouteDecision,
    fetchNavigation,
    clearCache
  }
}
