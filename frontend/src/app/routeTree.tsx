import { createRoute, createRootRoute } from '@tanstack/react-router'
import RootLayout from './RootLayout.js'
import { EditorPage } from '../editor/EditorPage.js'
import { RenderJobDashboard } from '../pages/RenderJobDashboard.js'
import { CapabilitiesPage } from '../shared/CapabilitiesPage.js'
import { SmokeEditorPage } from '../pages/SmokeEditorPage.js'
import { ObservabilityDashboard } from '../pages/ObservabilityDashboard.js'
import { DevConsolePage } from '../pages/DevConsolePage.js'
import TimelineGitConsolePage from '../pages/TimelineGitConsolePage.js'
import AdminRenderJobsPage from '../pages/AdminRenderJobsPage.js'
import UserRenderHistoryPage from '../pages/UserRenderHistoryPage.js'
import AdminStorageHealthPage from '../pages/AdminStorageHealthPage.js'

const rootRoute = createRootRoute({
  component: RootLayout,
})

const indexRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/',
  component: EditorPage,
})

const renderJobsRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/render-jobs',
  component: RenderJobDashboard,
})

const capabilitiesRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/capabilities',
  component: CapabilitiesPage,
})

const smokeEditorRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/smoke-editor',
  component: SmokeEditorPage,
})

const observabilityRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/observability',
  component: ObservabilityDashboard,
})

const timelineGitRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/dev/timeline-git',
  component: TimelineGitConsolePage,
})

const adminStorageHealthRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/admin/storage-health',
  component: AdminStorageHealthPage,
})

const userRenderHistoryRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/app/renders',
  component: UserRenderHistoryPage,
})

const adminRenderJobsRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/admin/render-jobs',
  component: AdminRenderJobsPage,
})

const devConsoleRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/dev/preview',
  component: DevConsolePage,
})

export const routeTree = rootRoute.addChildren([
  indexRoute,
  renderJobsRoute,
  capabilitiesRoute,
  smokeEditorRoute,
  observabilityRoute,
  devConsoleRoute,
  timelineGitRoute,
  adminRenderJobsRoute,
  userRenderHistoryRoute,
  adminStorageHealthRoute,
])
