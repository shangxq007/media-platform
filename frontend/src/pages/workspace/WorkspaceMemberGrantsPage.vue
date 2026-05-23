<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { WorkspaceEntitlementAPI } from '@/api/workspace'
import type { WorkspaceMember, EntitlementGrant } from '@/types'
import WorkspacePageLayout from '@/components/workspace/WorkspacePageLayout.vue'
import WorkspaceMemberGrantPanel from './WorkspaceMemberGrantPanel.vue'

const route = useRoute()
const workspaceId = route.params.workspaceId as string

const loading = ref(true)
const members = ref<WorkspaceMember[]>([])
const selectedMember = ref<WorkspaceMember | null>(null)
const memberEntitlements = ref<EntitlementGrant[]>([])

onMounted(loadMembers)

async function loadMembers() {
  loading.value = true
  try {
    members.value = await WorkspaceEntitlementAPI.getMembers(workspaceId)
    if (members.value.length > 0 && !selectedMember.value) {
      await selectMember(members.value[0])
    }
  } catch { /* backend may not be running */ }
  loading.value = false
}

async function selectMember(member: WorkspaceMember) {
  selectedMember.value = member
  try {
    memberEntitlements.value = await WorkspaceEntitlementAPI.getMemberEntitlements(workspaceId, member.userId)
  } catch { /* backend may not be running */ }
}

async function refreshGrants() {
  if (selectedMember.value) await selectMember(selectedMember.value)
}

function onMemberChange(e: Event) {
  const userId = (e.target as HTMLSelectElement).value
  const member = members.value.find(m => m.userId === userId)
  if (member) selectMember(member)
}
</script>

<template>
  <WorkspacePageLayout title="Member Grants">
    <template #actions>
      <button type="button" class="theme-btn theme-btn-secondary theme-btn-sm" @click="loadMembers">Refresh</button>
    </template>

    <div v-if="loading" class="text-sm text-text-muted">Loading members...</div>
    <div v-else-if="members.length === 0" class="text-sm text-text-muted">No members in this workspace.</div>
    <div v-else class="flex flex-col gap-md max-w-xl">
      <label class="text-xs text-text-secondary">
        Member
        <select
          class="mt-xs w-full bg-bg-surface border border-default rounded px-2 py-1 text-sm text-text-primary"
          :value="selectedMember?.userId"
          @change="onMemberChange"
        >
          <option v-for="m in members" :key="m.userId" :value="m.userId">{{ m.displayName }} ({{ m.email }})</option>
        </select>
      </label>
      <WorkspaceMemberGrantPanel
        v-if="selectedMember"
        :workspace-id="workspaceId"
        :member="selectedMember"
        :entitlements="memberEntitlements"
        @refresh="refreshGrants"
      />
    </div>
  </WorkspacePageLayout>
</template>
