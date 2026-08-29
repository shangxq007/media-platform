#!/usr/bin/env node

import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const repositoryRoot = resolve(dirname(fileURLToPath(import.meta.url)), '../..')
const routeTreePath = resolve(repositoryRoot, 'frontend/src/app/routeTree.tsx')
const routeTree = readFileSync(routeTreePath, 'utf8')

const requiredRoutes = [
  { path: '/', component: 'EditorPage' },
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
    const importPattern = new RegExp(`import\\s+\\{\\s*${route.component}\\s*\\}`)
    const routePattern = new RegExp(
      `createRoute\\(\\{[\\s\\S]*?path:\\s*['"]${escapeRegExp(route.path)}['"][\\s\\S]{0,180}?component:\\s*${route.component}[,\\s]`
    )
    if (!importPattern.test(routeTree)) {
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
