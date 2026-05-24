<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { WorkspaceEntitlementAPI } from '@/api/workspace'
import type { WorkspaceMember, EntitlementGrant } from '@/types'
import WorkspaceMemberGrantPanel from './WorkspaceMemberGrantPanel.vue'
import WorkspacePageLayout from '@/components/workspace/WorkspacePageLayout.vue'

const route = useRoute()
const workspaceId = route.params.workspaceId as string

const loading = ref(true)
const members = ref<WorkspaceMember[]>([])
const selectedMember = ref<WorkspaceMember | null>(null)
const memberEntitlements = ref<EntitlementGrant[]>([])
const showGrants = ref(false)

onMounted(loadMembers)

async function loadMembers() {
  loading.value = true
  try {
    members.value = await WorkspaceEntitlementAPI.getMembers(workspaceId)
  } catch { /* backend may not be running */ }
  loading.value = false
}

async function selectMember(member: WorkspaceMember) {
  selectedMember.value = member
  showGrants.value = true
  try {
    memberEntitlements.value = await WorkspaceEntitlementAPI.getMemberEntitlements(workspaceId, member.userId)
  } catch { /* backend may not be running */ }
}
</script>

<template>
  <WorkspacePageLayout title="Workspace Members">
    <template #actions>
      <button class="theme-btn theme-btn-secondary theme-btn-sm" type="button" @click="loadMembers">Refresh</button>
    </template>

    <div v-if="loading" class="text-text-secondary text-sm">Loading...</div>
    <div v-else-if="members.length === 0" class="text-text-tertiary text-sm">No members found</div>

    <div v-else class="flex gap-6">
      <div class="flex-1">
        <div class="bg-surface-2 border border-border-subtle rounded-lg overflow-hidden">
          <table class="w-full text-sm">
            <thead>
              <tr class="border-b border-border-subtle text-xs text-text-secondary">
                <th class="text-left px-4 py-2">Member</th>
                <th class="text-left px-4 py-2">Email</th>
                <th class="text-left px-4 py-2">Role</th>
                <th class="text-left px-4 py-2">Entitlements</th>
                <th class="text-left px-4 py-2">Joined</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="member in members" :key="member.userId"
                class="border-b border-border-subtle/50 cursor-pointer hover:bg-surface-3/30"
                :class="selectedMember?.userId === member.userId ? 'bg-blue-600/10' : ''"
                @click="selectMember(member)">
                <td class="px-4 py-2 text-xs text-white">{{ member.displayName }}</td>
                <td class="px-4 py-2 text-xs text-text-secondary">{{ member.email }}</td>
                <td class="px-4 py-2"><span class="px-1.5 py-0.5 rounded bg-surface-3 text-[10px] text-text-primary">{{ member.role }}</span></td>
                <td class="px-4 py-2 text-xs text-text-secondary">{{ member.entitlements.length }}</td>
                <td class="px-4 py-2 text-xs text-text-tertiary">{{ member.joinedAt }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>

      <div v-if="showGrants && selectedMember" class="w-80 shrink-0">
        <WorkspaceMemberGrantPanel
          :workspace-id="workspaceId"
          :member="selectedMember"
          :entitlements="memberEntitlements"
          @refresh="selectMember(selectedMember)" />
      </div>
    </div>
  </WorkspacePageLayout>
</template>
