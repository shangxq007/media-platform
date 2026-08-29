import assert from 'node:assert/strict'
import { existsSync, mkdirSync, mkdtempSync, rmSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import test from 'node:test'
import {
  AUTHORITY_RULES,
  defaultSourceRoot,
  scanFrontendArchitecture,
} from './frontend-architecture-guard.mjs'

test('governed frontend passes with all authority counts at zero', () => {
  const result = scanFrontendArchitecture(defaultSourceRoot)
  assert.ok(result.scannedFileCount > 0)
  for (const rule of AUTHORITY_RULES) {
    assert.equal(result.counts[rule.name], 0, rule.name)
  }
  console.log(`FRONTEND_ARCHITECTURE_POSITIVE_CONTROL=PASS files=${result.scannedFileCount}`)
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
]

for (const [ruleName, relativePath, source] of mutations) {
  test(`${ruleName} rejects its behavioral mutation and leaves zero residue`, () => {
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
