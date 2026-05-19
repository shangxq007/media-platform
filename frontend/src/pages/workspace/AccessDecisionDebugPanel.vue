<script setup lang="ts">
import { ref } from 'vue'
import { useRoute } from 'vue-router'
import { WorkspaceEntitlementAPI } from '@/api/workspace'
import type { AccessDecisionDebug } from '@/types'

const route = useRoute()
const workspaceId = route.params.workspaceId as string

const memberId = ref('')
const featureKey = ref('')
const loading = ref(false)
const result = ref<AccessDecisionDebug | null>(null)

const commonFeatures = [
  'gpu_rendering', '4k_export', 'remote_worker', 'custom_fonts',
  'ofx_effects', 'watermark_free', 'priority_queue'
]

async function debug() {
  if (!memberId.value || !featureKey.value) return
  loading.value = true
  try {
    result.value = await WorkspaceEntitlementAPI.debugAccessDecision(workspaceId, memberId.value, featureKey.value)
  } catch { /* handle error */ }
  loading.value = false
}

function ruleClass(r: string): string {
  switch (r) {
    case 'PASS': return 'text-green-400 bg-green-600/20'
    case 'FAIL': return 'text-red-400 bg-red-600/20'
    default: return 'text-gray-400 bg-gray-600/20'
  }
}
</script>

<template>
  <div class="bg-gray-800 border border-gray-700 rounded-lg p-4 space-y-4">
    <h3 class="text-sm font-semibold text-gray-300">Access Decision Debug</h3>

    <div class="space-y-2">
      <input v-model="memberId" placeholder="Member ID" class="w-full bg-gray-700 border border-gray-600 rounded px-2 py-1 text-xs text-white" />
      <select v-model="featureKey" class="w-full bg-gray-700 border border-gray-600 rounded px-2 py-1 text-xs text-white">
        <option value="">Select feature...</option>
        <option v-for="f in commonFeatures" :key="f" :value="f">{{ f }}</option>
      </select>
      <button class="w-full px-2 py-1 bg-purple-600 hover:bg-purple-500 text-white text-xs rounded" :disabled="loading || !memberId || !featureKey" @click="debug">
        {{ loading ? 'Debugging...' : 'Debug Decision' }}
      </button>
    </div>

    <div v-if="result" class="space-y-3">
      <div class="flex items-center gap-3">
        <span class="px-2 py-0.5 rounded text-[10px] font-medium"
          :class="result.decision === 'GRANTED' ? 'bg-green-600/20 text-green-400' : 'bg-red-600/20 text-red-400'">
          {{ result.decision }}
        </span>
        <span class="text-xs text-white">{{ result.featureKey }}</span>
        <span class="text-[10px] text-gray-500 ml-auto">{{ result.evaluatedAt }}</span>
      </div>

      <div class="space-y-1">
        <div class="text-[10px] text-gray-500">Evaluated Rules</div>
        <div v-for="(rule, idx) in result.evaluatedRules" :key="idx" class="flex items-center gap-2 p-1.5 rounded bg-gray-700/20 text-xs">
          <span class="px-1.5 py-0.5 rounded text-[10px] font-medium" :class="ruleClass(rule.result)">{{ rule.result }}</span>
          <span class="text-gray-300">{{ rule.ruleType }}</span>
          <span class="text-gray-500 ml-auto">{{ rule.detail }}</span>
        </div>
      </div>
    </div>
  </div>
</template>
