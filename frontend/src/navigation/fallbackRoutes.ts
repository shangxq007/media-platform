export interface FallbackNavRoute {
  routeKey: string
  path: string
  title: string
  icon: string
  menuGroup: string
  order: number
}

export const FALLBACK_USER_ROUTES: FallbackNavRoute[] = [
  { routeKey: 'me-dashboard', path: '/me', title: 'Dashboard', icon: 'bar-chart-3', menuGroup: 'main', order: 10 },
  { routeKey: 'me-projects', path: '/me/projects', title: 'Projects', icon: 'folder-open', menuGroup: 'main', order: 20 },
  { routeKey: 'me-shared-resources', path: '/me/shared-resources', title: 'Shared With Me', icon: 'share-2', menuGroup: 'content', order: 25 },
  { routeKey: 'editor', path: '/', title: 'Editor', icon: 'clapperboard', menuGroup: 'main', order: 30 },
  { routeKey: 'me-exports', path: '/me/exports', title: 'Exports', icon: 'upload', menuGroup: 'content', order: 40 },
  { routeKey: 'me-publish', path: '/me/publish', title: 'Publish', icon: 'globe', menuGroup: 'content', order: 35 },
  { routeKey: 'me-scheduler', path: '/me/scheduler', title: 'Scheduler', icon: 'calendar', menuGroup: 'content', order: 36 },
  { routeKey: 'me-publish-history', path: '/me/publish-history', title: 'Publish History', icon: 'file-text', menuGroup: 'content', order: 37 },
  { routeKey: 'me-reports', path: '/me/reports', title: 'Reports', icon: 'bar-chart-3', menuGroup: 'content', order: 90 },
  { routeKey: 'me-capabilities', path: '/me/capabilities', title: 'Capabilities', icon: 'shield', menuGroup: 'account', order: 50 },
  { routeKey: 'me-usage', path: '/me/usage', title: 'Usage', icon: 'line-chart', menuGroup: 'account', order: 60 },
  { routeKey: 'me-billing', path: '/me/billing', title: 'Billing', icon: 'credit-card', menuGroup: 'account', order: 70 },
  { routeKey: 'me-credits', path: '/me/credits', title: 'Credits', icon: 'wallet', menuGroup: 'account', order: 80 },
  { routeKey: 'me-feedback', path: '/me/feedback', title: 'Feedback', icon: 'message-circle', menuGroup: 'support', order: 100 },
  { routeKey: 'me-notifications', path: '/me/notifications', title: 'Notifications', icon: 'bell', menuGroup: 'support', order: 110 },
  { routeKey: 'me-notification-settings', path: '/me/notification-settings', title: 'Notification Settings', icon: 'bell', menuGroup: 'support', order: 115 },
  { routeKey: 'me-settings', path: '/me/settings', title: 'Settings', icon: 'settings', menuGroup: 'account', order: 120 },
]

export const FALLBACK_ADMIN_ROUTES: FallbackNavRoute[] = [
  { routeKey: 'admin-dashboard', path: '/admin', title: 'Admin Console', icon: 'key-round', menuGroup: 'admin', order: 10 },
]
