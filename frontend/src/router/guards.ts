import type { NavigationGuardNext, RouteLocationNormalized } from 'vue-router'
import { useNavigation } from '@/composables/useNavigation'

export async function navigationGuard(
  to: RouteLocationNormalized,
  _from: RouteLocationNormalized,
  next: NavigationGuardNext
) {
  const { fetchNavigation, isRouteVisible, isRouteEnabled, getRouteDecision } = useNavigation()

  try {
    await fetchNavigation()
  } catch {
    next()
    return
  }

  const routeName = to.name?.toString() ?? ''

  if (routeName.startsWith('admin-')) {
    next()
    return
  }

  if (!isRouteVisible(routeName)) {
    const decision = getRouteDecision(routeName)
    next({
      name: 'editor',
      query: {
        navBlocked: '1',
        reason: decision?.reasonCode ?? 'NAV-404-HIDDEN'
      }
    })
    return
  }

  if (!isRouteEnabled(routeName)) {
    const decision = getRouteDecision(routeName)
    next({
      path: to.path,
      query: {
        navDisabled: '1',
        reason: decision?.reasonCode ?? 'NAV-403-DISABLED',
        upgrade: decision?.requiredUpgrade ?? ''
      }
    })
    return
  }

  next()
}
