import api from './index'
import type {
  NavigationProfile,
  RouteVisibilityDecision,
  NavigationPreviewRequest,
  FrontendRouteDefinition,
  RouteDefinitionCreateRequest,
  RouteDefinitionUpdateRequest,
  NavigationPolicy
} from '@/types/routing'

export const NavigationClient = {
  async getNavigation(): Promise<NavigationProfile> {
    try {
      const { data } = await api.get('/me/navigation')
      return data
    } catch (err) {
      console.warn('[NavigationClient] Failed to fetch navigation, using fallback:', err)
      return { routes: [], menuGroups: {} }
    }
  },

  async getRoutes(): Promise<RouteVisibilityDecision[]> {
    try {
      const { data } = await api.get('/me/routes')
      return data
    } catch (err) {
      console.warn('[NavigationClient] Failed to fetch routes, using fallback:', err)
      return []
    }
  },

  async previewNavigation(request: NavigationPreviewRequest): Promise<NavigationProfile> {
    const { data } = await api.post('/navigation/preview', request)
    return data
  }
}

export const RouteManagementClient = {
  async listRoutes(): Promise<FrontendRouteDefinition[]> {
    const { data } = await api.get('/admin/navigation/routes')
    return data
  },

  async createRoute(request: RouteDefinitionCreateRequest): Promise<FrontendRouteDefinition> {
    const { data } = await api.post('/admin/navigation/routes', request)
    return data
  },

  async updateRoute(routeKey: string, request: RouteDefinitionUpdateRequest): Promise<FrontendRouteDefinition> {
    const { data } = await api.put(`/admin/navigation/routes/${routeKey}`, request)
    return data
  },

  async disableRoute(routeKey: string): Promise<void> {
    await api.post(`/admin/navigation/routes/${routeKey}/disable`)
  },

  async enableRoute(routeKey: string): Promise<void> {
    await api.post(`/admin/navigation/routes/${routeKey}/enable`)
  },

  async previewNavigation(request: NavigationPreviewRequest): Promise<NavigationProfile> {
    const { data } = await api.post('/admin/navigation/preview', request)
    return data
  },

  async listPolicies(): Promise<NavigationPolicy[]> {
    const { data } = await api.get('/admin/navigation/policies')
    return data
  }
}
