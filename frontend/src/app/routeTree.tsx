import { createRoute, createRootRoute } from '@tanstack/react-router'
import RootLayout from './RootLayout.js'
import { EditorPage } from '../editor/EditorPage.js'
import { RenderJobsPage } from '../render-job/RenderJobsPage.js'
import { CapabilitiesPage } from '../shared/CapabilitiesPage.js'

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
  component: RenderJobsPage,
})

const capabilitiesRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/capabilities',
  component: CapabilitiesPage,
})

export const routeTree = rootRoute.addChildren([
  indexRoute,
  renderJobsRoute,
  capabilitiesRoute,
])
