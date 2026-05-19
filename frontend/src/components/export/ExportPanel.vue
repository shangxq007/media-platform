<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, watch } from 'vue'
import { useProjectStore } from '@/stores/project'
import { useTimelineStore } from '@/stores/timeline'
import { useSubtitleStore } from '@/stores/subtitle'
import { RenderAPI, EntitlementAPI } from '@/api'
import type { ExportSettings, RenderJob, ExportValidationResult, BudgetStatus, Artifact } from '@/types'
import RenderJobStatus from './RenderJobStatus.vue'
import ArtifactResult from './ArtifactResult.vue'
import ArtifactPreviewModal from './ArtifactPreviewModal.vue'
import UpgradeHint from '@/components/ui/UpgradeHint.vue'
import { useExportFeatureFlags } from '@/composables/useFeatureFlag'

const projectStore = useProjectStore()
const timelineStore = useTimelineStore()
const subtitleStore = useSubtitleStore()

const subtitleMode = ref<'none' | 'burn-in' | 'external' | 'multi-external'>('none')
const selectedSubtitleLanguages = ref<string[]>([])

const currentTier = ref('FREE')
const selectedPreset = ref('free_720p_watermarked')
const submitting = ref(false)
const lastJob = ref<RenderJob | null>(null)
const pollInterval = ref<ReturnType<typeof setInterval> | null>(null)
const availablePresets = ref<any[]>([])
const providerInfo = ref<any>(null)
const workers = ref<any[]>([])
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
  clips: timelineStore.state.tracks.reduce((sum: number, t: any) => sum + t.clips.length, 0),
  subtitles: subtitleStore.tracks.length,
  effects: timelineStore.state.tracks.reduce(
    (sum: number, t: any) =>
      sum + t.clips.reduce((s: number, tc: any) => s + (tc.effects?.length || 0), 0),
    0
  ),
}))

const tierBadgeClass = computed(() => {
  switch (currentTier.value) {
    case 'FREE': return 'bg-gray-600'
    case 'PRO': return 'bg-blue-600'
    case 'TEAM': return 'bg-purple-600'
    case 'ENTERPRISE': return 'bg-amber-600'
    case 'EXPERIMENTAL': return 'bg-red-600'
    default: return 'bg-gray-600'
  }
})

const presetInfo = computed(() => {
  const preset = availablePresets.value.find((p: any) => p.name === selectedPreset.value)
  return preset || null
})

const canExport = computed(() =>
  projectStore.hasProject &&
  timelineStore.state.tracks.some((t: any) => t.clips.length > 0)
)

const recentJobs = computed(() =>
  projectStore.renderJobs.slice(-5).reverse()
)

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

const filteredPresets = computed(() => availablePresets.value.filter((p: any) => {
  if (p.name.startsWith('gpu_')) return gpuAvailable.value && isExportFlagEnabled('export.gpu.v2')
  if (p.name.includes('4k')) return currentTier.value === 'TEAM' || currentTier.value === 'ENTERPRISE'
  return true
}))

const unavailablePresets = computed(() => {
  const allowed = new Set(filteredPresets.value.map((p: any) => p.name))
  return availablePresets.value.filter((p: any) => !allowed.has(p.name))
})

const recommendedPreset = computed(() => validationResult.value?.recommendedPreset || null)

const isExportDisabled = computed(() =>
  !canExport.value || submitting.value || hasBudgetExceeded.value ||
  (validationResult.value !== null && !validationResult.value.allowed) ||
  featureFlagSubmitBlocked.value !== null
)

const watermarked = computed(() => presetInfo.value?.watermark || false)

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
    const result = await EntitlementAPI.validateExport(
      selectedPreset.value,
      settings.value.format,
      Math.max(60, timelineStore.state.duration)
    )
    validationResult.value = result
    exportValidationDetail.value = result
    if (result.budgetStatus) {
      budgetStatus.value = result.budgetStatus
    }
  } catch {
    validationResult.value = null
    exportValidationDetail.value = null
  } finally {
    validatingExport.value = false
  }
}

