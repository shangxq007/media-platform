#!/usr/bin/env node

import { spawnSync } from 'node:child_process'
import { existsSync, readFileSync, readdirSync, statSync } from 'node:fs'
import { dirname, posix, relative, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import ts from 'typescript'

const scriptDirectory = dirname(fileURLToPath(import.meta.url))
export const repositoryRoot = resolve(scriptDirectory, '../..')
export const defaultSourceRoot = resolve(repositoryRoot, 'frontend/src')

const SOURCE_EXTENSIONS = new Set(['.ts', '.tsx'])
const EXCLUDED_SEGMENTS = new Set(['dist', 'build', 'node_modules', 'vendor', 'fixtures'])
const PATH_LEDGER = resolve(repositoryRoot, 'docs/architecture/governance/frontend-product-path-classification-v1.tsv')
const FRONTEND_ROOT = resolve(repositoryRoot, 'frontend')
const ACTIVE_PRODUCT_PATH_PATTERN = /^(?:api\/render-jobs\.ts|components\/render-jobs\/|editor\/|pages\/(?:RenderJobDashboard|SmokeEditorPage)\.tsx|routes\/app\/renders\/|shared\/CapabilitiesPage\.tsx)/
const EXPLICIT_NON_PRODUCT_SURFACE_PATTERN = /^(?:api\/(?:admin|dev|operator)\/|components\/(?:admin|dev|operator)\/|pages\/(?:Admin|Dev|Observability|Operator)|routes\/(?:admin|dev|operator)\/|routes\/app\/(?:admin|dev|operator)\/)/i
const POST_H7_PRODUCT_PATH_PATTERN = /^(?:api\/app\/|app\/routeTree\.tsx|foundation\/projectContext\.tsx|surfaces\/FoundationPages\.tsx|product\/(?:canvas|review|timeline)\/)/
const POST_H7_COMPONENT_PATH_PATTERN = /^(?:app\/routeTree\.tsx|foundation\/projectContext\.tsx|surfaces\/FoundationPages\.tsx|product\/(?:canvas|review|timeline)\/)/
const VERSIONLESS_TRANSPORT_MODULE_PATTERN = /^api\/app\/versionless-api\.ts$/

export const API_APP_RUNTIME_ALLOWLIST = [
  'api/app/artifacts.client.ts',
  'api/app/asset.gateway.ts',
  'api/app/capability.gateway.ts',
  'api/app/gateway-error.ts',
  'api/app/index.ts',
  'api/app/operation.gateway.ts',
  'api/app/products.client.ts',
  'api/app/timeline-query.gateway.ts',
  'api/app/versionless-api.ts',
].sort()

export const POST_H7_GOVERNED_PATHS = [
  'api/app/artifacts.client.ts',
  'api/app/index.ts',
  'api/app/asset.gateway.ts',
  'api/app/capability.gateway.ts',
  'api/app/gateway-error.ts',
  'api/app/operation.gateway.ts',
  'api/app/products.client.ts',
  'api/app/timeline-query.gateway.ts',
  'api/app/versionless-api.ts',
  'app/routeTree.tsx',
  'foundation/projectContext.tsx',
  'surfaces/FoundationPages.tsx',
  'product/canvas/WorkspaceCanvas.tsx',
  'product/canvas/model.ts',
  'product/review/ReviewWorkspace.tsx',
  'product/timeline/NleWorkspace.tsx',
  'product/timeline/SemanticDiff.tsx',
  'product/timeline/TimelineNavigation.tsx',
  'product/timeline/editor-state.ts',
  'product/timeline/gateways.ts',
  'product/timeline/navigation.ts',
  'product/timeline/testing/mocks.ts',
  'product/timeline/types.ts',
].sort()

export const DELETE_SHADOW_PATHS = [
  'frontend/src/config/navigation.ts',
  'frontend/src/pages/UserRenderHistoryPage.tsx',
  'frontend/src/pages/UserRenderResultDetailPage.tsx',
  'frontend/src/render-job/RenderJobsPage.tsx',
  'frontend/src/style.css',
  'frontend/src/utils/demoProjectFactory.ts',
  'frontend/src/utils/demoTimelineFactory.ts',
]

export const LEGACY_ROUTE_PATHS = [
  '/legacy/editor', '/render-jobs', '/capabilities', '/smoke-editor', '/observability',
  '/dev/timeline-git', '/app/renders/$productId', '/admin/storage-health', '/app/renders',
  '/admin/render-jobs', '/dev/preview', '/dev/diagnostics', '/dev/storage-delivery-profiles',
  '/dev/ingest/preflight-policy',
]

const OLD_COMPONENT_PATTERN = /\b(?:UserRenderHistoryPage|UserRenderResultDetailPage|RenderJobsPage)\b/g
const OLD_SCHEMA_PATTERN = /\b(?:SmokeTimelineInput|RenderJobSummarySchema|RenderJobArtifactSchema)\b/g
const OLD_IMPORT_TARGETS = [
  /(?:^|\/)config\/navigation$/,
  /(?:^|\/)pages\/UserRenderHistoryPage$/,
  /(?:^|\/)pages\/UserRenderResultDetailPage$/,
  /(?:^|\/)render-job\/RenderJobsPage$/,
  /(?:^|\/)style$/,
  /(?:^|\/)utils\/demoProjectFactory$/,
  /(?:^|\/)utils\/demoTimelineFactory$/,
]

export const AUTHORITY_RULES = [
  {
    name: 'PRODUCT_CURRENT_REVISION_ID_FRONTEND_USAGE_COUNT',
    governedPathPattern: POST_H7_PRODUCT_PATH_PATTERN,
    patterns: [
      /\bcurrent_revision_id\b/i,
      /\bcurrentRevisionId\b/,
    ],
  },
  {
    name: 'CLIENT_LATEST_HEAD_INFERENCE_COUNT',
    governedPathPattern: POST_H7_PRODUCT_PATH_PATTERN,
    patterns: [
      /\b(?:revisions|history)\s*\[\s*0\s*\]/,
      /\b(?:revisions|history)\s*\.\s*at\(\s*0\s*\)/,
      /\b(?:latest|newest)(?:Revision|Head)\b/,
    ],
  },
  {
    name: 'CLIENT_CANONICAL_ACTOR_AUTHORITY_COUNT',
    governedPathPattern: POST_H7_PRODUCT_PATH_PATTERN,
    patterns: [
      /(?:[{,]\s*)(?:(?:['"](?:actorId|principalRef|createdBy)['"])|(?:\[\s*['"](?:actorId|principalRef|createdBy)['"]\s*\])|(?:actorId|principalRef|createdBy))\s*(?=[:,}])/,
      /\b(?:actorId|principalRef|createdBy)\s*=/,
      /\b(?:const|let|var)\s+[A-Za-z_$][\w$]*(?:actor|principal|creator)[\w$]*\s*=\s*(?:actorId|principalRef|createdBy)\b/i,
    ],
  },
  {
    name: 'CLIENT_CANONICAL_TENANT_OVERRIDE_COUNT',
    governedPathPattern: POST_H7_PRODUCT_PATH_PATTERN,
    patterns: [
      /(?:(?:['"]tenantId['"])|(?:\[\s*['"]tenantId['"]\s*\])|\btenantId\b)\s*:\s*(?:override|draft|form|input|local|manual|selected|scoped)[A-Za-z0-9_$]*/i,
      /(?:(?:['"]X-Tenant-ID['"])|(?:\[\s*['"]X-Tenant-ID['"]\s*\]))\s*(?::|=)/i,
      /\b(?:api|axios|transport)\s*(?:\.\s*(?:post|put|patch|delete)|\[\s*['"](?:post|put|patch|delete)['"]\s*\])\s*\(\s*[^,\n]+,\s*\{[^}]*(?:(?:['"]tenantId['"])|(?:\[\s*['"]tenantId['"]\s*\])|\btenantId\b)\s*(?:[:,}])/i,
      /\b(?:const|let|var)\s+(?:[A-Za-z_$][\w$]*)?(?:tenantOverride|overrideTenant|localTenant|draftTenant)[\w$]*\s*=/i,
      /\b(?:const|let|var)\s+(?:payload|body|request)\s*=\s*\{[^}]*\btenantId\s*(?:,|\})/s,
    ],
  },
  {
    name: 'NEW_FRONTEND_GENERIC_PATCH_USAGE_COUNT',
    governedPathPattern: POST_H7_PRODUCT_PATH_PATTERN,
    patterns: [
      /['"`]\/[^'"`\n]*(?:timeline-)?patch(?:\/|['"`])/i,
      /\bTimelinePatch(?:API|Request|Operation)?\b/,
    ],
  },
  {
    name: 'PHYSICAL_STORAGE_URI_AS_ENTITY_ID_COUNT',
    governedPathPattern: POST_H7_PRODUCT_PATH_PATTERN,
    patterns: [
      /\b(?:artifactId|mediaAssetId|mediaStreamId|clipId)\s*:\s*['"`](?:file|s3|gs|https?):\/\//i,
      /\bartifactId\(\s*['"`](?:file|s3|gs|https?):\/\//i,
    ],
  },
  {
    name: 'PROVIDER_KEY_AS_ARTIFACT_ID_COUNT',
    governedPathPattern: POST_H7_PRODUCT_PATH_PATTERN,
    patterns: [
      /\bartifactId\s*:\s*(?:providerKey|providerId|providerName)\b/,
      /\bartifactId\(\s*(?:providerKey|providerId|providerName)\b/,
    ],
  },
  {
    name: 'CLIENT_CANONICAL_MERGE_AUTHORITY_COUNT',
    governedPathPattern: POST_H7_PRODUCT_PATH_PATTERN,
    patterns: [
      /\b(?:api|axios|transport)\.post\([^\n]*['"`][^'"`]*\/merge['"`]/i,
      /\b(?:function|const)\s+(?:canonicalMerge|mergeTimeline|resolveMerge)\b/,
    ],
  },
  {
    name: 'H8_INTERNAL_IMPLEMENTATION_FRONTEND_DEPENDENCY_COUNT',
    governedPathPattern: POST_H7_PRODUCT_PATH_PATTERN,
    patterns: [
      /\b(?:OperationPlan|ApplyContext|TargetRevisionRef|RevisionWriteCommand)\b/,
      /(?:from|import\s*)\s*['"][^'"]*(?:operation-module|\/timeline\/(?:commands|store|intelligence|engine)\/)[^'"]*['"]/,
    ],
  },
  {
    name: 'POST_H7_AXIOS_IMPORT_BYPASS_COUNT',
    governedPathPattern: POST_H7_PRODUCT_PATH_PATTERN,
    excludedPathPattern: VERSIONLESS_TRANSPORT_MODULE_PATTERN,
    patterns: [
      /(?:\bfrom\s*|\bimport\s*(?:\(\s*)?|\brequire\s*\(\s*)['"]axios(?:\/[^'"]*)?['"]/,
    ],
  },
  {
    name: 'POST_H7_VERSIONLESS_TRANSPORT_IMPORT_BYPASS_COUNT',
    governedPathPattern: POST_H7_PRODUCT_PATH_PATTERN,
    excludedPathPattern: /^(?:api\/app\/versionless-api\.ts|api\/app\/(?:asset|capability|operation|timeline-query)\.gateway\.ts)$/,
    patterns: [
      /(?:\bfrom\s*|\bimport\s*(?:\(\s*)?|\brequire\s*\(\s*)['"][^'"]*\/versionless-api['"]/,
    ],
  },
  {
    name: 'UNSTABLE_ROUTE_DIRECT_COMPONENT_CALL_COUNT',
    governedPathPattern: POST_H7_COMPONENT_PATH_PATTERN,
    patterns: [
      /['"`]\/(?:timeline-git|render|tenants)(?:['"`]|\/)/,
      /\b(?:api|axios|transport)\s*(?:\.\s*(?:get|post|put|patch|delete)|\[\s*['"](?:get|post|put|patch|delete)['"]\s*\])\s*\(/,
      /\b(?:api|axios|transport)\s*\[[^\]\n]+\]\s*\(/,
      /\b[A-Za-z_$][\w$]*\s*\.\s*(?:get|post|put|patch|delete)\s*\(/,
      /\b(?:const|let|var)\s+[A-Za-z_$][\w$]*\s*=\s*(?:api|axios|transport)\s*(?:\.\s*(?:get|post|put|patch|delete)|\[\s*['"](?:get|post|put|patch|delete)['"]\s*\])/,
      /\b(?:const|let|var)\s*\{[^}\n]*(?:get|post|put|patch|delete)[^}\n]*\}\s*=\s*(?:api|axios|transport)\b/,
      /(?<![A-Za-z0-9_.])fetch\s*\(/,
    ],
  },
  {
    name: 'FRONTEND_CONCRETE_FFMPEG_AUTHORITY_COUNT',
    patterns: [
      /\b(?:selectedProvider|defaultProvider|provider(?:Id|Key|Name)?)\s*(?:=|:)\s*['"`][^'"`]*ffmpeg/i,
      /\bprovider(?:Id|Key|Name)?\s*={2,3}\s*['"`][^'"`]*ffmpeg/i,
    ],
  },
  {
    name: 'FRONTEND_PLAN_NAME_FEATURE_AUTHORITY_COUNT',
    patterns: [
      /\b(?:plan(?:Name|Key)?|tier|subscription(?:Plan)?)\s*(?:={2,3}|!={1,2})\s*['"`](?:FREE|PRO|ENTERPRISE)['"`]/,
      /\b(?:plan(?:Name|Key)?|tier)\s*(?::|=)[^;\n]*(?:\|\||\?\?)[^;\n]*['"`](?:FREE|PRO|ENTERPRISE)['"`]/,
    ],
  },
  {
    name: 'FRONTEND_LOCAL_CAN_RUN_DECISION_COUNT',
    patterns: [
      /\b(?:const|let|var)\s+(?:canRun|CAN_RUN)\s*=/,
      /\bfunction\s+canRun\s*\(/,
    ],
  },
  {
    name: 'FRONTEND_DUPLICATE_CANONICAL_TIMELINE_AUTHORITY_COUNT',
    patterns: [
      /\b(?:interface|type|class)\s+(?:FrontendTimeline|CanonicalTimeline|FrontendCanonicalMedia)\b/,
      /Canonical timeline representation for the canvas editor/,
    ],
  },
  {
    name: 'FRONTEND_CANONICAL_DOMAIN_SHADOW_COUNT',
    pathPattern: /(?:^|\/)domain\//,
    patterns: [
      /\b(?:FrontendProviderCapabilityAuthority|FrontendEntitlementDecision|FrontendRenderGraphSemantics)\b/,
      /single source of truth for frontend state/i,
    ],
  },
  {
    name: 'FRONTEND_PROVIDER_SELECTION_AUTHORITY_COUNT',
    patterns: [
      /\bfunction\s+(?:select|rank|choose)Provider\s*\(/i,
      /\b(?:const|let|var)\s+selectedProvider\s*=/,
      /\.sort\([^\n]*provider(?:Priority|Rank|Score)/i,
    ],
  },
  {
    name: 'FRONTEND_DIRECT_CANONICAL_MUTATION_BYPASS_COUNT',
    governedPathPattern: /(?:^|\/)(?:pages|components|editor|routes|features)\//,
    patterns: [
      /\b(?:api|axios)\.post\([^\n]*['"`][^'"`]*\/timeline\/(?:apply|sync|push|ai-edit|ai-proposals|snapshots?)[^'"`]*['"`]/i,
      /\bTimelineSyncAPI\.(?:push|sync)\s*\(/,
      /\bmutateCanonical(?:Timeline|Media|Workflow)\s*\(/,
    ],
  },
  {
    name: 'FRONTEND_PROVIDER_INTERNAL_GRAPH_AUTHORITY_COUNT',
    patterns: [
      /\b(?:interface|type|class|const)\s+(?:BmfGraph|ProviderInternalGraph|ProviderExecutionGraph)\b/i,
      /\b(?:UniversalNode|UniversalEdge|UniversalGraph)\b/,
    ],
  },
  {
    name: 'FRONTEND_SYNTHETIC_WORKSPACE_SCOPE_COUNT',
    governedPathPattern: ACTIVE_PRODUCT_PATH_PATTERN,
    excludedPathPattern: EXPLICIT_NON_PRODUCT_SURFACE_PATTERN,
    patterns: [
      /\b(?:tenantId|projectId)\s*:\s*['"]default['"]/,
    ],
  },
  {
    name: 'FRONTEND_ACTIVE_UNSCOPED_RENDER_API_COUNT',
    governedPathPattern: ACTIVE_PRODUCT_PATH_PATTERN,
    excludedPathPattern: EXPLICIT_NON_PRODUCT_SURFACE_PATTERN,
    patterns: [
      /\b(?:api|axios)\.(?:get|post|put|patch|delete)\s*\(\s*['"`]\/render\/jobs(?:[/?#][^'"`\n]*)?['"`]/i,
    ],
  },
  {
    name: 'FRONTEND_STALE_RENDER_STATUS_SHADOW_COUNT',
    governedPathPattern: ACTIVE_PRODUCT_PATH_PATTERN,
    excludedPathPattern: EXPLICIT_NON_PRODUCT_SURFACE_PATTERN,
    patterns: [
      /\brenderStatus\b/,
      /\bCANCELED\b/,
    ],
  },
]

// CLEAN FORWARD baselines are semantic residue ceilings, not file-count
// allowlists. Existing legacy residue may decrease, but any increase fails.
export const BOUNDED_RULES = [
  { name: 'FRONTEND_ROUTE_SELECTION_LIFETIME_VIOLATION_COUNT', maximum: 0, patterns: [] },
  { name: 'FRONTEND_SELECTION_BOUNDARY_VIOLATION_COUNT', maximum: 0, patterns: [] },
  {
    name: 'FRONTEND_DIRECT_TOLGEE_PRODUCT_IMPORT_COUNT',
    maximum: 0,
    patterns: [],
  },
  {
    name: 'FRONTEND_MIGRATED_HARDCODED_UI_COPY_COUNT',
    maximum: 0,
    governedPathPattern: /^(?:components\/(?:app-shell\/AppShell|design-system\/index)\.tsx|interaction\/InteractionShell\.tsx|product\/(?:canvas\/WorkspaceCanvas|timeline\/NleWorkspace|review\/ReviewWorkspace)\.tsx)$/,
    patterns: [
      /['"`](?:Commands|Command palette|Ask Agent|Agent conversation|Preview|Modify|Apply canonical change|Selection inspector|Infinite canvas workspace|Timeline workspace|Project review)['"`]/,
      />\s*(?:Commands|Command palette|Ask Agent|Agent conversation|Preview|Modify|Apply canonical change|Selection inspector|Infinite canvas workspace|Timeline workspace|Project review)\s*</,
    ],
  },
  {
    name: 'FRONTEND_RAW_STORAGE_PRODUCT_FIELD_COUNT',
    maximum: 0,
    patterns: [
      /\b(?:storageUri|storageURI|storageKey|objectKey|bucket|sourceUrl|assetUri)\b/,
    ],
  },
  {
    name: 'FRONTEND_DIRECT_STORAGE_URI_USE_COUNT',
    maximum: 0,
    patterns: [
      /['"`](?:file|s3|gs):\/\//i,
      /\b(?:storageUri|storageURI|storageKey|objectKey|bucket|assetUri)\s*(?:=|:)/,
    ],
  },
  {
    name: 'FRONTEND_SCATTERED_NATIVE_FETCH_COUNT',
    maximum: 0,
    excludedPathPattern: /^api\/core\/api-client\.ts$/,
    patterns: [/(?<![A-Za-z0-9_.])fetch\s*\(/],
  },
  {
    name: 'FRONTEND_DUPLICATE_CANONICAL_DTO_AUTHORITY_COUNT',
    maximum: 0,
    patterns: [
      /\b(?:interface|type|class)\s+(?:Canonical|Frontend)(?:Project|MediaAsset|Artifact|Timeline|Revision|Render|Workflow)(?:Dto|Entity|Model)?\b/,
    ],
  },
  {
    name: 'FRONTEND_UNCLASSIFIED_DOMAIN_MODEL_COUNT',
    maximum: 0,
    pathPattern: /(?:^|\/)domain\//,
    patterns: [],
  },
  {
    name: 'FRONTEND_COMMERCIAL_AUTHORITY_COUNT',
    maximum: 0,
    patterns: [
      /\b(?:const|let|var)\s+(?:isEntitled|hasQuota|billingAllowed)\s*=/,
      /\b(?:credits|quotaRemaining)\s*>\s*0\s*\?\s*(?:true|['"]AVAILABLE['"])/,
    ],
  },
  {
    name: 'FRONTEND_RUNTIME_ELIGIBILITY_AUTHORITY_COUNT',
    maximum: 0,
    patterns: [
      /\b(?:const|let|var)\s+(?:workerEligible|runtimeCompatible|providerAvailable)\s*=/,
      /\bfunction\s+(?:decideWorkerEligibility|decideRuntimeCompatibility)\s*\(/,
    ],
  },
]

function extension(path) {
  const match = path.match(/\.[^.\/]+$/)
  return match?.[0] ?? ''
}

function collectSourceFiles(root) {
  const files = []
  const visit = path => {
    const entry = statSync(path)
    if (entry.isDirectory()) {
      for (const name of readdirSync(path)) {
        if (EXCLUDED_SEGMENTS.has(name)) continue
        visit(resolve(path, name))
      }
      return
    }
    if (!SOURCE_EXTENSIONS.has(extension(path))) return
    if (/\.(?:test|spec)\.[cm]?[jt]sx?$/.test(path)) return
    if (/\.d\.ts$/.test(path)) return
    files.push(path)
  }
  visit(root)
  return files.sort()
}

function collectConsumerFiles(root) {
  const files = []
  const visit = path => {
    const entry = statSync(path)
    if (entry.isDirectory()) {
      for (const name of readdirSync(path)) {
        if (EXCLUDED_SEGMENTS.has(name)) continue
        visit(resolve(path, name))
      }
      return
    }
    if (!/\.(?:ts|tsx|css)$/.test(path) || /\.d\.ts$/.test(path)) return
    files.push(path)
  }
  visit(root)
  return files.sort()
}

function countMatches(text, pattern) {
  return [...text.matchAll(new RegExp(pattern.source, pattern.flags.includes('g') ? pattern.flags : `${pattern.flags}g`))].length
}

function countOldImports(files) {
  let count = 0
  const importPattern = /(?:\bfrom\s*|\bimport\s*(?:\(\s*)?|\brequire\s*\(\s*|@import\s*)['"]([^'"]+)['"]/g
  for (const file of files) {
    const text = readFileSync(file, 'utf8')
    for (const match of text.matchAll(importPattern)) {
      const normalized = match[1].replace(/\.(?:js|jsx|ts|tsx|css)$/, '')
      if (OLD_IMPORT_TARGETS.some(pattern => pattern.test(normalized))) count += 1
    }
  }
  return count
}

function countLegacyRoutes(sourceRoot) {
  const routeTreePath = resolve(sourceRoot, 'app/routeTree.tsx')
  if (!existsSync(routeTreePath)) return 0
  const routeTree = readFileSync(routeTreePath, 'utf8')
  return LEGACY_ROUTE_PATHS.reduce((count, path) => {
    const escaped = path.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
    return count + countMatches(routeTree, new RegExp(`route\\(\\s*['"]${escaped}['"]`, 'g'))
  }, 0)
}

function collectPhysicalFrontendPaths(root, pathRoot) {
  const paths = []
  const visit = path => {
    const entry = statSync(path)
    if (entry.isDirectory()) {
      for (const name of readdirSync(path)) {
        if (name === 'node_modules') continue
        visit(resolve(path, name))
      }
      return
    }
    paths.push(relative(pathRoot, path).replaceAll('\\', '/'))
  }
  visit(root)
  return paths.sort()
}

export function collectFrontendPaths(root) {
  const gitRootResult = spawnSync(
    'git',
    ['-C', root, 'rev-parse', '--show-toplevel'],
    { encoding: 'utf8' },
  )
  if (gitRootResult.status !== 0 || !gitRootResult.stdout.trim()) {
    return collectPhysicalFrontendPaths(root, repositoryRoot)
  }

  const gitRoot = resolve(gitRootResult.stdout.trim())
  const physicalPaths = collectPhysicalFrontendPaths(root, gitRoot)
  const rootPathspec = relative(gitRoot, root).replaceAll('\\', '/') || '.'
  const governedResult = spawnSync(
    'git',
    ['-C', gitRoot, 'ls-files', '--cached', '--others', '--exclude-standard', '-z', '--', rootPathspec],
    { encoding: 'utf8' },
  )
  if (governedResult.status !== 0) return physicalPaths

  const governedPaths = new Set(governedResult.stdout.split('\0').filter(Boolean))
  return physicalPaths.filter(path => governedPaths.has(path))
}

export function reconcileFrontendPathLedger(frontendRoot = FRONTEND_ROOT, ledgerPath = PATH_LEDGER) {
  const actualPaths = collectFrontendPaths(resolve(frontendRoot))
  const rows = readFileSync(resolve(ledgerPath), 'utf8').trimEnd().split('\n').slice(1).map(line => line.split('\t'))
  const ledgerPaths = rows.map(row => row[0]).sort()
  const actualSet = new Set(actualPaths)
  const ledgerSet = new Set(ledgerPaths)
  const unclassifiedPaths = actualPaths.filter(path => !ledgerSet.has(path))
  const stalePaths = ledgerPaths.filter(path => !actualSet.has(path))
  const duplicatePaths = ledgerPaths.filter((path, index) => index > 0 && path === ledgerPaths[index - 1])
  return { actualPaths, ledgerPaths, unclassifiedPaths, stalePaths, duplicatePaths }
}

function scanCleanForwardMetrics(sourceRoot, runtimeFiles, boundedCounts, authorityCounts) {
  const files = collectConsumerFiles(sourceRoot)
  const runtimePaths = runtimeFiles
    .map(file => relative(resolve(sourceRoot), file).replaceAll('\\', '/'))
  const governedPaths = runtimePaths.filter(path => POST_H7_PRODUCT_PATH_PATTERN.test(path)).sort()
  const apiAppRuntimePaths = runtimePaths.filter(path => /^api\/app\/.+\.(?:ts|tsx)$/.test(path)).sort()
  const expectedApiAppPaths = new Set(API_APP_RUNTIME_ALLOWLIST)
  const apiAppPathSet = new Set(apiAppRuntimePaths)
  const missingApiAppPaths = API_APP_RUNTIME_ALLOWLIST.filter(path => !apiAppPathSet.has(path))
  const unexpectedApiAppPaths = apiAppRuntimePaths.filter(path => !expectedApiAppPaths.has(path))
  const expectedPostH7Paths = new Set(POST_H7_GOVERNED_PATHS)
  const governedPathSet = new Set(governedPaths)
  const missingPostH7Paths = POST_H7_GOVERNED_PATHS.filter(path => !governedPathSet.has(path))
  const unexpectedPostH7Paths = governedPaths.filter(path => !expectedPostH7Paths.has(path))
  let oldComponentUsageCount = 0
  let oldSchemaUsageCount = 0
  for (const file of files) {
    const text = readFileSync(file, 'utf8')
    oldComponentUsageCount += countMatches(text, OLD_COMPONENT_PATTERN)
    oldSchemaUsageCount += countMatches(text, OLD_SCHEMA_PATTERN)
  }
  const isRepositorySource = resolve(sourceRoot) === resolve(defaultSourceRoot)
  const reconciliation = isRepositorySource
    ? reconcileFrontendPathLedger()
    : { unclassifiedPaths: [], stalePaths: [], duplicatePaths: [] }
  const localization = validateLocalizationSourceManifest(sourceRoot)
  return {
    OLD_IMPORT_COUNT: countOldImports(files),
    OLD_ROUTE_COUNT: countLegacyRoutes(sourceRoot),
    OLD_COMPONENT_USAGE_COUNT: oldComponentUsageCount,
    OLD_SCHEMA_USAGE_COUNT: oldSchemaUsageCount,
    LEGACY_RAW_STORAGE_USAGE_COUNT: boundedCounts.FRONTEND_RAW_STORAGE_PRODUCT_FIELD_COUNT,
    PLAN_NAME_AUTHORITY_BRANCH_COUNT: authorityCounts.FRONTEND_PLAN_NAME_FEATURE_AUTHORITY_COUNT,
    SCATTERED_RAW_FETCH_CALL_COUNT: boundedCounts.FRONTEND_SCATTERED_NATIVE_FETCH_COUNT,
    UNCLASSIFIED_FRONTEND_PATHS: reconciliation.unclassifiedPaths.length,
    DELETE_SHADOW_PATH_RESIDUE_COUNT: isRepositorySource
      ? DELETE_SHADOW_PATHS.filter(path => existsSync(resolve(repositoryRoot, path))).length
      : 0,
    PATH_LEDGER_STALE_PATH_COUNT: reconciliation.stalePaths.length,
    PATH_LEDGER_DUPLICATE_PATH_COUNT: reconciliation.duplicatePaths.length,
    POST_H7_GOVERNED_PATH_COUNT: governedPaths.length,
    POST_H7_GOVERNED_PATH_EXPECTED_COUNT: POST_H7_GOVERNED_PATHS.length,
    POST_H7_GOVERNED_PATH_MISSING_COUNT: missingPostH7Paths.length,
    POST_H7_GOVERNED_PATH_UNEXPECTED_COUNT: unexpectedPostH7Paths.length,
    API_APP_RUNTIME_PATH_COUNT: apiAppRuntimePaths.length,
    API_APP_RUNTIME_PATH_EXPECTED_COUNT: API_APP_RUNTIME_ALLOWLIST.length,
    API_APP_RUNTIME_PATH_MISSING_COUNT: missingApiAppPaths.length,
    API_APP_RUNTIME_PATH_UNEXPECTED_COUNT: unexpectedApiAppPaths.length,
    LOCALIZATION_SOURCE_MANIFEST_INVALID_COUNT: localization.invalidCount,
    LOCALIZATION_REQUIRED_SOURCE_KEY_MISSING_COUNT: localization.missingKeys.length,
  }
}

function unwrapExpression(node) {
  let current = node
  while (ts.isParenthesizedExpression(current) || ts.isAsExpression(current) || ts.isTypeAssertionExpression(current) || ts.isSatisfiesExpression(current)) current = current.expression
  return current
}

function propertyName(node) {
  if (ts.isIdentifier(node) || ts.isStringLiteral(node) || ts.isNumericLiteral(node)) return node.text
  return undefined
}

function collectEnglishSourceKeys(sourceText, fileName) {
  const sourceFile = ts.createSourceFile(fileName, sourceText, ts.ScriptTarget.Latest, true, ts.ScriptKind.TS)
  if (sourceFile.parseDiagnostics.length) return { ok: false, keys: new Set() }
  const englishInitializers = []
  for (const statement of sourceFile.statements) {
    if (!ts.isVariableStatement(statement)) continue
    for (const declaration of statement.declarationList.declarations) {
      if (ts.isIdentifier(declaration.name) && declaration.name.text === 'englishNamespaces' && declaration.initializer) englishInitializers.push(unwrapExpression(declaration.initializer))
    }
  }
  if (englishInitializers.length !== 1 || !ts.isObjectLiteralExpression(englishInitializers[0])) return { ok: false, keys: new Set() }
  const englishInitializer = englishInitializers[0]
  const keys = new Set()
  for (const namespaceProperty of englishInitializer.properties) {
    if (!ts.isPropertyAssignment(namespaceProperty)) continue
    const namespace = propertyName(namespaceProperty.name)
    const messages = unwrapExpression(namespaceProperty.initializer)
    if (!namespace || !ts.isObjectLiteralExpression(messages)) continue
    for (const messageProperty of messages.properties) {
      if (!ts.isPropertyAssignment(messageProperty)) continue
      const key = propertyName(messageProperty.name)
      const initializer = unwrapExpression(messageProperty.initializer)
      if (!key || !ts.isCallExpression(initializer) || !ts.isIdentifier(initializer.expression) || initializer.expression.text !== 'message') continue
      keys.add(`${namespace}.${key}`)
    }
  }
  return { ok: true, keys }
}

export function validateLocalizationSourceManifest(sourceRoot = defaultSourceRoot, { allowMissingLocalization = false } = {}) {
  const manifestPath = resolve(sourceRoot, 'localization/source-manifest.json')
  const catalogsPath = resolve(sourceRoot, 'localization/catalogs.ts')
  if (!existsSync(manifestPath) && !existsSync(catalogsPath)) return { invalidCount: allowMissingLocalization ? 0 : 1, missingKeys: [] }
  if (!existsSync(manifestPath) || !existsSync(catalogsPath)) return { invalidCount: 1, missingKeys: [] }
  try {
    const manifest = JSON.parse(readFileSync(manifestPath, 'utf8'))
    const exactFields = ['schemaVersion', 'catalogVersion', 'sourceLocale', 'fallbackLocale', 'namespaces', 'requiredSourceKeys'].sort()
    const fields = Object.keys(manifest).sort()
    const validFields = fields.length === exactFields.length && fields.every((field, index) => field === exactFields[index])
    const namespaces = manifest.namespaces
    const expectedNamespaces = ['common', 'shell', 'agent', 'canvas', 'timeline', 'review', 'workflow', 'render', 'settings', 'errors']
    const validNamespaces = Array.isArray(namespaces) && namespaces.length === expectedNamespaces.length && new Set(namespaces).size === namespaces.length && expectedNamespaces.every(namespace => namespaces.includes(namespace))
    const keys = manifest.requiredSourceKeys
    const validKeys = Array.isArray(keys) && keys.length > 0 && new Set(keys).size === keys.length && keys.every(key => typeof key === 'string' && /^[a-z][a-zA-Z0-9]*(?:[._-][a-zA-Z0-9]+)+$/.test(key) && namespaces?.includes(key.split('.')[0]))
    const valid = validFields && manifest.schemaVersion === 1 && manifest.catalogVersion === '2026.09.0' && manifest.sourceLocale === 'en' && manifest.fallbackLocale === 'en' && validNamespaces && validKeys
    if (!valid) return { invalidCount: 1, missingKeys: [] }
    const sourceKeys = collectEnglishSourceKeys(readFileSync(catalogsPath, 'utf8'), catalogsPath)
    if (!sourceKeys.ok) return { invalidCount: 1, missingKeys: [] }
    const missingKeys = keys.filter(key => !sourceKeys.keys.has(key))
    return { invalidCount: 0, missingKeys }
  } catch {
    return { invalidCount: 1, missingKeys: [] }
  }
}

function normalizeModuleTarget(importerPath, specifier) {
  let target
  if (specifier.startsWith('.')) target = posix.normalize(posix.join(posix.dirname(importerPath), specifier))
  else if (specifier.startsWith('@/')) target = posix.normalize(specifier.slice(2))
  else return specifier
  return target.replace(/\.(?:[cm]?[jt]sx?)$/, '').replace(/\/index$/, '')
}

function moduleReferences(sourceFile) {
  const references = []
  const visit = node => {
    let specifier
    if ((ts.isImportDeclaration(node) || ts.isExportDeclaration(node)) && node.moduleSpecifier && ts.isStringLiteralLike(node.moduleSpecifier)) {
      specifier = node.moduleSpecifier
    } else if (ts.isCallExpression(node) && node.arguments.length === 1 && ts.isStringLiteralLike(node.arguments[0])) {
      if (node.expression.kind === ts.SyntaxKind.ImportKeyword || (ts.isIdentifier(node.expression) && node.expression.text === 'require')) specifier = node.arguments[0]
    }
    if (specifier) references.push({ value: specifier.text, position: specifier.getStart(sourceFile) })
    ts.forEachChild(node, visit)
  }
  visit(sourceFile)
  return references
}

function scanLocalizationBoundaryImports(files, sourceRoot) {
  const violations = []
  for (const file of files) {
    const importerPath = relative(sourceRoot, file).replaceAll('\\', '/')
    const sourceText = readFileSync(file, 'utf8')
    const sourceFile = ts.createSourceFile(file, sourceText, ts.ScriptTarget.Latest, true, importerPath.endsWith('x') ? ts.ScriptKind.TSX : ts.ScriptKind.TS)
    for (const reference of moduleReferences(sourceFile)) {
      const target = normalizeModuleTarget(importerPath, reference.value)
      const vendorTarget = /^(?:@tolgee\/|tolgee(?:\/|$))/.test(reference.value)
      const integrationTarget = target === 'integrations/localization' || target.startsWith('integrations/localization/')
      const integrationInternal = importerPath.startsWith('integrations/localization/')
      const mainComposition = importerPath === 'main.tsx' && target === 'integrations/localization/config'
      if ((vendorTarget && !integrationInternal) || (integrationTarget && !integrationInternal && !mainComposition)) {
        violations.push({
          path: importerPath,
          line: sourceFile.getLineAndCharacterOfPosition(reference.position).line + 1,
          evidence: reference.value,
        })
      }
    }
  }
  return violations
}

// Parse runtime source, including aliases and arbitrary product descendants. Comments are not evidence.
function scanSelectionBoundaries(files, sourceRoot) {
  const violations = []
  for (const file of files) {
    const path = relative(sourceRoot, file).replaceAll('\\', '/')
    if (!/^(?:product|interaction)\//.test(path)) continue
    const source = ts.createSourceFile(file, readFileSync(file, 'utf8'), ts.ScriptTarget.Latest, true, path.endsWith('x') ? ts.ScriptKind.TSX : ts.ScriptKind.TS)
    const aliases = new Map()
    for (const statement of source.statements) {
      if (!ts.isImportDeclaration(statement)) continue
      const bindings = statement.importClause?.namedBindings
      if (bindings && ts.isNamedImports(bindings)) for (const binding of bindings.elements) aliases.set(binding.name.text, binding.propertyName?.text ?? binding.name.text)
    }
    const nameOf = expression => ts.isIdentifier(expression) ? aliases.get(expression.text) ?? expression.text : ts.isPropertyAccessExpression(expression) ? expression.name.text : ''
    const record = (node, reason) => violations.push({ path, line: source.getLineAndCharacterOfPosition(node.getStart(source)).line + 1, evidence: reason })
    for (const reference of moduleReferences(source)) {
      const target = normalizeModuleTarget(path, reference.value)
      if (/(?:^|\/)[^/]+-module(?:\/|$)|(?:^|\/)(?:backend|internal)(?:\/|$)/.test(target)
        || (path.startsWith('product/canvas/') && /(?:operation\.gateway|timeline\/(?:commands|store|engine)|(?:^|\/)api\/|foundation\/platformClient|(?:^|\/)(?:zustand|redux|jotai|mobx)(?:\/|$)|@tanstack\/react-router)/.test(target))
        || (path.startsWith('interaction/') && /(?:operation\.gateway|timeline\/(?:commands|store|engine))/.test(target))) {
        violations.push({ path, line: source.getLineAndCharacterOfPosition(reference.position).line + 1, evidence: reference.value })
      }
    }
    const visit = node => {
      // Slice1B: model-order hit testing, no persisted gesture authority. Reuse the existing counter.
      // Deliberately bounded syntax controls: viewport/focus rects and closest() remain legal.
      // They do not prove arbitrary helper dataflow or native pointer behavior; behavioral tests do that.
      if (path.startsWith('product/canvas/')) {
        if (ts.isCallExpression(node) && ['querySelectorAll', 'getElementsByClassName', 'getElementsByTagName', 'sort', 'toSorted', 'pushState', 'replaceState'].includes(nameOf(node.expression))) record(node, 'Canvas gestures use model order, not DOM collections, sorting or route persistence')
        if (ts.isNewExpression(node) && nameOf(node.expression) === 'URLSearchParams') record(node, 'No Canvas route selection persistence')
        if (ts.isPropertyAccessExpression(node) && (['localStorage', 'sessionStorage'].includes(node.name.text) || (ts.isIdentifier(node.expression) && ['localStorage', 'sessionStorage'].includes(node.expression.text)))) record(node, 'No Canvas gesture storage')
      }
      if (ts.isVariableDeclaration(node) && ts.isArrayBindingPattern(node.name) && node.initializer && ts.isCallExpression(node.initializer) && ['useState', 'useReducer'].includes(nameOf(node.initializer.expression))) {
        const binding = node.name.elements[0]
        if (binding && ts.isBindingElement(binding) && ts.isIdentifier(binding.name) && /^(?:selection|selectedIds|selectedObjects|selectedRefs|primarySelectedObject|primaryRef)$/.test(binding.name.text)) record(node, 'No untyped private membership state')
      }
      if (ts.isCallExpression(node)) {
        const name = nameOf(node.expression)
        if (name === 'createInteractionStore' && path !== 'interaction/SelectionContext.tsx') record(node, 'Selection stores belong only to the shell provider')
        if (['createContext', 'useState', 'useReducer', 'create', 'createStore'].includes(name)) {
          const typeNames = []
          const collectTypes = child => { if (ts.isTypeReferenceNode(child) && ts.isIdentifier(child.typeName)) typeNames.push(nameOf(child.typeName)); ts.forEachChild(child, collectTypes) }
          for (const argument of node.typeArguments ?? []) collectTypes(argument)
          if (typeNames.some(type => ['InteractionStore', 'SelectionState', 'PresentationSelectionRef'].includes(type)) && path !== 'interaction/SelectionContext.tsx') record(node, 'No private selection state or context')
        }
      }
      if ((ts.isPropertySignature(node) || ts.isPropertyAssignment(node)) && node.name) {
        const name = propertyName(node.name)
        if (['selectedPresentationId', 'selectedIds', 'selectedObjects', 'primarySelectedObject', 'selectedRefs', 'primaryRef'].includes(name) && path !== 'interaction/model.ts') record(node, 'Selection membership is owned by the interaction model')
        if (ts.isPropertyAssignment(node) && ['planDigest', 'authorization', 'authorizationReceipt', 'canonicalRevision', 'permissionGranted', 'entitlementGranted'].includes(name)) record(node, 'Selection cannot mint application authority')
        if (ts.isPropertyAssignment(node) && ts.isStringLiteralLike(node.initializer) && ((name === 'status' && node.initializer.text === 'APPLIED') || (name === 'kind' && node.initializer.text === 'EDGE'))) record(node, 'No canonical receipt or selectable edge')
        if (path.startsWith('product/canvas/') && ts.isPropertyAssignment(node) && name === 'meaning' && (!ts.isStringLiteralLike(node.initializer) || node.initializer.text !== 'VISUAL_ONLY')) record(node, 'Canvas edges are visual only')
        if (path.startsWith('product/canvas/') && ts.isPropertyAssignment(node) && ['references', 'semanticReferenceId'].includes(name)) {
          let owner = node.parent
          while (owner && !ts.isFunctionDeclaration(owner)) owner = owner.parent
          if (owner?.name?.text !== 'createCanvasState') record(node, 'Canvas may project references but cannot rewrite semantic reference authority')
        }
      }
      if (ts.isBinaryExpression(node) && path.startsWith('product/canvas/') && node.operatorToken.kind === ts.SyntaxKind.EqualsToken && ts.isPropertyAccessExpression(node.left) && ['references', 'semanticReferenceId', 'entityId', 'referenceId', 'label'].includes(node.left.name.text)) record(node, 'No Canvas semantic identity assignment')
      if (ts.isJsxElement(node)) {
        const canonicalLabel = node.children.some(child => ts.isJsxExpression(child) && child.expression && ts.isCallExpression(child.expression) && child.expression.arguments.some(argument => ts.isStringLiteralLike(argument) && ['agent.apply', 'agent.applyCanonical'].includes(argument.text)))
        if (canonicalLabel) {
          const disabled = node.openingElement.attributes.properties.find(attribute => ts.isJsxAttribute(attribute) && attribute.name.text === 'disabled')
          if (!disabled || (disabled.initializer && !(ts.isJsxExpression(disabled.initializer) && disabled.initializer.expression?.kind === ts.SyntaxKind.TrueKeyword))) record(node, 'Generic canonical Apply must be unconditionally disabled')
        }
      }
      ts.forEachChild(node, visit)
    }
    visit(source)
  }
  return violations
}

// Slice1C: heuristic syntax boundaries, not a proof of dataflow, matched-target ordering,
// history behavior or native bfcache. Actual application consumer tests cover observed ordering.
function scanRouteSelectionLifetimes(files, sourceRoot) {
  const violations = []
  const membership = /^(?:selection|selectionSnapshot|selectedPresentationId|selectedIds|selectedRefs|selectedObjects|primaryRef|primarySelectedObject|membership)$/
  const lifetimeFields = /^(?:lifetime|revision|revisionPair|history)$/
  const forbiddenMutation = /^(?:clearSelection|setSelectedRefs|restoreSelection|setRevision|setLifetime|setScope)$/
  for (const file of files) {
    const path = relative(sourceRoot, file).replaceAll('\\', '/')
    const router = /^(?:app|routes)\//.test(path)
    const boundary = router || /^(?:components\/app-shell|surfaces)\//.test(path)
    const governed = boundary || /^(?:interaction|product)\//.test(path)
    const selectionOwned = /^interaction\/(?:model\.ts|SelectionContext\.tsx)$/.test(path)
    const source = ts.createSourceFile(file, readFileSync(file, 'utf8'), ts.ScriptTarget.Latest, true, path.endsWith('x') ? ts.ScriptKind.TSX : ts.ScriptKind.TS)
    const aliases = new Map()
    for (const statement of source.statements) {
      if (!ts.isImportDeclaration(statement)) continue
      const bindings = statement.importClause?.namedBindings
      if (bindings && ts.isNamedImports(bindings)) for (const binding of bindings.elements) aliases.set(binding.name.text, binding.propertyName?.text ?? binding.name.text)
    }
    const nameOf = node => ts.isIdentifier(node) ? aliases.get(node.text) ?? node.text : ts.isPropertyAccessExpression(node) ? node.name.text : ts.isElementAccessExpression(node) && ts.isStringLiteralLike(node.argumentExpression) ? node.argumentExpression.text : ''
    const record = (node, guard, reason) => violations.push({ path, line: source.getLineAndCharacterOfPosition(node.getStart(source)).line + 1, evidence: `${guard}: ${reason}` })
    const contains = (node, predicate) => {
      if (predicate(node)) return true
      return Boolean(ts.forEachChild(node, child => contains(child, predicate) || undefined))
    }
    const hasSelection = node => contains(node, child => (ts.isIdentifier(child) || ts.isStringLiteralLike(child)) && membership.test(child.text))
    const bridgeNode = node => {
      if (/(?:lifecycle|bridge)/i.test(path)) return true
      for (let current = node.parent; current; current = current.parent) {
        if ((ts.isFunctionDeclaration(current) || ts.isVariableDeclaration(current) || ts.isInterfaceDeclaration(current)) && current.name && /(?:lifecycle|bridge)/i.test(current.name.getText(source))) return true
      }
      return false
    }
    const visit = node => {
      if (ts.isCallExpression(node)) {
        const name = nameOf(node.expression)
        // G02 scans every runtime source path, including arbitrary helper descendants.
        if (name === 'createInteractionStore' && path !== 'interaction/SelectionContext.tsx') record(node, 'G02', 'Only SelectionProvider creates product stores')
        if (router && (['useSelection', 'useInteractionStore', 'useSurfaceAdapter', 'applyProposal', 'retireSelectionOwner'].includes(name) || forbiddenMutation.test(name))) record(node, 'G01', 'Router is navigation-only')
        if (governed && hasSelection(node)) {
          if (boundary && (['navigate', 'get', 'getAll', 'parse', 'parseSearch', 'parseHash'].includes(name) || contains(node.expression, child => ts.isNewExpression(child) && nameOf(child.expression) === 'URLSearchParams'))) record(node, 'G03', 'URL must not transport presentation Selection IDs')
          if (contains(node.expression, child => ts.isIdentifier(child) && ['localStorage', 'sessionStorage', 'indexedDB'].includes(child.text)) || name === 'postMessage') record(node, 'G04', 'No persisted or broadcast Selection')
          if (['pushState', 'replaceState'].includes(name)) record(node, 'G05', 'No history Selection snapshots')
        }
        if (boundary && ['useEffect', 'useLayoutEffect'].includes(name) && node.arguments.some(argument => contains(argument, child => ts.isCallExpression(child) && (forbiddenMutation.test(nameOf(child.expression)) || (nameOf(child.expression) === 'select' && ts.isPropertyAccessExpression(child.expression)))))) record(node, 'G08', 'No effect-time Selection repair after target rendering')
      }
      if (governed && ts.isNewExpression(node) && ['BroadcastChannel', 'URLSearchParams'].includes(nameOf(node.expression)) && hasSelection(node)) record(node, nameOf(node.expression) === 'BroadcastChannel' ? 'G04' : 'G03', 'No Selection transport')
      if (governed && (ts.isPropertyAccessExpression(node) || ts.isElementAccessExpression(node)) && hasSelection(node) && contains(node, child => ts.isPropertyAccessExpression(child) && nameOf(child) === 'state' && nameOf(child.expression) === 'history')) record(node, 'G05', 'No history Selection restoration')
      if (governed && ts.isObjectLiteralExpression(node) && !selectionOwned) {
        const overridesScope = node.properties.some(property => ts.isPropertyAssignment(property) && ['surfaceId', 'workspaceId', 'projectId'].includes(propertyName(property.name)))
        if (overridesScope && node.properties.some(property => ts.isSpreadAssignment(property) && /ref|selection/i.test(property.expression.getText(source)))) record(node, 'G06', 'Do not relabel refs into another scope')
      }
      if (ts.isPropertySignature(node) || ts.isPropertyAssignment(node) || ts.isVariableDeclaration(node)) {
        const name = node.name ? propertyName(node.name) : ''
        if (router && (membership.test(name) || lifetimeFields.test(name))) record(node, 'G01', 'Router cannot own membership or lifetime fields')
        if (governed && !selectionOwned && bridgeNode(node) && (membership.test(name) || lifetimeFields.test(name))) record(node, 'G07', 'Lifecycle bridges cannot store Selection authority')
      }
      if (boundary && ts.isJsxAttribute(node)) {
        if (node.name.text === 'role' && node.initializer && ts.isStringLiteralLike(node.initializer) && node.initializer.text === 'application') record(node, 'G01', 'No route keyboard authority')
        if (node.name.text === 'key' && node.parent.parent.tagName?.getText(source) === 'SelectionProvider' && node.initializer && contains(node.initializer, child => ts.isIdentifier(child) && ['pathname', 'location', 'history'].includes(child.text))) record(node, 'G08', 'Route identity is not Selection scope')
      }
      ts.forEachChild(node, visit)
    }
    visit(source)
  }
  return violations
}

function cleanForwardMetricsPassed(metrics) {
  return metrics.OLD_IMPORT_COUNT === 0
    && metrics.OLD_ROUTE_COUNT <= LEGACY_ROUTE_PATHS.length
    && metrics.OLD_COMPONENT_USAGE_COUNT === 0
    && metrics.OLD_SCHEMA_USAGE_COUNT === 0
    && metrics.UNCLASSIFIED_FRONTEND_PATHS === 0
    && metrics.DELETE_SHADOW_PATH_RESIDUE_COUNT === 0
    && metrics.PATH_LEDGER_STALE_PATH_COUNT === 0
    && metrics.PATH_LEDGER_DUPLICATE_PATH_COUNT === 0
    && metrics.POST_H7_GOVERNED_PATH_COUNT === metrics.POST_H7_GOVERNED_PATH_EXPECTED_COUNT
    && metrics.POST_H7_GOVERNED_PATH_MISSING_COUNT === 0
    && metrics.POST_H7_GOVERNED_PATH_UNEXPECTED_COUNT === 0
    && metrics.API_APP_RUNTIME_PATH_COUNT === metrics.API_APP_RUNTIME_PATH_EXPECTED_COUNT
    && metrics.API_APP_RUNTIME_PATH_MISSING_COUNT === 0
    && metrics.API_APP_RUNTIME_PATH_UNEXPECTED_COUNT === 0
    && metrics.LOCALIZATION_SOURCE_MANIFEST_INVALID_COUNT === 0
    && metrics.LOCALIZATION_REQUIRED_SOURCE_KEY_MISSING_COUNT === 0
}

function lineNumber(text, index) {
  return text.slice(0, index).split('\n').length
}

export function scanFrontendArchitecture(sourceRoot = defaultSourceRoot) {
  const absoluteRoot = resolve(sourceRoot)
  const files = collectSourceFiles(absoluteRoot)
  if (files.length === 0) {
    throw new Error(`FRONTEND_ARCHITECTURE_GUARD_EMPTY_SCAN_UNIVERSE: ${absoluteRoot}`)
  }

  const allRules = [...AUTHORITY_RULES, ...BOUNDED_RULES]
  const violations = Object.fromEntries(allRules.map(rule => [rule.name, []]))
  violations.FRONTEND_ROUTE_SELECTION_LIFETIME_VIOLATION_COUNT.push(...scanRouteSelectionLifetimes(files, absoluteRoot))
  violations.FRONTEND_SELECTION_BOUNDARY_VIOLATION_COUNT.push(...scanSelectionBoundaries(files, absoluteRoot))
  violations.FRONTEND_DIRECT_TOLGEE_PRODUCT_IMPORT_COUNT.push(...scanLocalizationBoundaryImports(files, absoluteRoot))
  for (const file of files) {
    const path = relative(absoluteRoot, file).replaceAll('\\', '/')
    const text = readFileSync(file, 'utf8')
    for (const rule of allRules) {
      if (rule.governedPathPattern && !rule.governedPathPattern.test(path)) continue
      if (rule.excludedPathPattern?.test(path)) continue
      if (rule.pathPattern?.test(path)) {
        violations[rule.name].push({ path, line: 1, evidence: 'forbidden governed path' })
      }
      for (const pattern of rule.patterns) {
        const flags = pattern.flags.includes('g') ? pattern.flags : `${pattern.flags}g`
        const matcher = new RegExp(pattern.source, flags)
        for (const match of text.matchAll(matcher)) {
          violations[rule.name].push({
            path,
            line: lineNumber(text, match.index ?? 0),
            evidence: match[0].replaceAll('\n', ' ').slice(0, 160),
          })
        }
      }
    }
  }

  const counts = Object.fromEntries(
    AUTHORITY_RULES.map(rule => [rule.name, violations[rule.name].length])
  )
  const boundedCounts = Object.fromEntries(
    BOUNDED_RULES.map(rule => [rule.name, violations[rule.name].length])
  )
  return {
    sourceRoot: absoluteRoot,
    scannedFileCount: files.length,
    violations,
    counts,
    boundedCounts,
    cleanForwardCounts: scanCleanForwardMetrics(absoluteRoot, files, boundedCounts, counts),
  }
}

export function architectureGuardPassed(result) {
  const authorityTotal = Object.values(result.counts).reduce((sum, count) => sum + count, 0)
  const boundedPass = BOUNDED_RULES.every(rule => result.boundedCounts[rule.name] <= rule.maximum)
  return authorityTotal === 0 && boundedPass && cleanForwardMetricsPassed(result.cleanForwardCounts)
}

export function formatGuardResult(result) {
  const lines = [`FRONTEND_GOVERNED_SOURCE_FILE_COUNT=${result.scannedFileCount}`]
  for (const rule of AUTHORITY_RULES) {
    lines.push(`${rule.name}=${result.counts[rule.name]}`)
  }
  for (const rule of BOUNDED_RULES) {
    lines.push(`${rule.name}=${result.boundedCounts[rule.name]}`)
    lines.push(`${rule.name}_MAXIMUM=${rule.maximum}`)
  }
  for (const [name, count] of Object.entries(result.cleanForwardCounts)) {
    lines.push(`${name}=${count}`)
  }
  lines.push(`OLD_ROUTE_COUNT_MAXIMUM=${LEGACY_ROUTE_PATHS.length}`)
  for (const rule of [...AUTHORITY_RULES, ...BOUNDED_RULES]) {
    for (const violation of result.violations[rule.name]) {
      lines.push(`${rule.name}_EVIDENCE=${violation.path}:${violation.line}:${violation.evidence}`)
    }
  }
  lines.push(`FRONTEND_ARCHITECTURE_GUARD=${architectureGuardPassed(result) ? 'PASS' : 'FAIL'}`)
  return lines.join('\n')
}

function cliSourceRoot(argv) {
  const rootIndex = argv.indexOf('--root')
  if (rootIndex === -1) return defaultSourceRoot
  const value = argv[rootIndex + 1]
  if (!value) throw new Error('--root requires a path')
  return resolve(value)
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  try {
    const result = scanFrontendArchitecture(cliSourceRoot(process.argv.slice(2)))
    console.log(formatGuardResult(result))
    if (!architectureGuardPassed(result)) process.exitCode = 1
  } catch (error) {
    console.error(error instanceof Error ? error.message : String(error))
    process.exitCode = 1
  }
}
