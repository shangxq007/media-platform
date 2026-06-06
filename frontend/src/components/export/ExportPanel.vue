<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, watch } from 'vue'
import { useProjectStore } from '@/stores/project'
import { useTimelineStore } from '@/stores/timeline'
import { useSubtitleStore } from '@/stores/subtitle'
import { RenderAPI, EntitlementAPI, IncrementalRenderAPI } from '@/api'
import { ClientExportAPI } from '@/api/client-export'
import { ClientCompositor } from '@/clientExport/clientCompositor'
import { extractEffectKeys } from '@/clientExport/timelineParser'
import { resolveExportPath } from '@/clientExport/resolveExportPath'
import { detectClientExportCapabilities } from '@/clientExport/clientExportCapabilities'
import type { ExportSettings, RenderJob, ExportValidationResult, BudgetStatus, Artifact, RenderLocation } from '@/types'
import RenderJobStatus from './RenderJobStatus.vue'
import ArtifactResult from './ArtifactResult.vue'
import DeliveryStatusPanel from './DeliveryStatusPanel.vue'
import AiTimelineEditPanel from './AiTimelineEditPanel.vue'
import IncrementalRenderPanel from './IncrementalRenderPanel.vue'
import TimelineInternalPreviewPanel from './TimelineInternalPreviewPanel.vue'
import ImportedMetadataPanel from './ImportedMetadataPanel.vue'
import AiProposalsPanel from './AiProposalsPanel.vue'
import type { AiProposalDto } from '@/api/ai-timeline'
import { useExportUiStore } from '@/stores/exportUi'
import { buildEditorTimelineJson } from '@/utils/timelineExport'
import ArtifactPreviewModal from './ArtifactPreviewModal.vue'
import UpgradeHint from '@/components/ui/UpgradeHint.vue'
import { useExportFeatureFlags } from '@/composables/useFeatureFlag'
import { useTimelineSyncMetaStore } from '@/stores/timelineSyncMeta'

const projectStore = useProjectStore()
const metaStore = useTimelineSyncMetaStore()
const timelineStore = useTimelineStore()
const subtitleStore = useSubtitleStore()

const subtitleMode = ref<'none' | 'burn-in' | 'external' | 'multi-external'>('none')
const selectedSubtitleLanguages = ref<string[]>([])

interface ExportPreset {
  name: string
  displayName: string
  resolution: string
  format: string
  watermark: boolean
  renderLocation: 'CLIENT' | 'SERVER'
  [key: string]: unknown
}

interface ProviderInfo {
  name: string
  key?: string
  status: string
  capabilities?: {
    formats?: string[]
    codecs?: string[]
    [key: string]: unknown
  }
  [key: string]: unknown
}

interface WorkerInfo {
  workerId: string
  address: string
  status: string
  activeJobs: number
  [key: string]: unknown
}

const currentTier = ref('FREE')
const selectedPreset = ref('client_720p_watermarked')
const submitting = ref(false)
const saveToProject = ref(true)
const renderLocation = ref<RenderLocation>('SERVER')
const clientExportProgress = ref(0)
const clientExportMessage = ref<string | null>(null)
const clientExportAbort = ref<AbortController | null>(null)
const clientCompositor = new ClientCompositor()
const lastJob = ref<RenderJob | null>(null)
const pollInterval = ref<ReturnType<typeof setInterval> | null>(null)
const availablePresets = ref<ExportPreset[]>([])
const providerInfo = ref<ProviderInfo | null>(null)
const workers = ref<WorkerInfo[]>([])
const selectedWorkerType = ref<'local' | 'remote'>('local')
const loadingWorkers = ref(false)
const restFallback = ref(false)

const budgetStatus = ref<BudgetStatus | null>(null)
const loadingCapabilities = ref(false)
const validatingExport = ref(false)
const exportValidationDetail = ref<ExportValidationResult | null>(null)

const validationResult = ref<ExportValidationResult | null>(null)

const {
  flagMap: _exportFeatureFlags,
  loading: loadingExportFlags,
  isEnabled: isExportFlagEnabled,
  getDisabledReason: getExportFlagDisabledReason,
  refresh: refreshExportFlags,
} = useExportFeatureFlags()

const renderJobId = ref<string | null>(null)
const renderJobStatus = ref<string>('creating')
const renderProgress = ref(0)
const renderError = ref<string | null>(null)
const renderErrorCode = ref<string | undefined>(undefined)
const diagnosticInfo = ref<string | undefined>(undefined)

const completedArtifact = ref<Artifact | null>(null)
const previewOpen = ref(false)
const exportUi = useExportUiStore()
const exportMode = computed({
  get: () => exportUi.mode,
  set: (v: 'legacy' | 'incremental') => { exportUi.mode = v },
})
const editedTimelineJson = ref<string | null>(null)
const previewInternalJson = ref<string | null>(null)
const aiProposals = ref<AiProposalDto[]>([])
const panelMessage = ref<string | null>(null)
const editSessionId = ref(`sess-${Date.now()}`)

watch(
  editSessionId,
  id => {
    metaStore.setActiveEditSessionId(id?.trim() || null)
  },
  { immediate: true }
)

const hasSyncConflict = computed(() => !!metaStore.pendingConflict)

const settings = ref<ExportSettings>({
  format: 'mp4',
  resolution: '720p',
  profile: 'default_1080p',
  frameRate: 30,
  encoder: 'h264',
  audioTrack: 'all'
})

const timelineSummary = computed(() => ({
  duration: timelineStore.state.duration,
  tracks: timelineStore.state.tracks.length,
  clips: timelineStore.state.tracks.reduce((sum: number, t) => sum + (t.clips?.length ?? 0), 0),
  subtitles: subtitleStore.tracks.length,
  effects: timelineStore.state.tracks.reduce(
    (sum: number, t) =>
      sum + (t.clips?.reduce((s: number, tc: { effects?: unknown[] }) => s + (tc.effects?.length || 0), 0) ?? 0),
    0
  ),
}))

const tierBadgeClass = computed(() => {
  switch (currentTier.value) {
    case 'FREE': return 'bg-surface-4'
    case 'PRO': return 'bg-blue-600'
    case 'TEAM': return 'bg-purple-600'
    case 'ENTERPRISE': return 'bg-amber-600'
    case 'EXPERIMENTAL': return 'bg-red-600'
    default: return 'bg-surface-4'
  }
})

const presetInfo = computed(() => {
  const preset = availablePresets.value.find((p: ExportPreset) => p.name === selectedPreset.value)
  return preset || null
})

const canExport = computed(() =>
  projectStore.hasProject &&
  timelineStore.state.tracks.some((t) => (t.clips?.length ?? 0) > 0)
)

const recentJobs = computed(() =>
  projectStore.renderJobs.slice(-5).reverse()
)

