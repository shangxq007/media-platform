#!/usr/bin/env node

import { readFileSync, readdirSync, statSync } from 'node:fs'
import { dirname, relative, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const scriptDirectory = dirname(fileURLToPath(import.meta.url))
export const repositoryRoot = resolve(scriptDirectory, '../..')
export const defaultSourceRoot = resolve(repositoryRoot, 'frontend/src')

const SOURCE_EXTENSIONS = new Set(['.ts', '.tsx'])
const EXCLUDED_SEGMENTS = new Set(['dist', 'build', 'node_modules', 'vendor', 'fixtures'])
const ACTIVE_PRODUCT_PATH_PATTERN = /^(?:api\/render-jobs\.ts|components\/render-jobs\/|editor\/|pages\/(?:RenderJobDashboard|SmokeEditorPage)\.tsx|routes\/app\/renders\/|shared\/CapabilitiesPage\.tsx)/
const EXPLICIT_NON_PRODUCT_SURFACE_PATTERN = /^(?:api\/(?:admin|dev|operator)\/|components\/(?:admin|dev|operator)\/|pages\/(?:Admin|Dev|Observability|Operator)|routes\/(?:admin|dev|operator)\/|routes\/app\/(?:admin|dev|operator)\/)/i

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

function lineNumber(text, index) {
  return text.slice(0, index).split('\n').length
}

export function scanFrontendArchitecture(sourceRoot = defaultSourceRoot) {
  const absoluteRoot = resolve(sourceRoot)
  const files = collectSourceFiles(absoluteRoot)
  if (files.length === 0) {
    throw new Error(`FRONTEND_ARCHITECTURE_GUARD_EMPTY_SCAN_UNIVERSE: ${absoluteRoot}`)
  }

  const violations = Object.fromEntries(AUTHORITY_RULES.map(rule => [rule.name, []]))
  for (const file of files) {
    const path = relative(absoluteRoot, file).replaceAll('\\', '/')
    const text = readFileSync(file, 'utf8')
    for (const rule of AUTHORITY_RULES) {
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

  return {
    sourceRoot: absoluteRoot,
    scannedFileCount: files.length,
    violations,
    counts: Object.fromEntries(
      AUTHORITY_RULES.map(rule => [rule.name, violations[rule.name].length])
    ),
  }
}

export function formatGuardResult(result) {
  const lines = [`FRONTEND_GOVERNED_SOURCE_FILE_COUNT=${result.scannedFileCount}`]
  for (const rule of AUTHORITY_RULES) {
    lines.push(`${rule.name}=${result.counts[rule.name]}`)
  }
  for (const rule of AUTHORITY_RULES) {
    for (const violation of result.violations[rule.name]) {
      lines.push(`${rule.name}_EVIDENCE=${violation.path}:${violation.line}:${violation.evidence}`)
    }
  }
  const total = Object.values(result.counts).reduce((sum, count) => sum + count, 0)
  lines.push(`FRONTEND_ARCHITECTURE_GUARD=${total === 0 ? 'PASS' : 'FAIL'}`)
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
    const total = Object.values(result.counts).reduce((sum, count) => sum + count, 0)
    if (total !== 0) process.exitCode = 1
  } catch (error) {
    console.error(error instanceof Error ? error.message : String(error))
    process.exitCode = 1
  }
}
