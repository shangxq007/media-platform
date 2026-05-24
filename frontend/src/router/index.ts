import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import EditorPage from '@/pages/EditorPage.vue'
import PromptManagementPage from '@/pages/PromptManagementPage.vue'
import EffectPackEditor from '@/pages/EffectPackEditor.vue'
import AdminLayout from '@/components/admin/AdminLayout.vue'

// Task 21: User-side entitlement & billing pages
import MyCapabilitiesPage from '@/pages/entitlement/MyCapabilitiesPage.vue'
// Task 9-12: User portal pages
import UserDashboardPage from '@/pages/user/UserDashboardPage.vue'
import MyProjectsPage from '@/pages/user/MyProjectsPage.vue'
import UserMyCapabilitiesPage from '@/pages/user/MyCapabilitiesPage.vue'
import MyUsagePage from '@/pages/user/MyUsagePage.vue'
import MyBillingPage from '@/pages/user/MyBillingPage.vue'
import MyCreditsPage from '@/pages/user/MyCreditsPage.vue'
import MyFeedbackPage from '@/pages/user/MyFeedbackPage.vue'
import MySettingsPage from '@/pages/user/MySettingsPage.vue'
import MyExportsPage from '@/pages/user/MyExportsPage.vue'
import MyNotificationsPage from '@/pages/user/MyNotificationsPage.vue'
import MySharedResourcesPage from '@/pages/user/MySharedResourcesPage.vue'

import AnalyticsAssistantPage from '@/pages/analytics/AnalyticsAssistantPage.vue'
import MyReportsPage from '@/pages/analytics/MyReportsPage.vue'

// System pages
import ForbiddenPage from '@/pages/system/ForbiddenPage.vue'
import OauthCallbackPage from '@/pages/OauthCallbackPage.vue'
import RouteDisabledPage from '@/pages/system/RouteDisabledPage.vue'
import UpgradeRequiredPage from '@/pages/system/UpgradeRequiredPage.vue'

import { navigationGuard } from './guards'

