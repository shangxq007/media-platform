export interface Tenant {
  id: string
  name: string
  status: string
  createdAt: string
}

export interface Project {
  id: string
  tenantId: string
  name: string
  description: string
  status: string
  createdAt: string
}

export interface Clip {
  id: string
  name: string
  type: 'video' | 'audio' | 'text' | 'image' | 'subtitle'
  sourceUrl?: string
  thumbnailUrl?: string
  duration: number
  startTime: number
  endTime: number
  metadata: Record<string, string>
  fileSize?: number
  width?: number
  height?: number
  uploadStatus?: 'pending' | 'probing' | 'ready' | 'error'
  probeError?: string
}

export interface Track {
  id: string
  name: string
  type: 'video' | 'audio' | 'text' | 'image' | 'subtitle'
  clips: TrackClip[]
  muted: boolean
  locked: boolean
}

export interface TrackClip {
  id: string
  clipId: string
  trackId: string
  start: number
  duration: number
  clipStart: number
  clipEnd: number
  effects?: ClipEffect[]
}

export interface UploadItem {
  id: string
  file: File
  name: string
  progress: number
  status: 'uploading' | 'success' | 'failed' | 'cancelled'
  error?: string
  clipId?: string
}

export interface MediaProbeResult {
  duration?: number
  width?: number
  height?: number
  cueCount?: number
  error?: string
}

export interface TimelineState {
  tracks: Track[]
  duration: number
  currentTime: number
  zoom: number
  playing: boolean
}

export interface RenderJob {
  id: string
  projectId: string
  status: 'QUEUED' | 'PROCESSING' | 'COMPLETED' | 'FAILED'
  format: string
  resolution: string
  profile: string
  artifactId?: string
  createdAt: string
}

export interface ExportSettings {
  format: 'mp4' | 'ogg' | 'webm'
  resolution: '720p' | '1080p' | '4k'
  profile: string
  audioTrack: string
  frameRate: number
  encoder: string
  watermark?: boolean
  workerType?: 'local' | 'remote'
  gpuEnabled?: boolean
}

export interface Filter {
  id: string
  name: string
  type: 'transition' | 'video' | 'audio' | 'text'
  params: Record<string, unknown>
  ofxCompatible?: boolean
}

export interface OFXEffect {
  type: 'transition' | 'filter' | 'text' | 'compositor'
  name: string
  params: Record<string, unknown>
  duration?: number
  startTime?: number
}

export interface EffectPackEffect {
  effectKey: string
  displayName: string
  category: 'transition' | 'video' | 'audio' | 'text'
  description: string
  parameterSchema: Record<string, EffectParameterDef>
  defaultValues: Record<string, unknown>
  providerMappings: string[]
  allowedTiers: string[]
  thumbnailUrl?: string
  taxonomyCategory?: string
  isEffect?: boolean
  defaultParams?: Record<string, any>
  paramSchemas?: EffectParameterDef[]
}

export interface EffectParameterDef {
  type: 'int' | 'float' | 'string' | 'boolean' | 'color'
  defaultValue: unknown
  min?: number
  max?: number
  description: string
}

export interface EffectPack {
  packId: string
  version: string
  name: string
  description: string
  author: string
  effects: EffectPackEffect[]
  compatibility: string
  allowedTiers: string[]
  builtin?: boolean
  tenantId?: string | null
}

export interface ClipEffect {
  id: string
  effectKey: string
  packId?: string
  packVersion?: string
  providerPreference: string[]
  parameters: Record<string, unknown>
  duration?: number
  startTime?: number
}

export interface UserBehaviorEvent {
  eventId: string
  tenantId: string
  userId: string
  eventType: string
  action: string | null
  metadata: Record<string, string>
  occurredAt: string
}

export interface SubtitleCue {
  id: string
  index: number
  startTime: number
  endTime: number
  text: string
  style?: string
}

export interface SubtitleTrack {
  id: string
  language: string
  label: string
  format: 'srt' | 'ass' | 'vtt'
  cues: SubtitleCue[]
  fontId?: string
  fallbackFontIds: string[]
  burnIn: boolean
  externalFileUrl?: string
}

export interface SubtitleFont {
  fontId: string
  family: string
  format: 'ttf' | 'otf'
  uploadedBy: string
  uploadedAt: string
  glyphCoverage: string[]
  fallbackFontIds: string[]
  fileSize: number
  checksum: string
}

export interface ErrorResponse {
  errorCode: string
  message: string
  details: Record<string, unknown>
  timestamp: string
}

// -------------------------------------------------------------------------
// Prompt 44: Cost, Entitlement, Anomaly types
// -------------------------------------------------------------------------

