import type { NavigationProfile, RouteVisibilityDecision } from '@/types/routing'
import { FALLBACK_USER_ROUTES, FALLBACK_ADMIN_ROUTES, type FallbackNavRoute } from './fallbackRoutes'

function buildDecision(route: FallbackNavRoute): RouteVisibilityDecision {
  return {
    routeKey: route.routeKey,
    path: route.path,
    title: route.title,
    visible: true,
    enabled: true,
  }
}

export function getFallbackNavigation(): NavigationProfile {
  const allRoutes = [...FALLBACK_USER_ROUTES, ...FALLBACK_ADMIN_ROUTES]
  const decisions = allRoutes.map(buildDecision)

  const menuGroups: Record<string, RouteVisibilityDecision[]> = {}
  for (const route of allRoutes) {
    const group = route.menuGroup
    if (!menuGroups[group]) {
      menuGroups[group] = []
    }
    menuGroups[group].push(buildDecision(route))
  }

  for (const group of Object.keys(menuGroups)) {
    menuGroups[group].sort((a, b) => {
      const aOrder = allRoutes.find(r => r.routeKey === a.routeKey)?.order ?? 999
      const bOrder = allRoutes.find(r => r.routeKey === b.routeKey)?.order ?? 999
      return aOrder - bOrder
    })
  }

  return { routes: decisions, menuGroups }
}

export function mergeWithFallback(backendProfile: NavigationProfile): NavigationProfile {
  const fallback = getFallbackNavigation()
  const backendRouteKeys = new Set(backendProfile.routes.map(r => r.routeKey))

  const mergedRoutes = [...backendProfile.routes]
  for (const fallbackRoute of fallback.routes) {
    if (!backendRouteKeys.has(fallbackRoute.routeKey)) {
      mergedRoutes.push({
        ...fallbackRoute,
        visible: false,
        enabled: false,
        reasonCode: 'NAV-404-SYNC',
        userFriendlyMessage: 'Route registered in client fallback only; awaiting backend registry sync',
      })
    }
  }

  const mergedGroups: Record<string, RouteVisibilityDecision[]> = { ...backendProfile.menuGroups }
  for (const [group, items] of Object.entries(fallback.menuGroups)) {
    if (!mergedGroups[group]) {
      mergedGroups[group] = items.map(i => ({ ...i, visible: false, enabled: false }))
    }
  }

  return { routes: mergedRoutes, menuGroups: mergedGroups }
}
