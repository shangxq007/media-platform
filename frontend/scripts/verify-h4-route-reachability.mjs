#!/usr/bin/env node

import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const repositoryRoot = resolve(dirname(fileURLToPath(import.meta.url)), '../..')
const routeTreePath = resolve(repositoryRoot, 'frontend/src/app/routeTree.tsx')
const routeTree = readFileSync(routeTreePath, 'utf8')

const requiredRoutes = [
  { path: '/', component: 'RootLandingPage' },
  { path: '/w/$workspaceId/home', component: 'WorkspaceHomePage' },
  { path: '/w/$workspaceId/projects', component: 'ProjectListPage' },
  { path: '/w/$workspaceId/projects/$projectId/overview', component: 'ProjectOverviewPage' },
  { path: '/w/$workspaceId/projects/$projectId/edit', component: 'NlePage' },
  { path: '/w/$workspaceId/projects/$projectId/canvas', component: 'CanvasPage' },
  { path: '/w/$workspaceId/projects/$projectId/workflow', component: 'WorkflowPage' },
  { path: '/w/$workspaceId/projects/$projectId/recipe', component: 'HiddenCreativeFoundationPage' },
  { path: '/w/$workspaceId/projects/$projectId/agent', component: 'AgentPage' },
  { path: '/w/$workspaceId/projects/$projectId/review', component: 'ReviewPage' },
  { path: '/w/$workspaceId/projects/$projectId/production', component: 'ProductionPage' },
  { path: '/operations/overview', component: 'OperationsOverviewPage' },
  { path: '/admin/organization', component: 'ManagementFoundationPage' },
  { path: '/developer/capabilities', component: 'ManagementFoundationPage' },
  { path: '/legacy/editor', component: 'EditorPage' },
  { path: '/render-jobs', component: 'RenderJobDashboard' },
  { path: '/capabilities', component: 'CapabilitiesPage' },
  { path: '/smoke-editor', component: 'SmokeEditorPage' },
  { path: '/observability', component: 'ObservabilityDashboard' },
  { path: '/app/renders', component: 'RenderResultsListPage' },
  { path: '/app/renders/$productId', component: 'RenderResultDetailPage' },
]

function escapeRegExp(value) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
}

try {
  for (const route of requiredRoutes) {
    const componentPattern = new RegExp(`(?:import[\\s\\S]{0,100}?\\b${route.component}\\b|const\\s+${route.component}\\s*=\\s*lazy)`)
    const routePattern = new RegExp(
      `route\\(\\s*['"]${escapeRegExp(route.path)}['"]\\s*,\\s*(?:lazyPage\\()?${route.component}`
    )
    if (!componentPattern.test(routeTree)) {
      throw new Error(`missing route component import: ${route.component}`)
    }
    if (!routePattern.test(routeTree)) {
      throw new Error(`missing route registration: ${route.path} -> ${route.component}`)
    }
    console.log(`H4_ROUTE_REACHABLE=${route.path}:${route.component}`)
  }
  console.log(`H4_ROUTE_REACHABILITY_COUNT=${requiredRoutes.length}`)
  console.log('H4_ROUTE_REACHABILITY=PASS')
} catch (error) {
  console.error('H4_ROUTE_REACHABILITY=FAIL')
  console.error(error instanceof Error ? error.message : String(error))
  process.exitCode = 1
}
