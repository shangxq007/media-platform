export const ACTIVE_RENDER_STATUSES = ['QUEUED', 'SELECTING_PROVIDER', 'PROVIDER_SELECTED', 'EXECUTING', 'COMPLETING'] as const
export const TERMINAL_RENDER_STATUSES = ['COMPLETED', 'FAILED', 'CANCELED'] as const

export function isActiveRenderStatus(status: string): boolean {
  return ACTIVE_RENDER_STATUSES.includes(status as any)
}

export function isTerminalRenderStatus(status: string): boolean {
  return TERMINAL_RENDER_STATUSES.includes(status as any)
}

export function getRenderPollingInterval(status: string): number | false {
  if (isTerminalRenderStatus(status)) return false
  if (isActiveRenderStatus(status)) return 5000
  return false
}