onMounted(async () => {
  await loadCapabilities()
  await loadPresets()
  await loadWorkers()
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
    const resp = await fetch('/api/v1/render/presets', { headers: { 'X-Tenant-ID': 'tenant-1' } })
    if (resp.ok) {
      const data = await resp.json()
      availablePresets.value = data.presets || []
      currentTier.value = data.tier || currentTier.value
      providerInfo.value = data.provider || null
    }
  } catch {
    availablePresets.value = [
      { name: 'free_720p_watermarked', displayName: 'Free 720p (Watermarked)', resolution: '1280x720', watermark: true },
      { name: 'preview_720p', displayName: 'Preview 720p', resolution: '1280x720', watermark: false },
      { name: 'default_720p', displayName: 'Standard 720p', resolution: '1280x720', watermark: false },
      { name: 'default_1080p', displayName: 'Standard 1080p', resolution: '1920x1080', watermark: false },
      { name: 'pro_1080p', displayName: 'Pro 1080p', resolution: '1920x1080', watermark: false },
      { name: 'team_4k', displayName: 'Team 4K', resolution: '3840x2160', watermark: false },
      { name: 'gpu_h264', displayName: 'GPU H.264 (NVENC)', resolution: '1920x1080', watermark: false },
      { name: 'gpu_h265', displayName: 'GPU H.265 (NVENC)', resolution: '1920x1080', watermark: false },
      { name: 'hq_1080p', displayName: 'HQ 1080p', resolution: '1920x1080', watermark: false },
      { name: 'h265', displayName: 'H.265/HEVC', resolution: '1920x1080', watermark: false },
      { name: 'vp9', displayName: 'VP9', resolution: '1920x1080', watermark: false },
      { name: 'mobile_480p', displayName: 'Mobile 480p', resolution: '854x480', watermark: false },
      { name: 'social_1080p', displayName: 'Social 1080p', resolution: '1920x1080', watermark: false },
      { name: 'social_720p', displayName: 'Social 720p', resolution: '1280x720', watermark: false },
      { name: 'ofx_1080p', displayName: 'OFX 1080p', resolution: '1920x1080', watermark: false },
      { name: 'ofx_720p', displayName: 'OFX 720p', resolution: '1280x720', watermark: false }
    ]
  }
}

async function loadWorkers() {
  loadingWorkers.value = true
  try {
    const resp = await fetch('/api/v1/remote-worker/workers', { headers: { 'X-Tenant-ID': 'tenant-1' } })
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
    case 'OFFLINE': return 'bg-gray-500'
    case 'ERROR': return 'bg-red-500'
    default: return 'bg-gray-500'
  }
}