const completedJobs = computed(() =>
  projectStore.renderJobs.filter(j => j.status === 'COMPLETED')
)

function buildTimelineJson(): string {
  return buildEditorTimelineJson(
    timelineStore.toJSON() as Record<string, unknown>,
    timelineStore.clips,
    editedTimelineJson.value
  )
}

function onAiTimelineApplied(json: string) {
  editedTimelineJson.value = json
  panelMessage.value = 'AI 编辑已应用，可预览增量计划或导出'
}

function onProposalsUpdated(json: string, proposals: AiProposalDto[]) {
  editedTimelineJson.value = json
  aiProposals.value = proposals
  const pending = proposals.filter(p => p.status === 'PENDING')
  panelMessage.value = pending.length
    ? `有 ${pending.length} 条 AI 建议待采纳`
    : 'AI 建议已处理'
}

function onInternalPreviewed(json: string) {
  previewInternalJson.value = json
  panelMessage.value = '已生成 Internal Timeline 1.0 预览（提交增量时将优先使用）'
}

function effectiveTimelineJson(): string {
  return previewInternalJson.value || buildTimelineJson()
}

function onPanelError(msg: string) {
  panelMessage.value = msg
  projectStore.setError(msg)
}

async function onIncrementalSubmitted(jobId: string) {
  const tenantId = projectStore.currentTenant
  const projectId = projectStore.currentProject?.id
  renderJobId.value = jobId
  renderJobStatus.value = 'queued'
  renderProgress.value = 0
  if (projectId) {
    try {
      const job = await IncrementalRenderAPI.getJob(tenantId, projectId, jobId)
      const existing = projectStore.renderJobs.some(j => j.id === jobId)
      if (existing) {
        projectStore.updateRenderJob(jobId, job)
      } else {
        projectStore.addRenderJob(job)
      }
      lastJob.value = job
    } catch {
      projectStore.addRenderJob({
        id: jobId,
        projectId,
        status: 'QUEUED',
        format: settings.value.format,
        resolution: settings.value.resolution,
        profile: selectedPreset.value,
        createdAt: new Date().toISOString(),
      })
    }
  }
  startPolling(jobId)
  panelMessage.value = `增量作业已提交: ${jobId}`
}

const hasBudgetWarning = computed(() =>
  budgetStatus.value?.warning === true
)

const hasBudgetExceeded = computed(() =>
  budgetStatus.value?.allowed === false
)

const gpuAvailable = computed(() => currentTier.value !== 'FREE' && currentTier.value !== 'PRO')

const remoteWorkerAvailable = computed(() =>
  currentTier.value === 'TEAM' || currentTier.value === 'ENTERPRISE'
)

const exportFlagStatuses = computed(() => [
  { key: 'export.gpu.v2', label: 'GPU Export v2', enabled: isExportFlagEnabled('export.gpu.v2') },
  { key: 'export.remoteWorker.enabled', label: 'Remote Worker', enabled: isExportFlagEnabled('export.remoteWorker.enabled') },
  { key: 'export.providerRouting.v2', label: 'Provider Routing v2', enabled: isExportFlagEnabled('export.providerRouting.v2') },
  { key: 'export.newPresetSelector.enabled', label: 'New Preset Selector', enabled: isExportFlagEnabled('export.newPresetSelector.enabled') },
])

const featureFlagSubmitBlocked = computed(() => {
  if (selectedPreset.value.startsWith('gpu_') && !isExportFlagEnabled('export.gpu.v2')) {
    return getExportFlagDisabledReason('export.gpu.v2')
  }
  if (selectedWorkerType.value === 'remote' && !isExportFlagEnabled('export.remoteWorker.enabled')) {
    return getExportFlagDisabledReason('export.remoteWorker.enabled')
  }
  return null
})

const filteredPresets = computed(() => availablePresets.value.filter((p) => {
  if (p.name.startsWith('gpu_')) return gpuAvailable.value && isExportFlagEnabled('export.gpu.v2')
  if (p.name.includes('4k')) return currentTier.value === 'TEAM' || currentTier.value === 'ENTERPRISE'
  return true
}))

const unavailablePresets = computed(() => {
  const allowed = new Set(filteredPresets.value.map((p) => p.name))
  return availablePresets.value.filter((p) => !allowed.has(p.name))
})

const recommendedPreset = computed(() => validationResult.value?.recommendedPreset || null)

const isExportDisabled = computed(() =>
  exportMode.value === 'incremental' ||
  !canExport.value || submitting.value || hasBudgetExceeded.value ||
  hasSyncConflict.value ||
  (validationResult.value !== null && !validationResult.value.allowed) ||
  featureFlagSubmitBlocked.value !== null
)

const watermarked = computed(() => presetInfo.value?.watermark || false)

const clientExportFeatureOn = computed(
  () => isExportFlagEnabled('export.client.enabled') || currentTier.value === 'FREE'
)

const browserExportCapable = computed(() => detectClientExportCapabilities().supported)

function normalizeValidationResponse(raw: Record<string, unknown>): ExportValidationResult {
  const leg = (raw.legacyValidation ?? raw) as ExportValidationResult
  return {
    ...leg,
    allowed: Boolean(raw.allowed ?? leg.allowed),
    recommendedRenderLocation: (raw.recommendedRenderLocation ?? leg.recommendedRenderLocation) as RenderLocation | undefined,
    clientExportSupported: Boolean(raw.clientExportSupported ?? leg.clientExportSupported),
    clientExportUnsupportedReasons: (raw.clientExportUnsupportedReasons ?? leg.clientExportUnsupportedReasons) as string[] | undefined,
    budgetStatus: leg.budgetStatus ?? (raw.budgetStatus as BudgetStatus),
  }
}

let validateDebounce: ReturnType<typeof setTimeout> | null = null

function scheduleValidate() {
  if (validateDebounce) clearTimeout(validateDebounce)
  validateDebounce = setTimeout(() => {
    validateCurrentExport()
  }, 500)
}

watch(selectedPreset, async (newPreset) => {
  if (newPreset) {
    updateSettingsFromPreset(newPreset)
    await validateCurrentExport()
  }
})

watch(() => settings.value.format, async () => {
  await validateCurrentExport()
})

watch(() => timelineStore.state.duration, () => {
  scheduleValidate()
})

