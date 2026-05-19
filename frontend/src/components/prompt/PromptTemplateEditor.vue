<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { PromptAPI } from '@/api/prompt'
import { EntitlementAPI } from '@/api'
import type { PromptTemplate, PromptTemplateVersion, PromptRenderResult, PromptValidationResult, PromptQuotaInfo } from '@/types'

const props = defineProps<{
  templateId: string
}>()

const emit = defineEmits<{
  (e: 'back'): void
  (e: 'error', msg: string): void
}>()

const template = ref<PromptTemplate | null>(null)
const versions = ref<PromptTemplateVersion[]>([])
const loading = ref(true)
const activeTab = ref<'edit' | 'versions' | 'render' | 'risk'>('edit')

// Edit form
const editName = ref('')
const editDescription = ref('')
const editCategory = ref('')
const editTags = ref('')
const editBody = ref('')
const saving = ref(false)

// Render
const renderVariables = ref('{"name": "World"}')
const renderResult = ref<PromptRenderResult | null>(null)
const rendering = ref(false)

// Validation
const validationResult = ref<PromptValidationResult | null>(null)

// Risk
const riskContent = ref('')
const riskAnalysis = ref<any>(null)
const analyzing = ref(false)

// Task 24: Prompt quota info
const promptQuota = ref<PromptQuotaInfo | null>(null)
const loadingQuota = ref(false)

const currentVersion = computed(() =>
  versions.value.length > 0 ? versions.value[versions.value.length - 1] : null
)

watch(() => props.templateId, loadTemplate, { immediate: true })

async function loadTemplate() {
  loading.value = true
  try {
    template.value = await PromptAPI.getTemplate(props.templateId)
    versions.value = await PromptAPI.getVersions(props.templateId)
    if (currentVersion.value) {
      editBody.value = currentVersion.value.templateBody || ''
    }
    editName.value = template.value?.name || ''
    editDescription.value = template.value?.description || ''
    editCategory.value = template.value?.category || ''
    editTags.value = template.value?.tags?.join(', ') || ''
  } catch (e: any) {
    emit('error', e.message || 'Failed to load template')
  } finally {
    loading.value = false
  }
  loadPromptQuota()
}

async function loadPromptQuota() {
  if (!props.templateId) return
  loadingQuota.value = true
  try {
    const caps = await EntitlementAPI.getCapabilities()
    promptQuota.value = {
      templateId: props.templateId,
      executionQuota: caps.entitlementPolicy?.monthlyRenderMinutes || 100,
      executionsUsed: 0,
      executionsRemaining: caps.entitlementPolicy?.monthlyRenderMinutes || 100,
      estimatedCostPerExecution: 0.001,
      currency: 'USD'
    }
  } catch {
    promptQuota.value = null
  } finally {
    loadingQuota.value = false
  }
}

async function saveTemplate() {
  if (!template.value) return
  saving.value = true
  try {
    await PromptAPI.updateTemplate(props.templateId, {
      name: editName.value,
      description: editDescription.value,
      category: editCategory.value,
      tags: editTags.value.split(',').map(t => t.trim()).filter(Boolean)
    })
    // Create new version if body changed
    if (editBody.value !== currentVersion.value?.templateBody) {
      await PromptAPI.createVersion(props.templateId, {
        templateBody: editBody.value,
        variableSchemaJson: '{}',
        changelog: 'Updated via editor',
        createdBy: 'user'
      })
    }
    await loadTemplate()
  } catch (e: any) {
    emit('error', e.message || 'Failed to save')
  } finally {
    saving.value = false
  }
}

async function renderPreview() {
  rendering.value = true
  try {
    const vars = JSON.parse(renderVariables.value || '{}')
    renderResult.value = await PromptAPI.render(props.templateId, {
      promptVersion: currentVersion.value?.promptVersion,
      variables: vars,
      dryRun: true
    })
  } catch (e: any) {
    emit('error', e.message || 'Render failed')
  } finally {
    rendering.value = false
  }
}

async function validateTemplate() {
  try {
    validationResult.value = await PromptAPI.validate(props.templateId)
  } catch (e: any) {
    emit('error', e.message || 'Validation failed')
  }
}

async function analyzeRisk() {
  analyzing.value = true
  try {
    riskAnalysis.value = await PromptAPI.analyzeRisk({
      content: riskContent.value || editBody.value,
      variables: JSON.parse(renderVariables.value || '{}'),
      tenantId: 'tenant-1',
      userId: 'user-1',
      environment: 'dev',
      category: editCategory.value || 'general'
    })
  } catch (e: any) {
    emit('error', e.message || 'Risk analysis failed')
  } finally {
    analyzing.value = false
  }
}

