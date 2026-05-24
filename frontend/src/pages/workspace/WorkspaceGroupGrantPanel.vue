<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { WorkspaceEntitlementAPI } from '@/api/workspace'
import type { WorkspaceGroupGrant } from '@/types'
import WorkspacePageLayout from '@/components/workspace/WorkspacePageLayout.vue'

const route = useRoute()
const workspaceId = route.params.workspaceId as string

const loading = ref(true)
const grants = ref<WorkspaceGroupGrant[]>([])
const newGroupId = ref('')
const newGroupName = ref('')
const newFeatureKey = ref('')
const newExpiry = ref('')
const granting = ref(false)

const commonFeatures = [
  'gpu_rendering', '4k_export', 'remote_worker', 'custom_fonts',
  'ofx_effects', 'watermark_free', 'priority_queue'
]

onMounted(loadGrants)

async function loadGrants() {
  loading.value = true
  try {
    grants.value = await WorkspaceEntitlementAPI.getGroupGrants(workspaceId)
  } catch { /* backend may not be running */ }
  loading.value = false
}

async function grant() {
  if (!newGroupId.value || !newFeatureKey.value) return
  granting.value = true
  try {
    await WorkspaceEntitlementAPI.grantGroupEntitlement(
      workspaceId, newGroupId.value, newFeatureKey.value, newExpiry.value || undefined
    )
    newGroupId.value = ''
    newGroupName.value = ''
    newFeatureKey.value = ''
    newExpiry.value = ''
    await loadGrants()
  } catch { /* handle error */ }
  granting.value = false
}

async function revoke(grantId: string) {
  await WorkspaceEntitlementAPI.revokeGroupEntitlement(workspaceId, grantId)
  await loadGrants()
}
</script>

<template>
  <WorkspacePageLayout title="Group Grants">
  <div class="bg-surface-2 border border-border-subtle rounded-lg p-4 space-y-4">
    <div class="flex items-center justify-between">
      <h3 class="text-sm font-semibold text-text-primary">Group Grants</h3>
      <button class="text-[10px] text-info hover:text-info" @click="loadGrants">Refresh</button>
    </div>

    <div v-if="loading" class="text-text-tertiary text-xs">Loading...</div>
    <div v-else-if="grants.length === 0" class="text-text-tertiary text-xs">No group grants</div>
    <div v-else class="space-y-1">
      <div v-for="grant in grants" :key="grant.grantId" class="flex items-center justify-between p-2 rounded bg-surface-3/20 text-xs">
        <div>
          <span class="text-white font-medium">{{ grant.groupName }}</span>
          <span class="text-text-secondary ml-1">→ {{ grant.featureName }}</span>
          <span v-if="grant.expiresAt" class="text-[10px] text-text-tertiary ml-1">expires {{ grant.expiresAt }}</span>
        </div>
        <button class="text-[10px] text-danger hover:text-danger" @click="revoke(grant.grantId)">Revoke</button>
      </div>
    </div>

    <div class="pt-3 border-t border-border-subtle space-y-2">
      <div class="text-[10px] text-text-tertiary font-medium">New Group Grant</div>
      <input v-model="newGroupId" placeholder="Group ID" class="w-full bg-surface-3 border border-border-default rounded px-2 py-1 text-xs text-white" />
      <input v-model="newGroupName" placeholder="Group Name" class="w-full bg-surface-3 border border-border-default rounded px-2 py-1 text-xs text-white" />
      <select v-model="newFeatureKey" class="w-full bg-surface-3 border border-border-default rounded px-2 py-1 text-xs text-white">
        <option value="">Select feature...</option>
        <option v-for="feat in commonFeatures" :key="feat" :value="feat">{{ feat }}</option>
      </select>
      <input v-model="newExpiry" type="date" class="w-full bg-surface-3 border border-border-default rounded px-2 py-1 text-xs text-white" />
      <button class="w-full px-2 py-1 bg-blue-600 hover:bg-blue-500 text-white text-xs rounded" :disabled="granting || !newGroupId || !newFeatureKey" @click="grant">
        {{ granting ? 'Granting...' : 'Grant' }}
      </button>
    </div>
  </div>
  </WorkspacePageLayout>
</template>
