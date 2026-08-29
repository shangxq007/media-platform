#!/usr/bin/env node

import { existsSync, readFileSync, readdirSync, statSync } from 'node:fs'
import { dirname, relative, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const repositoryRoot = resolve(dirname(fileURLToPath(import.meta.url)), '../..')
const frontendSource = resolve(repositoryRoot, 'frontend/src')

const retiredPaths = [
  'frontend/src/api/assets.ts',
  'frontend/src/api/client-export.ts',
  'frontend/src/api/contracts',
  'frontend/src/api/smoke-editor.ts',
  'frontend/src/components/artifacts/ArtifactPreview.tsx',
  'frontend/src/components/assets',
  'frontend/src/components/smoke-editor',
  'frontend/src/domain',
  'frontend/src/hooks/useAssets.ts',
  'frontend/src/observability',
  'frontend/src/query/app/useRenderJob.ts',
  'frontend/src/query/shared/polling-policy.ts',
  'frontend/src/timeline/mappers/timelineToRender.ts',
  'frontend/src/utils/timelineConflictMerge.ts',
]

const retiredSymbols = /\b(?:SmokeEditorAPI|SmokeTimelineInput|ClientExportAPI|AssetDomain|toAssetDomain|mergeEditorTimelines|detectTimelineConflict|trackClipsEqual|mapTimelineToRender|validateTimelineForRender|RenderJobSummarySchema|RenderJobArtifactSchema|useCancelRenderJob)\b/g

function sourceFiles(root) {
  const files = []
  const visit = path => {
    const entry = statSync(path)
    if (entry.isDirectory()) {
      for (const name of readdirSync(path)) {
        if (['fixtures', 'node_modules', 'dist', 'build'].includes(name)) continue
        visit(resolve(path, name))
      }
      return
    }
    if (!/\.(?:ts|tsx)$/.test(path) || /\.(?:test|spec)\.[cm]?[jt]sx?$/.test(path)) return
    files.push(path)
  }
  visit(root)
  return files
}

function matches(files, pattern) {
  const found = []
  for (const file of files) {
    const text = readFileSync(file, 'utf8')
    const matcher = new RegExp(pattern.source, pattern.flags.includes('g') ? pattern.flags : `${pattern.flags}g`)
    for (const match of text.matchAll(matcher)) {
      found.push(`${relative(repositoryRoot, file)}:${match[0]}`)
    }
  }
  return found
}

try {
  const files = sourceFiles(frontendSource)
  if (files.length === 0) throw new Error('empty frontend source universe')

  const retiredPathResidue = retiredPaths.filter(path => existsSync(resolve(repositoryRoot, path)))
  const retiredSymbolUsage = matches(files, retiredSymbols)

  const activeProductFiles = [
    'frontend/src/pages/RenderJobDashboard.tsx',
    'frontend/src/pages/SmokeEditorPage.tsx',
    'frontend/src/shared/CapabilitiesPage.tsx',
    'frontend/src/components/render-jobs/ArtifactView.tsx',
    'frontend/src/components/render-jobs/JobDetail.tsx',
    'frontend/src/components/render-jobs/JobList.tsx',
  ].map(path => resolve(repositoryRoot, path))
  const activeRawStorageAssumptions = matches(
    activeProductFiles,
    /\b(?:storageUri|storageKey|sourceUrl|assetUri|file:\/\/|s3:\/\/)\b/gi
  )

  const renderAlignmentFiles = [
    resolve(repositoryRoot, 'frontend/src/api/render-jobs.ts'),
    resolve(repositoryRoot, 'frontend/src/contracts/app/render-job.ts'),
    ...activeProductFiles,
  ]
  const retiredRenderStatusAliases = matches(renderAlignmentFiles, /\bPROCESSING\b/g)
  const unscopedRenderQueries = matches(
    [resolve(repositoryRoot, 'frontend/src/api/render-jobs.ts')],
    /api\.get\((?:['"]\/render\/jobs['"]|`\/render\/jobs\/\$\{jobId\}`)\)/g
  )

  const defaultApiPath = resolve(repositoryRoot, 'frontend/src/api/index.ts')
  if (!existsSync(defaultApiPath)) throw new Error('default frontend API compatibility entrypoint is missing')

  console.log(`H4_RETIRED_PATH_RESIDUE_COUNT=${retiredPathResidue.length}`)
  console.log(`H4_RETIRED_SYMBOL_USAGE_COUNT=${retiredSymbolUsage.length}`)
  console.log(`H4_ACTIVE_RAW_STORAGE_ASSUMPTION_COUNT=${activeRawStorageAssumptions.length}`)
  console.log(`H4_RETIRED_RENDER_STATUS_ALIAS_COUNT=${retiredRenderStatusAliases.length}`)
  console.log(`H4_UNSCOPED_RENDER_LIST_OR_DETAIL_QUERY_COUNT=${unscopedRenderQueries.length}`)
  console.log('H4_DEFAULT_API_COMPATIBILITY_ENTRYPOINT=PRESERVED')

  const failures = [
    ...retiredPathResidue,
    ...retiredSymbolUsage,
    ...activeRawStorageAssumptions,
    ...retiredRenderStatusAliases,
    ...unscopedRenderQueries,
  ]
  if (failures.length > 0) {
    for (const failure of failures) console.error(`H4_CLEAN_FORWARD_EVIDENCE=${failure}`)
    throw new Error('clean-forward residue detected')
  }
  console.log('H4_CLEAN_FORWARD=PASS')
} catch (error) {
  console.error('H4_CLEAN_FORWARD=FAIL')
  console.error(error instanceof Error ? error.message : String(error))
  process.exitCode = 1
}
