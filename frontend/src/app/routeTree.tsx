import { createRoute, createRootRoute } from '@tanstack/react-router'
import RootLayout from './RootLayout.js'
import { EditorPage } from '../editor/EditorPage.js'
import { RenderJobDashboard } from '../pages/RenderJobDashboard.js'
import { CapabilitiesPage } from '../shared/CapabilitiesPage.js'
import { SmokeEditorPage } from '../pages/SmokeEditorPage.js'
import { ObservabilityDashboard } from '../pages/ObservabilityDashboard.js'
import { DevConsolePage } from '../pages/DevConsolePage.js'

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
])
