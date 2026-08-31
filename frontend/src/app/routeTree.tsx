import { Suspense, lazy, type ComponentType } from 'react'
import { createRoute, createRootRoute, type RouteComponent } from '@tanstack/react-router'
import RootLayout from './RootLayout.js'
import { EditorPage } from '../editor/EditorPage.js'
import { RenderJobDashboard } from '../pages/RenderJobDashboard.js'
import { CapabilitiesPage } from '../shared/CapabilitiesPage.js'
import { SmokeEditorPage } from '../pages/SmokeEditorPage.js'
import { ObservabilityDashboard } from '../pages/ObservabilityDashboard.js'
import { DevConsolePage } from '../pages/DevConsolePage.js'
import TimelineGitConsolePage from '../pages/TimelineGitConsolePage.js'
import AdminRenderJobsPage from '../pages/AdminRenderJobsPage.js'
import AdminStorageHealthPage from '../pages/AdminStorageHealthPage.js'
import { DevDiagnosticsHubPage } from '../pages/DevDiagnosticsHubPage.js'
import { DevStorageDeliveryProfileDiagnosticsPage } from '../pages/DevStorageDeliveryProfileDiagnosticsPage.js'
import { DevIngestPreflightPolicyDiagnosticsPage } from '../pages/DevIngestPreflightPolicyDiagnosticsPage.js'
import { RenderResultsListPage } from '../routes/app/renders/RenderResultsListPage.js'
import { RenderResultDetailPage } from '../routes/app/renders/RenderResultDetailPage.js'

const RootLandingPage = lazy(() => import('../surfaces/FoundationPages.js').then(module => ({ default: module.RootLandingPage })))
const WorkspaceHomePage = lazy(() => import('../surfaces/FoundationPages.js').then(module => ({ default: module.WorkspaceHomePage })))
const ProjectListPage = lazy(() => import('../surfaces/FoundationPages.js').then(module => ({ default: module.ProjectListPage })))
const ProjectOverviewPage = lazy(() => import('../surfaces/FoundationPages.js').then(module => ({ default: module.ProjectOverviewPage })))
const NlePage = lazy(() => import('../product/timeline/NleWorkspace.js').then(module => ({ default: module.NlePage })))
const CanvasPage = lazy(() => import('../product/canvas/WorkspaceCanvas.js').then(module => ({ default: module.CanvasPage })))
const WorkflowPage = lazy(() => import('../surfaces/FoundationPages.js').then(module => ({ default: module.WorkflowPage })))
const AgentPage = lazy(() => import('../surfaces/FoundationPages.js').then(module => ({ default: module.AgentPage })))
const ReviewPage = lazy(() => import('../product/review/ReviewWorkspace.js').then(module => ({ default: module.ReviewPage })))
const ProductionPage = lazy(() => import('../surfaces/FoundationPages.js').then(module => ({ default: module.ProductionPage })))
const OperationsOverviewPage = lazy(() => import('../surfaces/FoundationPages.js').then(module => ({ default: module.OperationsOverviewPage })))
const OperationsProjectionPage = lazy(() => import('../surfaces/FoundationPages.js').then(module => ({ default: module.OperationsProjectionPage })))
const ManagementFoundationPage = lazy(() => import('../surfaces/FoundationPages.js').then(module => ({ default: module.ManagementFoundationPage })))
const HiddenCreativeFoundationPage = lazy(() => import('../surfaces/FoundationPages.js').then(module => ({ default: module.HiddenCreativeFoundationPage })))

function lazyPage(Component: ComponentType) {
  return function LazyRoutePage() {
    return <Suspense fallback={<div className="ff-route-loading" role="status">Loading surface…</div>}><Component /></Suspense>
  }
}

export const implementedRouteInventory = [
  '/',
  '/w/$workspaceId/home', '/w/$workspaceId/projects',
  '/w/$workspaceId/projects/$projectId/overview', '/w/$workspaceId/projects/$projectId/edit',
  '/w/$workspaceId/projects/$projectId/canvas', '/w/$workspaceId/projects/$projectId/storyboard',
  '/w/$workspaceId/projects/$projectId/script', '/w/$workspaceId/projects/$projectId/workflow',
  '/w/$workspaceId/projects/$projectId/recipe',
  '/w/$workspaceId/projects/$projectId/agent', '/w/$workspaceId/projects/$projectId/review',
  '/w/$workspaceId/projects/$projectId/production', '/operations/overview', '/operations/renders',
  '/operations/storage', '/admin/organization', '/admin/members', '/admin/workspaces', '/admin/roles',
  '/admin/security', '/admin/billing', '/admin/entitlements', '/admin/usage', '/admin/quota',
  '/admin/policies', '/admin/audit', '/developer/capabilities', '/developer/plugins',
  '/developer/providers', '/developer/integrations', '/developer/mcp', '/developer/api-keys',
  '/developer/webhooks', '/developer/agents', '/developer/recipes',
] as const

