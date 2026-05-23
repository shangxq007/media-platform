export interface NavItem {
  key: string
  label: string
  /** Lucide icon name — see AppIcon registry */
  icon: string
  path: string
  divider?: boolean
  section?: string
}

/** Default workspace for collaboration links until workspace picker is wired. */
export const DEFAULT_WORKSPACE_ID = 'ws-default'

export const userNavItems: NavItem[] = [
  { key: 'dashboard', label: 'Dashboard', icon: 'layout-dashboard', path: '/me', section: 'Home' },
  { key: 'projects', label: 'Projects', icon: 'folder-open', path: '/me/projects' },
  { key: 'shared', label: 'Shared', icon: 'share-2', path: '/me/shared-resources' },
  { key: 'exports', label: 'Exports', icon: 'upload', path: '/me/exports' },
  { key: 'delivery', label: 'Delivery', icon: 'package', path: '/me/delivery-destinations' },

  { key: 'divider-publish', label: '', icon: '', path: '', divider: true },
  { key: 'publish', label: 'Publish', icon: 'share-2', path: '/me/publish', section: 'Social' },
  { key: 'scheduler', label: 'Scheduler', icon: 'calendar', path: '/me/scheduler' },
  { key: 'publish-history', label: 'Publish History', icon: 'history', path: '/me/publish-history' },

  { key: 'divider-tools', label: '', icon: '', path: '', divider: true },
  { key: 'editor', label: 'Video Editor', icon: 'clapperboard', path: '/', section: 'Tools' },
  { key: 'prompts', label: 'Prompts', icon: 'bot', path: '/prompts' },
  { key: 'effect-packs', label: 'Effect Packs', icon: 'sparkles', path: '/effect-packs' },

  { key: 'divider-account', label: '', icon: '', path: '', divider: true },
  { key: 'capabilities', label: 'Capabilities', icon: 'shield', path: '/me/capabilities', section: 'Account' },
  { key: 'usage', label: 'Usage', icon: 'bar-chart-3', path: '/me/usage' },
  { key: 'billing', label: 'Billing', icon: 'credit-card', path: '/me/billing' },
  { key: 'credits', label: 'Credits', icon: 'wallet', path: '/me/credits' },

  { key: 'divider-insights', label: '', icon: '', path: '', divider: true },
  { key: 'reports', label: 'Reports', icon: 'file-text', path: '/me/reports', section: 'Insights' },
  { key: 'analytics', label: 'Analytics', icon: 'line-chart', path: '/me/analytics' },

  { key: 'divider-settings', label: '', icon: '', path: '', divider: true },
  { key: 'notifications', label: 'Inbox', icon: 'bell', path: '/me/notifications', section: 'Settings' },
  { key: 'notification-settings', label: 'Notification Settings', icon: 'bell-off', path: '/me/notification-settings' },
  { key: 'feedback', label: 'Feedback', icon: 'message-circle', path: '/me/feedback' },
  { key: 'settings', label: 'Settings', icon: 'settings', path: '/me/settings' },

  { key: 'divider-workspace', label: '', icon: '', path: '', divider: true },
  { key: 'workspace-members', label: 'Workspace Members', icon: 'users', path: `/workspace/${DEFAULT_WORKSPACE_ID}/members`, section: 'Workspace' },
  { key: 'workspace-roles', label: 'Roles & Permissions', icon: 'key-round', path: `/workspace/${DEFAULT_WORKSPACE_ID}/roles` },
]

/** Map quick-action keys from API to Lucide icons */
export const quickActionIcons: Record<string, string> = {
  new_project: 'plus',
  open_editor: 'clapperboard',
  export: 'upload',
  prompts: 'bot',
  effect_packs: 'sparkles',
  billing: 'credit-card',
}
