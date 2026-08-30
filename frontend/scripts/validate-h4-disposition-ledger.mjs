#!/usr/bin/env node

import { readFileSync, readdirSync, statSync } from 'node:fs'
import { dirname, relative, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { AUTHORITY_RULES, scanFrontendArchitecture } from './frontend-architecture-guard.mjs'

const repositoryRoot = resolve(dirname(fileURLToPath(import.meta.url)), '../..')
const historicalLedgerPath = resolve(
  repositoryRoot,
  'docs/architecture/governance/h4-frontend-product-surface-disposition-ledger-v1.json'
)
const currentScopeLedgerPath = resolve(
  repositoryRoot,
  'docs/architecture/governance/frontend-current-governed-scope-ledger-v1.tsv'
)
const sourceRoot = resolve(repositoryRoot, 'frontend/src')
const h4BaseSha = '9cd899a3ad6196e04cdfda21430ed61529abf49a'

const allowedScopeStates = new Set(['ACTIVE', 'RETIRED'])
const allowedScopeOrigins = new Set(['H4_BASELINE', 'POST_H4_ADDITION'])
const requiredScopeLaws = new Set([
  'HISTORICAL_CLOSURE_LEDGER_IS_IMMUTABLE_V1',
  'CURRENT_GOVERNED_SCOPE_EVOLVES_APPEND_FORWARD_V1',
])

const allowedDispositions = new Set([
  'REUSE_AS_PRODUCT_SURFACE',
  'MIGRATE_TO_CANONICAL_CONTRACT',
  'DELETE_SHADOW',
  'REUSE_MECHANICS_ONLY',
  'BLOCKED_BY_BACKEND_PROJECTION',
  'DEFER',
  'UNCLASSIFIED',
])

function assert(condition, message) {
  if (!condition) throw new Error(message)
}

function countGovernedFiles(root) {
  const governedFiles = new Set()
  const visit = path => {
    const entry = statSync(path)
    if (entry.isDirectory()) {
      for (const name of readdirSync(path)) {
        if (['dist', 'build', 'node_modules', 'fixtures'].includes(name)) continue
        visit(resolve(path, name))
      }
      return
    }
    if (!/\.(?:ts|tsx|css)$/.test(path)) return
    if (/\.(?:test|spec)\.[cm]?[jt]sx?$/.test(path) || /\.d\.ts$/.test(path)) return
    governedFiles.add(relative(repositoryRoot, path).replaceAll('\\', '/'))
  }
  visit(root)
  return governedFiles
}

function parseCurrentScopeLedger(path) {
  const lines = readFileSync(path, 'utf8').split(/\r?\n/)
  const metadata = new Map()
  const rows = []
  let headerSeen = false

  for (const [index, line] of lines.entries()) {
    if (line === '') continue
    if (line.startsWith('#')) {
      assert(!headerSeen, `metadata after header at line ${index + 1}`)
      const match = /^# ([a-z0-9_]+)=(.+)$/.exec(line)
      assert(match, `malformed metadata at line ${index + 1}`)
      assert(!metadata.has(match[1]), `duplicate metadata key: ${match[1]}`)
      metadata.set(match[1], match[2])
      continue
    }
    if (!headerSeen) {
      assert(line === 'path\tstate\torigin\trationale', 'invalid current scope ledger header')
      headerSeen = true
      continue
    }

    const fields = line.split('\t')
    assert(fields.length === 4, `malformed row at line ${index + 1}`)
    const [identityPath, state, origin, rationale] = fields
    assert(
      identityPath.length > 0 && state.length > 0 && origin.length > 0 && rationale.length > 0,
      `malformed row at line ${index + 1}: empty field`
    )
    assert(
      identityPath.startsWith('frontend/src/') &&
        !identityPath.includes('\\') &&
        !identityPath.split('/').some(segment => segment === '.' || segment === '..'),
      `malformed governed identity path: ${identityPath}`
    )
    assert(allowedScopeStates.has(state), `unknown current scope state: ${state}`)
    assert(allowedScopeOrigins.has(origin), `unknown current scope origin: ${origin}`)
    rows.push({ path: identityPath, state, origin, rationale })
  }

  assert(headerSeen, 'missing current scope ledger header')
  assert(rows.length > 0, 'empty current scope ledger')
  assert(metadata.get('h4_base_sha') === h4BaseSha, 'current scope ledger H4 base SHA mismatch')
  const laws = new Set((metadata.get('laws') ?? '').split(',').filter(Boolean))
  for (const law of requiredScopeLaws) {
    assert(laws.has(law), `missing current scope ledger law: ${law}`)
  }
  return rows
}

try {
  const ledger = JSON.parse(readFileSync(historicalLedgerPath, 'utf8'))
  assert(ledger.schemaVersion === 'H4_FRONTEND_PRODUCT_SURFACE_DISPOSITION_LEDGER_V1', 'invalid schemaVersion')
  assert(Array.isArray(ledger.conceptFamilies) && ledger.conceptFamilies.length > 0, 'empty conceptFamilies')
  assert(Array.isArray(ledger.backendProjectionGaps), 'missing backendProjectionGaps')
  assert(ledger.FRONTEND_TECHNOLOGY_AND_PRODUCT_ARCHITECTURE_ALIGNMENT, 'missing technology alignment section')
  assert(ledger.appendForwardCorrection?.taskId === 'H4_BOUNDED_CORRECTION_R1_R6', 'missing correction task metadata')
  assert(
    ledger.appendForwardCorrection?.reviewedCandidateSha === '95f53f52b7a50c0f5a5d82bf369d354f62cbb762',
    'reviewed candidate SHA mismatch'
  )
  assert(ledger.appendForwardCorrection?.reviewedCandidateHistoryMutated === false, 'reviewed history mutation flag must be false')

  const actualCounts = {}
  const conceptIds = new Set()
  for (const entry of ledger.conceptFamilies) {
    assert(!conceptIds.has(entry.id), `duplicate concept id: ${entry.id}`)
    conceptIds.add(entry.id)
    assert(allowedDispositions.has(entry.disposition), `invalid disposition: ${entry.disposition}`)
    for (const field of ['id', 'concept', 'pathsOrPatterns', 'evidence', 'runtimeReachability', 'rationale']) {
      assert(entry[field] != null, `${entry.id ?? 'unknown'} missing ${field}`)
    }
    actualCounts[entry.disposition] = (actualCounts[entry.disposition] ?? 0) + 1
  }
  assert((actualCounts.UNCLASSIFIED ?? 0) === 0, 'UNCLASSIFIED must equal zero')
  assert(
    ledger.mechanicalTotals.classifiedConceptFamilies === ledger.conceptFamilies.length,
    'classifiedConceptFamilies does not equal entry count'
  )
  for (const [disposition, count] of Object.entries(ledger.mechanicalTotals.dispositionCounts)) {
    assert((actualCounts[disposition] ?? 0) === count, `disposition count mismatch: ${disposition}`)
  }
  for (const [disposition, count] of Object.entries(ledger.mechanicalTotals.postCorrection.dispositionCounts)) {
    assert((actualCounts[disposition] ?? 0) === count, `postCorrection disposition count mismatch: ${disposition}`)
  }
  assert(
    ledger.mechanicalTotals.postCorrection.classifiedConceptFamilies === ledger.conceptFamilies.length,
    'postCorrection classifiedConceptFamilies does not equal entry count'
  )

  const gapIds = new Set()
  for (const gap of ledger.backendProjectionGaps) {
    assert(!gapIds.has(gap.id), `duplicate gap id: ${gap.id}`)
    gapIds.add(gap.id)
    for (const field of ['id', 'uiConsumer', 'missingProjection', 'smallestBackendContract', 'severity', 'blocking', 'CROSS_LANE_REQUIREMENT_FOUND']) {
      assert(gap[field] != null, `${gap.id ?? 'unknown gap'} missing ${field}`)
    }
  }
  const artifactGap = ledger.backendProjectionGaps.find(gap => gap.id === 'H4-GAP-006')
  assert(artifactGap?.blocking === true, 'H4-GAP-006 must block the active artifact-list surface')
  assert(/tenant\/project-scoped redacted artifact summary\/list projection/i.test(artifactGap?.missingProjection ?? ''), 'H4-GAP-006 missing scoped redacted summary/list requirement')
  assert(/no storage coordinates/i.test(artifactGap?.missingProjection ?? ''), 'H4-GAP-006 must prohibit storage coordinates')
  assert(/\/tenants\/\{tenantId\}\/projects\/\{projectId\}\/render-jobs\/\{jobId\}\/artifacts\/\{artifactId\}\/access/.test(artifactGap?.smallestBackendContract ?? ''), 'H4-GAP-006 missing separately scoped on-demand access endpoint')

  const governedFiles = countGovernedFiles(sourceRoot)
  assert(governedFiles.size > 0, 'empty actual governed frontend file universe')
  const scopeRows = parseCurrentScopeLedger(currentScopeLedgerPath)
  const activePaths = new Set()
  const retiredPaths = new Set()
  const seenScopePaths = new Map()
  let additions = 0
  for (const row of scopeRows) {
    const priorState = seenScopePaths.get(row.path)
    if (priorState != null) {
      assert(priorState === row.state, `active+retired conflict: ${row.path}`)
      assert(false, `duplicate current scope path row: ${row.path}`)
    }
    seenScopePaths.set(row.path, row.state)
    if (row.state === 'ACTIVE') {
      activePaths.add(row.path)
      if (row.origin === 'POST_H4_ADDITION') additions += 1
    } else {
      assert(row.origin === 'H4_BASELINE', `RETIRED identity must originate from H4 baseline: ${row.path}`)
      assert(
        row.rationale === 'POST_H4_ZERO_USE_RETIREMENT',
        `invalid RETIRED identity rationale: ${row.path}`
      )
      retiredPaths.add(row.path)
    }
  }
  assert(activePaths.size > 0, 'empty expected ACTIVE governed frontend file universe')
  for (const path of activePaths) {
    assert(governedFiles.has(path), `stale ACTIVE current scope identity: ${path}`)
  }
  for (const path of governedFiles) {
    assert(activePaths.has(path), `missing actual current scope identity: ${path}`)
  }
  for (const path of retiredPaths) {
    assert(!governedFiles.has(path), `RETIRED current scope identity still present: ${path}`)
  }
  assert(
    activePaths.size === governedFiles.size,
    `ACTIVE/current governed identity count mismatch: active=${activePaths.size} actual=${governedFiles.size}`
  )
  const guardResult = scanFrontendArchitecture(sourceRoot)
  assert(
    Object.keys(ledger.requiredAuthorityCounts).length === AUTHORITY_RULES.length,
    'requiredAuthorityCounts must cover every architecture guard rule'
  )
  for (const rule of AUTHORITY_RULES) {
    assert(Object.hasOwn(ledger.requiredAuthorityCounts, rule.name), `missing required authority count: ${rule.name}`)
    assert(ledger.requiredAuthorityCounts[rule.name] === 0, `${rule.name} must be numeric zero`)
    assert(
      guardResult.counts[rule.name] === ledger.requiredAuthorityCounts[rule.name],
      `${rule.name} actual count does not match ledger requirement`
    )
  }
  for (const name of [
    'FRONTEND_SYNTHETIC_WORKSPACE_SCOPE_COUNT',
    'FRONTEND_ACTIVE_UNSCOPED_RENDER_API_COUNT',
    'FRONTEND_STALE_RENDER_STATUS_SHADOW_COUNT',
  ]) {
    assert(
      ledger.mechanicalTotals.postCorrection[name] === guardResult.counts[name],
      `postCorrection ${name} does not match architecture guard`
    )
  }

  console.log(`H4_LEDGER_SCHEMA=PASS`)
  console.log(`H4_LEDGER_CLASSIFIED_CONCEPT_FAMILY_COUNT=${ledger.conceptFamilies.length}`)
  console.log(`H4_LEDGER_BACKEND_PROJECTION_GAP_COUNT=${ledger.backendProjectionGaps.length}`)
  console.log(`H4_LEDGER_HISTORICAL_GOVERNED_FRONTEND_FILE_COUNT=${ledger.mechanicalTotals.postCorrection.governedFrontendFiles}`)
  console.log(`H4_LEDGER_CURRENT_GOVERNED_FRONTEND_FILE_COUNT=${governedFiles.size}`)
  console.log(`H4_LEDGER_ACTIVE_SCOPE_IDENTITY_COUNT=${activePaths.size}`)
  console.log(`H4_LEDGER_POST_H4_ADDITION_COUNT=${additions}`)
  console.log(`H4_LEDGER_RETIRED_BASELINE_IDENTITY_COUNT=${retiredPaths.size}`)
  console.log(`H4_LEDGER_GUARDED_TYPESCRIPT_FILE_COUNT=${guardResult.scannedFileCount}`)
  console.log(`H4_LEDGER_UNCLASSIFIED_COUNT=${actualCounts.UNCLASSIFIED ?? 0}`)
} catch (error) {
  console.error(`H4_LEDGER_SCHEMA=FAIL`)
  console.error(error instanceof Error ? error.message : String(error))
  process.exitCode = 1
}