export interface EntitlementPolicy {
  policyId: string
  tier: string
  maxResolutionWidth: number
  maxResolutionHeight: number
  monthlyRenderMinutes: number
  watermark: boolean
  allowedProviders: string[]
  gpuAllowed: boolean
  remoteWorkerAllowed: boolean
  maxSubtitleTracks: number
  customFontsAllowed: boolean
  effectPacksAllowed: string[]
  exportFormats: string[]
  maxConcurrentJobs: number
}

export interface ExportCapabilityPolicy {
  policyId: string
  tier: string
  allowedFormats: string[]
  allowedPresets: string[]
  maxResolutionWidth: number
  maxResolutionHeight: number
  watermarkRequired: boolean
  gpuExportAllowed: boolean
  remoteExportAllowed: boolean
  maxConcurrentExports: number
}

export interface ProviderAccessPolicy {
  policyId: string
  tier: string
  allowedProviders: string[]
  gpuAllowed: boolean
  remoteWorkerAllowed: boolean
  allowedGpuPresets: string[]
}

export interface FeatureFlag {
  flagKey: string
  displayName: string
  enabled: boolean
  scope: string
  targetTier: string
  description: string
}

export interface MyCapabilities {
  tenantId: string
  userId: string
  tier: string
  entitlementPolicy: EntitlementPolicy
  exportCapabilities: ExportCapabilityPolicy
  providerAccess: ProviderAccessPolicy
  featureFlags: FeatureFlag[]
}

export type RenderLocation = 'CLIENT' | 'SERVER'

export interface ExportValidationResult {
  allowed: boolean
  reasonCode: string
  currentTier: string
  requestedPreset: string
  recommendedPreset: string
  providerCandidates: string[]
  estimatedCost: number
  currency: string
  budgetStatus: BudgetStatus
  upgradeOptions: string[]
  userFriendlyMessage: string
  violations: string[]
  recommendations: string[]
  recommendedRenderLocation?: RenderLocation
  clientExportSupported?: boolean
  clientExportUnsupportedReasons?: string[]
  legacyValidation?: ExportValidationResult
}

export interface BudgetStatus {
  allowed: boolean
  warning: boolean
  currentSpend: number
  budgetLimit: number
  remainingBudget: number
  message: string | null
}

export interface UsageAlert {
  alertId: string
  tenantId: string
  userId: string
  ruleType: string
  severity: string
  message: string
  action: string
  details: Record<string, unknown>
  acknowledged: boolean
  createdAt: string
}

export interface UsageRiskProfile {
  tenantId: string
  userId: string
  riskScore: number
  riskLevel: string
  activeAnomalies: string[]
  recentMitigationActions: string[]
  usageStats: Record<string, unknown>
  evaluatedAt: string
}

export interface CostEstimate {
  estimatedCost: number
  currency: string
  providerKey: string
  preset: string
  estimatedDurationSeconds: number
  useGpu: boolean
}

// -------------------------------------------------------------------------
// Prompt Engineering Types
// -------------------------------------------------------------------------

export interface PromptTemplate {
  templateId: string
  name: string
  description: string
  category: string
  tags: string[]
  owner: string
  status: 'DRAFT' | 'ACTIVE' | 'DEPRECATED' | 'ARCHIVED'
  schemaVersion: string
  currentPromptVersion: string | null
  createdAt: string
  updatedAt: string
}

export interface PromptTemplateVersion {
  versionId: string
  templateId: string
  promptVersion: string
  templateBody: string
  variableSchemaJson: string
  changelog: string
  createdBy: string
  createdAt: string
  checksum: string
  previousVersion: string | null
  deprecated: boolean
}

export interface PromptRenderResult {
  renderedPrompt: string
  redactedPrompt: string
  missingVariables: string[]
  warnings: string[]
}

export interface PromptValidationResult {
  valid: boolean
  errors: string[]
  warnings: string[]
}

export interface PromptRiskAnalysis {
  riskLevel: string
  action: string
  explanation: string
  secretFindings: string[]
  commandFindings: string[]
}

export interface PromptExecutionRun {
  executionId: string
  templateId: string
  promptVersion: string
  tenantId: string
  userId: string
  modelProvider: string
  modelName: string
  renderedPromptHash: string
  redactedPromptPreview: string
  inputVariablesRedactedJson: string
  outputSummary: string | null
  status: string
  riskLevel: string
  tokenEstimate: number
  costEstimate: number
  startedAt: string
  finishedAt: string | null
  errorCode: string | null
  errorDetailsJson: string | null
  relatedPromptFile: string | null
  relatedManifestEntry: string | null
}