const staticRoutes: RouteRecordRaw[] = [
  { path: '/', name: 'editor', component: EditorPage },
  { path: '/project/:id', name: 'project', component: EditorPage },
  { path: '/prompts', name: 'prompts', component: PromptManagementPage },
  { path: '/prompts/:templateId', name: 'prompt-editor', component: PromptManagementPage },
  { path: '/effect-packs', name: 'effect-packs', component: EffectPackEditor },

  { path: '/oauth/callback', name: 'oauth-callback', component: OauthCallbackPage },

  // System pages
  { path: '/forbidden', name: 'forbidden', component: ForbiddenPage },
  { path: '/route-disabled', name: 'route-disabled', component: RouteDisabledPage },
  { path: '/upgrade-required', name: 'upgrade-required', component: UpgradeRequiredPage },

  // Task 21: User-side routes (legacy entitlement pages)
  { path: '/me/plan', name: 'me-plan', component: MyCapabilitiesPage },
  { path: '/me/upgrades', name: 'me-upgrades', component: MyCapabilitiesPage },

  // Task 9-12: User portal routes
  { path: '/me', name: 'me-dashboard', component: UserDashboardPage },
  { path: '/me/projects', name: 'me-projects', component: MyProjectsPage },
  { path: '/me/capabilities', name: 'me-capabilities', component: UserMyCapabilitiesPage },
  { path: '/me/usage', name: 'me-usage', component: MyUsagePage },
  { path: '/me/billing', name: 'me-billing', component: MyBillingPage },
  { path: '/me/credits', name: 'me-credits', component: MyCreditsPage },
  { path: '/me/feedback', name: 'me-feedback', component: MyFeedbackPage },
  { path: '/me/settings', name: 'me-settings', component: MySettingsPage },
  { path: '/me/exports', name: 'me-exports', component: MyExportsPage },
  { path: '/me/delivery-destinations', name: 'me-delivery-destinations', component: () => import('@/pages/user/DeliveryDestinationsPage.vue') },
  { path: '/me/notifications', name: 'me-notifications', component: MyNotificationsPage },
  { path: '/me/shared-resources', name: 'me-shared-resources', component: MySharedResourcesPage },
  { path: '/me/notification-settings', name: 'me-notification-settings', component: () => import('@/pages/user/NotificationSettingsPage.vue') },
  { path: '/me/publish', name: 'me-publish', component: () => import('@/pages/user/SocialPublishPage.vue') },
  { path: '/me/scheduler', name: 'me-scheduler', component: () => import('@/pages/user/PostSchedulerPage.vue') },
  { path: '/me/publish-history', name: 'me-publish-history', component: () => import('@/pages/user/PublishHistoryPage.vue') },

  // NLQ user-side routes
  { path: '/me/analytics', name: 'me-analytics', component: AnalyticsAssistantPage },
  { path: '/me/reports', name: 'me-reports', component: MyReportsPage },

  // Task 22: Workspace routes
  { path: '/workspace/:workspaceId/members', name: 'workspace-members', component: () => import('@/pages/workspace/WorkspaceMembersPage.vue') },
  { path: '/workspace/:workspaceId/roles', name: 'workspace-roles', component: () => import('@/pages/workspace/RoleManagementPanel.vue') },
  { path: '/workspace/:workspaceId/entitlements/pool', name: 'workspace-pool', component: () => import('@/pages/workspace/WorkspaceEntitlementPoolPanel.vue') },
  { path: '/workspace/:workspaceId/entitlements/grants', name: 'workspace-grants', component: () => import('@/pages/workspace/WorkspaceMemberGrantsPage.vue') },
  { path: '/workspace/:workspaceId/entitlements/groups', name: 'workspace-groups', component: () => import('@/pages/workspace/WorkspaceGroupGrantPanel.vue') },
  { path: '/workspace/:workspaceId/entitlements/quota', name: 'workspace-quota', component: () => import('@/pages/workspace/QuotaAllocationEditor.vue') },
  { path: '/workspace/:workspaceId/entitlements/preview', name: 'workspace-preview', component: () => import('@/pages/workspace/EntitlementDecisionPreview.vue') },
  { path: '/workspace/:workspaceId/entitlements/debug', name: 'workspace-debug', component: () => import('@/pages/workspace/AccessDecisionDebugPanel.vue') },

  {
    path: '/admin',
    component: AdminLayout,
    children: [
      { path: '', name: 'admin-dashboard', component: () => import('@/pages/admin/AdminDashboard.vue') },
      { path: 'tenants', name: 'admin-tenants', component: () => import('@/pages/admin/TenantManagement.vue') },
      { path: 'render-jobs', name: 'admin-render-jobs', component: () => import('@/pages/admin/RenderJobManagement.vue') },
      { path: 'delivery', name: 'admin-delivery', component: () => import('@/pages/admin/DeliveryAdminPage.vue') },
      { path: 'extensions', name: 'admin-extensions', component: () => import('@/pages/admin/ExtensionManagement.vue') },
      { path: 'quota-billing', name: 'admin-quota-billing', component: () => import('@/pages/admin/QuotaBilling.vue') },
      { path: 'analytics', name: 'admin-analytics', component: () => import('@/pages/admin/UserAnalytics.vue') },
      { path: 'notifications', name: 'admin-notifications', component: () => import('@/pages/admin/NotificationManagement.vue') },
      { path: 'notifications/overview', name: 'admin-notifications-overview', component: () => import('@/pages/admin/NotificationAdminPage.vue') },
      { path: 'notifications/events', name: 'admin-notification-events', component: () => import('@/pages/admin/NotificationEventDefinitionPage.vue') },
      { path: 'notifications/deliveries', name: 'admin-notification-deliveries', component: () => import('@/pages/admin/NotificationDeliveryLogPage.vue') },
      { path: 'audit', name: 'admin-audit', component: () => import('@/pages/admin/AuditCompliance.vue') },
      { path: 'config', name: 'admin-config', component: () => import('@/pages/admin/ConfigManagement.vue') },
      { path: 'feature-flags', name: 'admin-feature-flags', component: () => import('@/pages/admin/FeatureFlags.vue') },
      { path: 'feature-flags/manage', name: 'admin-feature-flag-mgmt', component: () => import('@/pages/admin/FeatureFlagManagementPage.vue') },
      { path: 'policies', name: 'admin-policies', component: () => import('@/pages/admin/PolicyManagementPage.vue') },
      { path: 'routes', name: 'admin-routes', component: () => import('@/pages/admin/RouteManagementPage.vue') },
      { path: 'monitoring', name: 'admin-monitoring', component: () => import('@/pages/admin/MonitoringFeedbackPage.vue') },
      { path: 'audit-log', name: 'admin-audit-log', component: () => import('@/pages/admin/AuditLogPage.vue') },
      { path: 'feedback', name: 'admin-feedback', component: () => import('@/pages/admin/FeedbackAdminPage.vue') },

      // NLQ admin routes
      { path: 'analytics/datasets', name: 'admin-nlq-datasets', component: () => import('@/pages/admin/DatasetCatalogPage.vue') },
      { path: 'analytics/query-audit', name: 'admin-nlq-query-audit', component: () => import('@/pages/admin/QueryAuditPage.vue') },

      // Task 23: Admin entitlement & billing routes
      { path: 'entitlements/bundles', name: 'admin-entitlement-bundles', component: () => import('@/pages/admin/EntitlementBundleList.vue') },
      { path: 'entitlements/overrides', name: 'admin-entitlement-overrides', component: () => import('@/pages/admin/TenantOverridePanel.vue') },
      { path: 'entitlements/grants', name: 'admin-entitlement-grants', component: () => import('@/pages/admin/UserGrantPanel.vue') },
      { path: 'entitlements/shared-grants', name: 'admin-shared-grants', component: () => import('@/pages/admin/SharedGrantsAdminPage.vue') },
      { path: 'entitlements/quota', name: 'admin-entitlement-quota', component: () => import('@/pages/admin/QuotaPolicyEditor.vue') },
      { path: 'billing/plans', name: 'admin-billing-plans', component: () => import('@/pages/admin/BillingPlanManagementPage.vue') },
      { path: 'billing/pricing', name: 'admin-billing-pricing', component: () => import('@/pages/admin/PricingRuleEditor.vue') },
      { path: 'billing/usage', name: 'admin-billing-usage', component: () => import('@/pages/admin/UsageLedgerPage.vue') },
      { path: 'billing/credits', name: 'admin-billing-credits', component: () => import('@/pages/admin/CreditWalletAdminPanel.vue') },
      { path: 'billing/quotes', name: 'admin-billing-quotes', component: () => import('@/pages/admin/BillingQuotePanel.vue') },
      { path: 'billing/invoices', name: 'admin-billing-invoices', component: () => import('@/pages/admin/InvoicePreviewPage.vue') },
    ]
  }
]

