<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { RouteManagementClient } from '@/api/navigation'
import type { FrontendRouteDefinition } from '@/types/routing'
import RouteDefinitionList from '@/components/admin/RouteDefinitionList.vue'
import RouteDefinitionEditor from '@/components/admin/RouteDefinitionEditor.vue'
import NavigationPreviewPanel from '@/components/admin/NavigationPreviewPanel.vue'

const loading = ref(true)
const routes = ref<FrontendRouteDefinition[]>([])
const selectedRoute = ref<FrontendRouteDefinition | null>(null)
const editingRoute = ref<FrontendRouteDefinition | null>(null)
const showEditor = ref(false)
const showPreview = ref(false)
const error = ref<string | null>(null)

const filterGroup = ref<string>('')
const filterStatus = ref<'all' | 'enabled' | 'disabled' | 'hidden'>('all')
const filterFlagStatus = ref<'all' | 'flagged' | 'unflagged'>('all')

const menuGroups = computed(() => {
  const groups = new Set<string>()
  for (const r of routes.value) {
    if (r.menuGroup) groups.add(r.menuGroup)
  }
  return Array.from(groups).sort()
})

const filteredRoutes = computed(() => {
  let result = routes.value
  if (filterGroup.value) {
    result = result.filter(r => r.menuGroup === filterGroup.value)
  }
  if (filterStatus.value === 'enabled') {
    result = result.filter(r => r.visible !== false && r.enabled !== false)
  } else if (filterStatus.value === 'disabled') {
    result = result.filter(r => r.visible !== false && r.enabled === false)
  } else   if (filterStatus.value === 'hidden') {
    result = result.filter(r => r.visible === false)
  }
  if (filterFlagStatus.value === 'flagged') {
    result = result.filter(r => (r as any)?.requiredFeatureFlags?.length > 0)
  } else if (filterFlagStatus.value === 'unflagged') {
    result = result.filter(r => !((r as any)?.requiredFeatureFlags?.length > 0))
  }
  return result
})

async function loadRoutes() {
  loading.value = true
  error.value = null
  try {
    routes.value = await RouteManagementClient.listRoutes()
  } catch (err) {
    const msg = err instanceof Error ? err.message : String(err)
    error.value = msg
    console.error('[RouteManagementPage] Failed to load routes:', err)
  } finally {
    loading.value = false
  }
}

function onCreateNew() {
  editingRoute.value = null
  showEditor.value = true
}

function onEdit(route: FrontendRouteDefinition) {
  editingRoute.value = { ...route }
  showEditor.value = true
}

async function onDisable(route: FrontendRouteDefinition) {
  try {
    await RouteManagementClient.disableRoute(route.routeKey)
    await loadRoutes()
  } catch (err) {
    console.error('[RouteManagementPage] Failed to disable route:', err)
  }
}

async function onEnable(route: FrontendRouteDefinition) {
  try {
    await RouteManagementClient.enableRoute(route.routeKey)
    await loadRoutes()
  } catch (err) {
    console.error('[RouteManagementPage] Failed to enable route:', err)
  }
}

async function onSave(routeData: Partial<FrontendRouteDefinition>) {
  try {
    if (editingRoute.value && editingRoute.value.routeKey) {
      await RouteManagementClient.updateRoute(editingRoute.value.routeKey, routeData)
    } else {
      await RouteManagementClient.createRoute(routeData as any)
    }
    showEditor.value = false
    editingRoute.value = null
    await loadRoutes()
  } catch (err) {
    console.error('[RouteManagementPage] Failed to save route:', err)
  }
}

function onPreview(route: FrontendRouteDefinition) {
  selectedRoute.value = route
  showPreview.value = true
}

function onEditorClose() {
  showEditor.value = false
  editingRoute.value = null
}

onMounted(() => {
  loadRoutes()
})
</script>

<template>
  <div class="flex-1 overflow-y-auto p-6">
    <div class="flex items-center justify-between mb-6">
      <h1 class="text-xl font-bold">Route Management</h1>
      <button
        class="px-3 py-1.5 text-sm bg-blue-600 hover:bg-blue-500 text-white rounded-lg transition-colors"
        @click="onCreateNew"
      >
        + Add Route
      </button>
    </div>

    <div v-if="error" class="mb-4 p-3 bg-danger-muted border border-danger rounded-lg text-danger text-sm">
      {{ error }}
    </div>

    <div v-if="loading" class="text-text-secondary text-sm">Loading routes...</div>

    <template v-else>
      <div class="flex items-center gap-4 mb-4">
        <select
          v-model="filterGroup"
          class="bg-surface-2 border border-border-subtle rounded px-2 py-1 text-sm text-text-primary"
        >
          <option value="">All Groups</option>
          <option v-for="group in menuGroups" :key="group" :value="group">{{ group }}</option>
        </select>

        <select
          v-model="filterStatus"
          class="bg-surface-2 border border-border-subtle rounded px-2 py-1 text-sm text-text-primary"
        >
          <option value="all">All Status</option>
          <option value="enabled">Enabled</option>
          <option value="disabled">Disabled</option>
          <option value="hidden">Hidden</option>
        </select>

        <select
          v-model="filterFlagStatus"
          class="bg-surface-2 border border-border-subtle rounded px-2 py-1 text-sm text-text-primary"
        >
          <option value="all">All Flags</option>
          <option value="flagged">Has Flags</option>
          <option value="unflagged">No Flags</option>
        </select>

        <span class="text-xs text-text-tertiary">{{ filteredRoutes.length }} routes</span>
       </div>

       <RouteDefinitionList
        :routes="filteredRoutes"
        @edit="onEdit"
        @disable="onDisable"
        @enable="onEnable"
        @preview="onPreview"
      />
    </template>

    <RouteDefinitionEditor
      v-if="showEditor"
      :route="editingRoute"
      @save="onSave"
      @close="onEditorClose"
    />

    <NavigationPreviewPanel
      v-if="showPreview"
      :route="selectedRoute"
      @close="showPreview = false"
    />
  </div>
</template>
