/** Current tenant id for tenant-scoped APIs (dev default matches backend seed). */
export function getTenantId(): string {
  return localStorage.getItem('tenant_id') || 'tenant-1'
}
