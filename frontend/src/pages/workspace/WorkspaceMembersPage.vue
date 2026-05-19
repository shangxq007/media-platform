<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { WorkspaceEntitlementAPI } from '@/api/workspace'
import type { WorkspaceMember, EntitlementGrant } from '@/types'
import WorkspaceMemberGrantPanel from './WorkspaceMemberGrantPanel.vue'

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
  <div class="flex-1 overflow-y-auto p-6 space-y-6">
    <div class="flex items-center justify-between">
      <h1 class="text-xl font-bold text-white">Workspace Members</h1>
      <button class="px-3 py-1.5 bg-gray-700 hover:bg-gray-600 text-sm rounded text-white" @click="loadMembers">Refresh</button>
    </div>

    <div v-if="loading" class="text-gray-400 text-sm">Loading...</div>
    <div v-else-if="members.length === 0" class="text-gray-500 text-sm">No members found</div>

    <div v-else class="flex gap-6">
      <div class="flex-1">
        <div class="bg-gray-800 border border-gray-700 rounded-lg overflow-hidden">
          <table class="w-full text-sm">
            <thead>
              <tr class="border-b border-gray-700 text-xs text-gray-400">
                <th class="text-left px-4 py-2">Member</th>
                <th class="text-left px-4 py-2">Email</th>
                <th class="text-left px-4 py-2">Role</th>
                <th class="text-left px-4 py-2">Entitlements</th>
                <th class="text-left px-4 py-2">Joined</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="member in members" :key="member.userId"
                class="border-b border-gray-700/50 cursor-pointer hover:bg-gray-700/30"
                :class="selectedMember?.userId === member.userId ? 'bg-blue-600/10' : ''"
                @click="selectMember(member)">
                <td class="px-4 py-2 text-xs text-white">{{ member.displayName }}</td>
                <td class="px-4 py-2 text-xs text-gray-400">{{ member.email }}</td>
                <td class="px-4 py-2"><span class="px-1.5 py-0.5 rounded bg-gray-700 text-[10px] text-gray-300">{{ member.role }}</span></td>
                <td class="px-4 py-2 text-xs text-gray-400">{{ member.entitlements.length }}</td>
                <td class="px-4 py-2 text-xs text-gray-500">{{ member.joinedAt }}</td>
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
  </div>
</template>