function updateSettingsFromPreset(presetName: string) {
  const preset = availablePresets.value.find((p: any) => p.name === presetName)
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

async function submitRender() {
  if (!projectStore.currentProject) return

  const ffBlockReason = featureFlagSubmitBlocked.value
  if (ffBlockReason) {
    projectStore.setError(ffBlockReason)
    renderError.value = ffBlockReason
    renderErrorCode.value = 'FEATURE_FLAG_DISABLED'
    renderJobStatus.value = 'failed'
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

    completedArtifact.value = null
    renderJobId.value = null
    renderProgress.value = 0
    renderError.value = null
    renderErrorCode.value = undefined

    const job = await RenderAPI.createJob(projectStore.currentProject.id, {
      format: settings.value.format,
      resolution: settings.value.resolution,
      profile: selectedPreset.value,
      audioTrack: settings.value.audioTrack,
      frameRate: String(settings.value.frameRate),
      encoder: settings.value.encoder
    })
    projectStore.addRenderJob(job)
    lastJob.value = job
    renderJobId.value = job.id
    renderJobStatus.value = 'queued'
    startPolling(job.id)
  } catch (err: any) {
    const errorCode = err.response?.data?.errorCode || 'COMMON-500-001'
    const errorMsg = err.response?.data?.message || err.message || 'Failed to submit render job'
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
  pollInterval.value = setInterval(async () => {
    try {
      const job = await RenderAPI.getJob(jobId)
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
    case 'COMPLETED': return 'text-green-400'
    case 'FAILED': return 'text-red-400'
    case 'PROCESSING': case 'RENDERING': return 'text-yellow-400'
    default: return 'text-gray-400'
  }
}

function getPresetDisabledReason(preset: any): string {
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
    <div v-if="restFallback" class="px-2 py-1 rounded bg-blue-900/20 border border-blue-700/50 text-[10px] text-blue-300">
      Using REST fallback — GraphQL endpoint unavailable
    </div>

    <!-- Feature Flag Status Display -->
    <div class="p-2 rounded bg-gray-800/50 border border-gray-700 text-xs space-y-1">
      <div class="flex items-center justify-between">
        <span class="text-gray-400 font-medium">Feature Flags</span>
        <button class="text-[10px] text-blue-400 hover:text-blue-300" @click="refreshExportFlags()">
          {{ loadingExportFlags ? 'Loading...' : 'Refresh' }}
        </button>
      </div>
      <div v-for="flag in exportFlagStatuses" :key="flag.key" class="flex items-center justify-between">
        <span class="text-gray-400">{{ flag.label }}</span>
        <span class="text-[10px] px-1.5 py-0.5 rounded"
          :class="flag.enabled ? 'bg-green-600/20 text-green-300' : 'bg-gray-600/20 text-gray-400'">
          {{ flag.enabled ? 'ON' : 'OFF' }}
        </span>
      </div>
    </div>

    <!-- Feature Flag Block Notice -->
    <div v-if="featureFlagSubmitBlocked" class="p-2 rounded bg-yellow-900/20 border border-yellow-700/50 text-xs">
      <div class="flex items-center gap-1 text-yellow-400 font-medium">
        <span>🚩</span>
        <span>Feature Flag Block</span>
      </div>
      <div class="text-yellow-300 mt-1">{{ featureFlagSubmitBlocked }}</div>
    </div>

    <!-- Timeline Summary -->
    <div class="p-2 rounded bg-gray-800/50 border border-gray-700 text-xs space-y-1">
      <div class="text-gray-400 font-medium">Timeline Summary</div>
      <div class="flex justify-between">
        <span class="text-gray-400">Duration</span>
        <span class="text-white">{{ timelineSummary.duration.toFixed(1) }}s</span>
      </div>
      <div class="flex justify-between">
        <span class="text-gray-400">Tracks</span>
        <span class="text-white">{{ timelineSummary.tracks }}</span>
      </div>
      <div class="flex justify-between">
        <span class="text-gray-400">Clips</span>
        <span class="text-white">{{ timelineSummary.clips }}</span>
      </div>
      <div class="flex justify-between">
        <span class="text-gray-400">Subtitles</span>
        <span class="text-white">{{ timelineSummary.subtitles }}</span>
      </div>
      <div class="flex justify-between">
        <span class="text-gray-400">Effects</span>
        <span class="text-white">{{ timelineSummary.effects }}</span>
      </div>
    </div>

    <!-- Budget Status -->
    <div v-if="budgetStatus" class="p-2 rounded border text-xs space-y-1"
      :class="hasBudgetExceeded ? 'bg-red-900/30 border-red-700' : hasBudgetWarning ? 'bg-yellow-900/30 border-yellow-700' : 'bg-gray-800/50 border-gray-700'">
      <div class="flex items-center justify-between">
        <span class="text-gray-400">Budget</span>
        <span v-if="hasBudgetExceeded" class="text-red-400 font-medium">Exceeded</span>
        <span v-else-if="hasBudgetWarning" class="text-yellow-400 font-medium">Warning</span>
        <span v-else class="text-green-400 font-medium">OK</span>
      </div>
      <div class="w-full bg-gray-700 rounded-full h-1.5">
        <div class="h-1.5 rounded-full transition-all"
          :class="hasBudgetExceeded ? 'bg-red-500' : hasBudgetWarning ? 'bg-yellow-500' : 'bg-green-500'"
          :style="{ width: budgetStatus.budgetLimit > 0 ? Math.min(100, (budgetStatus.currentSpend / budgetStatus.budgetLimit * 100)) + '%' : '0%' }">
        </div>
      </div>
      <div class="flex justify-between text-[10px]">
        <span class="text-gray-500">${{ budgetStatus.currentSpend?.toFixed(2) || '0.00' }} spent</span>
        <span class="text-gray-500">${{ budgetStatus.budgetLimit?.toFixed(2) || '0.00' }} limit</span>
      </div>
      <div v-if="budgetStatus.message" class="text-[10px] mt-1"
        :class="hasBudgetExceeded ? 'text-red-400' : 'text-yellow-400'">
        {{ budgetStatus.message }}
      </div>
    </div>

    <!-- Validation Not Allowed -->
    <div v-if="validationResult && !validationResult.allowed" class="p-2 rounded bg-red-900/20 border border-red-700/50 text-xs space-y-1">
      <div class="flex items-center gap-1 text-red-400 font-medium">
        <span>⚠</span>
        <span>Export Blocked</span>
      </div>
      <div class="text-red-300">{{ validationResult.userFriendlyMessage }}</div>
      <div v-if="validationResult.violations?.length" class="mt-1 space-y-0.5">
        <div v-for="(v, i) in validationResult.violations" :key="i" class="text-[10px] text-red-300/80">{{ v }}</div>
      </div>
      <div v-if="validationResult.recommendedPreset" class="mt-1">
        <button class="text-[10px] text-blue-400 hover:text-blue-300 underline" @click="applyRecommendedPreset">
          Use recommended preset: {{ validationResult.recommendedPreset }}
        </button>
      </div>
      <div v-if="validationResult.upgradeOptions?.length">
        <UpgradeHint variant="card" title="Upgrade Required" :description="validationResult.upgradeOptions.join(', ')" class="mt-1" />
      </div>
    </div>

    <!-- Validation Allowed -->
    <div v-if="validationResult && validationResult.allowed && validationResult.recommendations?.length" class="p-2 rounded bg-blue-900/20 border border-blue-700/50 text-xs">
      <div v-for="(rec, i) in validationResult.recommendations" :key="i" class="text-blue-300 text-[10px]">
        {{ rec }}
      </div>
    </div>

    <!-- Worker Status -->
    <div class="p-2 rounded bg-gray-800/50 border border-gray-700">
      <div class="flex items-center justify-between mb-1">
        <span class="text-xs text-gray-400">Render Worker</span>
        <button class="text-[10px] text-blue-400 hover:text-blue-300" @click="loadWorkers()">Refresh</button>
      </div>

      <div class="flex gap-1 mb-2">
        <button class="flex-1 px-2 py-1 rounded text-xs font-medium transition-colors"
          :class="selectedWorkerType === 'local' ? 'bg-blue-600 text-white' : 'bg-gray-700 text-gray-400'"
          @click="selectedWorkerType = 'local'">
          Local
        </button>
        <button class="flex-1 px-2 py-1 rounded text-xs font-medium transition-colors"
          :class="selectedWorkerType === 'remote' ? 'bg-blue-600 text-white' : 'bg-gray-700 text-gray-400'"
          :disabled="!remoteWorkerAvailable || !isExportFlagEnabled('export.remoteWorker.enabled')"
          :title="!remoteWorkerAvailable ? 'Remote worker requires TEAM tier' : !isExportFlagEnabled('export.remoteWorker.enabled') ? getExportFlagDisabledReason('export.remoteWorker.enabled') : ''"
          @click="selectedWorkerType = 'remote'; loadWorkers()">
          Remote
          <span v-if="!remoteWorkerAvailable || !isExportFlagEnabled('export.remoteWorker.enabled')" class="text-[9px] text-gray-500">(TEAM+)</span>
        </button>
      </div>

      <div v-if="selectedWorkerType === 'local'" class="text-xs space-y-1">
        <div class="flex items-center gap-1.5">
          <span class="w-2 h-2 rounded-full bg-green-500"></span>
          <span class="text-white">Local Worker</span>
          <span class="text-gray-500 ml-auto">{{ providerInfo?.key || 'auto' }}</span>
        </div>
        <div class="text-gray-500 text-[10px]">
          {{ providerInfo?.capabilities?.formats?.join(', ') || 'MP4, WebM' }} ·
          {{ providerInfo?.capabilities?.codecs?.join(', ') || 'H.264, VP9' }}
        </div>
      </div>

      <div v-else class="space-y-1">
        <div v-if="loadingWorkers" class="text-xs text-gray-500">Loading workers...</div>
        <div v-else-if="workers.length === 0" class="text-xs text-gray-500">No remote workers registered</div>
        <div v-for="worker in workers" :key="worker.workerId" class="flex items-center gap-1.5 text-xs">
          <span class="w-2 h-2 rounded-full" :class="getWorkerStatusClass(worker.status)"></span>
          <span class="text-white font-mono">{{ worker.workerId }}</span>
          <span class="text-gray-500 ml-auto">{{ worker.status }} ({{ worker.activeJobs }}/{{ worker.maxConcurrentJobs }})</span>
        </div>
      </div>

      <div v-if="selectedPreset.startsWith('gpu_')" class="mt-1.5 pt-1.5 border-t border-gray-700">
        <div class="flex items-center gap-1 text-[10px]">
          <span :class="gpuAvailable && isExportFlagEnabled('export.gpu.v2') ? 'text-purple-400' : 'text-gray-500'">⚡ GPU Accelerated</span>
          <span v-if="!gpuAvailable || !isExportFlagEnabled('export.gpu.v2')" class="text-gray-500">(TEAM+ required)</span>
          <span class="text-gray-500 ml-auto">{{ selectedPreset.includes('h265') ? 'NVENC HEVC' : 'NVENC H.264' }}</span>
        </div>
      </div>
    </div>

    <!-- Preset Selection -->
    <div>
      <label class="text-xs text-gray-400 block mb-1">Preset</label>
      <select v-model="selectedPreset"
        class="w-full bg-gray-800 border border-gray-600 rounded px-2 py-1 text-xs text-white"
        @change="updateSettingsFromPreset(selectedPreset)">
        <option v-for="preset in availablePresets" :key="preset.name" :value="preset.name">
          {{ preset.displayName }} {{ preset.watermark ? '(Watermarked)' : '' }}
        </option>
      </select>
      <div v-if="recommendedPreset" class="text-[10px] text-blue-400 mt-0.5">
        Recommended: {{ recommendedPreset }}
      </div>
    </div>

    <!-- Estimated Cost -->
    <div v-if="validationResult?.estimatedCost" class="p-2 rounded bg-gray-800/50 border border-gray-700 text-xs">
      <div class="flex justify-between">
        <span class="text-gray-400">Estimated Cost</span>
        <span class="text-white font-medium">
          ${{ validationResult.estimatedCost.toFixed(4) }} {{ validationResult.currency }}
        </span>
      </div>
      <div v-if="validationResult.providerCandidates?.length" class="flex justify-between mt-0.5">
        <span class="text-gray-400">Provider</span>
        <span class="text-gray-300">{{ validationResult.providerCandidates.join(', ') }}</span>
      </div>
      <div class="flex justify-between mt-0.5">
        <span class="text-gray-400">Quota Remaining</span>
        <span class="text-white">{{ budgetStatus?.remainingBudget ?? '—' }}</span>
      </div>
    </div>

    <!-- Preset Info -->
    <div v-if="presetInfo" class="p-2 rounded bg-gray-800/50 border border-gray-700 text-xs space-y-1">
      <div class="flex justify-between">
        <span class="text-gray-400">Resolution</span>
        <span class="text-white">{{ presetInfo.resolution }}</span>
      </div>
      <div class="flex justify-between">
        <span class="text-gray-400">Watermark</span>
        <span :class="watermarked ? 'text-yellow-400' : 'text-green-400'">
          {{ watermarked ? 'Yes' : 'No' }}
        </span>
      </div>
      <div v-if="providerInfo" class="flex justify-between">
        <span class="text-gray-400">Provider</span>
        <span class="text-white">{{ providerInfo.key || 'auto' }}</span>
      </div>
    </div>

    <!-- Unavailable Presets -->
    <div v-if="unavailablePresets.length > 0" class="p-2 rounded bg-gray-800/30 border border-gray-700/50 text-xs">
      <div class="text-gray-500 mb-1">Unavailable Presets ({{ currentTier }})</div>
      <div v-for="preset in unavailablePresets.slice(0, 3)" :key="preset.name" class="flex justify-between text-[10px]">
        <span class="text-gray-500">{{ preset.displayName }}</span>
        <span class="text-gray-600" :title="getPresetDisabledReason(preset)">
          {{ getPresetDisabledReason(preset) }}
        </span>
      </div>
      <div v-if="unavailablePresets.length > 3" class="text-[10px] text-gray-600 mt-0.5">
        +{{ unavailablePresets.length - 3 }} more
      </div>
    </div>

    <!-- Format -->
    <div>
      <label class="text-xs text-gray-400 block mb-1">Format</label>
      <select v-model="settings.format" class="w-full bg-gray-800 border border-gray-600 rounded px-2 py-1 text-xs text-white">
        <option value="mp4">MP4 (H.264)</option>
        <option value="webm">WebM (VP9)</option>
        <option value="mov">MOV</option>
      </select>
    </div>

    <!-- Frame Rate -->
    <div>
      <label class="text-xs text-gray-400 block mb-1">Frame Rate</label>
      <select v-model="settings.frameRate" class="w-full bg-gray-800 border border-gray-600 rounded px-2 py-1 text-xs text-white">
        <option :value="24">24 fps</option>
        <option :value="30">30 fps</option>
        <option :value="60">60 fps</option>
      </select>
    </div>

    <!-- Encoder -->
    <div>
      <label class="text-xs text-gray-400 block mb-1">Encoder</label>
      <select v-model="settings.encoder" class="w-full bg-gray-800 border border-gray-600 rounded px-2 py-1 text-xs text-white">
        <option value="h264">H.264</option>
        <option value="vp9">VP9</option>
        <option value="aac">AAC</option>
      </select>
    </div>

    <!-- Subtitle Mode -->
    <div v-if="subtitleStore.tracks.length > 0">
      <label class="text-xs text-gray-400 block mb-1">Subtitle Mode</label>
      <select v-model="subtitleMode" class="w-full bg-gray-800 border border-gray-600 rounded px-2 py-1 text-xs text-white">
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
    <div v-if="subtitleStore.tracks.length > 0" class="p-2 rounded bg-gray-800/50 border border-gray-700 text-xs space-y-1">
      <div class="text-gray-400">Subtitle Tracks</div>
      <div v-for="track in subtitleStore.tracks" :key="track.id" class="flex justify-between">
        <span class="text-white">{{ track.label }}</span>
        <span class="text-gray-500">
          {{ track.cues.length }} cues
          <span v-if="track.fontId"> · {{ track.fontId }}</span>
          <span v-if="!track.burnIn" class="text-blue-400"> · EXT</span>
        </span>
      </div>
      <div v-if="subtitleStore.fonts.length" class="mt-1 pt-1 border-t border-gray-700">
        <div class="text-gray-400">Fonts</div>
        <div v-for="font in subtitleStore.fonts" :key="font.fontId" class="flex justify-between text-[10px]">
          <span class="text-white">{{ font.family }}</span>
          <span class="text-gray-500">{{ font.format.toUpperCase() }} · {{ (font.fileSize / 1024).toFixed(0) }}KB</span>
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

    <!-- Artifact Preview Modal -->
    <ArtifactPreviewModal
      :open="previewOpen"
      :artifact="completedArtifact"
      @close="previewOpen = false"
    />

    <!-- Export Button -->
    <button class="w-full py-2 rounded text-sm font-medium transition-colors"
      :class="!isExportDisabled ? 'bg-clip-video hover:bg-clip-video/80 text-white' : 'bg-gray-600 text-gray-400 cursor-not-allowed'"
      :disabled="isExportDisabled"
      @click="submitRender">
      {{ submitting ? 'Submitting...' : featureFlagSubmitBlocked ? 'Feature Disabled' : hasBudgetExceeded ? 'Budget Exceeded' : validationResult && !validationResult.allowed ? 'Export Blocked' : 'Export Video' }}
    </button>

    <!-- Last Job -->
    <div v-if="lastJob && !renderJobId" class="p-2 rounded bg-gray-800/50 border border-gray-700">
      <div class="text-xs text-gray-400">Last Job</div>
      <div class="flex items-center justify-between mt-1">
        <span class="text-xs font-mono text-white">{{ lastJob.id?.slice(0, 12) }}...</span>
        <span class="text-xs font-medium" :class="getStatusColor(lastJob.status)">{{ lastJob.status }}</span>
      </div>
    </div>

    <!-- Recent Jobs -->
    <div v-if="recentJobs.length && !renderJobId" class="space-y-1">
      <div class="text-xs text-gray-400">Recent Jobs</div>
      <div v-for="job in recentJobs" :key="job.id" class="flex items-center justify-between p-1.5 rounded bg-gray-800/30">
        <span class="text-xs font-mono text-gray-300">{{ job.id?.slice(0, 8) }}</span>
        <span class="text-xs" :class="getStatusColor(job.status)">{{ job.status }}</span>
      </div>
    </div>

    <!-- Error -->
    <div v-if="projectStore.error" class="p-2 rounded bg-red-900/30 border border-red-700 text-xs text-red-400">
      {{ projectStore.error }}
    </div>
  </div>
</template>
