<script setup lang="ts">
import { ref, computed } from 'vue'
import { FeatureFlagAPI } from '@/api/admin/feature-flags'
import type { FeatureFlagDefinition, FeatureFlagEvaluationContext, FeatureFlagEvaluationResult } from '@/api/admin/feature-flags'
import EmptyState from '@/components/ui/EmptyState.vue'

const props = defineProps<{
  flags: FeatureFlagDefinition[]
}>()

const selectedFlagKey = ref('')
const context = ref<FeatureFlagEvaluationContext>({
  tenant: '',
  workspace: '',
  user: '',
  role: '',
  group: '',
  tier: '',
  region: '',
  requestSource: '',
  environment: '',
})

const evaluating = ref(false)
const result = ref<FeatureFlagEvaluationResult | null>(null)
const error = ref<string | null>(null)

const selectedFlag = computed(() => props.flags.find(f => f.flagKey === selectedFlagKey.value) || null)

function parseContext(): FeatureFlagEvaluationContext {
  const parsed: FeatureFlagEvaluationContext = {}
  for (const [key, value] of Object.entries(context.value)) {
    if (value && String(value).trim()) {
      (parsed as Record<string, string>)[key] = String(value).trim()
    }
  }
  return parsed
}

async function evaluate() {
  if (!selectedFlagKey.value) return
  evaluating.value = true
  error.value = null
  result.value = null
  try {
    result.value = await FeatureFlagAPI.evaluateFeatureFlag(selectedFlagKey.value, parseContext())
  } catch (err) {
    error.value = err instanceof Error ? err.message : String(err)
  } finally {
    evaluating.value = false
  }
}

function resetContext() {
  context.value = { tenant: '', workspace: '', user: '', role: '', group: '', tier: '', region: '', requestSource: '', environment: '' }
}
</script>

