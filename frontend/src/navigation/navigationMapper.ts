import type { RouteVisibilityDecision } from '@/types/routing'

export interface NavItem {
  routeKey: string
  path: string
  title: string
  icon?: string
  visible: boolean
  enabled: boolean
  reasonCode?: string
  requiredUpgrade?: string
  requiredPermission?: string
  requiredEntitlement?: string
  requiredFeatureFlag?: string
  userFriendlyMessage?: string
  menuGroup?: string
  order?: number
}

export function mapToNavItems(decisions: RouteVisibilityDecision[]): NavItem[] {
  return decisions.map(d => ({
    routeKey: d.routeKey,
    path: d.path,
    title: d.title,
    visible: d.visible,
    enabled: d.enabled,
    reasonCode: d.reasonCode,
    requiredUpgrade: d.requiredUpgrade,
    requiredPermission: d.requiredPermission,
    requiredEntitlement: d.requiredEntitlement,
    userFriendlyMessage: d.userFriendlyMessage,
  }))
}

export function mapToMenuGroups(
  decisions: RouteVisibilityDecision[]
): Record<string, NavItem[]> {
  const groups: Record<string, NavItem[]> = {}

  const mainItems: NavItem[] = []
  const contentItems: NavItem[] = []
  const accountItems: NavItem[] = []
  const supportItems: NavItem[] = []

  for (const d of decisions) {
    if (!d.visible) continue
    const item: NavItem = {
      routeKey: d.routeKey,
      path: d.path,
      title: d.title,
      visible: d.visible,
      enabled: d.enabled,
      reasonCode: d.reasonCode,
      requiredUpgrade: d.requiredUpgrade,
      requiredPermission: d.requiredPermission,
      requiredEntitlement: d.requiredEntitlement,
      userFriendlyMessage: d.userFriendlyMessage,
    }

    if (d.routeKey.startsWith('admin-')) continue

    switch (d.routeKey) {
      case 'me-dashboard':
      case 'me-projects':
      case 'me-shared-resources':
      case 'editor':
        mainItems.push(item)
        break
      case 'me-exports':
      case 'me-reports':
        contentItems.push(item)
        break
      case 'me-capabilities':
      case 'me-usage':
      case 'me-billing':
      case 'me-credits':
      case 'me-settings':
        accountItems.push(item)
        break
      case 'me-feedback':
      case 'me-notifications':
      case 'me-notification-settings':
        supportItems.push(item)
        break
      default:
        mainItems.push(item)
    }
  }

  if (mainItems.length > 0) groups.main = mainItems
  if (contentItems.length > 0) groups.content = contentItems
  if (accountItems.length > 0) groups.account = accountItems
  if (supportItems.length > 0) groups.support = supportItems

  return groups
}