const componentMap: Record<string, () => Promise<typeof import('*.vue')>> = {
  EditorPage: () => import('@/pages/EditorPage.vue'),
  PromptManagementPage: () => import('@/pages/PromptManagementPage.vue'),
  EffectPackEditor: () => import('@/pages/EffectPackEditor.vue'),
  AdminLayout: () => import('@/components/admin/AdminLayout.vue'),
  AdminDashboard: () => import('@/pages/admin/AdminDashboard.vue'),
  TenantManagement: () => import('@/pages/admin/TenantManagement.vue'),
  RenderJobManagement: () => import('@/pages/admin/RenderJobManagement.vue'),
  ExtensionManagement: () => import('@/pages/admin/ExtensionManagement.vue'),
  QuotaBilling: () => import('@/pages/admin/QuotaBilling.vue'),
  UserAnalytics: () => import('@/pages/admin/UserAnalytics.vue'),
  NotificationManagement: () => import('@/pages/admin/NotificationManagement.vue'),
  NotificationAdminPage: () => import('@/pages/admin/NotificationAdminPage.vue'),
  NotificationEventDefinitionPage: () => import('@/pages/admin/NotificationEventDefinitionPage.vue'),
  NotificationDeliveryLogPage: () => import('@/pages/admin/NotificationDeliveryLogPage.vue'),
  AuditCompliance: () => import('@/pages/admin/AuditCompliance.vue'),
  ConfigManagement: () => import('@/pages/admin/ConfigManagement.vue'),
  FeatureFlags: () => import('@/pages/admin/FeatureFlags.vue'),
  FeatureFlagManagementPage: () => import('@/pages/admin/FeatureFlagManagementPage.vue'),
  PolicyManagementPage: () => import('@/pages/admin/PolicyManagementPage.vue'),
  RouteManagementPage: () => import('@/pages/admin/RouteManagementPage.vue'),
  FeedbackAdminPage: () => import('@/pages/admin/FeedbackAdminPage.vue'),
  AuditLogPage: () => import('@/pages/admin/AuditLogPage.vue'),
  UserDashboardPage: () => import('@/pages/user/UserDashboardPage.vue'),
  MyProjectsPage: () => import('@/pages/user/MyProjectsPage.vue'),
  UserMyCapabilitiesPage: () => import('@/pages/user/MyCapabilitiesPage.vue'),
  MyUsagePage: () => import('@/pages/user/MyUsagePage.vue'),
  MyBillingPage: () => import('@/pages/user/MyBillingPage.vue'),
  MyCreditsPage: () => import('@/pages/user/MyCreditsPage.vue'),
  MyFeedbackPage: () => import('@/pages/user/MyFeedbackPage.vue'),
  MySettingsPage: () => import('@/pages/user/MySettingsPage.vue'),
  MyExportsPage: () => import('@/pages/user/MyExportsPage.vue'),
  MyNotificationsPage: () => import('@/pages/user/MyNotificationsPage.vue'),
  MySharedResourcesPage: () => import('@/pages/user/MySharedResourcesPage.vue'),
}

const router = createRouter({
  history: createWebHistory(),
  routes: [...staticRoutes]
})

router.beforeEach(navigationGuard)

export async function syncDynamicRoutes(componentKeys: Record<string, string>) {
  for (const [routeKey, componentKey] of Object.entries(componentKeys)) {
    if (router.hasRoute(routeKey)) continue

    const loader = componentMap[componentKey]
    if (!loader) {
      console.warn(`[router] No component mapping for key: ${componentKey}`)
      continue
    }

    try {
      const component = await loader()
      const existingRoute = router.getRoutes().find(r => r.name === routeKey)
      if (existingRoute) continue
      router.addRoute({ path: `/${routeKey}`, name: routeKey, component: component.default })
    } catch (err) {
      console.warn(`[router] Failed to add dynamic route ${routeKey}:`, err)
    }
  }
}

export function resetRouter() {
  const baseRoutes = [...staticRoutes]
  const currentRoutes = router.getRoutes()
  for (const route of currentRoutes) {
    if (route.name && !staticRoutes.some(sr => sr.name === route.name)) {
      router.removeRoute(route.name.toString())
    }
  }
  router.clearRoutes()
  for (const route of baseRoutes) {
    router.addRoute(route)
  }
}

export default router