export interface PromptEvaluationResult {
  evaluationId: string
  executionId: string
  templateId: string
  evaluatorUserId: string
  acceptanceCriteriaMet: boolean
  documentationUpdated: boolean
  manifestUpdated: boolean
  testsPass: boolean
  hasHighRiskChanges: boolean
  hasHumanReviewItems: boolean
  hasScopeCreep: boolean
  hasFalseClaims: boolean
  overallVerdict: string
  dimensionScores: Record<string, string>
  evaluatedAt: string
}

export interface PromptVersionDiff {
  fromVersion: string
  toVersion: string
  bodyDiff: string
  variableSchemaChanged: boolean
}

export interface PromptFileScanResult {
  imported: number
  conflicts: number
  skipped: number
  errors: string[]
}

export type {
  FrontendRouteDefinition,
  RouteVisibilityDecision,
  NavigationProfile,
  NavigationPreviewRequest,
  RouteDefinitionCreateRequest,
  RouteDefinitionUpdateRequest,
  NavigationPolicy
} from './routing'

// -------------------------------------------------------------------------
// Entitlement & Billing Types (Prompt 59)
// -------------------------------------------------------------------------

export interface GrantSource {
  sourceType: 'TIER' | 'BUNDLE' | 'OVERRIDE' | 'USER_GRANT' | 'WORKSPACE_GRANT' | 'GROUP_GRANT'
  sourceId: string
  sourceName: string
  grantedAt: string
  expiresAt?: string
}

export interface EntitlementGrant {
  grantId: string
  featureKey: string
  featureName: string
  granted: boolean
  sources: GrantSource[]
  expiresAt?: string
}

export interface UsageSummary {
  tenantId: string
  userId: string
  period: string
  renderMinutesUsed: number
  renderMinutesLimit: number
  storageGbUsed: number
  storageGbLimit: number
  apiCallsUsed: number
  apiCallsLimit: number
  exportsUsed: number
  exportsLimit: number
  lastUpdatedAt: string
}

export interface BillingLedgerEntry {
  entryId: string
  tenantId: string
  type: 'CHARGE' | 'CREDIT' | 'REFUND' | 'ADJUSTMENT'
  amount: number
  currency: string
  description: string
  status: 'PENDING' | 'COMPLETED' | 'FAILED' | 'REVERSED'
  referenceId?: string
  createdAt: string
}

export interface Invoice {
  invoiceId: string
  tenantId: string
  invoiceNumber: string
  amount: number
  currency: string
  status: 'DRAFT' | 'ISSUED' | 'PAID' | 'OVERDUE' | 'VOID'
  lineItems: InvoiceLineItem[]
  issuedAt: string
  dueAt: string
  paidAt?: string
}

export interface InvoiceLineItem {
  description: string
  quantity: number
  unitPrice: number
  total: number
}

export interface CreditWallet {
  walletId: string
  subjectId: string
  subjectType: 'TENANT' | 'USER' | 'WORKSPACE'
  balance: number
  currency: string
  heldBalance: number
  lastTransactionAt?: string
}

export interface CreditTransaction {
  transactionId: string
  walletId: string
  type: 'TOP_UP' | 'DEDUCTION' | 'REFUND' | 'HOLD' | 'RELEASE'
  amount: number
  balanceAfter: number
  description: string
  referenceId?: string
  createdAt: string
}

export interface SubscriptionPlan {
  planId: string
  name: string
  tier: string
  description: string
  monthlyPrice: number
  annualPrice: number
  currency: string
  includedQuota: PlanQuota
  features: string[]
  isActive: boolean
}

export interface PlanQuota {
  renderMinutes: number
  storageGb: number
  apiCalls: number
  exports: number
  subtitleTracks: number
  maxResolution: string
  gpuAllowed: boolean
  remoteWorkerAllowed: boolean
  customFontsAllowed: boolean
}

export interface UpgradeOption {
  targetTier: string
  targetPlanId: string
  targetPlanName: string
  monthlyPrice: number
  annualPrice: number
  currency: string
  additionalFeatures: string[]
  additionalQuota: Partial<PlanQuota>
  recommended?: boolean
}

export interface EntitlementExplanation {
  featureKey: string
  featureName: string
  available: boolean
  reason: string
  currentTier: string
  requiredTier?: string
  upgradeOptions: UpgradeOption[]
  violations: string[]
}

// Workspace types
export interface WorkspaceMember {
  userId: string
  email: string
  displayName: string
  role: string
  entitlements: EntitlementGrant[]
  joinedAt: string
}

export interface WorkspaceRole {
  roleId: string
  name: string
  description: string
  permissions: string[]
  memberCount: number
}

export interface WorkspaceEntitlementPool {
  workspaceId: string
  featureKey: string
  featureName: string
  totalQuota: number
  allocated: number
  remaining: number
  unit: string
}

