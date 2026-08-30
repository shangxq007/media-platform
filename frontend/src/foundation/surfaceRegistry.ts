import type { ReferenceKind } from './references'

export type SurfaceMaturity = 'FOUNDATION' | 'PREVIEW' | 'AVAILABLE' | 'HIDDEN'
export type SurfaceCategory =
  | 'WORKSPACE'
  | 'CREATIVE'
  | 'REVIEW'
  | 'PRODUCTION'
  | 'OPERATIONS'
  | 'ADMIN'
  | 'DEVELOPER'

export type SurfaceId =
  | 'workspace'
  | 'project-overview'
  | 'nle'
  | 'canvas'
  | 'storyboard'
  | 'screenplay'
  | 'agent'
  | 'workflow'
  | 'recipe'
  | 'review'
  | 'production'
  | 'operations'
  | 'admin'
  | 'developer'

export type ShellRegionId =
  | 'workspace-header'
  | 'global-navigation'
  | 'project-navigation'
  | 'surface-switcher'
  | 'asset-browser'
  | 'inspector'
  | 'center-workspace'
  | 'bottom-panel'
  | 'activity-panel'

export interface SurfaceRouteContext {
  workspaceId?: string
  projectId?: string
}

export interface SurfaceDefinition {
  readonly id: SurfaceId
  readonly displayName: string
  readonly icon: string
  readonly routeTemplate: string
  readonly buildRoute: (context: SurfaceRouteContext) => string
  readonly category: SurfaceCategory
  readonly projectScoped: boolean
  readonly requiredBackendCapabilityIds: readonly string[]
  readonly requiredEffectiveAccessKey: string | null
  readonly shellRegions: Readonly<Record<ShellRegionId, 'VISIBLE' | 'HIDDEN' | 'COLLAPSIBLE' | 'RESIZABLE'>>
  readonly compatibleReferenceKinds: readonly ReferenceKind[]
  readonly maturity: SurfaceMaturity
}

const workspaceRegions: SurfaceDefinition['shellRegions'] = {
  'workspace-header': 'VISIBLE',
  'global-navigation': 'VISIBLE',
  'project-navigation': 'HIDDEN',
  'surface-switcher': 'HIDDEN',
  'asset-browser': 'HIDDEN',
  inspector: 'HIDDEN',
  'center-workspace': 'VISIBLE',
  'bottom-panel': 'HIDDEN',
  'activity-panel': 'COLLAPSIBLE',
}

const creativeRegions: SurfaceDefinition['shellRegions'] = {
  'workspace-header': 'VISIBLE',
  'global-navigation': 'VISIBLE',
  'project-navigation': 'VISIBLE',
  'surface-switcher': 'VISIBLE',
  'asset-browser': 'RESIZABLE',
  inspector: 'RESIZABLE',
  'center-workspace': 'VISIBLE',
  'bottom-panel': 'RESIZABLE',
  'activity-panel': 'COLLAPSIBLE',
}

function scopedRoute(segment: string) {
  return ({ workspaceId, projectId }: SurfaceRouteContext) =>
    `/w/${encodeURIComponent(workspaceId ?? '')}/projects/${encodeURIComponent(projectId ?? '')}/${segment}`
}

function workspaceRoute(segment: string) {
  return ({ workspaceId }: SurfaceRouteContext) =>
    `/w/${encodeURIComponent(workspaceId ?? '')}/${segment}`
}

const allCreativeReferences: readonly ReferenceKind[] = [
  'PROJECT', 'MEDIA_ASSET', 'ARTIFACT', 'TIMELINE', 'REVISION', 'RENDER', 'WORKFLOW',
]

