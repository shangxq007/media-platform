/**
 * DEPRECATED: Do NOT use this value for authentication or tenant-scoped API calls.
 *
 * Tenant ID must be resolved server-side from the authenticated principal
 * (JWT claims / OAuth2 token / TenantContext), never from client-side storage.
 *
 * This function is retained only for UI hinting (e.g. displaying the current
 * workspace context). The returned value is NOT sent to the backend.
 *
 * TODO: Replace with selectedWorkspaceId for UI state only.
 */
export function getTenantId(): string | null {
  return localStorage.getItem('tenant_id') || null
}
