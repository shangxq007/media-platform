export interface FallbackNavRoute {
  routeKey: string
  path: string
  title: string
  icon: string
  menuGroup: string
  order: number
}

export const FALLBACK_USER_ROUTES: FallbackNavRoute[] = [
  { routeKey: 'me-dashboard', path: '/me', title: 'Dashboard', icon: '📊', menuGroup: 'main', order: 10 },
  { routeKey: 'me-projects', path: '/me/projects', title: 'Projects', icon: '📁', menuGroup: 'main', order: 20 },
  { routeKey: 'me-shared-resources', path: '/me/shared-resources', title: 'Shared With Me', icon: '🔗', menuGroup: 'content', order: 25 },
  { routeKey: 'editor', path: '/', title: 'Editor', icon: '✂️', menuGroup: 'main', order: 30 },
  { routeKey: 'me-exports', path: '/me/exports', title: 'Exports', icon: '📤', menuGroup: 'content', order: 40 },
  { routeKey: 'me-publish', path: '/me/publish', title: 'Publish', icon: '📱', menuGroup: 'content', order: 35 },
  { routeKey: 'me-scheduler', path: '/me/scheduler', title: 'Scheduler', icon: '📅', menuGroup: 'content', order: 36 },
  { routeKey: 'me-publish-history', path: '/me/publish-history', title: 'Publish History', icon: '📋', menuGroup: 'content', order: 37 },
  { routeKey: 'me-reports', path: '/me/reports', title: 'Reports', icon: '📊', menuGroup: 'content', order: 90 },
  { routeKey: 'me-capabilities', path: '/me/capabilities', title: 'Capabilities', icon: '🛡️', menuGroup: 'account', order: 50 },
  { routeKey: 'me-usage', path: '/me/usage', title: 'Usage', icon: '📈', menuGroup: 'account', order: 60 },
  { routeKey: 'me-billing', path: '/me/billing', title: 'Billing', icon: '💳', menuGroup: 'account', order: 70 },
  { routeKey: 'me-credits', path: '/me/credits', title: 'Credits', icon: '💰', menuGroup: 'account', order: 80 },
  { routeKey: 'me-feedback', path: '/me/feedback', title: 'Feedback', icon: '💬', menuGroup: 'support', order: 100 },
  { routeKey: 'me-notifications', path: '/me/notifications', title: 'Notifications', icon: '🔔', menuGroup: 'support', order: 110 },
  { routeKey: 'me-notification-settings', path: '/me/notification-settings', title: 'Notification Settings', icon: '🔔', menuGroup: 'support', order: 115 },
  { routeKey: 'me-settings', path: '/me/settings', title: 'Settings', icon: '⚙️', menuGroup: 'account', order: 120 },
]

export const FALLBACK_ADMIN_ROUTES: FallbackNavRoute[] = [
  { routeKey: 'admin-dashboard', path: '/admin', title: 'Admin Console', icon: '🔐', menuGroup: 'admin', order: 10 },
]
