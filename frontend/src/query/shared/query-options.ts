export interface AppQueryScope {
  tenantId: string
  projectId: string
}

export function isScopeComplete(scope: Partial<AppQueryScope>): scope is AppQueryScope {
  return Boolean(scope.tenantId && scope.projectId)
}

export function enabledWhenScoped(scope: Partial<AppQueryScope>): boolean {
  return isScopeComplete(scope)
}
