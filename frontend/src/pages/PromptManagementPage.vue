<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { useRoute } from 'vue-router'
import { useGraphQLQuery } from '@/composables/useGraphQLQuery'
import PromptTemplateList from '@/components/prompt/PromptTemplateList.vue'
import PromptTemplateEditor from '@/components/prompt/PromptTemplateEditor.vue'
import PromptExecutionList from '@/components/prompt/PromptExecutionList.vue'
import PromptManifestPanel from '@/components/prompt/PromptManifestPanel.vue'
import PortalPageHeader from '@/components/ui/PortalPageHeader.vue'
import PROMPT_TEMPLATE_DETAIL from '@/graphql/queries/promptTemplateDetail.graphql?raw'
import { usePromptFeatureFlags } from '@/composables/useFeatureFlag'

interface TemplateVersion {
  version: string
  createdAt: { iso: string }
  createdBy: string
  changelog: string
}

interface TemplateExecution {
  executionId: string
  status: string
  riskLevel: string
  costEstimate: { amount: number; currency: string }
  startedAt: { iso: string }
  finishedAt: { iso: string }
}

interface PromptTemplateDetailData {
  id: string
  name: string
  status: string
  currentVersion: string
  tags: string[]
  versions: TemplateVersion[]
  executions: TemplateExecution[]
  featureFlagStatus?: {
    promptManagementEnabled: boolean
    riskReviewEnabled: boolean
    executionCostPreviewEnabled: boolean
    manifestPanelEnabled: boolean
  }
}

const route = useRoute()
const currentTemplateId = computed(() => route.params.templateId as string || null)
const activeTab = ref<'templates' | 'executions' | 'manifest'>('templates')
const showCreateForm = ref(false)
const restFallback = ref(false)

const {
  loading: loadingPromptFlags,
  isEnabled: isPromptFlagEnabled,
  refresh: refreshPromptFlags,
} = usePromptFeatureFlags()

const gqlTemplateDetail = ref<PromptTemplateDetailData | null>(null)
const gqlLoading = ref(false)

const { loading, error, errorCode, refetch } = useGraphQLQuery<PromptTemplateDetailData>({
  query: PROMPT_TEMPLATE_DETAIL,
  variables: { id: currentTemplateId.value || '' },
  immediate: false,
})

async function fetchTemplateDetailREST(): Promise<PromptTemplateDetailData> {
  restFallback.value = true
  if (!currentTemplateId.value) {
    return { id: '', name: '', status: '', currentVersion: '', tags: [], versions: [], executions: [] }
  }
  const resp = await fetch(`/api/v1/prompts/templates/${currentTemplateId.value}`)
  if (!resp.ok) throw new Error('Failed to fetch template detail')
  return resp.json()
}

watch(currentTemplateId, async (newId) => {
  if (newId) {
    gqlLoading.value = true
    try {
      const result = await refetch()
      if (result) {
        gqlTemplateDetail.value = {
          ...result,
          featureFlagStatus: {
            promptManagementEnabled: isPromptFlagEnabled('prompt.management.enabled'),
            riskReviewEnabled: isPromptFlagEnabled('prompt.riskReview.enabled'),
            executionCostPreviewEnabled: isPromptFlagEnabled('prompt.executionCostPreview.enabled'),
            manifestPanelEnabled: isPromptFlagEnabled('prompt.manifestPanel.enabled'),
          },
        }
      }
    } catch {
      const restResult = await fetchTemplateDetailREST()
      gqlTemplateDetail.value = {
        ...restResult,
        featureFlagStatus: {
          promptManagementEnabled: isPromptFlagEnabled('prompt.management.enabled'),
          riskReviewEnabled: isPromptFlagEnabled('prompt.riskReview.enabled'),
          executionCostPreviewEnabled: isPromptFlagEnabled('prompt.executionCostPreview.enabled'),
          manifestPanelEnabled: isPromptFlagEnabled('prompt.manifestPanel.enabled'),
        },
      }
    } finally {
      gqlLoading.value = false
    }
  } else {
    gqlTemplateDetail.value = null
  }
}, { immediate: false })

function onSelectTemplate(_template: any) {
}

function onBack() {
  showCreateForm.value = false
}

const pageAccessEnabled = computed(() => isPromptFlagEnabled('prompt.management.enabled'))
const showManifestPanel = computed(() => activeTab.value === 'manifest' && isPromptFlagEnabled('prompt.manifestPanel.enabled'))
</script>