<template>
  <div class="space-y-6">
    <EmptyState
      v-if="props.flags.length === 0"
      icon="🚩"
      title="No feature flags available"
      description="Create feature flags first to use the evaluation preview."
    />

    <template v-else>
      <div class="bg-gray-800 border border-gray-700 rounded-lg p-4">
        <h2 class="text-sm font-semibold text-gray-300 mb-3">Select Flag</h2>
        <select
          v-model="selectedFlagKey"
          class="w-full bg-gray-900 border border-gray-700 rounded px-3 py-1.5 text-sm text-gray-200"
        >
          <option value="">Choose a flag...</option>
          <option v-for="flag in props.flags" :key="flag.flagKey" :value="flag.flagKey">
            {{ flag.flagKey }} — {{ flag.name }} ({{ flag.enabled ? 'Active' : 'Disabled' }})
          </option>
        </select>
        <p v-if="selectedFlag" class="text-xs text-gray-500 mt-2">
          Type: {{ selectedFlag.type }} | Default: {{ selectedFlag.defaultValue }} | Rules: {{ selectedFlag.targetingRules.length }}
        </p>
      </div>

      <div class="bg-gray-800 border border-gray-700 rounded-lg p-4">
        <div class="flex items-center justify-between mb-3">
          <h2 class="text-sm font-semibold text-gray-300">Evaluation Context</h2>
          <button class="text-xs text-gray-400 hover:text-white" @click="resetContext">Reset</button>
        </div>
        <div class="grid grid-cols-3 gap-3">
          <div>
            <label class="block text-xs text-gray-400 mb-1">Tenant</label>
            <input v-model="context.tenant" type="text" class="w-full bg-gray-900 border border-gray-700 rounded px-3 py-1.5 text-sm text-gray-200" placeholder="tenant-1" />
          </div>
          <div>
            <label class="block text-xs text-gray-400 mb-1">Workspace</label>
            <input v-model="context.workspace" type="text" class="w-full bg-gray-900 border border-gray-700 rounded px-3 py-1.5 text-sm text-gray-200" placeholder="workspace-1" />
          </div>
          <div>
            <label class="block text-xs text-gray-400 mb-1">User</label>
            <input v-model="context.user" type="text" class="w-full bg-gray-900 border border-gray-700 rounded px-3 py-1.5 text-sm text-gray-200" placeholder="user-1" />
          </div>
          <div>
            <label class="block text-xs text-gray-400 mb-1">Role</label>
            <input v-model="context.role" type="text" class="w-full bg-gray-900 border border-gray-700 rounded px-3 py-1.5 text-sm text-gray-200" placeholder="ADMIN" />
          </div>
          <div>
            <label class="block text-xs text-gray-400 mb-1">Group</label>
            <input v-model="context.group" type="text" class="w-full bg-gray-900 border border-gray-700 rounded px-3 py-1.5 text-sm text-gray-200" placeholder="engineering" />
          </div>
          <div>
            <label class="block text-xs text-gray-400 mb-1">Tier</label>
            <select v-model="context.tier" class="w-full bg-gray-900 border border-gray-700 rounded px-3 py-1.5 text-sm text-gray-200">
              <option value="">Any</option>
              <option value="FREE">Free</option>
              <option value="PRO">Pro</option>
              <option value="TEAM">Team</option>
              <option value="ENTERPRISE">Enterprise</option>
            </select>
          </div>
          <div>
            <label class="block text-xs text-gray-400 mb-1">Region</label>
            <input v-model="context.region" type="text" class="w-full bg-gray-900 border border-gray-700 rounded px-3 py-1.5 text-sm text-gray-200" placeholder="us-east-1" />
          </div>
          <div>
            <label class="block text-xs text-gray-400 mb-1">Request Source</label>
            <input v-model="context.requestSource" type="text" class="w-full bg-gray-900 border border-gray-700 rounded px-3 py-1.5 text-sm text-gray-200" placeholder="WEB" />
          </div>
          <div>
            <label class="block text-xs text-gray-400 mb-1">Environment</label>
            <input v-model="context.environment" type="text" class="w-full bg-gray-900 border border-gray-700 rounded px-3 py-1.5 text-sm text-gray-200" placeholder="production" />
          </div>
        </div>
        <button
          class="mt-4 px-4 py-1.5 bg-blue-600 hover:bg-blue-500 text-white text-sm rounded transition-colors disabled:opacity-50"
          :disabled="!selectedFlagKey || evaluating"
          @click="evaluate"
        >
          {{ evaluating ? 'Evaluating...' : 'Evaluate' }}
        </button>
      </div>

      <div v-if="error" class="p-3 bg-red-900/30 border border-red-700 rounded-lg text-red-300 text-sm">
        {{ error }}
      </div>

      <div v-if="result" class="bg-gray-800 border border-gray-700 rounded-lg p-4">
        <h2 class="text-sm font-semibold text-gray-300 mb-3">Evaluation Result</h2>
        <div class="grid grid-cols-2 gap-4 mb-4">
          <div>
            <div class="text-xs text-gray-400">Flag</div>
            <div class="text-sm font-mono text-blue-300">{{ result.flagKey }}</div>
          </div>
          <div>
            <div class="text-xs text-gray-400">Result</div>
            <span
              class="text-sm px-2 py-0.5 rounded"
              :class="result.enabled ? 'bg-green-600/20 text-green-300' : 'bg-red-600/20 text-red-300'"
            >
              {{ result.enabled ? 'ENABLED' : 'DISABLED' }}
            </span>
          </div>
          <div v-if="result.variant">
            <div class="text-xs text-gray-400">Variant</div>
            <div class="text-sm text-gray-200">{{ result.variant }}</div>
          </div>
          <div v-if="result.matchedRule">
            <div class="text-xs text-gray-400">Matched Rule</div>
            <div class="text-sm text-gray-200">{{ result.matchedRule }}</div>
          </div>
        </div>
        <div class="mb-3">
          <div class="text-xs text-gray-400 mb-1">Reason</div>
          <div class="text-sm text-gray-200">{{ result.reason }}</div>
        </div>
        <div v-if="result.steps.length > 0">
          <div class="text-xs text-gray-400 mb-2">Evaluation Steps</div>
          <div class="space-y-1">
            <div v-for="(step, idx) in result.steps" :key="idx" class="flex items-start gap-2 p-2 bg-gray-900/50 rounded text-xs">
              <span class="text-gray-500 font-mono shrink-0">{{ idx + 1 }}.</span>
              <div>
                <span class="text-gray-300">{{ step.step }}</span>
                <span class="mx-2 text-gray-600">→</span>
                <span :class="step.result === 'MATCH' ? 'text-green-400' : step.result === 'NO_MATCH' ? 'text-yellow-400' : 'text-gray-400'">
                  {{ step.result }}
                </span>
                <span v-if="step.detail" class="text-gray-500 ml-2">{{ step.detail }}</span>
              </div>
            </div>
          </div>
        </div>
      </div>
    </template>
  </div>
</template>
