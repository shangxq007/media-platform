import type { NavigationGuardNext, RouteLocationNormalized } from 'vue-router'
import { useNavigation } from '@/composables/useNavigation'
import { isOidcEnabled } from '@/auth/oidcConfig'
import { getOidcUser, signInRedirect } from '@/auth/oidcClient'

function getRouteKeyFromRoute(route: RouteLocationNormalized): string {
  return route.name?.toString() ?? route.path
}

export async function navigationGuard(
  to: RouteLocationNormalized,
  _from: RouteLocationNormalized,
  next: NavigationGuardNext
) {
  if (isOidcEnabled() && to.name !== 'oauth-callback') {
    const user = await getOidcUser()
    if (!user || user.expired) {
      sessionStorage.setItem('oidc_post_login_redirect', to.fullPath)
      await signInRedirect()
      return
    }
  }

  const { fetchNavigation, getRouteDecision, isUsingFallback } = useNavigation()

  try {
    await fetchNavigation()
  } catch {
    next()
    return
  }

  const routeKey = getRouteKeyFromRoute(to)

  if (routeKey.startsWith('admin-')) {
    next()
    return
  }

  if (routeKey === 'forbidden' || routeKey === 'route-disabled' || routeKey === 'upgrade-required') {
    next()
    return
  }

  const decision = getRouteDecision(routeKey)

  if (decision && !decision.visible) {
    const reasonCode = decision.reasonCode ?? 'NAV-404-HIDDEN'
    if (reasonCode === 'NAV-404-HIDDEN') {
      next({
        name: 'forbidden',
        query: {
          routeKey,
          reasonCode,
          message: decision.userFriendlyMessage || '',
        }
      })
    } else {
      next({
        name: 'forbidden',
        query: {
          routeKey,
          reasonCode,
          message: decision.userFriendlyMessage || '',
        }
      })
    }
    return
  }

  if (decision && !decision.enabled) {
    const reasonCode = decision.reasonCode ?? 'NAV-403-DISABLED'

    if (reasonCode === 'NAV-403-TIER' && decision.requiredUpgrade) {
      next({
        name: 'upgrade-required',
        query: {
          routeKey,
          pageName: decision.title,
          reasonCode,
          requiredUpgrade: decision.requiredUpgrade,
          upgradeOptions: decision.upgradeOptions?.join(',') ?? '',
        }
      })
      return
    }

    if (reasonCode === 'NAV-403-FEAT') {
      next({
        name: 'route-disabled',
        query: {
          routeKey,
          pageName: decision.title,
          reasonCode: 'NAV-403-FEAT',
          requiredFeatureFlag: decision.requiredEntitlement || '',
          message: decision.userFriendlyMessage || '',
        }
      })
      return
    }

    if (decision.requiredUpgrade) {
      next({
        name: 'upgrade-required',
        query: {
          routeKey,
          pageName: decision.title,
          reasonCode,
          requiredUpgrade: decision.requiredUpgrade,
          upgradeOptions: decision.upgradeOptions?.join(',') ?? '',
        }
      })
      return
    }

    next({
      name: 'route-disabled',
      query: {
        routeKey,
        pageName: decision.title,
        reasonCode,
        requiredPermission: decision.requiredPermission ?? '',
        requiredEntitlement: decision.requiredEntitlement ?? '',
        upgrade: decision.requiredUpgrade ?? '',
      }
    })
    return
  }

  if (isUsingFallback.value) {
    console.info('[navigationGuard] Using fallback navigation — backend navigation unavailable')
  }

  next()
}