async function validateCurrentExport() {
  if (!selectedPreset.value) return
  validatingExport.value = true
  try {
    const timelineJson = buildTimelineJson()
    const raw = await EntitlementAPI.validateExport(
      selectedPreset.value,
      settings.value.format,
      Math.max(1, Math.ceil(timelineStore.state.duration)),
      { effectKeys: extractEffectKeys(timelineJson), timelineJson }
    )
    const result = normalizeValidationResponse(raw as Record<string, unknown>)
    validationResult.value = result
    exportValidationDetail.value = result
    renderLocation.value = resolveExportPath({
      allowed: result.allowed,
      recommendedRenderLocation: result.recommendedRenderLocation,
      clientExportSupported: result.clientExportSupported,
      currentTier: result.currentTier,
      preset: selectedPreset.value,
      clientFeatureEnabled: clientExportFeatureOn.value && browserExportCapable.value,
    })
    if (result.budgetStatus) {
      budgetStatus.value = result.budgetStatus
    }
  } catch {
    validationResult.value = null
    exportValidationDetail.value = null
    renderLocation.value = 'SERVER'
  } finally {
    validatingExport.value = false
  }
}

async function loadProjectRenderJobs() {
  const projectId = projectStore.currentProject?.id
  if (!projectId) return
  try {
    const jobs = await IncrementalRenderAPI.listJobs(projectStore.currentTenant, projectId)
    projectStore.setRenderJobs(jobs)
  } catch {
    try {
      const all = await RenderAPI.listJobs()
      projectStore.setRenderJobs(all.filter(j => j.projectId === projectId))
    } catch {
      /* keep local list */
    }
  }
}

watch(
  () => projectStore.currentProject?.id,
  () => {
    void loadProjectRenderJobs()
  },
  { immediate: true }
)

onMounted(async () => {
  await loadCapabilities()
  await loadPresets()
  await loadWorkers()
  await loadProjectRenderJobs()
})

onUnmounted(() => {
  if (pollInterval.value) clearInterval(pollInterval.value)
  if (validateDebounce) clearTimeout(validateDebounce)
})

async function loadCapabilities() {
  loadingCapabilities.value = true
  try {
    const caps = await EntitlementAPI.getCapabilities()
    currentTier.value = caps.tier || 'FREE'
    if (currentTier.value === 'FREE' && !selectedPreset.value.startsWith('client')) {
      selectedPreset.value = 'client_720p_watermarked'
    }
    budgetStatus.value = {
      allowed: true,
      warning: false,
      currentSpend: 0,
      budgetLimit: caps.entitlementPolicy?.monthlyRenderMinutes || 60,
      remainingBudget: caps.entitlementPolicy?.monthlyRenderMinutes || 60,
      message: null
    }
  } catch {
    budgetStatus.value = {
      allowed: true,
      warning: false,
      currentSpend: 0,
      budgetLimit: 60,
      remainingBudget: 60,
      message: null
    }
  } finally {
    loadingCapabilities.value = false
  }
}

async function loadPresets() {
  try {
    const resp = await fetch('/api/v1/render/presets')
    if (resp.ok) {
      const data = await resp.json()
      availablePresets.value = data.presets || []
      currentTier.value = data.tier || currentTier.value
      providerInfo.value = data.provider || null
    }
  } catch {
    availablePresets.value = [
      { name: 'client_720p_watermarked', displayName: 'Free 720p (Browser)', resolution: '1280x720', format: 'mp4', watermark: true, renderLocation: 'CLIENT' as const },
      { name: 'free_720p_watermarked', displayName: 'Free 720p (Cloud)', resolution: '1280x720', format: 'mp4', watermark: true, renderLocation: 'SERVER' as const },
      { name: 'preview_720p', displayName: 'Preview 720p', resolution: '1280x720', format: 'mp4', watermark: false, renderLocation: 'CLIENT' as const },
      { name: 'default_720p', displayName: 'Standard 720p', resolution: '1280x720', format: 'mp4', watermark: false, renderLocation: 'SERVER' as const },
      { name: 'default_1080p', displayName: 'Standard 1080p', resolution: '1920x1080', format: 'mp4', watermark: false, renderLocation: 'SERVER' as const },
      { name: 'pro_1080p', displayName: 'Pro 1080p', resolution: '1920x1080', format: 'mp4', watermark: false, renderLocation: 'SERVER' as const },
      { name: 'team_4k', displayName: 'Team 4K', resolution: '3840x2160', format: 'mp4', watermark: false, renderLocation: 'SERVER' as const },
      { name: 'gpu_h264', displayName: 'GPU H.264 (NVENC)', resolution: '1920x1080', format: 'mp4', watermark: false, renderLocation: 'SERVER' as const },
      { name: 'gpu_h265', displayName: 'GPU H.265 (NVENC)', resolution: '1920x1080', format: 'mp4', watermark: false, renderLocation: 'SERVER' as const },
      { name: 'hq_1080p', displayName: 'HQ 1080p', resolution: '1920x1080', format: 'mp4', watermark: false, renderLocation: 'SERVER' as const },
      { name: 'h265', displayName: 'H.265/HEVC', resolution: '1920x1080', format: 'mp4', watermark: false, renderLocation: 'SERVER' as const },
      { name: 'vp9', displayName: 'VP9', resolution: '1920x1080', format: 'webm', watermark: false, renderLocation: 'SERVER' as const },
      { name: 'mobile_480p', displayName: 'Mobile 480p', resolution: '854x480', format: 'mp4', watermark: false, renderLocation: 'SERVER' as const },
      { name: 'social_1080p', displayName: 'Social 1080p', resolution: '1920x1080', format: 'mp4', watermark: false, renderLocation: 'SERVER' as const },
      { name: 'social_720p', displayName: 'Social 720p', resolution: '1280x720', format: 'mp4', watermark: false, renderLocation: 'SERVER' as const },
      { name: 'ofx_1080p', displayName: 'OFX 1080p', resolution: '1920x1080', format: 'mp4', watermark: false, renderLocation: 'SERVER' as const },
      { name: 'ofx_720p', displayName: 'OFX 720p', resolution: '1280x720', format: 'mp4', watermark: false, renderLocation: 'SERVER' as const }
    ]
  }
}

async function loadWorkers() {
  loadingWorkers.value = true
  try {
    const resp = await fetch('/api/v1/remote-worker/workers')
    if (resp.ok) {
      const data = await resp.json()
      workers.value = Object.values(data.workers || {})
    }
  } catch {
    workers.value = []
  } finally {
    loadingWorkers.value = false
  }
}

function getWorkerStatusClass(status: string): string {
  switch (status) {
    case 'IDLE': return 'bg-green-500'
    case 'BUSY': return 'bg-yellow-500'
    case 'OFFLINE': return 'bg-surface-4'
    case 'ERROR': return 'bg-red-500'
    default: return 'bg-surface-4'
  }
}

function updateSettingsFromPreset(presetName: string) {
  const preset = availablePresets.value.find((p: ExportPreset) => p.name === presetName)
  if (preset) {
    const [_width, height] = (preset.resolution || '1280x720').split('x')
    settings.value.resolution = parseInt(height) >= 1080 ? (parseInt(height) >= 2160 ? '4k' : '1080p') : '720p'
    settings.value.watermark = preset.watermark || false
  }
}

