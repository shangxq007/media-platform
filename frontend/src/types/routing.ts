export interface FrontendRouteDefinition {
  routeKey: string
  path: string
  componentKey: string
  title: string
  description?: string
  menuGroup?: string
  icon?: string
  order?: number
  parentRouteKey?: string
  requiredPermissions?: string[]
  requiredRoles?: string[]
  requiredEntitlements?: string[]
  requiredTier?: string
  requiredFeatures?: string[]
  supportedSources?: ('WEB' | 'MCP' | 'ADMIN' | 'INTERNAL')[]
  visible?: boolean
  enabled?: boolean
  hiddenReason?: string
  disabledReason?: string
  upgradeOptions?: string[]
}

export interface RouteVisibilityDecision {
  routeKey: string
  path: string
  title: string
  visible: boolean
  enabled: boolean
  reasonCode?: string
  userFriendlyMessage?: string
  requiredUpgrade?: string
  requiredPermission?: string
  requiredEntitlement?: string
  children?: RouteVisibilityDecision[]
}

export interface NavigationProfile {
  routes: RouteVisibilityDecision[]
  menuGroups: Record<string, RouteVisibilityDecision[]>
}

export interface NavigationPreviewRequest {
  userId?: string
  tenantId?: string
  roles?: string[]
  permissions?: string[]
  tier?: string
  features?: string[]
  entitlements?: string[]
  source?: 'WEB' | 'MCP' | 'ADMIN' | 'INTERNAL'
}

export interface RouteDefinitionCreateRequest {
  routeKey: string
  path: string
  componentKey: string
  title: string
  description?: string
  menuGroup?: string
  icon?: string
  order?: number
  parentRouteKey?: string
  requiredPermissions?: string[]
  requiredRoles?: string[]
  requiredEntitlements?: string[]
  requiredTier?: string
  requiredFeatures?: string[]
  supportedSources?: ('WEB' | 'MCP' | 'ADMIN' | 'INTERNAL')[]
  visible?: boolean
  enabled?: boolean
}

export interface RouteDefinitionUpdateRequest {
  path?: string
  componentKey?: string
  title?: string
  description?: string
  menuGroup?: string
  icon?: string
  order?: number
  parentRouteKey?: string
  requiredPermissions?: string[]
  requiredRoles?: string[]
  requiredEntitlements?: string[]
  requiredTier?: string
  requiredFeatures?: string[]
  supportedSources?: ('WEB' | 'MCP' | 'ADMIN' | 'INTERNAL')[]
  visible?: boolean
  enabled?: boolean
}

export interface NavigationPolicy {
  policyId: string
  routeKey: string
  policyType: 'RBAC' | 'ABAC' | 'ENTITLEMENT' | 'TIER' | 'FEATURE'
  condition: string
  effect: 'SHOW' | 'HIDE' | 'DISABLE'
  reasonCode: string
  reasonMessage: string
  upgradeOptions: string[]
  priority: number
}
