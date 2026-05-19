<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { IdentityAPI } from '@/api/admin/identity'
import type { AdminTenant, AdminProject, AdminUser, AdminApiKey } from '@/api/admin/identity'

type Tab = 'tenants' | 'projects' | 'users' | 'apikeys'

const loading = ref(true)
const activeTab = ref<Tab>('tenants')
const tenants = ref<AdminTenant[]>([])
const projects = ref<AdminProject[]>([])
const users = ref<AdminUser[]>([])
const apiKeys = ref<AdminApiKey[]>([])

// Forms
const showForm = ref(false)
const formMode = ref<'create' | 'edit'>('create')
const selectedTenantId = ref('')

// Tenant form
const tenantName = ref('')
// Project form
const projectName = ref('')
const projectDesc = ref('')
// User form
const userName = ref('')
const userEmail = ref('')
// API key form
const apiKeyName = ref('')

const tabs: { key: Tab; label: string }[] = [
  { key: 'tenants', label: 'Tenants' },
  { key: 'projects', label: 'Projects' },
  { key: 'users', label: 'Users' },
  { key: 'apikeys', label: 'API Keys' },
]

onMounted(async () => {
  await loadTenants()
  loading.value = false
})

async function loadTenants() {
  try {
    // Create a default tenant if none exist
    const result = await IdentityAPI.getAccessOverview()
    if (result.tenants === 0) {
      const t = await IdentityAPI.createTenant('Default Tenant')
      tenants.value = [t]
    }
  } catch {
    tenants.value = [{ id: 'tenant-1', name: 'Default Tenant', status: 'ACTIVE' }]
  }
  selectedTenantId.value = tenants.value[0]?.id || 'tenant-1'
  await loadTabData()
}

async function loadTabData() {
  const tid = selectedTenantId.value
  try {
    switch (activeTab.value) {
      case 'tenants':
        break
      case 'projects':
        projects.value = await IdentityAPI.listProjects(tid)
        break
      case 'users':
        users.value = await IdentityAPI.listUsers(tid)
        break
      case 'apikeys':
        apiKeys.value = await IdentityAPI.listApiKeys(tid)
        break
    }
  } catch { /* backend may not be running */ }
}

function switchTab(tab: Tab) {
  activeTab.value = tab
  showForm.value = false
  loadTabData()
}

function openForm(mode: 'create' | 'edit') {
  formMode.value = mode
  showForm.value = true
  tenantName.value = ''
  projectName.value = ''
  projectDesc.value = ''
  userName.value = ''
  userEmail.value = ''
  apiKeyName.value = ''
}

async function submitForm() {
  try {
    switch (activeTab.value) {
      case 'tenants':
        if (tenantName.value.trim()) {
          await IdentityAPI.createTenant(tenantName.value.trim())
          await loadTenants()
        }
        break
      case 'projects':
        if (projectName.value.trim()) {
          await IdentityAPI.createProject(selectedTenantId.value, projectName.value.trim(), projectDesc.value)
          await loadTabData()
        }
        break
      case 'users':
        if (userName.value.trim() && userEmail.value.trim()) {
          await IdentityAPI.createUser(selectedTenantId.value, userName.value.trim(), userEmail.value.trim())
          await loadTabData()
        }
        break
      case 'apikeys':
        if (apiKeyName.value.trim()) {
          await IdentityAPI.createApiKey(selectedTenantId.value, apiKeyName.value.trim())
          await loadTabData()
        }
        break
    }
    showForm.value = false
  } catch { /* backend may not be running */ }
}
</script>