<template>
  <div class="h-full flex flex-col bg-bg-base text-text-primary px-lg py-md">
    <div v-if="!pageAccessEnabled" class="flex-1 flex items-center justify-center">
      <div class="text-center space-y-4 max-w-md">
        <div class="text-4xl">🚩</div>
        <h2 class="text-lg font-semibold">Prompt Management Unavailable</h2>
        <p class="text-sm text-text-secondary">The prompt.management.enabled feature flag is currently disabled. Contact your administrator to enable this feature.</p>
        <button type="button" class="theme-btn theme-btn-secondary theme-btn-sm" @click="refreshPromptFlags">
          {{ loadingPromptFlags ? 'Checking...' : 'Refresh Status' }}
        </button>
      </div>
    </div>

    <template v-else>
      <PortalPageHeader title="Prompt Engineering" subtitle="Templates, executions, and manifest governance" />

      <div class="flex gap-sm border-b border-default mb-md flex-shrink-0">
        <button
          type="button"
          class="px-md py-sm text-sm border-b-2 -mb-px transition-colors"
          :class="activeTab === 'templates' ? 'border-primary-500 text-primary-500' : 'border-transparent text-text-secondary'"
          @click="activeTab = 'templates'"
        >
          Templates
        </button>
        <button
          type="button"
          class="px-md py-sm text-sm border-b-2 -mb-px transition-colors"
          :class="activeTab === 'executions' ? 'border-primary-500 text-primary-500' : 'border-transparent text-text-secondary'"
          @click="activeTab = 'executions'"
        >
          Executions
          <span v-if="isPromptFlagEnabled('prompt.riskReview.enabled')" class="ml-xs theme-badge text-[9px]">RISK</span>
        </button>
        <button
          type="button"
          class="px-md py-sm text-sm border-b-2 -mb-px transition-colors"
          :class="activeTab === 'manifest' ? 'border-primary-500 text-primary-500' : 'border-transparent text-text-secondary'"
          :disabled="!isPromptFlagEnabled('prompt.manifestPanel.enabled')"
          @click="activeTab = 'manifest'"
        >
          Manifest
        </button>
        <button type="button" class="ml-auto theme-btn theme-btn-ghost theme-btn-sm" @click="refreshPromptFlags">
          {{ loadingPromptFlags ? '…' : 'Refresh flags' }}
        </button>
      </div>

      <div class="flex-1 flex flex-col min-h-0 overflow-hidden">
        <!-- GraphQL Status Bar -->
        <div v-if="currentTemplateId" class="px-md py-sm border-b border-default bg-bg-surface flex items-center justify-between text-xs flex-shrink-0">
          <div class="flex items-center gap-3">
            <span v-if="restFallback" class="text-info">REST fallback</span>
            <span v-else-if="gqlTemplateDetail" class="text-success">GraphQL loaded</span>
            <span v-else-if="loading || gqlLoading" class="text-warning">Loading...</span>
            <span v-if="error" class="text-danger">{{ errorCode || error.message }}</span>
          </div>
          <div class="flex items-center gap-3 text-text-secondary">
            <span v-if="gqlTemplateDetail">v{{ gqlTemplateDetail.currentVersion }}</span>
            <span v-if="gqlTemplateDetail">{{ gqlTemplateDetail.tags?.length || 0 }} tags</span>
            <span v-if="gqlTemplateDetail">{{ gqlTemplateDetail.executions?.length || 0 }} recent executions</span>
          </div>
        </div>

        <!-- Feature Flag Status Bar -->
        <div v-if="gqlTemplateDetail?.featureFlagStatus" class="px-4 py-1 border-b border-border-subtle bg-surface-2/30 flex items-center gap-4 text-[10px]">
          <span class="text-text-tertiary">FF:</span>
          <span :class="gqlTemplateDetail.featureFlagStatus.promptManagementEnabled ? 'text-success' : 'text-text-tertiary'">
            management={{ gqlTemplateDetail.featureFlagStatus.promptManagementEnabled ? 'ON' : 'OFF' }}
          </span>
          <span :class="gqlTemplateDetail.featureFlagStatus.riskReviewEnabled ? 'text-success' : 'text-text-tertiary'">
            risk={{ gqlTemplateDetail.featureFlagStatus.riskReviewEnabled ? 'ON' : 'OFF' }}
          </span>
          <span :class="gqlTemplateDetail.featureFlagStatus.executionCostPreviewEnabled ? 'text-success' : 'text-text-tertiary'">
            cost={{ gqlTemplateDetail.featureFlagStatus.executionCostPreviewEnabled ? 'ON' : 'OFF' }}
          </span>
          <span :class="gqlTemplateDetail.featureFlagStatus.manifestPanelEnabled ? 'text-success' : 'text-text-tertiary'">
            manifest={{ gqlTemplateDetail.featureFlagStatus.manifestPanelEnabled ? 'ON' : 'OFF' }}
          </span>
        </div>

        <template v-if="activeTab === 'templates'">
          <div v-if="currentTemplateId" class="flex-1 flex">
            <PromptTemplateEditor :template-id="currentTemplateId" @back="onBack"
              @error="err => console.error(err)" />
          </div>
          <div v-else class="flex-1 flex">
            <PromptTemplateList @create="showCreateForm = true" @select="onSelectTemplate" />
          </div>
        </template>
        <template v-else-if="activeTab === 'executions'">
          <PromptExecutionList />
        </template>
        <template v-else-if="activeTab === 'manifest'">
          <PromptManifestPanel v-if="showManifestPanel" />
          <div v-else class="flex-1 flex items-center justify-center">
            <div class="text-center space-y-3">
              <div class="text-3xl">🚩</div>
              <p class="text-sm text-text-secondary">Manifest panel is disabled by feature flag</p>
              <p class="text-xs text-text-tertiary">Enable prompt.manifestPanel.enabled to access this feature</p>
            </div>
          </div>
        </template>
      </div>
    </template>
  </div>
</template>
