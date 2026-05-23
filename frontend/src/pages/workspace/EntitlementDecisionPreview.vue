<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { WorkspaceEntitlementAPI } from '@/api/workspace'
import type { WorkspaceMember, EntitlementDecision } from '@/types'
import WorkspacePageLayout from '@/components/workspace/WorkspacePageLayout.vue'

const route = useRoute()
const workspaceId = route.params.workspaceId as string

const members = ref<WorkspaceMember[]>([])
const selectedMemberId = ref('')
const selectedFeatureKey = ref('')
const loading = ref(false)
const result = ref<EntitlementDecision | null>(null)

const commonFeatures = [
  'gpu_rendering', '4k_export', 'remote_worker', 'custom_fonts',
  'ofx_effects', 'watermark_free', 'priority_queue', 'api_extended'
]

onMounted(async () => {
  try {
    members.value = await WorkspaceEntitlementAPI.getMembers(workspaceId)
  } catch { /* backend may not be running */ }
})

async function preview() {
  if (!selectedMemberId.value || !selectedFeatureKey.value) return
  loading.value = true
  try {
    result.value = await WorkspaceEntitlementAPI.previewDecision(workspaceId, selectedMemberId.value, selectedFeatureKey.value)
  } catch { /* handle error */ }
  loading.value = false
}
</script>

<template>
  <WorkspacePageLayout title="Entitlement Decision Preview">
  <div class="bg-gray-800 border border-gray-700 rounded-lg p-4 space-y-4">
    <h3 class="text-sm font-semibold text-gray-300">Decision Preview</h3>

    <div class="space-y-2">
      <div>
        <label class="text-[10px] text-gray-500 block mb-1">Member</label>
        <select v-model="selectedMemberId" class="w-full bg-gray-700 border border-gray-600 rounded px-2 py-1 text-xs text-white">
          <option value="">Select member...</option>
          <option v-for="m in members" :key="m.userId" :value="m.userId">{{ m.displayName }} ({{ m.email }})</option>
        </select>
      </div>
      <div>
        <label class="text-[10px] text-gray-500 block mb-1">Feature</label>
        <select v-model="selectedFeatureKey" class="w-full bg-gray-700 border border-gray-600 rounded px-2 py-1 text-xs text-white">
          <option value="">Select feature...</option>
          <option v-for="f in commonFeatures" :key="f" :value="f">{{ f }}</option>
        </select>
      </div>
      <button class="w-full px-2 py-1 bg-blue-600 hover:bg-blue-500 text-white text-xs rounded" :disabled="loading || !selectedMemberId || !selectedFeatureKey" @click="preview">
        {{ loading ? 'Evaluating...' : 'Preview Decision' }}
      </button>
    </div>

    <div v-if="result" class="p-3 rounded border" :class="result.granted ? 'bg-green-900/20 border-green-700/50' : 'bg-red-900/20 border-red-700/50'">
      <div class="flex items-center gap-2 mb-2">
        <span class="px-2 py-0.5 rounded text-[10px] font-medium"
          :class="result.granted ? 'bg-green-600/20 text-green-400' : 'bg-red-600/20 text-red-400'">
          {{ result.granted ? 'GRANTED' : 'DENIED' }}
        </span>
        <span class="text-xs text-white">{{ result.featureKey }}</span>
      </div>
      <p class="text-xs text-gray-400">{{ result.reason }}</p>
      <div v-if="result.sources.length" class="mt-2 space-y-1">
        <div class="text-[10px] text-gray-500">Sources:</div>
        <div v-for="src in result.sources" :key="src.sourceId" class="flex items-center gap-2 text-[10px]">
          <span class="px-1 py-0.5 rounded bg-gray-700 text-gray-300">{{ src.sourceType }}</span>
          <span class="text-gray-400">{{ src.sourceName }}</span>
        </div>
      </div>
      <div v-if="result.expiry" class="text-[10px] text-gray-500 mt-1">Expires: {{ result.expiry }}</div>
    </div>
  </div>
  </WorkspacePageLayout>
</template>