<template>
  <div class="flex-1 overflow-y-auto p-6">
    <div class="flex items-center justify-between mb-6">
      <h1 class="text-xl font-bold">Tenant Management</h1>
      <button
        v-if="activeTab !== 'tenants'"
        class="px-3 py-1.5 bg-blue-600 hover:bg-blue-500 text-sm rounded"
        @click="openForm('create')"
      >
        + New {{ activeTab === 'apikeys' ? 'API Key' : activeTab === 'users' ? 'User' : 'Project' }}
      </button>
    </div>

    <!-- Tenant selector -->
    <div class="mb-4 flex items-center gap-3">
      <label class="text-xs text-gray-400">Selected Tenant:</label>
      <select
        v-model="selectedTenantId"
        class="bg-gray-800 border border-gray-600 rounded px-2 py-1.5 text-sm text-white"
        @change="loadTabData"
      >
        <option v-for="t in tenants" :key="t.id" :value="t.id">{{ t.name }} ({{ t.id }})</option>
      </select>
    </div>

    <!-- Tabs -->
    <div class="flex border-b border-gray-700 mb-4">
      <button
        v-for="tab in tabs"
        :key="tab.key"
        class="px-4 py-2 text-sm"
        :class="activeTab === tab.key ? 'text-blue-400 border-b-2 border-blue-400' : 'text-gray-400 hover:text-white'"
        @click="switchTab(tab.key)"
      >
        {{ tab.label }}
      </button>
    </div>

    <div v-if="loading" class="text-gray-400 text-sm">Loading...</div>

    <!-- Tenants -->
    <template v-else-if="activeTab === 'tenants'">
      <div class="space-y-2">
        <div v-for="t in tenants" :key="t.id" class="bg-gray-800 border border-gray-700 rounded-lg p-3 flex items-center justify-between">
          <div>
            <span class="text-sm font-medium">{{ t.name }}</span>
            <span class="text-xs text-gray-500 ml-2 font-mono">{{ t.id }}</span>
          </div>
          <span class="text-xs px-1.5 py-0.5 rounded bg-green-600/20 text-green-300">{{ t.status }}</span>
        </div>
      </div>
    </template>

    <!-- Projects -->
    <template v-else-if="activeTab === 'projects'">
      <div v-if="projects.length === 0 && !showForm" class="text-gray-500 text-sm">No projects</div>
      <div class="space-y-2">
        <div v-for="p in projects" :key="p.id" class="bg-gray-800 border border-gray-700 rounded-lg p-3">
          <div class="flex items-center justify-between">
            <span class="text-sm font-medium">{{ p.name }}</span>
            <span class="text-xs text-gray-500 font-mono">{{ p.id }}</span>
          </div>
          <p class="text-xs text-gray-400 mt-1">{{ p.description || 'No description' }}</p>
        </div>
      </div>
    </template>

    <!-- Users -->
    <template v-else-if="activeTab === 'users'">
      <div v-if="users.length === 0 && !showForm" class="text-gray-500 text-sm">No users</div>
      <div class="space-y-2">
        <div v-for="u in users" :key="u.id" class="bg-gray-800 border border-gray-700 rounded-lg p-3 flex items-center justify-between">
          <div>
            <span class="text-sm">{{ u.name }}</span>
            <span class="text-xs text-gray-500 ml-2">{{ u.email }}</span>
          </div>
          <span class="text-xs text-gray-500 font-mono">{{ u.id }}</span>
        </div>
      </div>
    </template>

    <!-- API Keys -->
    <template v-else-if="activeTab === 'apikeys'">
      <div v-if="apiKeys.length === 0 && !showForm" class="text-gray-500 text-sm">No API keys</div>
      <div class="space-y-2">
        <div v-for="k in apiKeys" :key="k.id || k.key" class="bg-gray-800 border border-gray-700 rounded-lg p-3 flex items-center justify-between">
          <div>
            <span class="text-sm">{{ k.name }}</span>
            <span class="text-xs text-gray-500 ml-2 font-mono">{{ k.key?.slice(0, 12) }}...</span>
          </div>
          <span class="text-xs px-1.5 py-0.5 rounded bg-green-600/20 text-green-300">{{ k.status || 'ACTIVE' }}</span>
        </div>
      </div>
    </template>

    <!-- Create Form -->
    <div v-if="showForm" class="mt-4 bg-gray-800 border border-gray-700 rounded-lg p-4">
      <h3 class="text-sm font-semibold mb-3">Create {{ activeTab === 'apikeys' ? 'API Key' : activeTab === 'users' ? 'User' : activeTab === 'tenants' ? 'Tenant' : 'Project' }}</h3>
      <div class="space-y-3 max-w-md">
        <!-- Tenant name -->
        <div v-if="activeTab === 'tenants'">
          <label class="text-xs text-gray-400 block mb-1">Name</label>
          <input v-model="tenantName" type="text" class="w-full bg-gray-700 border border-gray-600 rounded px-2 py-1.5 text-sm text-white" placeholder="Tenant name" />
        </div>
        <!-- Project fields -->
        <template v-if="activeTab === 'projects'">
          <div>
            <label class="text-xs text-gray-400 block mb-1">Name</label>
            <input v-model="projectName" type="text" class="w-full bg-gray-700 border border-gray-600 rounded px-2 py-1.5 text-sm text-white" placeholder="Project name" />
          </div>
          <div>
            <label class="text-xs text-gray-400 block mb-1">Description</label>
            <input v-model="projectDesc" type="text" class="w-full bg-gray-700 border border-gray-600 rounded px-2 py-1.5 text-sm text-white" placeholder="Optional" />
          </div>
        </template>
        <!-- User fields -->
        <template v-if="activeTab === 'users'">
          <div>
            <label class="text-xs text-gray-400 block mb-1">Name</label>
            <input v-model="userName" type="text" class="w-full bg-gray-700 border border-gray-600 rounded px-2 py-1.5 text-sm text-white" placeholder="User name" />
          </div>
          <div>
            <label class="text-xs text-gray-400 block mb-1">Email</label>
            <input v-model="userEmail" type="email" class="w-full bg-gray-700 border border-gray-600 rounded px-2 py-1.5 text-sm text-white" placeholder="user@example.com" />
          </div>
        </template>
        <!-- API key name -->
        <div v-if="activeTab === 'apikeys'">
          <label class="text-xs text-gray-400 block mb-1">Name</label>
          <input v-model="apiKeyName" type="text" class="w-full bg-gray-700 border border-gray-600 rounded px-2 py-1.5 text-sm text-white" placeholder="Key name" />
        </div>

        <div class="flex gap-2">
          <button class="px-3 py-1.5 bg-blue-600 hover:bg-blue-500 text-sm rounded" @click="submitForm">Create</button>
          <button class="px-3 py-1.5 bg-gray-700 hover:bg-gray-600 text-sm rounded" @click="showForm = false">Cancel</button>
        </div>
      </div>
    </div>
  </div>
</template>