function getRiskClass(level: string): string {
  switch (level) {
    case 'CRITICAL': return 'bg-red-600'
    case 'HIGH': return 'bg-orange-500'
    case 'MEDIUM': return 'bg-yellow-500'
    default: return 'bg-green-500'
  }
}

function getActionClass(action: string): string {
  switch (action) {
    case 'BLOCK': return 'text-red-400'
    case 'REQUIRE_REVIEW': return 'text-orange-400'
    case 'WARN': return 'text-yellow-400'
    default: return 'text-green-400'
  }
}
</script>

<template>
  <div class="flex flex-col h-full">
    <!-- Header -->
    <div class="flex items-center justify-between p-3 border-b border-gray-700">
      <div class="flex items-center gap-2">
        <button class="text-gray-400 hover:text-white text-sm" @click="emit('back')">← Back</button>
        <h2 class="text-lg font-semibold text-white">{{ template?.name || 'Loading...' }}</h2>
        <span class="px-1.5 py-0.5 rounded text-[10px]"
          :class="template?.status === 'ACTIVE' ? 'bg-green-600' : 'bg-gray-600'">
          {{ template?.status }}
        </span>
      </div>
    </div>

    <!-- Prompt Quota Info -->
    <div v-if="promptQuota" class="px-3 py-2 border-b border-gray-700 bg-gray-800/50">
      <div class="flex items-center gap-4 text-[10px]">
        <span class="text-gray-400">Executions:</span>
        <span class="text-white">{{ promptQuota.executionsUsed }} / {{ promptQuota.executionQuota }}</span>
        <div class="w-24 bg-gray-700 rounded-full h-1.5">
          <div class="h-1.5 rounded-full bg-blue-500 transition-all"
            :style="{ width: Math.min(100, (promptQuota.executionsUsed / promptQuota.executionQuota * 100)) + '%' }" />
        </div>
        <span class="text-gray-500">Est. cost: ${{ promptQuota.estimatedCostPerExecution.toFixed(4) }} {{ promptQuota.currency }}/exec</span>
      </div>
    </div>

    <!-- Tabs -->
    <div class="flex border-b border-gray-700">
      <button v-for="tab in ['edit', 'versions', 'render', 'risk']" :key="tab"
        class="px-4 py-2 text-sm capitalize"
        :class="activeTab === tab ? 'text-white border-b-2 border-blue-500' : 'text-gray-400 hover:text:text-white'"
        @click="activeTab = tab as any">
        {{ tab }}
      </button>
    </div>

    <div v-if="loading" class="p-3 text-gray-400">Loading...</div>

    <!-- Edit Tab -->
    <div v-else-if="activeTab === 'edit'" class="flex-1 overflow-y-auto p-3 space-y-3">
      <div>
        <label class="text-xs text-gray-400 block mb-1">Name</label>
        <input v-model="editName" class="w-full bg-gray-800 border border-gray-600 rounded px-2 py-1 text-sm text-white" />
      </div>
      <div>
        <label class="text-xs text-gray-400 block mb-1">Description</label>
        <input v-model="editDescription" class="w-full bg-gray-800 border border-gray-600 rounded px-2 py-1 text-sm text-white" />
      </div>
      <div>
        <label class="text-xs text-gray-400 block mb-1">Category</label>
        <input v-model="editCategory" class="w-full bg-gray-800 border border-gray-600 rounded px-2 py-1 text-sm text-white" />
      </div>
      <div>
        <label class="text-xs text-gray-400 block mb-1">Tags (comma-separated)</label>
        <input v-model="editTags" class="w-full bg-gray-800 border border-gray-600 rounded px-2 py-1 text-sm text-white" />
      </div>
      <div>
        <label class="text-xs text-gray-400 block mb-1">Template Body</label>
        <textarea v-model="editBody" rows="12"
          class="w-full bg-gray-800 border border-gray-600 rounded px-2 py-1 text-sm text-white font-mono" />
      </div>
      <div class="flex gap-2">
        <button class="px-3 py-1 bg-blue-600 hover:bg-blue-500 text-white text-sm rounded"
          :disabled="saving" @click="saveTemplate">
          {{ saving ? 'Saving...' : 'Save' }}
        </button>
        <button class="px-3 py-1 bg-gray-600 hover:bg-gray-500 text-white text-sm rounded"
          @click="validateTemplate">Validate</button>
      </div>
      <div v-if="validationResult" class="p-2 rounded text-xs"
        :class="validationResult.valid ? 'bg-green-900/30 text-green-400' : 'bg-red-900/30 text-red-400'">
        <div v-for="err in validationResult.errors" :key="err">{{ err }}</div>
        <div v-for="warn in validationResult.warnings" :key="warn" class="text-yellow-400">{{ warn }}</div>
      </div>
    </div>

    <!-- Versions Tab -->
    <div v-else-if="activeTab === 'versions'" class="flex-1 overflow-y-auto p-3 space-y-2">
      <div v-for="version in versions" :key="version.versionId"
        class="p-2 rounded bg-gray-800/50 border border-gray-700">
        <div class="flex items-center justify-between">
          <span class="text-white text-sm font-mono">v{{ version.promptVersion }}</span>
          <span class="text-gray-500 text-xs">{{ version.changelog }}</span>
        </div>
        <div class="text-gray-400 text-xs mt-1">{{ version.templateBody?.substring(0, 100) }}...</div>
        <div class="flex gap-1 mt-1">
          <button class="text-[10px] text-blue-400 hover:text-blue-300"
            @click="editBody = version.templateBody || ''; activeTab = 'edit'">
            Use this version
          </button>
        </div>
      </div>
    </div>

    <!-- Render Tab -->
    <div v-else-if="activeTab === 'render'" class="flex-1 overflow-y-auto p-3 space-y-3">
      <div>
        <label class="text-xs text-gray-400 block mb-1">Variables (JSON)</label>
        <textarea v-model="renderVariables" rows="4"
          class="w-full bg-gray-800 border border-gray-600 rounded px-2 py-1 text-sm text-white font-mono" />
      </div>
      <button class="px-3 py-1 bg-purple-600 hover:bg-purple-500 text-white text-sm rounded"
        :disabled="rendering" @click="renderPreview">
        {{ rendering ? 'Rendering...' : 'Preview Render' }}
      </button>
      <div v-if="renderResult" class="space-y-2">
        <div class="p-2 rounded bg-gray-800/50 border border-gray-700">
          <div class="text-xs text-gray-400 mb-1">Rendered Output</div>
          <pre class="text-white text-xs whitespace-pre-wrap">{{ renderResult.renderedPrompt }}</pre>
        </div>
        <div class="p-2 rounded bg-gray-800/50 border border-gray-700">
          <div class="text-xs text-gray-400 mb-1">Redacted Output</div>
          <pre class="text-white text-xs whitespace-pre-wrap">{{ renderResult.redactedPrompt }}</pre>
        </div>
        <div v-if="renderResult.missingVariables?.length" class="text-yellow-400 text-xs">
          Missing: {{ renderResult.missingVariables.join(', ') }}
        </div>
        <div v-if="renderResult.warnings?.length" class="text-orange-400 text-xs">
          {{ renderResult.warnings.join('; ') }}
        </div>
      </div>
    </div>

    <!-- Risk Tab -->
    <div v-else-if="activeTab === 'risk'" class="flex-1 overflow-y-auto p-3 space-y-3">
      <div>
        <label class="text-xs text-gray-400 block mb-1">Content to Analyze</label>
        <textarea v-model="riskContent" rows="6"
          class="w-full bg-gray-800 border border-gray-600 rounded px-2 py-1 text-sm text-white font-mono"
          placeholder="Paste prompt content to analyze..." />
      </div>
      <button class="px-3 py-1 bg-red-600 hover:bg-red-500 text-white text-sm rounded"
        :disabled="analyzing" @click="analyzeRisk">
        {{ analyzing ? 'Analyzing...' : 'Analyze Risk' }}
      </button>
      <div v-if="riskAnalysis" class="space-y-2">
        <div class="flex items-center gap-2">
          <span class="px-2 py-0.5 rounded text-xs font-medium"
            :class="getRiskClass(riskAnalysis.riskLevel)">
            {{ riskAnalysis.riskLevel }}
          </span>
          <span :class="getActionClass(riskAnalysis.action)" class="text-sm font-medium">
            {{ riskAnalysis.action }}
          </span>
        </div>
        <div class="text-gray-300 text-xs">{{ riskAnalysis.explanation }}</div>
        <div v-if="riskAnalysis.secretFindings?.length" class="text-red-400 text-xs">
          <div v-for="f in riskAnalysis.secretFindings" :key="f">{{ f }}</div>
        </div>
        <div v-if="riskAnalysis.commandFindings?.length" class="text-orange-400 text-xs">
          <div v-for="f in riskAnalysis.commandFindings" :key="f">{{ f }}</div>
        </div>
      </div>
    </div>
  </div>
</template>
