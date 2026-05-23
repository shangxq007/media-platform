<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { WorkspaceEntitlementAPI } from '@/api/workspace'
import type { WorkspaceRole } from '@/types'
import WorkspacePageLayout from '@/components/workspace/WorkspacePageLayout.vue'

const route = useRoute()
const workspaceId = route.params.workspaceId as string

const loading = ref(true)
const roles = ref<WorkspaceRole[]>([])
const showCreate = ref(false)
const newRole = ref({ name: '', description: '', permissions: [] as string[] })
const editingRole = ref<WorkspaceRole | null>(null)

const availablePermissions = [
  'READ', 'WRITE', 'DELETE', 'ADMIN',
  'MANAGE_MEMBERS', 'MANAGE_ROLES', 'MANAGE_ENTITLEMENTS',
  'VIEW_BILLING', 'MANAGE_BILLING'
]

onMounted(loadRoles)

async function loadRoles() {
  loading.value = true
  try {
    roles.value = await WorkspaceEntitlementAPI.getRoles(workspaceId)
  } catch { /* backend may not be running */ }
  loading.value = false
}

async function createRole() {
  if (!newRole.value.name) return
  await WorkspaceEntitlementAPI.createRole(workspaceId, newRole.value)
  newRole.value = { name: '', description: '', permissions: [] }
  showCreate.value = false
  await loadRoles()
}

async function updateRole() {
  if (!editingRole.value) return
  await WorkspaceEntitlementAPI.updateRole(workspaceId, editingRole.value.roleId, {
    name: editingRole.value.name,
    description: editingRole.value.description,
    permissions: editingRole.value.permissions
  })
  editingRole.value = null
  await loadRoles()
}

async function deleteRole(roleId: string) {
  await WorkspaceEntitlementAPI.deleteRole(workspaceId, roleId)
  await loadRoles()
}

function togglePermission(permissions: string[], perm: string) {
  const idx = permissions.indexOf(perm)
  if (idx >= 0) permissions.splice(idx, 1)
  else permissions.push(perm)
}
</script>

<template>
  <WorkspacePageLayout title="Roles & Permissions">
  <div class="bg-gray-800 border border-gray-700 rounded-lg p-4 space-y-4">
    <div class="flex items-center justify-between">
      <h3 class="text-sm font-semibold text-gray-300">Roles</h3>
      <button class="text-[10px] text-blue-400 hover:text-blue-300" @click="showCreate = !showCreate">
        {{ showCreate ? 'Cancel' : '+ New Role' }}
      </button>
    </div>

    <div v-if="showCreate" class="p-3 rounded bg-gray-700/30 border border-gray-600 space-y-2">
      <input v-model="newRole.name" placeholder="Role name" class="w-full bg-gray-700 border border-gray-600 rounded px-2 py-1 text-xs text-white" />
      <input v-model="newRole.description" placeholder="Description" class="w-full bg-gray-700 border border-gray-600 rounded px-2 py-1 text-xs text-white" />
      <div class="flex flex-wrap gap-1">
        <button v-for="perm in availablePermissions" :key="perm"
          class="px-1.5 py-0.5 rounded text-[10px]"
          :class="newRole.permissions.includes(perm) ? 'bg-blue-600 text-white' : 'bg-gray-700 text-gray-400'"
          @click="togglePermission(newRole.permissions, perm)">
          {{ perm }}
        </button>
      </div>
      <button class="px-2 py-1 bg-blue-600 hover:bg-blue-500 text-white text-xs rounded" @click="createRole">Create</button>
    </div>

    <div v-if="loading" class="text-gray-500 text-xs">Loading...</div>
    <div v-else class="space-y-2">
      <div v-for="role in roles" :key="role.roleId" class="p-2 rounded bg-gray-700/20">
        <div v-if="editingRole?.roleId === role.roleId" class="space-y-2">
          <input v-model="editingRole.name" class="w-full bg-gray-700 border border-gray-600 rounded px-2 py-1 text-xs text-white" />
          <input v-model="editingRole.description" class="w-full bg-gray-700 border border-gray-600 rounded px-2 py-1 text-xs text-white" />
          <div class="flex flex-wrap gap-1">
            <button v-for="perm in availablePermissions" :key="perm"
              class="px-1.5 py-0.5 rounded text-[10px]"
              :class="editingRole.permissions.includes(perm) ? 'bg-blue-600 text-white' : 'bg-gray-700 text-gray-400'"
              @click="togglePermission(editingRole.permissions, perm)">
              {{ perm }}
            </button>
          </div>
          <div class="flex gap-1">
            <button class="px-2 py-1 bg-blue-600 text-white text-[10px] rounded" @click="updateRole">Save</button>
            <button class="px-2 py-1 bg-gray-600 text-white text-[10px] rounded" @click="editingRole = null">Cancel</button>
          </div>
        </div>
        <div v-else>
          <div class="flex items-center justify-between">
            <div>
              <span class="text-xs text-white font-medium">{{ role.name }}</span>
              <span class="text-[10px] text-gray-500 ml-2">{{ role.memberCount }} members</span>
            </div>
            <div class="flex gap-1">
              <button class="text-[10px] text-blue-400 hover:text-blue-300" @click="editingRole = { ...role, permissions: [...role.permissions] }">Edit</button>
              <button class="text-[10px] text-red-400 hover:text-red-300" @click="deleteRole(role.roleId)">Delete</button>
            </div>
          </div>
          <div class="text-[10px] text-gray-500 mt-1">{{ role.description }}</div>
          <div class="flex flex-wrap gap-1 mt-1">
            <span v-for="perm in role.permissions" :key="perm" class="px-1 py-0.5 rounded bg-gray-700 text-[10px] text-gray-400">{{ perm }}</span>
          </div>
        </div>
      </div>
    </div>
  </div>
  </WorkspacePageLayout>
</template>
