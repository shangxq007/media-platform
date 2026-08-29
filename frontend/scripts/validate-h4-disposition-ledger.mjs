#!/usr/bin/env node

import { readFileSync, readdirSync, statSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { AUTHORITY_RULES, scanFrontendArchitecture } from './frontend-architecture-guard.mjs'

const repositoryRoot = resolve(dirname(fileURLToPath(import.meta.url)), '../..')
const ledgerPath = resolve(
  repositoryRoot,
  'docs/architecture/governance/h4-frontend-product-surface-disposition-ledger-v1.json'
)
const sourceRoot = resolve(repositoryRoot, 'frontend/src')

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
  let count = 0
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
    count += 1
  }
  visit(root)
  return count
}

try {
  const ledger = JSON.parse(readFileSync(ledgerPath, 'utf8'))
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
  assert(governedFiles > 0, 'empty governed frontend file universe')
  assert(
    ledger.mechanicalTotals.postCorrection.governedFrontendFiles === governedFiles,
    `postCorrection governed file mismatch: ledger=${ledger.mechanicalTotals.postCorrection.governedFrontendFiles} actual=${governedFiles}`
  )
  const guardResult = scanFrontendArchitecture(sourceRoot)
  assert(
    ledger.mechanicalTotals.postCorrection.guardedTypeScriptFiles === guardResult.scannedFileCount,
    `postCorrection guarded TypeScript file mismatch: ledger=${ledger.mechanicalTotals.postCorrection.guardedTypeScriptFiles} actual=${guardResult.scannedFileCount}`
  )
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
  console.log(`H4_LEDGER_GOVERNED_FRONTEND_FILE_COUNT=${governedFiles}`)
  console.log(`H4_LEDGER_GUARDED_TYPESCRIPT_FILE_COUNT=${guardResult.scannedFileCount}`)
  console.log(`H4_LEDGER_UNCLASSIFIED_COUNT=${actualCounts.UNCLASSIFIED ?? 0}`)
} catch (error) {
  console.error(`H4_LEDGER_SCHEMA=FAIL`)
  console.error(error instanceof Error ? error.message : String(error))
  process.exitCode = 1
}