async function applyRecommendedPreset() {
  if (validationResult.value?.recommendedPreset) {
    selectedPreset.value = validationResult.value.recommendedPreset
  }
}

async function submitClientExport() {
  if (!projectStore.currentProject) return

  submitting.value = true
  clientExportProgress.value = 0
  clientExportMessage.value = null
  renderJobStatus.value = 'running'
  renderError.value = null
  clientExportAbort.value = new AbortController()

  let sessionId: string | null = null
  try {
    const timelineJson = buildTimelineJson()
    const snapshot = await RenderAPI.saveTimelineSnapshot(
      projectStore.currentProject.id,
      JSON.parse(timelineJson) as Record<string, unknown>,
      { ensureInternal: true }
    )

    if (saveToProject.value) {
      const session = await ClientExportAPI.startSession({
        projectId: projectStore.currentProject.id,
        tier: currentTier.value,
        preset: selectedPreset.value,
        timelineSnapshotId: snapshot.snapshotId,
      })
      sessionId = session.sessionId
      if (session.renderLocation === 'CLIENT') {
        clientExportMessage.value = `Browser export: ${session.resolution} ${session.format}`
      }
    }

    if (sessionId) {
      ClientExportAPI.updateProgress(sessionId, 'EXPORTING', 0).catch(() => {})
    }

    const result = await clientCompositor.exportTimeline(timelineJson, {
      watermark: watermarked.value,
      signal: clientExportAbort.value.signal,
      onProgress: (p) => {
        clientExportProgress.value = p.progress
        clientExportMessage.value = p.message ?? p.phase
        renderProgress.value = p.progress
        if (sessionId && p.progress % 10 === 0) {
          ClientExportAPI.updateProgress(sessionId, 'EXPORTING', p.progress).catch(() => {})
        }
      },
    })

    let downloadUrl: string | undefined
    let artifactId: string | undefined

    if (sessionId) {
      const completed = await ClientExportAPI.uploadAndComplete(sessionId, result.blob, {
        durationSeconds: result.durationSeconds,
        registerArtifact: true,
      })
      downloadUrl = completed.downloadUrl
      artifactId = completed.artifactId || undefined
    } else {
      downloadUrl = URL.createObjectURL(result.blob)
    }

    completedArtifact.value = {
      id: artifactId || `client_${Date.now()}`,
      renderJobId: sessionId || 'client-export',
      projectId: projectStore.currentProject.id,
      name: `${projectStore.currentProject.name}_browser`,
      outputFormat: settings.value.format,
      duration: result.durationSeconds,
      fileSize: result.blob.size,
      provider: 'client',
      createdAt: new Date().toISOString(),
      outputUrl: downloadUrl,
    }
    renderJobStatus.value = 'completed'
    renderProgress.value = 100
    panelMessage.value = '浏览器导出完成'
  } catch (err: unknown) {
    if (err instanceof DOMException && err.name === 'AbortError') {
      renderJobStatus.value = 'cancelled'
      panelMessage.value = '已取消浏览器导出'
      if (sessionId) {
        ClientExportAPI.cancelSession(sessionId).catch(() => {})
      }
    } else {
      const msg = err instanceof Error ? err.message : 'Browser export failed'
      renderError.value = msg
      renderJobStatus.value = 'failed'
      projectStore.setError(msg)
      if (sessionId) {
        ClientExportAPI.failSession(sessionId, 'BROWSER_EXPORT_ERROR', msg).catch(() => {})
      }
    }
  } finally {
    submitting.value = false
    clientExportAbort.value = null
  }
}

async function submitRender() {
  if (!projectStore.currentProject) return

  if (hasSyncConflict.value) {
    const msg = '请先解决时间线同步冲突后再导出'
    projectStore.setError(msg)
    panelMessage.value = msg
    return
  }

  const ffBlockReason = featureFlagSubmitBlocked.value
  if (ffBlockReason) {
    projectStore.setError(ffBlockReason)
    renderError.value = ffBlockReason
    renderErrorCode.value = 'FEATURE_FLAG_DISABLED'
    renderJobStatus.value = 'failed'
    return
  }

  if (exportMode.value === 'incremental') {
    panelMessage.value = '请使用下方「增量导出」按钮提交'
    return
  }

  submitting.value = true
  projectStore.setError(null)

  try {
    await validateCurrentExport()
    if (validationResult.value && !validationResult.value.allowed) {
      projectStore.setError(validationResult.value.userFriendlyMessage)
      submitting.value = false
      return
    }

    if (renderLocation.value === 'CLIENT') {
      submitting.value = false
      await submitClientExport()
      return
    }

    completedArtifact.value = null
    renderJobId.value = null
    renderProgress.value = 0
    renderError.value = null
    renderErrorCode.value = undefined

    const editorTimeline = {
      ...timelineStore.toJSON(),
      clips: timelineStore.clips,
    }
    const snapshot = await RenderAPI.saveTimelineSnapshot(projectStore.currentProject.id, editorTimeline, {
      ensureInternal: true,
    })

    const job = await RenderAPI.createJob(projectStore.currentProject.id, {
      timelineSnapshotId: snapshot.snapshotId,
      profile: selectedPreset.value,
    })

    await RenderAPI.executeJob(job.id)
    projectStore.addRenderJob(job)
    lastJob.value = job
    renderJobId.value = job.id
    renderJobStatus.value = 'queued'
    startPolling(job.id)
  } catch (err: unknown) {
    const errObj = err as Record<string, unknown>
    const responseData = errObj?.response ? (errObj.response as Record<string, unknown>)?.data as Record<string, unknown> : undefined
    const errorCode = String(responseData?.errorCode || 'COMMON-500-001')
    const errorMsg = String(responseData?.message || (err instanceof Error ? err.message : 'Failed to submit render job'))
    projectStore.setError(`${errorCode}: ${errorMsg}`)
    renderError.value = `${errorCode}: ${errorMsg}`
    renderErrorCode.value = errorCode
    renderJobStatus.value = 'failed'
  } finally {
    submitting.value = false
  }
}

