import assert from 'node:assert/strict'
import { existsSync, mkdirSync, mkdtempSync, rmSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join, relative } from 'node:path'
import test from 'node:test'
import {
  AUTHORITY_RULES,
  BOUNDED_RULES,
  architectureGuardPassed,
  defaultSourceRoot,
  reconcileFrontendPathLedger,
  repositoryRoot,
  scanFrontendArchitecture,
} from './frontend-architecture-guard.mjs'

test('governed frontend passes with all authority counts at zero', () => {
  const result = scanFrontendArchitecture(defaultSourceRoot)
  assert.ok(result.scannedFileCount > 0)
  for (const rule of AUTHORITY_RULES) {
    assert.equal(result.counts[rule.name], 0, rule.name)
  }
  for (const rule of BOUNDED_RULES) {
    assert.ok(result.boundedCounts[rule.name] <= rule.maximum, rule.name)
  }
  assert.equal(architectureGuardPassed(result), true)
  assert.equal(result.cleanForwardCounts.OLD_IMPORT_COUNT, 0)
  assert.equal(result.cleanForwardCounts.OLD_COMPONENT_USAGE_COUNT, 0)
  assert.equal(result.cleanForwardCounts.OLD_SCHEMA_USAGE_COUNT, 0)
  assert.equal(result.cleanForwardCounts.UNCLASSIFIED_FRONTEND_PATHS, 0)
  assert.equal(result.cleanForwardCounts.DELETE_SHADOW_PATH_RESIDUE_COUNT, 0)
  assert.equal(result.cleanForwardCounts.PATH_LEDGER_STALE_PATH_COUNT, 0)
  assert.equal(result.cleanForwardCounts.PATH_LEDGER_DUPLICATE_PATH_COUNT, 0)
  console.log(`FRONTEND_ARCHITECTURE_POSITIVE_CONTROL=PASS files=${result.scannedFileCount}`)
})

const cleanForwardMutations = [
  ['OLD_IMPORT_COUNT', "import '../config/navigation.js'"],
  ['OLD_COMPONENT_USAGE_COUNT', 'const obsolete = <RenderJobsPage />'],
  ['OLD_SCHEMA_USAGE_COUNT', 'const schema = RenderJobSummarySchema'],
]

for (const [field, source] of cleanForwardMutations) {
  test(`${field} rejects reintroduced legacy usage`, () => {
    const root = mkdtempSync(join(tmpdir(), 'frontend-foundation-clean-forward-'))
    try {
      writeFileSync(join(root, 'consumer.tsx'), source)
      const result = scanFrontendArchitecture(root)
      assert.ok(result.cleanForwardCounts[field] > 0, `${field} did not detect mutation`)
      assert.equal(architectureGuardPassed(result), false)
    } finally {
      rmSync(root, { recursive: true, force: true })
    }
  })
}

test('OLD_ROUTE_COUNT rejects an increase above the preserved compatibility ceiling', () => {
  const root = mkdtempSync(join(tmpdir(), 'frontend-foundation-old-routes-'))
  try {
    mkdirSync(join(root, 'app'))
    const paths = [
      '/legacy/editor', '/render-jobs', '/capabilities', '/smoke-editor', '/observability',
      '/dev/timeline-git', '/app/renders/$productId', '/admin/storage-health', '/app/renders',
      '/admin/render-jobs', '/dev/preview', '/dev/diagnostics', '/dev/storage-delivery-profiles',
      '/dev/ingest/preflight-policy', '/render-jobs',
    ]
    writeFileSync(join(root, 'app/routeTree.tsx'), paths.map(path => `route('${path}', Component)`).join('\n'))
    const result = scanFrontendArchitecture(root)
    assert.equal(result.cleanForwardCounts.OLD_ROUTE_COUNT, 15)
    assert.equal(architectureGuardPassed(result), false)
  } finally {
    rmSync(root, { recursive: true, force: true })
  }
})

