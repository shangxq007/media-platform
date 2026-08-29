#!/usr/bin/env node

import { readFileSync, readdirSync, statSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

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

  const actualCounts = {}
  for (const entry of ledger.conceptFamilies) {
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

  for (const gap of ledger.backendProjectionGaps) {
    for (const field of ['id', 'uiConsumer', 'missingProjection', 'smallestBackendContract', 'severity', 'blocking', 'CROSS_LANE_REQUIREMENT_FOUND']) {
      assert(gap[field] != null, `${gap.id ?? 'unknown gap'} missing ${field}`)
    }
  }

  const governedFiles = countGovernedFiles(sourceRoot)
  assert(governedFiles > 0, 'empty governed frontend file universe')
  assert(
    ledger.mechanicalTotals.postAlignment.governedFrontendFiles === governedFiles,
    `postAlignment governed file mismatch: ledger=${ledger.mechanicalTotals.postAlignment.governedFrontendFiles} actual=${governedFiles}`
  )
  for (const [name, count] of Object.entries(ledger.requiredAuthorityCounts)) {
    assert(count === 0, `${name} must be numeric zero`)
  }

  console.log(`H4_LEDGER_SCHEMA=PASS`)
  console.log(`H4_LEDGER_CLASSIFIED_CONCEPT_FAMILY_COUNT=${ledger.conceptFamilies.length}`)
  console.log(`H4_LEDGER_BACKEND_PROJECTION_GAP_COUNT=${ledger.backendProjectionGaps.length}`)
  console.log(`H4_LEDGER_GOVERNED_FRONTEND_FILE_COUNT=${governedFiles}`)
  console.log(`H4_LEDGER_UNCLASSIFIED_COUNT=${actualCounts.UNCLASSIFIED ?? 0}`)
} catch (error) {
  console.error(`H4_LEDGER_SCHEMA=FAIL`)
  console.error(error instanceof Error ? error.message : String(error))
  process.exitCode = 1
}
