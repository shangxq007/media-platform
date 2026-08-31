import assert from 'node:assert/strict'
import { existsSync, mkdirSync, mkdtempSync, rmSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join, relative } from 'node:path'
import test from 'node:test'
import {
  API_APP_RUNTIME_ALLOWLIST,
  AUTHORITY_RULES,
  BOUNDED_RULES,
  POST_H7_GOVERNED_PATHS,
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
  assert.equal(result.cleanForwardCounts.POST_H7_GOVERNED_PATH_COUNT, POST_H7_GOVERNED_PATHS.length)
  assert.equal(result.cleanForwardCounts.POST_H7_GOVERNED_PATH_EXPECTED_COUNT, 21)
  assert.equal(result.cleanForwardCounts.POST_H7_GOVERNED_PATH_MISSING_COUNT, 0)
  assert.equal(result.cleanForwardCounts.POST_H7_GOVERNED_PATH_UNEXPECTED_COUNT, 0)
  assert.equal(result.cleanForwardCounts.API_APP_RUNTIME_PATH_COUNT, API_APP_RUNTIME_ALLOWLIST.length)
  assert.equal(result.cleanForwardCounts.API_APP_RUNTIME_PATH_EXPECTED_COUNT, 9)
  assert.equal(result.cleanForwardCounts.API_APP_RUNTIME_PATH_MISSING_COUNT, 0)
  assert.equal(result.cleanForwardCounts.API_APP_RUNTIME_PATH_UNEXPECTED_COUNT, 0)
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

const postH7Mutations = [
  ['PRODUCT_CURRENT_REVISION_ID_FRONTEND_USAGE_COUNT', 'product/timeline/bad-head.ts', 'const currentRevisionId = product.current_revision_id'],
  ['CLIENT_LATEST_HEAD_INFERENCE_COUNT', 'product/timeline/bad-latest.ts', 'const inferredHead = revisions[0]'],
  ['CLIENT_CANONICAL_ACTOR_AUTHORITY_COUNT', 'product/timeline/bad-actor.ts', "const request = { actorId: 'admin' }"],
  ['CLIENT_CANONICAL_TENANT_OVERRIDE_COUNT', 'product/timeline/bad-tenant.ts', 'const request = { tenantId: overrideTenantId }'],
  ['NEW_FRONTEND_GENERIC_PATCH_USAGE_COUNT', 'product/timeline/bad-patch.ts', "transport.post('/timeline-git/products/p1/patch/apply', body)"],
  ['PHYSICAL_STORAGE_URI_AS_ENTITY_ID_COUNT', 'product/timeline/bad-physical-id.ts', "const clip = { artifactId: 's3://bucket/item' }"],
  ['PROVIDER_KEY_AS_ARTIFACT_ID_COUNT', 'product/timeline/bad-provider-id.ts', 'const clip = { artifactId: providerKey }'],
  ['CLIENT_CANONICAL_MERGE_AUTHORITY_COUNT', 'product/review/bad-merge.ts', "transport.post('/render/projects/p1/timeline/revisions/merge', body)"],
  ['H8_INTERNAL_IMPLEMENTATION_FRONTEND_DEPENDENCY_COUNT', 'product/timeline/bad-internal.ts', "import { timelineStore } from '../../timeline/store/timelineStore'"],
  ['UNSTABLE_ROUTE_DIRECT_COMPONENT_CALL_COUNT', 'product/timeline/bad-route.tsx', "const route = '/timeline-git/products/p1/revisions/current'"],
]

for (const [ruleName, relativePath, source] of postH7Mutations) {
  test(`${ruleName} rejects its post-H7 negative control and leaves zero residue`, () => {
    const root = mkdtempSync(join(tmpdir(), 'post-h7-frontend-guard-'))
    try {
      const file = join(root, relativePath)
      mkdirSync(join(file, '..'), { recursive: true })
      writeFileSync(file, source)
      const result = scanFrontendArchitecture(root)
      assert.ok(result.counts[ruleName] > 0, `${ruleName} did not detect mutation`)
      assert.equal(architectureGuardPassed(result), false)
    } finally {
      rmSync(root, { recursive: true, force: true })
    }
    assert.equal(existsSync(root), false)
    console.log(`${ruleName}_NEGATIVE_CONTROL=PASS residue=0`)
  })
}

test('post-H7 rules allow authenticated tenant path scope and read-only server compare', () => {
  const root = mkdtempSync(join(tmpdir(), 'post-h7-frontend-legitimate-'))
  try {
    const file = join(root, 'api/app/operation.gateway.ts')
    mkdirSync(join(file, '..'), { recursive: true })
    writeFileSync(file, [
      'export async function preview(tenantId, projectId, transport) {',
      "  await transport.post(`/tenants/${tenantId}/projects/${projectId}/timeline-operations/add-media-clip/preview`, request)",
      "  return transport.get(`/render/projects/${projectId}/timeline/revisions/compare`, { params: { from, to } })",
      '}',
    ].join('\n'))
    const result = scanFrontendArchitecture(root)
    assert.equal(result.counts.CLIENT_CANONICAL_TENANT_OVERRIDE_COUNT, 0)
    assert.equal(result.counts.CLIENT_CANONICAL_MERGE_AUTHORITY_COUNT, 0)
    assert.equal(result.counts.NEW_FRONTEND_GENERIC_PATCH_USAGE_COUNT, 0)
    assert.equal(result.counts.UNSTABLE_ROUTE_DIRECT_COMPONENT_CALL_COUNT, 0)
  } finally {
    rmSync(root, { recursive: true, force: true })
  }
  assert.equal(existsSync(root), false)
  console.log('POST_H7_AUTHENTICATED_TENANT_AND_READ_ONLY_COMPARE_CONTROL=PASS residue=0')
})

const multiFormPostH7Mutations = [
  ['CLIENT_CANONICAL_ACTOR_AUTHORITY_COUNT', 'app/routeTree.tsx', 'const request = { actorId }', 'actor shorthand'],
  ['CLIENT_CANONICAL_ACTOR_AUTHORITY_COUNT', 'foundation/projectContext.tsx', "const request = { 'principalRef': value }", 'quoted actor key'],
  ['CLIENT_CANONICAL_ACTOR_AUTHORITY_COUNT', 'surfaces/FoundationPages.tsx', "const request = { ['createdBy']: value }", 'computed actor key'],
  ['CLIENT_CANONICAL_ACTOR_AUTHORITY_COUNT', 'product/review/alias.ts', 'const localActor = actorId', 'actor alias'],
  ['CLIENT_CANONICAL_TENANT_OVERRIDE_COUNT', 'app/routeTree.tsx', 'transport.post(path, { tenantId })', 'tenant shorthand request body'],
  ['CLIENT_CANONICAL_TENANT_OVERRIDE_COUNT', 'foundation/projectContext.tsx', "const request = { ['tenantId']: overrideTenantId }", 'computed tenant override'],
  ['CLIENT_CANONICAL_TENANT_OVERRIDE_COUNT', 'surfaces/FoundationPages.tsx', "const headers = { 'X-Tenant-ID': value }", 'quoted tenant header'],
  ['CLIENT_CANONICAL_TENANT_OVERRIDE_COUNT', 'product/review/tenant-alias.ts', 'const localTenant = projectedTenant', 'tenant alias'],
  ['CLIENT_CANONICAL_TENANT_OVERRIDE_COUNT', 'product/review/split-payload.ts', 'const payload = {\n  tenantId,\n  projectId,\n}', 'split tenant payload declaration'],
  ['POST_H7_AXIOS_IMPORT_BYPASS_COUNT', 'product/review/alternate-client.tsx', "import httpClient from 'axios'\nhttpClient.post(path, payload)", 'aliased Axios import'],
  ['POST_H7_VERSIONLESS_TRANSPORT_IMPORT_BYPASS_COUNT', 'product/timeline/versionless-bypass.tsx', "import { versionlessTransport as alternateClient } from '../../api/app/versionless-api'", 'aliased versionless transport import'],
  ['UNSTABLE_ROUTE_DIRECT_COMPONENT_CALL_COUNT', 'product/timeline/raw-route.tsx', "const path = '/render/projects/p1/timeline/revisions'", 'raw unstable route'],
  ['UNSTABLE_ROUTE_DIRECT_COMPONENT_CALL_COUNT', 'product/timeline/composed-route.tsx', "const routeRoot = '/timeline-git'\nconst path = routeRoot + '/products/' + projectId", 'composed unstable route'],
  ['UNSTABLE_ROUTE_DIRECT_COMPONENT_CALL_COUNT', 'product/review/alternate-call.tsx', "import { client as alternateClient } from './transport'\nalternateClient.post(endpoint, payload)", 'alternate transport client call'],
  ['UNSTABLE_ROUTE_DIRECT_COMPONENT_CALL_COUNT', 'foundation/projectContext.tsx', 'transport[method](path, body)', 'dynamic transport method'],
  ['UNSTABLE_ROUTE_DIRECT_COMPONENT_CALL_COUNT', 'surfaces/FoundationPages.tsx', "const send = axios['post']", 'transport method alias'],
  ['UNSTABLE_ROUTE_DIRECT_COMPONENT_CALL_COUNT', 'app/routeTree.tsx', 'const { post: send } = transport', 'destructured transport alias'],
]

for (const [ruleName, relativePath, source, form] of multiFormPostH7Mutations) {
  test(`${ruleName} rejects ${form} on a governed runtime bridge`, () => {
    const root = mkdtempSync(join(tmpdir(), 'post-h7-multiform-'))
    try {
      const file = join(root, relativePath)
      mkdirSync(join(file, '..'), { recursive: true })
      writeFileSync(file, source)
      const result = scanFrontendArchitecture(root)
      assert.ok(result.counts[ruleName] > 0, `${ruleName} did not detect ${form}`)
      assert.equal(architectureGuardPassed(result), false)
    } finally {
      rmSync(root, { recursive: true, force: true })
    }
    assert.equal(existsSync(root), false)
  })
}

test('tenant authority guard allows authenticated ProjectContext projection and gateway path scope', () => {
  const root = mkdtempSync(join(tmpdir(), 'post-h7-tenant-scope-'))
  try {
    const sources = [
      ['foundation/projectContext.tsx', 'const ProjectContext = { tenantId: authenticatedHome.tenantId }'],
      ['api/app/operation.gateway.ts', "function operationPath(tenantId, projectId) { return `/tenants/${tenantId}/projects/${projectId}/timeline-operations/add-media-clip/apply` }"],
    ]
    for (const [relativePath, source] of sources) {
      const file = join(root, relativePath)
      mkdirSync(join(file, '..'), { recursive: true })
      writeFileSync(file, source)
    }
    const result = scanFrontendArchitecture(root)
    assert.equal(result.counts.CLIENT_CANONICAL_TENANT_OVERRIDE_COUNT, 0)
    assert.equal(result.counts.UNSTABLE_ROUTE_DIRECT_COMPONENT_CALL_COUNT, 0)
  } finally {
    rmSync(root, { recursive: true, force: true })
  }
  assert.equal(existsSync(root), false)
})

function writeApiAppRuntimeAllowlist(root) {
  for (const relativePath of API_APP_RUNTIME_ALLOWLIST) {
    const file = join(root, relativePath)
    mkdirSync(join(file, '..'), { recursive: true })
    writeFileSync(file, 'export {}')
  }
}

test('api/app runtime allowlist rejects an unexpected non-test TypeScript module', () => {
  const root = mkdtempSync(join(tmpdir(), 'api-app-runtime-allowlist-'))
  try {
    writeApiAppRuntimeAllowlist(root)
    const file = join(root, 'api/app/alternate-transport.ts')
    writeFileSync(file, 'export {}')
    const result = scanFrontendArchitecture(root)
    assert.equal(result.cleanForwardCounts.API_APP_RUNTIME_PATH_COUNT, API_APP_RUNTIME_ALLOWLIST.length + 1)
    assert.equal(result.cleanForwardCounts.API_APP_RUNTIME_PATH_MISSING_COUNT, 0)
    assert.equal(result.cleanForwardCounts.API_APP_RUNTIME_PATH_UNEXPECTED_COUNT, 1)
    assert.equal(architectureGuardPassed(result), false)
  } finally {
    rmSync(root, { recursive: true, force: true })
  }
  assert.equal(existsSync(root), false)
})

test('hostile A: an unallowlisted nested api/app .ts runtime fails closed', () => {
  const root = mkdtempSync(join(tmpdir(), 'api-app-nested-ts-'))
  try {
    writeApiAppRuntimeAllowlist(root)
    const file = join(root, 'api/app/nested/alternate-transport.ts')
    mkdirSync(join(file, '..'), { recursive: true })
    writeFileSync(file, 'export {}')
    const result = scanFrontendArchitecture(root)
    assert.equal(result.cleanForwardCounts.API_APP_RUNTIME_PATH_COUNT, API_APP_RUNTIME_ALLOWLIST.length + 1)
    assert.equal(result.cleanForwardCounts.API_APP_RUNTIME_PATH_UNEXPECTED_COUNT, 1)
    assert.equal(architectureGuardPassed(result), false)
  } finally {
    rmSync(root, { recursive: true, force: true })
  }
})

test('hostile B: an unallowlisted nested api/app .tsx runtime fails closed', () => {
  const root = mkdtempSync(join(tmpdir(), 'api-app-nested-tsx-'))
  try {
    writeApiAppRuntimeAllowlist(root)
    const file = join(root, 'api/app/nested/alternate-view.tsx')
    mkdirSync(join(file, '..'), { recursive: true })
    writeFileSync(file, 'export const AlternateView = () => null')
    const result = scanFrontendArchitecture(root)
    assert.equal(result.cleanForwardCounts.API_APP_RUNTIME_PATH_COUNT, API_APP_RUNTIME_ALLOWLIST.length + 1)
    assert.equal(result.cleanForwardCounts.API_APP_RUNTIME_PATH_UNEXPECTED_COUNT, 1)
    assert.equal(architectureGuardPassed(result), false)
  } finally {
    rmSync(root, { recursive: true, force: true })
  }
})

test('hostile C: post-H7 authority rules scan forbidden patterns in nested api/app runtime', () => {
  const root = mkdtempSync(join(tmpdir(), 'api-app-nested-authority-'))
  try {
    const file = join(root, 'api/app/nested/deeper/bad-head.ts')
    mkdirSync(join(file, '..'), { recursive: true })
    writeFileSync(file, 'const currentRevisionId = product.current_revision_id')
    const result = scanFrontendArchitecture(root)
    assert.ok(result.counts.PRODUCT_CURRENT_REVISION_ID_FRONTEND_USAGE_COUNT > 0)
    assert.equal(result.cleanForwardCounts.API_APP_RUNTIME_PATH_UNEXPECTED_COUNT, 1)
    assert.equal(architectureGuardPassed(result), false)
  } finally {
    rmSync(root, { recursive: true, force: true })
  }
})

test('hostile D: nested api/app test modules are excluded from runtime inventory', () => {
  const root = mkdtempSync(join(tmpdir(), 'api-app-nested-test-'))
  try {
    writeApiAppRuntimeAllowlist(root)
    const file = join(root, 'api/app/nested/alternate.test.ts')
    mkdirSync(join(file, '..'), { recursive: true })
    writeFileSync(file, 'const currentRevisionId = product.current_revision_id')
    const result = scanFrontendArchitecture(root)
    assert.equal(result.cleanForwardCounts.API_APP_RUNTIME_PATH_COUNT, API_APP_RUNTIME_ALLOWLIST.length)
    assert.equal(result.cleanForwardCounts.API_APP_RUNTIME_PATH_UNEXPECTED_COUNT, 0)
    assert.equal(result.counts.PRODUCT_CURRENT_REVISION_ID_FRONTEND_USAGE_COUNT, 0)
  } finally {
    rmSync(root, { recursive: true, force: true })
  }
})

test('hostile E: nested api/app declaration modules are excluded from runtime inventory', () => {
  const root = mkdtempSync(join(tmpdir(), 'api-app-nested-declaration-'))
  try {
    writeApiAppRuntimeAllowlist(root)
    const file = join(root, 'api/app/nested/alternate.d.ts')
    mkdirSync(join(file, '..'), { recursive: true })
    writeFileSync(file, 'declare const currentRevisionId: string')
    const result = scanFrontendArchitecture(root)
    assert.equal(result.cleanForwardCounts.API_APP_RUNTIME_PATH_COUNT, API_APP_RUNTIME_ALLOWLIST.length)
    assert.equal(result.cleanForwardCounts.API_APP_RUNTIME_PATH_UNEXPECTED_COUNT, 0)
    assert.equal(result.counts.PRODUCT_CURRENT_REVISION_ID_FRONTEND_USAGE_COUNT, 0)
  } finally {
    rmSync(root, { recursive: true, force: true })
  }
})

test('hostile F: an unseen arbitrarily deep api/app directory cannot escape classification', () => {
  const root = mkdtempSync(join(tmpdir(), 'api-app-unseen-depth-'))
  try {
    writeApiAppRuntimeAllowlist(root)
    const file = join(root, 'api/app/unseen/one/two/three/runtime.ts')
    mkdirSync(join(file, '..'), { recursive: true })
    writeFileSync(file, 'export {}')
    const result = scanFrontendArchitecture(root)
    assert.deepEqual(result.cleanForwardCounts.API_APP_RUNTIME_PATH_UNEXPECTED_COUNT, 1)
    assert.equal(architectureGuardPassed(result), false)
  } finally {
    rmSync(root, { recursive: true, force: true })
  }
})

test('post-H7 governed path manifest fails closed when one expected runtime path is omitted', () => {
  const root = mkdtempSync(join(tmpdir(), 'post-h7-path-manifest-'))
  try {
    for (const relativePath of POST_H7_GOVERNED_PATHS.slice(0, -1)) {
      const file = join(root, relativePath)
      mkdirSync(join(file, '..'), { recursive: true })
      writeFileSync(file, 'export {}')
    }
    const result = scanFrontendArchitecture(root)
    assert.equal(result.cleanForwardCounts.POST_H7_GOVERNED_PATH_COUNT, POST_H7_GOVERNED_PATHS.length - 1)
    assert.equal(result.cleanForwardCounts.POST_H7_GOVERNED_PATH_MISSING_COUNT, 1)
    assert.equal(architectureGuardPassed(result), false)
  } finally {
    rmSync(root, { recursive: true, force: true })
  }
})

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

const rawStorageProductFieldLexemes = [
  'sourceUrl',
  'storageUri',
  'storageKey',
  'objectKey',
  'bucket',
  'assetUri',
]

for (const lexeme of rawStorageProductFieldLexemes) {
  test(`FRONTEND_RAW_STORAGE_PRODUCT_FIELD_COUNT rejects ${lexeme} and leaves zero residue`, () => {
    const root = mkdtempSync(join(tmpdir(), 'frontend-foundation-raw-storage-'))
    try {
      const file = join(root, 'types/product.ts')
      mkdirSync(join(file, '..'), { recursive: true })
      writeFileSync(file, `export interface GovernedProductClip { ${lexeme}?: string }`)
      const result = scanFrontendArchitecture(root)
      assert.equal(result.boundedCounts.FRONTEND_RAW_STORAGE_PRODUCT_FIELD_COUNT, 1)
      assert.equal(architectureGuardPassed(result), false)
    } finally {
      rmSync(root, { recursive: true, force: true })
    }
    assert.equal(existsSync(root), false)
    console.log(`FRONTEND_RAW_STORAGE_PRODUCT_FIELD_COUNT_${lexeme}_NEGATIVE_CONTROL=PASS residue=0`)
  })
}

const boundedMutations = [
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