function startPolling(jobId: string) {
  if (pollInterval.value) clearInterval(pollInterval.value)
  const tenantId = projectStore.currentTenant
  const projectId = projectStore.currentProject?.id
  pollInterval.value = setInterval(async () => {
    try {
      const job = projectId
        ? await IncrementalRenderAPI.getJob(tenantId, projectId, jobId)
        : await RenderAPI.getJob(jobId)
      projectStore.updateRenderJob(jobId, job)

      switch (job.status) {
        case 'QUEUED':
          renderJobStatus.value = 'queued'
          break
        case 'PROCESSING':
          renderJobStatus.value = 'running'
          renderProgress.value = Math.min(90, renderProgress.value + 10)
          break
        case 'COMPLETED':
          renderJobStatus.value = 'completed'
          renderProgress.value = 100
          lastJob.value = job
          if (pollInterval.value) clearInterval(pollInterval.value)

          if (job.artifactId) {
            completedArtifact.value = {
              id: job.artifactId,
              renderJobId: job.id,
              projectId: job.projectId,
              name: `${projectStore.currentProject?.name || 'Export'}_${job.id.slice(0, 8)}`,
              outputFormat: settings.value.format,
              duration: timelineStore.state.duration,
              fileSize: 0,
              provider: providerInfo.value?.key || 'stub',
              createdAt: new Date().toISOString(),
            }
          } else {
            completedArtifact.value = {
              id: `artifact_${job.id}`,
              renderJobId: job.id,
              projectId: job.projectId,
              name: `${projectStore.currentProject?.name || 'Export'}_${job.id.slice(0, 8)}`,
              outputFormat: settings.value.format,
              duration: timelineStore.state.duration,
              fileSize: 0,
              provider: providerInfo.value?.key || 'stub',
              createdAt: new Date().toISOString(),
            }
          }
          break
        case 'FAILED':
          renderJobStatus.value = 'failed'
          renderError.value = `RENDER-500-001: Render execution failed`
          renderErrorCode.value = 'RENDER-500-001'
          lastJob.value = job
          if (pollInterval.value) clearInterval(pollInterval.value)
          break
      }
    } catch {
      if (pollInterval.value) clearInterval(pollInterval.value)
    }
  }, 3000)
}

function getStatusColor(status: string): string {
  switch (status) {
    case 'COMPLETED': return 'text-success'
    case 'FAILED': return 'text-danger'
    case 'PROCESSING': case 'RENDERING': return 'text-warning'
    default: return 'text-text-secondary'
  }
}

function getPresetDisabledReason(preset: ExportPreset): string {
  if (preset.name.startsWith('gpu_') && !gpuAvailable.value) {
    return 'GPU rendering requires TEAM tier or above'
  }
  if (preset.name.includes('4k') && currentTier.value !== 'TEAM' && currentTier.value !== 'ENTERPRISE') {
    return '4K export requires TEAM tier or above'
  }
  if (preset.name.startsWith('gpu_') && !isExportFlagEnabled('export.gpu.v2')) {
    return getExportFlagDisabledReason('export.gpu.v2')
  }
  return `Not available in ${currentTier.value} tier`
}

function handleRetry() {
  if (renderJobId.value) {
    renderError.value = null
    renderProgress.value = 0
    renderJobStatus.value = 'queued'
    startPolling(renderJobId.value)
  }
}

function handleCancel() {
  if (clientExportAbort.value) {
    clientExportAbort.value.abort()
  }
  if (pollInterval.value) clearInterval(pollInterval.value)
  renderJobStatus.value = 'cancelled'
}

function handleCopyDiagnostic() {
  diagnosticInfo.value = `Job: ${renderJobId.value}, Status: ${renderJobStatus.value}, Time: ${new Date().toISOString()}`
}

function handlePreview(_artifact: Artifact) {
  previewOpen.value = true
}

function handleDownload(artifact: Artifact) {
  if (artifact.outputUrl) {
    window.open(artifact.outputUrl, '_blank')
  }
}

function handleCopyId(id: string) {
  navigator.clipboard.writeText(id)
}

function handleOpenCatalog(catalogId: string) {
  window.open(`/catalog/${catalogId}`, '_blank')
}

function handleViewLogs(url: string) {
  window.open(url, '_blank')
}
</script>

