#!/usr/bin/env node

import { existsSync, readFileSync, readdirSync, statSync } from 'node:fs'
import { dirname, relative, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const scriptDirectory = dirname(fileURLToPath(import.meta.url))
export const repositoryRoot = resolve(scriptDirectory, '../..')
export const defaultSourceRoot = resolve(repositoryRoot, 'frontend/src')

const SOURCE_EXTENSIONS = new Set(['.ts', '.tsx'])
const EXCLUDED_SEGMENTS = new Set(['dist', 'build', 'node_modules', 'vendor', 'fixtures'])
const PATH_LEDGER = resolve(repositoryRoot, 'docs/architecture/governance/frontend-product-path-classification-v1.tsv')
const FRONTEND_ROOT = resolve(repositoryRoot, 'frontend')
const ACTIVE_PRODUCT_PATH_PATTERN = /^(?:api\/render-jobs\.ts|components\/render-jobs\/|editor\/|pages\/(?:RenderJobDashboard|SmokeEditorPage)\.tsx|routes\/app\/renders\/|shared\/CapabilitiesPage\.tsx)/
const EXPLICIT_NON_PRODUCT_SURFACE_PATTERN = /^(?:api\/(?:admin|dev|operator)\/|components\/(?:admin|dev|operator)\/|pages\/(?:Admin|Dev|Observability|Operator)|routes\/(?:admin|dev|operator)\/|routes\/app\/(?:admin|dev|operator)\/)/i

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
  {
    name: 'FRONTEND_RAW_STORAGE_PRODUCT_FIELD_COUNT',
    maximum: 1,
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

function collectFrontendPaths(root) {
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
    paths.push(relative(repositoryRoot, path).replaceAll('\\', '/'))
  }
  visit(root)
  return paths.sort()
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

function scanCleanForwardMetrics(sourceRoot, boundedCounts, authorityCounts) {
  const files = collectConsumerFiles(sourceRoot)
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
  }
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
    cleanForwardCounts: scanCleanForwardMetrics(absoluteRoot, boundedCounts, counts),
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