export interface WorkspaceMemberGrant {
  grantId: string
  workspaceId: string
  memberId: string
  memberEmail: string
  featureKey: string
  featureName: string
  granted: boolean
  grantedBy: string
  grantedAt: string
  expiresAt?: string
}

export interface WorkspaceGroupGrant {
  grantId: string
  workspaceId: string
  groupId: string
  groupName: string
  featureKey: string
  featureName: string
  granted: boolean
  grantedBy: string
  grantedAt: string
  expiresAt?: string
}

export interface EntitlementDecisionRequest {
  workspaceId: string
  memberId: string
  featureKey: string
}

export interface EntitlementDecision {
  featureKey: string
  granted: boolean
  reason: string
  sources: GrantSource[]
  expiry?: string
}

export interface AccessDecisionDebug {
  requestId: string
  memberId: string
  featureKey: string
  decision: 'GRANTED' | 'DENIED'
  evaluatedRules: DebugRuleResult[]
  evaluatedAt: string
}

export interface DebugRuleResult {
  ruleType: string
  ruleId: string
  result: 'PASS' | 'FAIL' | 'SKIPPED'
  detail: string
}

// Admin types
export interface EntitlementBundle {
  bundleId: string
  name: string
  description: string
  tier: string
  features: string[]
  featureFlagKeys?: string[]
  quota: Record<string, number>
  status: 'ACTIVE' | 'ARCHIVED' | 'DRAFT'
  createdAt: string
  updatedAt: string
}

export interface TenantOverride {
  overrideId: string
  tenantId: string
  tenantName: string
  featureKey: string
  featureName: string
  overrideType: 'GRANT' | 'DENY' | 'QUOTA'
  quotaValue?: number
  reason: string
  createdBy: string
  createdAt: string
  expiresAt?: string
}

export interface UserGrant {
  grantId: string
  userId: string
  userEmail: string
  featureKey: string
  featureName: string
  granted: boolean
  reason: string
  createdBy: string
  createdAt: string
  expiresAt?: string
}

export interface QuotaPolicy {
  policyId: string
  name: string
  description: string
  featureKey: string
  scope: 'GLOBAL' | 'TIER' | 'TENANT'
  scopeId?: string
  limit: number
  period: 'DAILY' | 'MONTHLY' | 'YEARLY'
  softLimit?: number
  action: 'BLOCK' | 'WARN' | 'THROTTLE'
}

export interface BillingPlan {
  planId: string
  name: string
  tier: string
  description: string
  monthlyPrice: number
  annualPrice: number
  currency: string
  trialDays: number
  isActive: boolean
  features: string[]
  featureFlagKeys?: string[]
  quota: PlanQuota
  createdAt: string
  updatedAt: string
}

export interface PricingRule {
  ruleId: string
  name: string
  description: string
  metric: string
  tier: string
  unitPrice: number
  currency: string
  minimumQuantity?: number
  maximumQuantity?: number
  effectiveFrom: string
  effectiveTo?: string
  isActive: boolean
}

export interface UsageRecord {
  recordId: string
  tenantId: string
  userId: string
  metric: string
  quantity: number
  unit: string
  ratedCost: number
  currency: string
  recordedAt: string
  ratedAt?: string
}

export interface BillingQuote {
  quoteId: string
  tenantId: string
  tier: string
  monthlyEstimate: number
  annualEstimate: number
  currency: string
  breakdown: QuoteLineItem[]
  validUntil: string
  createdAt: string
}

export interface QuoteLineItem {
  description: string
  quantity: number
  unitPrice: number
  total: number
}

export interface ExtensionQuotaInfo {
  extensionKey: string
  executionQuota: number
  executionsUsed: number
  executionsRemaining: number
  estimatedCost: number
  currency: string
  riskLevel: 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL'
}

export interface PromptQuotaInfo {
  templateId: string
  executionQuota: number
  executionsUsed: number
  executionsRemaining: number
  estimatedCostPerExecution: number
  currency: string
}

// -------------------------------------------------------------------------
// Render Artifact Types (Prompt 62)
// -------------------------------------------------------------------------

export interface Artifact {
  id: string
  renderJobId: string
  projectId: string
  name: string
  outputFormat: string
  duration: number
  fileSize: number
  width?: number
  height?: number
  provider: string
  outputUrl?: string
  thumbnailUrl?: string
  renderLogsUrl?: string
  catalogId?: string
  createdAt: string
}

export interface RenderJobDetailed {
  id: string
  projectId: string
  status: 'creating' | 'queued' | 'running' | 'completed' | 'failed' | 'cancelled'
  progress: number
  format: string
  resolution: string
  profile: string
  preset: string
  artifact?: Artifact
  errorCode?: string
  errorMessage?: string
  diagnosticInfo?: string
  createdAt: string
  updatedAt: string
  completedAt?: string
}
