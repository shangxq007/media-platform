<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { useRoute } from 'vue-router'
import { useGraphQLQuery } from '@/composables/useGraphQLQuery'
import PromptTemplateList from '@/components/prompt/PromptTemplateList.vue'
import PromptTemplateEditor from '@/components/prompt/PromptTemplateEditor.vue'
import PromptExecutionList from '@/components/prompt/PromptExecutionList.vue'
import PromptManifestPanel from '@/components/prompt/PromptManifestPanel.vue'
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
  <div class="flex h-full bg-gray-900 text-white">
    <!-- Page access blocked by feature flag -->
    <div v-if="!pageAccessEnabled" class="flex-1 flex items-center justify-center">
      <div class="text-center space-y-4 max-w-md">
        <div class="text-4xl">🚩</div>
        <h2 class="text-lg font-semibold text-gray-200">Prompt Management Unavailable</h2>
        <p class="text-sm text-gray-400">The prompt.management.enabled feature flag is currently disabled. Contact your administrator to enable this feature.</p>
        <button class="theme-btn theme-btn-secondary theme-btn-sm" @click="refreshPromptFlags">
          {{ loadingPromptFlags ? 'Checking...' : 'Refresh Status' }}
        </button>
      </div>
    </div>

    <template v-else>
      <!-- Sidebar -->
      <div class="w-56 border-r border-gray-700 flex flex-col">
        <div class="p-3 border-b border-gray-700">
          <h2 class="text-sm font-semibold">Prompt Platform</h2>
        </div>
        <nav class="flex-1 p-2 space-y-1">
          <button class="w-full text-left px-3 py-2 rounded text-sm flex items-center justify-between"
            :class="activeTab === 'templates' ? 'bg-blue-600 text-white' : 'text-gray-400 hover:bg-gray-800'"
            @click="activeTab = 'templates'">
            <span>Templates</span>
          </button>
          <button class="w-full text-left px-3 py-2 rounded text-sm flex items-center justify-between"
            :class="activeTab === 'executions' ? 'bg-blue-600 text-white' : 'text-gray-400 hover:bg-gray-800'"
            @click="activeTab = 'executions'">
            <span>Executions</span>
            <span v-if="isPromptFlagEnabled('prompt.riskReview.enabled')" class="text-[8px] px-1 py-0 rounded bg-purple-600/30 text-purple-300 font-medium">RISK</span>
          </button>
          <button class="w-full text-left px-3 py-2 rounded text-sm flex items-center justify-between"
            :class="activeTab === 'manifest' ? 'bg-blue-600 text-white' : 'text-gray-400 hover:bg-gray-800'"
            :disabled="!isPromptFlagEnabled('prompt.manifestPanel.enabled')"
            :title="!isPromptFlagEnabled('prompt.manifestPanel.enabled') ? 'Manifest panel feature flag is disabled' : ''"
            @click="activeTab = 'manifest'">
            <span>Manifest</span>
            <span v-if="!isPromptFlagEnabled('prompt.manifestPanel.enabled')" class="text-[8px] px-1 py-0 rounded bg-gray-600/30 text-gray-400 font-medium">OFF</span>
          </button>
        </nav>

        <!-- Feature Flag Status in Sidebar -->
        <div class="p-3 border-t border-gray-700 space-y-1">
          <div class="text-[10px] text-gray-500 font-medium">Feature Flags</div>
          <div class="flex items-center justify-between text-[10px]">
            <span class="text-gray-500">Risk Review</span>
            <span :class="isPromptFlagEnabled('prompt.riskReview.enabled') ? 'text-green-400' : 'text-gray-500'">
              {{ isPromptFlagEnabled('prompt.riskReview.enabled') ? 'ON' : 'OFF' }}
            </span>
          </div>
          <div class="flex items-center justify-between text-[10px]">
            <span class="text-gray-500">Cost Preview</span>
            <span :class="isPromptFlagEnabled('prompt.executionCostPreview.enabled') ? 'text-green-400' : 'text-gray-500'">
              {{ isPromptFlagEnabled('prompt.executionCostPreview.enabled') ? 'ON' : 'OFF' }}
            </span>
          </div>
          <div class="flex items-center justify-between text-[10px]">
            <span class="text-gray-500">Manifest</span>
            <span :class="isPromptFlagEnabled('prompt.manifestPanel.enabled') ? 'text-green-400' : 'text-gray-500'">
              {{ isPromptFlagEnabled('prompt.manifestPanel.enabled') ? 'ON' : 'OFF' }}
            </span>
          </div>
        </div>

        <div class="p-3 border-t border-gray-700">
          <router-link to="/" class="text-xs text-blue-400 hover:text-blue-300">
            ← Back to Editor
          </router-link>
        </div>
      </div>

      <!-- Main Content -->
      <div class="flex-1 flex flex-col overflow-hidden">
        <!-- GraphQL Status Bar -->
        <div v-if="currentTemplateId" class="px-4 py-1.5 border-b border-gray-700 bg-gray-800/50 flex items-center justify-between text-xs">
          <div class="flex items-center gap-3">
            <span v-if="restFallback" class="text-blue-400">REST fallback</span>
            <span v-else-if="gqlTemplateDetail" class="text-green-400">GraphQL loaded</span>
            <span v-else-if="loading || gqlLoading" class="text-yellow-400">Loading...</span>
            <span v-if="error" class="text-red-400">{{ errorCode || error.message }}</span>
          </div>
          <div class="flex items-center gap-3 text-gray-400">
            <span v-if="gqlTemplateDetail">v{{ gqlTemplateDetail.currentVersion }}</span>
            <span v-if="gqlTemplateDetail">{{ gqlTemplateDetail.tags?.length || 0 }} tags</span>
            <span v-if="gqlTemplateDetail">{{ gqlTemplateDetail.executions?.length || 0 }} recent executions</span>
          </div>
        </div>

        <!-- Feature Flag Status Bar -->
        <div v-if="gqlTemplateDetail?.featureFlagStatus" class="px-4 py-1 border-b border-gray-700 bg-gray-800/30 flex items-center gap-4 text-[10px]">
          <span class="text-gray-500">FF:</span>
          <span :class="gqlTemplateDetail.featureFlagStatus.promptManagementEnabled ? 'text-green-400' : 'text-gray-500'">
            management={{ gqlTemplateDetail.featureFlagStatus.promptManagementEnabled ? 'ON' : 'OFF' }}
          </span>
          <span :class="gqlTemplateDetail.featureFlagStatus.riskReviewEnabled ? 'text-green-400' : 'text-gray-500'">
            risk={{ gqlTemplateDetail.featureFlagStatus.riskReviewEnabled ? 'ON' : 'OFF' }}
          </span>
          <span :class="gqlTemplateDetail.featureFlagStatus.executionCostPreviewEnabled ? 'text-green-400' : 'text-gray-500'">
            cost={{ gqlTemplateDetail.featureFlagStatus.executionCostPreviewEnabled ? 'ON' : 'OFF' }}
          </span>
          <span :class="gqlTemplateDetail.featureFlagStatus.manifestPanelEnabled ? 'text-green-400' : 'text-gray-500'">
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
              <p class="text-sm text-gray-400">Manifest panel is disabled by feature flag</p>
              <p class="text-xs text-gray-500">Enable prompt.manifestPanel.enabled to access this feature</p>
            </div>
          </div>
        </template>
      </div>
    </template>
  </div>
</template>