test('path ledger reconciliation detects unclassified, stale, and duplicate paths', () => {
  const root = mkdtempSync(join(tmpdir(), 'frontend-foundation-path-ledger-'))
  try {
    const frontendRoot = join(root, 'frontend')
    mkdirSync(frontendRoot)
    const classifiedFile = join(frontendRoot, 'classified.ts')
    const unclassifiedFile = join(frontendRoot, 'unclassified.ts')
    writeFileSync(classifiedFile, 'export {}')
    writeFileSync(unclassifiedFile, 'export {}')
    const classifiedPath = relative(repositoryRoot, classifiedFile).replaceAll('\\', '/')
    const stalePath = `${relative(repositoryRoot, frontendRoot).replaceAll('\\', '/')}/stale.ts`
    const ledgerPath = join(root, 'ledger.tsv')
    writeFileSync(ledgerPath, [
      'path\tclassification\trationale',
      `${classifiedPath}\tREUSE\tTEST`,
      `${classifiedPath}\tREUSE\tTEST_DUPLICATE`,
      `${stalePath}\tREUSE\tTEST_STALE`,
    ].join('\n'))
    const result = reconcileFrontendPathLedger(frontendRoot, ledgerPath)
    assert.equal(result.unclassifiedPaths.length, 1)
    assert.equal(result.stalePaths.length, 1)
    assert.equal(result.duplicatePaths.length, 1)
  } finally {
    rmSync(root, { recursive: true, force: true })
  }
})

const mutations = [
  ['FRONTEND_CONCRETE_FFMPEG_AUTHORITY_COUNT', 'pages/Produce.tsx', "const defaultProvider = 'ffmpeg-cpu'"],
  ['FRONTEND_PLAN_NAME_FEATURE_AUTHORITY_COUNT', 'components/Export.tsx', "const enabled = planName === 'PRO'"],
  ['FRONTEND_LOCAL_CAN_RUN_DECISION_COUNT', 'features/render.ts', 'const canRun = runtimePath.length > 0'],
  ['FRONTEND_DUPLICATE_CANONICAL_TIMELINE_AUTHORITY_COUNT', 'editor/model.ts', 'interface FrontendTimeline { id: string }'],
  ['FRONTEND_CANONICAL_DOMAIN_SHADOW_COUNT', 'domain/timeline.ts', 'export interface Timeline { id: string }'],
  ['FRONTEND_PROVIDER_SELECTION_AUTHORITY_COUNT', 'features/provider.ts', 'function selectProvider() { return candidates[0] }'],
  ['FRONTEND_DIRECT_CANONICAL_MUTATION_BYPASS_COUNT', 'pages/Edit.tsx', "api.post('/timeline/apply', payload)"],
  ['FRONTEND_PROVIDER_INTERNAL_GRAPH_AUTHORITY_COUNT', 'features/graph.ts', 'interface ProviderInternalGraph { nodes: unknown[] }'],
  ['FRONTEND_SYNTHETIC_WORKSPACE_SCOPE_COUNT', 'routes/app/renders/List.tsx', "const scope = { tenantId: 'default' }", 'tenant synthetic default'],
  ['FRONTEND_SYNTHETIC_WORKSPACE_SCOPE_COUNT', 'routes/app/renders/Detail.tsx', 'const scope = { projectId: "default" }', 'project synthetic default'],
  ['FRONTEND_ACTIVE_UNSCOPED_RENDER_API_COUNT', 'api/render-jobs.ts', "api.get(`/render/jobs/${jobId}/artifacts`)", 'nested unscoped artifact endpoint'],
  ['FRONTEND_ACTIVE_UNSCOPED_RENDER_API_COUNT', 'api/render-jobs.ts', "axios.patch(`/render/jobs/${jobId}/artifacts/${artifactId}`, payload)", 'nested unscoped artifact mutation'],
  ['FRONTEND_STALE_RENDER_STATUS_SHADOW_COUNT', 'routes/app/renders/List.tsx', 'const renderStatus = "CANCELED"', 'stale render status shadow'],
]

for (const [ruleName, relativePath, source, mutationName = ruleName] of mutations) {
  test(`${ruleName} rejects ${mutationName} and leaves zero residue`, () => {
    const root = mkdtempSync(join(tmpdir(), 'h4-frontend-guard-'))
    try {
      const file = join(root, relativePath)
      mkdirSync(join(file, '..'), { recursive: true })
      writeFileSync(file, source)
      const result = scanFrontendArchitecture(root)
      assert.ok(result.counts[ruleName] > 0, `${ruleName} did not detect mutation`)
    } finally {
      rmSync(root, { recursive: true, force: true })
    }
    assert.equal(existsSync(root), false)
    console.log(`${ruleName}_NEGATIVE_CONTROL=PASS residue=0`)
  })
}