export const legacyRouteInventory = [
  '/legacy/editor', '/render-jobs', '/capabilities', '/smoke-editor', '/observability',
  '/dev/timeline-git', '/app/renders/$productId', '/admin/storage-health', '/app/renders',
  '/admin/render-jobs', '/dev/preview', '/dev/diagnostics', '/dev/storage-delivery-profiles',
  '/dev/ingest/preflight-policy',
] as const

const rootRoute = createRootRoute({ component: RootLayout })
const route = <const TPath extends string>(path: TPath, component: RouteComponent) =>
  createRoute({ getParentRoute: () => rootRoute, path, component })

const additionalAdminSegments = ['members', 'workspaces', 'roles', 'security', 'billing', 'entitlements', 'usage', 'quota', 'policies', 'audit'] as const
const additionalDeveloperSegments = ['plugins', 'providers', 'integrations', 'mcp', 'api-keys', 'webhooks', 'agents', 'recipes'] as const

const foundationRoutes = [
  route('/', lazyPage(RootLandingPage)),
  route('/w/$workspaceId/home', lazyPage(WorkspaceHomePage)),
  route('/w/$workspaceId/projects', lazyPage(ProjectListPage)),
  route('/w/$workspaceId/projects/$projectId/overview', lazyPage(ProjectOverviewPage)),
  route('/w/$workspaceId/projects/$projectId/edit', lazyPage(NlePage)),
  route('/w/$workspaceId/projects/$projectId/canvas', lazyPage(CanvasPage)),
  route('/w/$workspaceId/projects/$projectId/storyboard', lazyPage(HiddenCreativeFoundationPage)),
  route('/w/$workspaceId/projects/$projectId/script', lazyPage(HiddenCreativeFoundationPage)),
  route('/w/$workspaceId/projects/$projectId/workflow', lazyPage(WorkflowPage)),
  route('/w/$workspaceId/projects/$projectId/recipe', lazyPage(HiddenCreativeFoundationPage)),
  route('/w/$workspaceId/projects/$projectId/agent', lazyPage(AgentPage)),
  route('/w/$workspaceId/projects/$projectId/review', lazyPage(ReviewPage)),
  route('/w/$workspaceId/projects/$projectId/production', lazyPage(ProductionPage)),
  route('/operations/overview', lazyPage(OperationsOverviewPage)),
  route('/operations/renders', lazyPage(OperationsProjectionPage)),
  route('/operations/storage', lazyPage(OperationsProjectionPage)),
  route('/admin/organization', lazyPage(ManagementFoundationPage)),
  ...additionalAdminSegments.map(segment => route(`/admin/${segment}`, lazyPage(ManagementFoundationPage))),
  route('/developer/capabilities', lazyPage(ManagementFoundationPage)),
  ...additionalDeveloperSegments.map(segment => route(`/developer/${segment}`, lazyPage(ManagementFoundationPage))),
]

const legacyRoutes = [
  route('/legacy/editor', EditorPage), route('/render-jobs', RenderJobDashboard),
  route('/capabilities', CapabilitiesPage), route('/smoke-editor', SmokeEditorPage),
  route('/observability', ObservabilityDashboard), route('/dev/timeline-git', TimelineGitConsolePage),
  route('/app/renders/$productId', RenderResultDetailPage), route('/admin/storage-health', AdminStorageHealthPage),
  route('/app/renders', RenderResultsListPage), route('/admin/render-jobs', AdminRenderJobsPage),
  route('/dev/preview', DevConsolePage), route('/dev/diagnostics', DevDiagnosticsHubPage),
  route('/dev/storage-delivery-profiles', DevStorageDeliveryProfileDiagnosticsPage),
  route('/dev/ingest/preflight-policy', DevIngestPreflightPolicyDiagnosticsPage),
]

export const routeTree = rootRoute.addChildren([...foundationRoutes, ...legacyRoutes])
