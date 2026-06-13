import { z } from 'zod'

// =============================================================================
// API Contract Schemas
// =============================================================================
// These schemas define the stable contract between frontend and backend.
// Changes here must be backward-compatible or explicitly versioned.
//
// Convention:
// - Schema names use PascalCase (e.g., AssetSchema)
// - Derived types use PascalCase (e.g., Asset)
// - All schemas use .strict() to reject unknown fields
// - All date strings use z.string().datetime() where applicable
// =============================================================================

// ---------------------------------------------------------------------------
// Asset Schema
// ---------------------------------------------------------------------------
export const AssetMediaType = z.enum([
  'VIDEO',
  'IMAGE',
  'AUDIO',
  'SUBTITLE',
  'UNKNOWN',
])

export const AssetSchema = z.object({
  id: z.string(),
  tenantId: z.string(),
  projectId: z.string(),
  storageKey: z.string(),
  mediaType: AssetMediaType,
  filename: z.string(),
  sizeBytes: z.number().nullable(),
  checksum: z.string().nullable(),
  durationMs: z.number().nullable(),
  width: z.number().nullable(),
  height: z.number().nullable(),
  createdAt: z.string(),
}).strict()

export type Asset = z.infer<typeof AssetSchema>

// ---------------------------------------------------------------------------
// RenderJob Schema
// ---------------------------------------------------------------------------
export const RenderJobStatus = z.enum([
  'QUEUED',
  'PROCESSING',
  'COMPLETED',
  'FAILED',
])

export const RenderJobSchema = z.object({
  id: z.string(),
  projectId: z.string(),
  status: RenderJobStatus,
  format: z.string(),
  resolution: z.string(),
  profile: z.string(),
  artifactId: z.string().optional(),
  createdAt: z.string(),
}).strict()

export type RenderJob = z.infer<typeof RenderJobSchema>

// ---------------------------------------------------------------------------
// RenderJobSummary Schema (API list response)
// ---------------------------------------------------------------------------
export const RenderJobSummarySchema = z.object({
  id: z.string(),
  projectId: z.string(),
  timelineSnapshotId: z.string(),
  profile: z.string(),
  status: z.string(),
}).strict()

export type RenderJobSummary = z.infer<typeof RenderJobSummarySchema>

// ---------------------------------------------------------------------------
// Artifact Schema
// ---------------------------------------------------------------------------
export const ArtifactSchema = z.object({
  id: z.string(),
  renderJobId: z.string(),
  projectId: z.string(),
  name: z.string(),
  outputFormat: z.string(),
  duration: z.number(),
  fileSize: z.number(),
  width: z.number().optional(),
  height: z.number().optional(),
  provider: z.string(),
  outputUrl: z.string().optional(),
  thumbnailUrl: z.string().optional(),
  renderLogsUrl: z.string().optional(),
  catalogId: z.string().optional(),
  createdAt: z.string(),
}).strict()

export type Artifact = z.infer<typeof ArtifactSchema>

// ---------------------------------------------------------------------------
// RenderJobArtifact Schema (API list response)
// ---------------------------------------------------------------------------
export const RenderJobArtifactSchema = z.object({
  id: z.string(),
  storageUri: z.string(),
  format: z.string().optional(),
}).strict()

export type RenderJobArtifact = z.infer<typeof RenderJobArtifactSchema>

// ---------------------------------------------------------------------------
// EffectParameterDef Schema
// ---------------------------------------------------------------------------
export const EffectParameterDefSchema = z.object({
  type: z.enum(['int', 'float', 'string', 'boolean', 'color']),
  defaultValue: z.unknown(),
  min: z.number().optional(),
  max: z.number().optional(),
  description: z.string(),
}).strict()

export type EffectParameterDef = z.infer<typeof EffectParameterDefSchema>

// ---------------------------------------------------------------------------
// EffectPackEffect Schema
// ---------------------------------------------------------------------------
export const EffectPackEffectSchema = z.object({
  effectKey: z.string(),
  displayName: z.string(),
  category: z.enum(['transition', 'video', 'audio', 'text', 'compositor']),
  description: z.string(),
  parameterSchema: z.record(EffectParameterDefSchema),
  defaultValues: z.record(z.unknown()),
  providerMappings: z.array(z.string()),
  allowedTiers: z.array(z.string()),
  thumbnailUrl: z.string().optional(),
  taxonomyCategory: z.string().optional(),
  isEffect: z.boolean().optional(),
  defaultParams: z.record(z.any()).optional(),
  paramSchemas: z.array(EffectParameterDefSchema).optional(),
}).strict()

export type EffectPackEffect = z.infer<typeof EffectPackEffectSchema>

// ---------------------------------------------------------------------------
// EffectPack Schema
// ---------------------------------------------------------------------------
export const EffectPackSchema = z.object({
  packId: z.string(),
  version: z.string(),
  name: z.string(),
  description: z.string(),
  author: z.string(),
  effects: z.array(EffectPackEffectSchema),
  compatibility: z.string(),
  allowedTiers: z.array(z.string()),
  builtin: z.boolean().optional(),
  tenantId: z.string().nullable().optional(),
}).strict()

export type EffectPack = z.infer<typeof EffectPackSchema>

// ---------------------------------------------------------------------------
// ClipEffect Schema
// ---------------------------------------------------------------------------
export const ClipEffectSchema = z.object({
  id: z.string(),
  effectKey: z.string(),
  packId: z.string().optional(),
  packVersion: z.string().optional(),
  providerPreference: z.array(z.string()),
  parameters: z.record(z.unknown()),
  duration: z.number().optional(),
  startTime: z.number().optional(),
}).strict()

export type ClipEffect = z.infer<typeof ClipEffectSchema>

// ---------------------------------------------------------------------------
// Project Schema
// ---------------------------------------------------------------------------
export const ProjectSchema = z.object({
  id: z.string(),
  tenantId: z.string(),
  name: z.string(),
  description: z.string(),
  status: z.string(),
  createdAt: z.string(),
}).strict()

export type Project = z.infer<typeof ProjectSchema>

// ---------------------------------------------------------------------------
// Schema Registry (for runtime validation)
// ---------------------------------------------------------------------------
export const schemaRegistry = {
  asset: AssetSchema,
  renderJob: RenderJobSchema,
  renderJobSummary: RenderJobSummarySchema,
  artifact: ArtifactSchema,
  renderJobArtifact: RenderJobArtifactSchema,
  effectPack: EffectPackSchema,
  effectPackEffect: EffectPackEffectSchema,
  clipEffect: ClipEffectSchema,
  project: ProjectSchema,
} as const

export type SchemaKey = keyof typeof schemaRegistry

// ---------------------------------------------------------------------------
// Safe Parse Utility
// ---------------------------------------------------------------------------
export function safeParseResponse<T extends SchemaKey>(
  schemaKey: T,
  data: unknown
): { success: true; data: z.infer<(typeof schemaRegistry)[T]> } | { success: false; error: z.ZodError } {
  const schema = schemaRegistry[schemaKey]
  const result = schema.safeParse(data)
  if (result.success) {
    return { success: true, data: result.data }
  }
  return { success: false, error: result.error }
}

// ---------------------------------------------------------------------------
// Validate and Warn Utility (warn-only, never blocks)
// ---------------------------------------------------------------------------
export function validateAndWarn<T>(schema: z.ZodType<T>, data: unknown, context: string): T | null {
  const result = schema.safeParse(data)
  if (result.success) {
    return result.data
  }
  console.warn(`[API Contract] Validation failed for ${context}:`, result.error.format())
  return null
}