const boundedMutations = [
  ['FRONTEND_RAW_STORAGE_PRODUCT_FIELD_COUNT', 'types/raw.ts', Array.from({ length: 5 }, () => "const value = { sourceUrl: '' }").join('\n')],
  ['FRONTEND_DIRECT_STORAGE_URI_USE_COUNT', 'features/storage.ts', "const location = 's3://private/object'"],
  ['FRONTEND_SCATTERED_NATIVE_FETCH_COUNT', 'pages/Fetch.tsx', Array.from({ length: 4 }, () => "fetch('/api/value')").join('\n')],
  ['FRONTEND_DUPLICATE_CANONICAL_DTO_AUTHORITY_COUNT', 'features/model.ts', 'interface FrontendArtifactDto { id: string }'],
  ['FRONTEND_UNCLASSIFIED_DOMAIN_MODEL_COUNT', 'domain/project.ts', 'interface ProjectRecord { id: string }'],
  ['FRONTEND_COMMERCIAL_AUTHORITY_COUNT', 'features/access.ts', 'const isEntitled = credits > 0'],
  ['FRONTEND_RUNTIME_ELIGIBILITY_AUTHORITY_COUNT', 'features/runtime.ts', 'const workerEligible = capacity > 0'],
]

for (const [ruleName, relativePath, source] of boundedMutations) {
  test(`${ruleName} rejects a baseline increase`, () => {
    const root = mkdtempSync(join(tmpdir(), 'frontend-foundation-bounded-guard-'))
    try {
      const file = join(root, relativePath)
      mkdirSync(join(file, '..'), { recursive: true })
      writeFileSync(file, source)
      const result = scanFrontendArchitecture(root)
      const rule = BOUNDED_RULES.find(candidate => candidate.name === ruleName)
      assert.ok(rule)
      assert.ok(result.boundedCounts[ruleName] > rule.maximum, `${ruleName} did not exceed its baseline`)
      assert.equal(architectureGuardPassed(result), false)
    } finally {
      rmSync(root, { recursive: true, force: true })
    }
    assert.equal(existsSync(root), false)
  })
}

test('active product rules exclude explicit admin, developer, and operator surfaces', () => {
  const root = mkdtempSync(join(tmpdir(), 'h4-frontend-guard-non-product-'))
  try {
    const sources = [
      ['pages/AdminRenderJobsPage.tsx', "const scope = { tenantId: 'default' }"],
      ['pages/DevConsolePage.tsx', "api.get('/render/jobs/job-1/artifacts')"],
      ['pages/ObservabilityDashboard.tsx', 'const renderStatus = "CANCELED"'],
    ]
    for (const [relativePath, source] of sources) {
      const file = join(root, relativePath)
      mkdirSync(join(file, '..'), { recursive: true })
      writeFileSync(file, source)
    }
    const result = scanFrontendArchitecture(root)
    assert.equal(result.counts.FRONTEND_SYNTHETIC_WORKSPACE_SCOPE_COUNT, 0)
    assert.equal(result.counts.FRONTEND_ACTIVE_UNSCOPED_RENDER_API_COUNT, 0)
    assert.equal(result.counts.FRONTEND_STALE_RENDER_STATUS_SHADOW_COUNT, 0)
  } finally {
    rmSync(root, { recursive: true, force: true })
  }
  assert.equal(existsSync(root), false)
  console.log('FRONTEND_EXPLICIT_NON_PRODUCT_SURFACE_EXCLUSION_CONTROL=PASS residue=0')
})

test('guard fails closed on an empty scan universe', () => {
  const root = mkdtempSync(join(tmpdir(), 'h4-frontend-guard-empty-'))
  try {
    assert.throws(
      () => scanFrontendArchitecture(root),
      /FRONTEND_ARCHITECTURE_GUARD_EMPTY_SCAN_UNIVERSE/
    )
  } finally {
    rmSync(root, { recursive: true, force: true })
  }
  assert.equal(existsSync(root), false)
  console.log('FRONTEND_ARCHITECTURE_EMPTY_UNIVERSE_NEGATIVE_CONTROL=PASS residue=0')
})