export const surfaceRegistry = [
  { id: 'workspace', displayName: 'Workspace', icon: 'home', routeTemplate: '/w/$workspaceId/home', buildRoute: workspaceRoute('home'), category: 'WORKSPACE', projectScoped: false, requiredBackendCapabilityIds: [], requiredEffectiveAccessKey: null, shellRegions: workspaceRegions, compatibleReferenceKinds: ['PROJECT', 'MEDIA_ASSET', 'ARTIFACT', 'RENDER'], maturity: 'FOUNDATION' },
  { id: 'project-overview', displayName: 'Overview', icon: 'project', routeTemplate: '/w/$workspaceId/projects/$projectId/overview', buildRoute: scopedRoute('overview'), category: 'WORKSPACE', projectScoped: true, requiredBackendCapabilityIds: [], requiredEffectiveAccessKey: 'project.view', shellRegions: creativeRegions, compatibleReferenceKinds: allCreativeReferences, maturity: 'FOUNDATION' },
  { id: 'nle', displayName: 'Edit', icon: 'timeline', routeTemplate: '/w/$workspaceId/projects/$projectId/edit', buildRoute: scopedRoute('edit'), category: 'CREATIVE', projectScoped: true, requiredBackendCapabilityIds: ['timeline.revision.query', 'operation.apply'], requiredEffectiveAccessKey: 'surface.nle.view', shellRegions: creativeRegions, compatibleReferenceKinds: allCreativeReferences, maturity: 'FOUNDATION' },
  { id: 'canvas', displayName: 'Canvas', icon: 'canvas', routeTemplate: '/w/$workspaceId/projects/$projectId/canvas', buildRoute: scopedRoute('canvas'), category: 'CREATIVE', projectScoped: true, requiredBackendCapabilityIds: ['operation.apply'], requiredEffectiveAccessKey: 'surface.canvas.view', shellRegions: creativeRegions, compatibleReferenceKinds: allCreativeReferences, maturity: 'PREVIEW' },
  { id: 'storyboard', displayName: 'Storyboard', icon: 'storyboard', routeTemplate: '/w/$workspaceId/projects/$projectId/storyboard', buildRoute: scopedRoute('storyboard'), category: 'CREATIVE', projectScoped: true, requiredBackendCapabilityIds: ['operation.apply'], requiredEffectiveAccessKey: 'surface.storyboard.view', shellRegions: creativeRegions, compatibleReferenceKinds: ['PROJECT', 'MEDIA_ASSET', 'ARTIFACT', 'REVISION'], maturity: 'HIDDEN' },
  { id: 'screenplay', displayName: 'Screenplay', icon: 'script', routeTemplate: '/w/$workspaceId/projects/$projectId/script', buildRoute: scopedRoute('script'), category: 'CREATIVE', projectScoped: true, requiredBackendCapabilityIds: ['operation.apply'], requiredEffectiveAccessKey: 'surface.screenplay.view', shellRegions: creativeRegions, compatibleReferenceKinds: ['PROJECT', 'REVISION'], maturity: 'HIDDEN' },
  { id: 'agent', displayName: 'Agent', icon: 'agent', routeTemplate: '/w/$workspaceId/projects/$projectId/agent', buildRoute: scopedRoute('agent'), category: 'CREATIVE', projectScoped: true, requiredBackendCapabilityIds: ['operation.plan'], requiredEffectiveAccessKey: 'surface.agent.view', shellRegions: creativeRegions, compatibleReferenceKinds: allCreativeReferences, maturity: 'PREVIEW' },
  { id: 'workflow', displayName: 'Workflow', icon: 'workflow', routeTemplate: '/w/$workspaceId/projects/$projectId/workflow', buildRoute: scopedRoute('workflow'), category: 'CREATIVE', projectScoped: true, requiredBackendCapabilityIds: ['workflow.definition.query'], requiredEffectiveAccessKey: 'surface.workflow.view', shellRegions: creativeRegions, compatibleReferenceKinds: allCreativeReferences, maturity: 'PREVIEW' },
  { id: 'recipe', displayName: 'Recipe / Template', icon: 'recipe', routeTemplate: '/w/$workspaceId/projects/$projectId/recipe', buildRoute: scopedRoute('recipe'), category: 'CREATIVE', projectScoped: true, requiredBackendCapabilityIds: ['workflow.definition.query'], requiredEffectiveAccessKey: 'surface.recipe.view', shellRegions: creativeRegions, compatibleReferenceKinds: allCreativeReferences, maturity: 'HIDDEN' },
  { id: 'review', displayName: 'Review', icon: 'review', routeTemplate: '/w/$workspaceId/projects/$projectId/review', buildRoute: scopedRoute('review'), category: 'REVIEW', projectScoped: true, requiredBackendCapabilityIds: ['timeline.revision.compare', 'review.query'], requiredEffectiveAccessKey: 'surface.review.view', shellRegions: creativeRegions, compatibleReferenceKinds: allCreativeReferences, maturity: 'PREVIEW' },
  { id: 'production', displayName: 'Production', icon: 'production', routeTemplate: '/w/$workspaceId/projects/$projectId/production', buildRoute: scopedRoute('production'), category: 'PRODUCTION', projectScoped: true, requiredBackendCapabilityIds: [], requiredEffectiveAccessKey: 'surface.production.view', shellRegions: creativeRegions, compatibleReferenceKinds: allCreativeReferences, maturity: 'PREVIEW' },
  { id: 'operations', displayName: 'Operations', icon: 'operations', routeTemplate: '/operations/overview', buildRoute: () => '/operations/overview', category: 'OPERATIONS', projectScoped: false, requiredBackendCapabilityIds: ['render.job.query'], requiredEffectiveAccessKey: 'surface.operations.view', shellRegions: workspaceRegions, compatibleReferenceKinds: ['PROJECT', 'ARTIFACT', 'RENDER', 'WORKFLOW'], maturity: 'FOUNDATION' },
  { id: 'admin', displayName: 'Admin', icon: 'admin', routeTemplate: '/admin/organization', buildRoute: () => '/admin/organization', category: 'ADMIN', projectScoped: false, requiredBackendCapabilityIds: [], requiredEffectiveAccessKey: 'surface.admin.view', shellRegions: workspaceRegions, compatibleReferenceKinds: ['PROJECT'], maturity: 'FOUNDATION' },
  { id: 'developer', displayName: 'Developer', icon: 'developer', routeTemplate: '/developer/capabilities', buildRoute: () => '/developer/capabilities', category: 'DEVELOPER', projectScoped: false, requiredBackendCapabilityIds: ['capability.catalog.query'], requiredEffectiveAccessKey: 'surface.developer.view', shellRegions: workspaceRegions, compatibleReferenceKinds: ['WORKFLOW'], maturity: 'PREVIEW' },
] as const satisfies readonly SurfaceDefinition[]

export function getSurface(id: SurfaceId): SurfaceDefinition {
  const surface = surfaceRegistry.find(candidate => candidate.id === id)
  if (!surface) throw new Error(`Unknown surface: ${id}`)
  return surface
}

export function detectRegistryConflicts(): string[] {
  const duplicates = (values: readonly string[]) =>
    values.filter((value, index) => values.indexOf(value) !== index)
  return [
    ...duplicates(surfaceRegistry.map(surface => surface.id)).map(id => `duplicate-id:${id}`),
    ...duplicates(surfaceRegistry.map(surface => surface.routeTemplate)).map(route => `duplicate-route:${route}`),
  ]
}
