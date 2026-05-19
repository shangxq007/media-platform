<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { WorkspaceEntitlementAPI } from '@/api/workspace'
import type { WorkspaceMember, EntitlementGrant, WorkspaceMemberGrant } from '@/types'

const props = defineProps<{
  workspaceId: string
  member: WorkspaceMember
  entitlements: EntitlementGrant[]
}>()

const emit = defineEmits<{
  (e: 'refresh'): void
}>()

const loading = ref(true)
const grants = ref<WorkspaceMemberGrant[]>([])
const newFeatureKey = ref('')
const newExpiry = ref('')
const granting = ref(false)

const commonFeatures = [
  'gpu_rendering', '4k_export', 'remote_worker', 'custom_fonts',
  'ofx_effects', 'watermark_free', 'priority_queue', 'api_extended'
]

onMounted(loadGrants)

async function loadGrants() {
  loading.value = true
  try {
    const allGrants = await WorkspaceEntitlementAPI.getMemberGrants(props.workspaceId)
    grants.value = allGrants.filter(g => g.memberId === props.member.userId)
  } catch { /* backend may not be running */ }
  loading.value = false
}

async function grant() {
  if (!newFeatureKey.value) return
  granting.value = true
  try {
    await WorkspaceEntitlementAPI.grantMemberEntitlement(
      props.workspaceId, props.member.userId, newFeatureKey.value, newExpiry.value || undefined
    )
    newFeatureKey.value = ''
    newExpiry.value = ''
    await loadGrants()
    emit('refresh')
  } catch { /* handle error */ }
  granting.value = false
}

async function revoke(grantId: string) {
  await WorkspaceEntitlementAPI.revokeMemberEntitlement(props.workspaceId, grantId)
  await loadGrants()
  emit('refresh')
}
</script>

<template>
  <div class="bg-gray-800 border border-gray-700 rounded-lg p-4 space-y-4">
    <h3 class="text-sm font-semibold text-gray-300">Member Grants</h3>
    <div class="text-xs text-gray-500">{{ member.displayName }} ({{ member.email }})</div>

    <div v-if="loading" class="text-gray-500 text-xs">Loading...</div>
    <div v-else>
      <div v-if="grants.length" class="space-y-1">
        <div v-for="grant in grants" :key="grant.grantId" class="flex items-center justify-between p-1.5 rounded bg-gray-700/20 text-xs">
          <div>
            <span class="text-white">{{ grant.featureName }}</span>
            <span v-if="grant.expiresAt" class="text-[10px] text-gray-500 ml-1">expires {{ grant.expiresAt }}</span>
          </div>
          <button class="text-[10px] text-red-400 hover:text-red-300" @click="revoke(grant.grantId)">Revoke</button>
        </div>
      </div>
      <div v-else class="text-gray-500 text-xs">No grants</div>
    </div>

    <div class="pt-2 border-t border-gray-700 space-y-2">
      <div class="text-[10px] text-gray-500">Grant Feature</div>
      <select v-model="newFeatureKey" class="w-full bg-gray-700 border border-gray-600 rounded px-2 py-1 text-xs text-white">
        <option value="">Select feature...</option>
        <option v-for="feat in commonFeatures" :key="feat" :value="feat">{{ feat }}</option>
      </select>
      <input v-model="newExpiry" type="date" class="w-full bg-gray-700 border border-gray-600 rounded px-2 py-1 text-xs text-white" placeholder="Expiry (optional)" />
      <button class="w-full px-2 py-1 bg-blue-600 hover:bg-blue-500 text-white text-xs rounded" :disabled="granting || !newFeatureKey" @click="grant">
        {{ granting ? 'Granting...' : 'Grant' }}
      </button>
    </div>
  </div>
</template>