<template>
  <div class="flex flex-col h-full p-2 space-y-3 overflow-y-auto">
    <div class="flex items-center justify-between">
      <h3 class="text-sm font-semibold text-white">Export</h3>
      <span class="px-2 py-0.5 rounded text-xs font-medium text-white" :class="tierBadgeClass">
        {{ currentTier }}
      </span>
    </div>

    <!-- REST Fallback notice -->
    <div v-if="restFallback" class="px-2 py-1 rounded bg-info-muted border border-info/50 text-[10px] text-info">
      Using REST fallback — GraphQL endpoint unavailable
    </div>

    <!-- Feature Flag Status Display -->
    <div class="p-2 rounded bg-surface-2/50 border border-border-subtle text-xs space-y-1">
      <div class="flex items-center justify-between">
        <span class="text-text-secondary font-medium">Feature Flags</span>
        <button class="text-[10px] text-info hover:text-info" @click="refreshExportFlags()">
          {{ loadingExportFlags ? 'Loading...' : 'Refresh' }}
        </button>
      </div>
      <div v-for="flag in exportFlagStatuses" :key="flag.key" class="flex items-center justify-between">
        <span class="text-text-secondary">{{ flag.label }}</span>
        <span class="text-[10px] px-1.5 py-0.5 rounded"
          :class="flag.enabled ? 'bg-success-muted text-success' : 'bg-surface-4/20 text-text-secondary'">
          {{ flag.enabled ? 'ON' : 'OFF' }}
        </span>
      </div>
    </div>

    <!-- Feature Flag Block Notice -->
    <div v-if="featureFlagSubmitBlocked" class="p-2 rounded bg-warning-muted border border-warning/50 text-xs">
      <div class="flex items-center gap-1 text-warning font-medium">
        <span>🚩</span>
        <span>Feature Flag Block</span>
      </div>
      <div class="text-warning mt-1">{{ featureFlagSubmitBlocked }}</div>
    </div>

    <!-- Timeline Summary -->
    <div class="p-2 rounded bg-surface-2/50 border border-border-subtle text-xs space-y-1">
      <div class="text-text-secondary font-medium">Timeline Summary</div>
      <div class="flex justify-between">
        <span class="text-text-secondary">Duration</span>
        <span class="text-white">{{ timelineSummary.duration.toFixed(1) }}s</span>
      </div>
      <div class="flex justify-between">
        <span class="text-text-secondary">Tracks</span>
        <span class="text-white">{{ timelineSummary.tracks }}</span>
      </div>
      <div class="flex justify-between">
        <span class="text-text-secondary">Clips</span>
        <span class="text-white">{{ timelineSummary.clips }}</span>
      </div>
      <div class="flex justify-between">
        <span class="text-text-secondary">Subtitles</span>
        <span class="text-white">{{ timelineSummary.subtitles }}</span>
      </div>
      <div class="flex justify-between">
        <span class="text-text-secondary">Effects</span>
        <span class="text-white">{{ timelineSummary.effects }}</span>
      </div>
    </div>

    <!-- Budget Status -->
    <div v-if="budgetStatus" class="p-2 rounded border text-xs space-y-1"
      :class="hasBudgetExceeded ? 'bg-danger-muted border-danger' : hasBudgetWarning ? 'bg-yellow-900/30 border-warning' : 'bg-surface-2/50 border-border-subtle'">
      <div class="flex items-center justify-between">
        <span class="text-text-secondary">Budget</span>
        <span v-if="hasBudgetExceeded" class="text-danger font-medium">Exceeded</span>
        <span v-else-if="hasBudgetWarning" class="text-warning font-medium">Warning</span>
        <span v-else class="text-success font-medium">OK</span>
      </div>
      <div class="w-full bg-surface-3 rounded-full h-1.5">
        <div class="h-1.5 rounded-full transition-all"
          :class="hasBudgetExceeded ? 'bg-red-500' : hasBudgetWarning ? 'bg-yellow-500' : 'bg-green-500'"
          :style="{ width: budgetStatus.budgetLimit > 0 ? Math.min(100, (budgetStatus.currentSpend / budgetStatus.budgetLimit * 100)) + '%' : '0%' }">
        </div>
      </div>
      <div class="flex justify-between text-[10px]">
        <span class="text-text-tertiary">${{ budgetStatus.currentSpend?.toFixed(2) || '0.00' }} spent</span>
        <span class="text-text-tertiary">${{ budgetStatus.budgetLimit?.toFixed(2) || '0.00' }} limit</span>
      </div>
      <div v-if="budgetStatus.message" class="text-[10px] mt-1"
        :class="hasBudgetExceeded ? 'text-danger' : 'text-warning'">
        {{ budgetStatus.message }}
      </div>
    </div>

    <!-- Validation Not Allowed -->
    <div v-if="validationResult && !validationResult.allowed" class="p-2 rounded bg-danger-muted border border-danger/50 text-xs space-y-1">
      <div class="flex items-center gap-1 text-danger font-medium">
        <span>⚠</span>
        <span>Export Blocked</span>
      </div>
      <div class="text-danger">{{ validationResult.userFriendlyMessage }}</div>
      <div v-if="validationResult.violations?.length" class="mt-1 space-y-0.5">
        <div v-for="(v, i) in validationResult.violations" :key="i" class="text-[10px] text-danger/80">{{ v }}</div>
      </div>
      <div v-if="validationResult.recommendedPreset" class="mt-1">
        <button class="text-[10px] text-info hover:text-info underline" @click="applyRecommendedPreset">
          Use recommended preset: {{ validationResult.recommendedPreset }}
        </button>
      </div>
      <div v-if="validationResult.upgradeOptions?.length">
        <UpgradeHint variant="card" title="Upgrade Required" :description="validationResult.upgradeOptions.join(', ')" class="mt-1" />
      </div>
    </div>

    <!-- Render location -->
    <div v-if="validationResult?.allowed" class="p-2 rounded bg-surface-2/50 border border-border-subtle text-xs space-y-1">
      <div class="flex justify-between">
        <span class="text-text-secondary">Export path</span>
        <span :class="renderLocation === 'CLIENT' ? 'text-emerald-400' : 'text-info'">
          {{ renderLocation === 'CLIENT' ? 'Browser (WebCodecs)' : 'Cloud render' }}
        </span>
      </div>
      <div v-if="renderLocation === 'CLIENT'" class="flex items-center gap-2">
        <label class="flex items-center gap-1 text-text-secondary cursor-pointer">
          <input v-model="saveToProject" type="checkbox" class="rounded border-border-default" />
          Save to project
        </label>
      </div>
      <div v-if="validationResult.clientExportUnsupportedReasons?.length && renderLocation === 'SERVER'" class="text-[10px] text-amber-400">
        {{ validationResult.clientExportUnsupportedReasons.join(', ') }}
      </div>
      <div v-if="renderLocation === 'CLIENT' && clientExportMessage" class="text-[10px] text-text-secondary">
        {{ clientExportMessage }} ({{ clientExportProgress }}%)
      </div>
    </div>

    <!-- Validation Allowed -->
    <div v-if="validationResult && validationResult.allowed && validationResult.recommendations?.length" class="p-2 rounded bg-info-muted border border-info/50 text-xs">
      <div v-for="(rec, i) in validationResult.recommendations" :key="i" class="text-info text-[10px]">
        {{ rec }}
      </div>
    </div>

    <!-- Worker Status -->
    <div class="p-2 rounded bg-surface-2/50 border border-border-subtle">
      <div class="flex items-center justify-between mb-1">
        <span class="text-xs text-text-secondary">Render Worker</span>
        <button class="text-[10px] text-info hover:text-info" @click="loadWorkers()">Refresh</button>
      </div>

      <div class="flex gap-1 mb-2">
        <button class="flex-1 px-2 py-1 rounded text-xs font-medium transition-colors"
          :class="selectedWorkerType === 'local' ? 'bg-blue-600 text-white' : 'bg-surface-3 text-text-secondary'"
          @click="selectedWorkerType = 'local'">
          Local
        </button>
        <button class="flex-1 px-2 py-1 rounded text-xs font-medium transition-colors"
          :class="selectedWorkerType === 'remote' ? 'bg-blue-600 text-white' : 'bg-surface-3 text-text-secondary'"
          :disabled="!remoteWorkerAvailable || !isExportFlagEnabled('export.remoteWorker.enabled')"
          :title="!remoteWorkerAvailable ? 'Remote worker requires TEAM tier' : !isExportFlagEnabled('export.remoteWorker.enabled') ? getExportFlagDisabledReason('export.remoteWorker.enabled') : ''"
          @click="selectedWorkerType = 'remote'; loadWorkers()">
          Remote
          <span v-if="!remoteWorkerAvailable || !isExportFlagEnabled('export.remoteWorker.enabled')" class="text-[9px] text-text-tertiary">(TEAM+)</span>
        </button>
      </div>

      <div v-if="selectedWorkerType === 'local'" class="text-xs space-y-1">
        <div class="flex items-center gap-1.5">
          <span class="w-2 h-2 rounded-full bg-green-500"></span>
          <span class="text-white">Local Worker</span>
          <span class="text-text-tertiary ml-auto">{{ providerInfo?.key || 'auto' }}</span>
        </div>
        <div class="text-text-tertiary text-[10px]">
          {{ providerInfo?.capabilities?.formats?.join(', ') || 'MP4, WebM' }} ·
          {{ providerInfo?.capabilities?.codecs?.join(', ') || 'H.264, VP9' }}
        </div>
      </div>

      <div v-else class="space-y-1">
        <div v-if="loadingWorkers" class="text-xs text-text-tertiary">Loading workers...</div>
        <div v-else-if="workers.length === 0" class="text-xs text-text-tertiary">No remote workers registered</div>
        <div v-for="worker in workers" :key="worker.workerId" class="flex items-center gap-1.5 text-xs">
          <span class="w-2 h-2 rounded-full" :class="getWorkerStatusClass(worker.status)"></span>
          <span class="text-white font-mono">{{ worker.workerId }}</span>
          <span class="text-text-tertiary ml-auto">{{ worker.status }} ({{ worker.activeJobs }}/{{ worker.maxConcurrentJobs }})</span>
        </div>
      </div>

      <div v-if="selectedPreset.startsWith('gpu_')" class="mt-1.5 pt-1.5 border-t border-border-subtle">
        <div class="flex items-center gap-1 text-[10px]">
          <span :class="gpuAvailable && isExportFlagEnabled('export.gpu.v2') ? 'text-accent-400' : 'text-text-tertiary'">⚡ GPU Accelerated</span>
          <span v-if="!gpuAvailable || !isExportFlagEnabled('export.gpu.v2')" class="text-text-tertiary">(TEAM+ required)</span>
          <span class="text-text-tertiary ml-auto">{{ selectedPreset.includes('h265') ? 'NVENC HEVC' : 'NVENC H.264' }}</span>
        </div>
      </div>
    </div>

    <!-- Preset Selection -->
    <div>
      <label class="text-xs text-text-secondary block mb-1">Preset</label>
      <select v-model="selectedPreset"
        class="w-full bg-surface-2 border border-border-default rounded px-2 py-1 text-xs text-white"
        @change="updateSettingsFromPreset(selectedPreset)">
        <option v-for="preset in availablePresets" :key="preset.name" :value="preset.name">
          {{ preset.displayName }} {{ preset.watermark ? '(Watermarked)' : '' }}
        </option>
      </select>
      <div v-if="recommendedPreset" class="text-[10px] text-info mt-0.5">
        Recommended: {{ recommendedPreset }}
      </div>
    </div>

    <!-- Estimated Cost -->
    <div v-if="validationResult?.estimatedCost" class="p-2 rounded bg-surface-2/50 border border-border-subtle text-xs">
      <div class="flex justify-between">
        <span class="text-text-secondary">Estimated Cost</span>
        <span class="text-white font-medium">
          ${{ validationResult.estimatedCost.toFixed(4) }} {{ validationResult.currency }}
        </span>
      </div>
      <div v-if="validationResult.providerCandidates?.length" class="flex justify-between mt-0.5">
        <span class="text-text-secondary">Provider</span>
        <span class="text-text-primary">{{ validationResult.providerCandidates.join(', ') }}</span>
      </div>
      <div class="flex justify-between mt-0.5">
        <span class="text-text-secondary">Quota Remaining</span>
        <span class="text-white">{{ budgetStatus?.remainingBudget ?? '—' }}</span>
      </div>
    </div>

    <!-- Preset Info -->
    <div v-if="presetInfo" class="p-2 rounded bg-surface-2/50 border border-border-subtle text-xs space-y-1">
      <div class="flex justify-between">
        <span class="text-text-secondary">Resolution</span>
        <span class="text-white">{{ presetInfo.resolution }}</span>
      </div>
      <div class="flex justify-between">
        <span class="text-text-secondary">Watermark</span>
        <span :class="watermarked ? 'text-warning' : 'text-success'">
          {{ watermarked ? 'Yes' : 'No' }}
        </span>
      </div>
      <div v-if="providerInfo" class="flex justify-between">
        <span class="text-text-secondary">Provider</span>
        <span class="text-white">{{ providerInfo.key || 'auto' }}</span>
      </div>
    </div>

    <!-- Unavailable Presets -->
    <div v-if="unavailablePresets.length > 0" class="p-2 rounded bg-surface-2/30 border border-border-subtle/50 text-xs">
      <div class="text-text-tertiary mb-1">Unavailable Presets ({{ currentTier }})</div>
      <div v-for="preset in unavailablePresets.slice(0, 3)" :key="preset.name" class="flex justify-between text-[10px]">
        <span class="text-text-tertiary">{{ preset.displayName }}</span>
        <span class="text-text-tertiary" :title="getPresetDisabledReason(preset)">
          {{ getPresetDisabledReason(preset) }}
        </span>
      </div>
      <div v-if="unavailablePresets.length > 3" class="text-[10px] text-text-tertiary mt-0.5">
        +{{ unavailablePresets.length - 3 }} more
      </div>
    </div>

    <!-- Format -->
    <div>
      <label class="text-xs text-text-secondary block mb-1">Format</label>
      <select v-model="settings.format" class="w-full bg-surface-2 border border-border-default rounded px-2 py-1 text-xs text-white">
        <option value="mp4">MP4 (H.264)</option>
        <option value="webm">WebM (VP9)</option>
        <option value="mov">MOV</option>
      </select>
    </div>

    <!-- Frame Rate -->
    <div>
      <label class="text-xs text-text-secondary block mb-1">Frame Rate</label>
      <select v-model="settings.frameRate" class="w-full bg-surface-2 border border-border-default rounded px-2 py-1 text-xs text-white">
        <option :value="24">24 fps</option>
        <option :value="30">30 fps</option>
        <option :value="60">60 fps</option>
      </select>
    </div>

    <!-- Encoder -->
    <div>
      <label class="text-xs text-text-secondary block mb-1">Encoder</label>
      <select v-model="settings.encoder" class="w-full bg-surface-2 border border-border-default rounded px-2 py-1 text-xs text-white">
        <option value="h264">H.264</option>
        <option value="vp9">VP9</option>
        <option value="aac">AAC</option>
      </select>
    </div>

    <!-- Subtitle Mode -->
    <div v-if="subtitleStore.tracks.length > 0">
      <label class="text-xs text-text-secondary block mb-1">Subtitle Mode</label>
      <select v-model="subtitleMode" class="w-full bg-surface-2 border border-border-default rounded px-2 py-1 text-xs text-white">
        <option value="none">No Subtitles</option>
        <option value="burn-in">Burn-in (Hardcoded)</option>
        <option value="external">External Subtitle File</option>
        <option value="multi-external" v-if="subtitleStore.tracks.length > 1">Multi-language Package</option>
      </select>

      <div v-if="subtitleMode === 'multi-external'" class="mt-1 space-y-0.5">
        <div v-for="track in subtitleStore.tracks" :key="track.id" class="flex items-center gap-1">
          <input type="checkbox" :id="track.id" :value="track.language" v-model="selectedSubtitleLanguages" class="rounded" />
          <label :for="track.id" class="text-xs text-white">{{ track.label }}</label>
        </div>
      </div>
    </div>

    <!-- Subtitle & Font Info -->
    <div v-if="subtitleStore.tracks.length > 0" class="p-2 rounded bg-surface-2/50 border border-border-subtle text-xs space-y-1">
      <div class="text-text-secondary">Subtitle Tracks</div>
      <div v-for="track in subtitleStore.tracks" :key="track.id" class="flex justify-between">
        <span class="text-white">{{ track.label }}</span>
        <span class="text-text-tertiary">
          {{ track.cues.length }} cues
          <span v-if="track.fontId"> · {{ track.fontId }}</span>
          <span v-if="!track.burnIn" class="text-info"> · EXT</span>
        </span>
      </div>
      <div v-if="subtitleStore.fonts.length" class="mt-1 pt-1 border-t border-border-subtle">
        <div class="text-text-secondary">Fonts</div>
        <div v-for="font in subtitleStore.fonts" :key="font.fontId" class="flex justify-between text-[10px]">
          <span class="text-white">{{ font.family }}</span>
          <span class="text-text-tertiary">{{ font.format.toUpperCase() }} · {{ (font.fileSize / 1024).toFixed(0) }}KB</span>
        </div>
      </div>
    </div>

    <!-- Render Job Status -->
    <RenderJobStatus
      v-if="renderJobId && renderJobStatus !== 'completed'"
      :job-id="renderJobId"
      :status="renderJobStatus"
      :progress="renderProgress"
      :error="renderError"
      :error-code="renderErrorCode"
      :diagnostic-info="diagnosticInfo"
      @retry="handleRetry"
      @cancel="handleCancel"
      @copy-diagnostic="handleCopyDiagnostic"
    />

    <!-- Artifact Result -->
    <ArtifactResult
      v-if="completedArtifact && renderJobStatus === 'completed'"
      :artifact="completedArtifact"
      @preview="handlePreview"
      @download="handleDownload"
      @copy-id="handleCopyId"
      @open-catalog="handleOpenCatalog"
      @view-logs="handleViewLogs"
    />

    <DeliveryStatusPanel
      v-if="renderJobId && renderJobStatus === 'completed' && projectStore.currentProject?.id"
      :tenant-id="projectStore.currentTenant"
      :project-id="projectStore.currentProject.id"
      :job-id="renderJobId"
    />

    <!-- Artifact Preview Modal -->
    <ArtifactPreviewModal
      :open="previewOpen"
      :artifact="completedArtifact"
      @close="previewOpen = false"
    />

    <!-- Export mode -->
    <div class="flex gap-1 text-xs">
      <button
        type="button"
        class="flex-1 py-1 rounded border"
        :class="exportMode === 'incremental' ? 'border-emerald-500 text-emerald-400 bg-emerald-900/20' : 'border-border-default text-text-secondary'"
        @click="exportMode = 'incremental'"
      >
        增量 / AI
      </button>
      <button
        type="button"
        class="flex-1 py-1 rounded border"
        :class="exportMode === 'legacy' ? 'border-info text-info bg-info-muted' : 'border-border-default text-text-secondary'"
        @click="exportMode = 'legacy'"
      >
        传统导出
      </button>
    </div>

    <p
      v-if="panelMessage"
      class="text-xs text-violet-300"
    >
      {{ panelMessage }}
    </p>

    <template v-if="exportMode === 'incremental' && projectStore.currentProject">
      <TimelineInternalPreviewPanel
        :project-id="projectStore.currentProject.id"
        :timeline-json="buildTimelineJson()"
        @previewed="onInternalPreviewed"
        @error="onPanelError"
      />
      <AiTimelineEditPanel
        :project-id="projectStore.currentProject.id"
        :timeline-json="effectiveTimelineJson()"
        :completed-jobs="completedJobs"
        v-model:edit-session-id="editSessionId"
        @applied="onAiTimelineApplied"
        @proposals-updated="onProposalsUpdated"
        @error="onPanelError"
      />
      <AiProposalsPanel
        v-if="aiProposals.length"
        :project-id="projectStore.currentProject.id"
        :timeline-json="editedTimelineJson || effectiveTimelineJson()"
        :proposals="aiProposals"
        :edit-session-id="editSessionId"
        @updated="onProposalsUpdated"
        @error="onPanelError"
      />
      <IncrementalRenderPanel
        :project-id="projectStore.currentProject.id"
        :timeline-json="effectiveTimelineJson()"
        :profile="selectedPreset"
        :completed-jobs="completedJobs"
        :edit-session-id="editSessionId"
        @submitted="onIncrementalSubmitted"
        @error="onPanelError"
      />
    </template>

    <!-- Export Button -->
    <button class="w-full py-2 rounded text-sm font-medium transition-colors"
      :class="!isExportDisabled ? 'bg-clip-video hover:bg-clip-video/80 text-white' : 'bg-surface-4 text-text-secondary cursor-not-allowed'"
      :disabled="isExportDisabled"
      @click="submitRender">
      {{ exportMode === 'incremental' ? '使用下方增量导出' : submitting ? (renderLocation === 'CLIENT' ? 'Exporting in browser...' : 'Submitting...') : hasSyncConflict ? 'Resolve sync conflict' : featureFlagSubmitBlocked ? 'Feature Disabled' : hasBudgetExceeded ? 'Budget Exceeded' : validationResult && !validationResult.allowed ? 'Export Blocked' : renderLocation === 'CLIENT' ? 'Export in Browser' : 'Export Video' }}
    </button>

    <!-- Last Job -->
    <div v-if="lastJob && !renderJobId" class="p-2 rounded bg-surface-2/50 border border-border-subtle">
      <div class="text-xs text-text-secondary">Last Job</div>
      <div class="flex items-center justify-between mt-1">
        <span class="text-xs font-mono text-white">{{ lastJob.id?.slice(0, 12) }}...</span>
        <span class="text-xs font-medium" :class="getStatusColor(lastJob.status)">{{ lastJob.status }}</span>
      </div>
    </div>

    <!-- Recent Jobs -->
    <div v-if="recentJobs.length && !renderJobId" class="space-y-1">
      <div class="text-xs text-text-secondary">Recent Jobs</div>
      <div v-for="job in recentJobs" :key="job.id" class="flex items-center justify-between p-1.5 rounded bg-surface-2/30">
        <span class="text-xs font-mono text-text-primary">{{ job.id?.slice(0, 8) }}</span>
        <span class="text-xs" :class="getStatusColor(job.status)">{{ job.status }}</span>
      </div>
    </div>

    <!-- Error -->
    <div v-if="projectStore.error" class="p-2 rounded bg-danger-muted border border-danger text-xs text-danger">
      {{ projectStore.error }}
    </div>

    <!-- Imported Metadata Preview -->
    <div v-if="projectStore.currentProject">
      <ImportedMetadataPanel />
    </div>
  </div>
</template>
