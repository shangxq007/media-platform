<script setup lang="ts">
import { ref, watch } from 'vue'
import { RouteManagementClient } from '@/api/navigation'
import type { FrontendRouteDefinition, NavigationProfile, NavigationPreviewRequest } from '@/types/routing'

const props = defineProps<{
  route: FrontendRouteDefinition | null
}>()

const emit = defineEmits<{
  close: []
}>()

const previewLoading = ref(false)
const previewResult = ref<NavigationProfile | null>(null)
const previewError = ref<string | null>(null)

const previewContext = ref<NavigationPreviewRequest>({
  source: 'WEB',
  tier: 'FREE',
  roles: [],
  permissions: [],
  features: [],
  entitlements: []
})

const rolesInput = ref('')
const permissionsInput = ref('')
const featuresInput = ref('')
const entitlementsInput = ref('')

watch(() => props.route, () => {
  previewResult.value = null
  previewError.value = null
  previewContext.value = {
    source: 'WEB',
    tier: 'FREE',
    roles: [],
    permissions: [],
    features: [],
    entitlements: []
  }
  rolesInput.value = ''
  permissionsInput.value = ''
  featuresInput.value = ''
  entitlementsInput.value = ''
}, { immediate: true })

async function runPreview() {
  previewLoading.value = true
  previewError.value = null
  previewResult.value = null

  const request: NavigationPreviewRequest = {
    source: previewContext.value.source,
    tier: previewContext.value.tier,
    roles: rolesInput.value ? rolesInput.value.split(',').map(s => s.trim()).filter(Boolean) : [],
    permissions: permissionsInput.value ? permissionsInput.value.split(',').map(s => s.trim()).filter(Boolean) : [],
    features: featuresInput.value ? featuresInput.value.split(',').map(s => s.trim()).filter(Boolean) : [],
    entitlements: entitlementsInput.value ? entitlementsInput.value.split(',').map(s => s.trim()).filter(Boolean) : []
  }

  try {
    previewResult.value = await RouteManagementClient.previewNavigation(request)
  } catch (err) {
    previewError.value = err instanceof Error ? err.message : String(err)
  } finally {
    previewLoading.value = false
  }
}
</script>

<template>
  <div class="fixed inset-0 z-50 flex items-center justify-center bg-black/60" @click.self="emit('close')">
    <div class="bg-gray-800 border border-gray-700 rounded-xl shadow-2xl w-full max-w-3xl max-h-[90vh] overflow-y-auto">
      <div class="flex items-center justify-between px-6 py-4 border-b border-gray-700">
        <h2 class="text-lg font-semibold text-white">Navigation Preview</h2>
        <button class="text-gray-400 hover:text-white text-xl leading-none" @click="emit('close')">×</button>
      </div>

      <div class="px-6 py-4 space-y-4">
        <div v-if="route" class="bg-gray-900 border border-gray-700 rounded-lg p-3">
          <div class="text-xs text-gray-400 mb-1">Previewing for route:</div>
          <div class="text-sm font-mono text-blue-300">{{ route.routeKey }}</div>
          <div class="text-xs text-gray-500 mt-1">{{ route.path }} — {{ route.title }}</div>
        </div>

        <div class="grid grid-cols-2 gap-4">
          <div>
            <label class="block text-xs text-gray-400 mb-1">Source</label>
            <select
              v-model="previewContext.source"
              class="w-full bg-gray-900 border border-gray-700 rounded px-3 py-1.5 text-sm text-gray-200"
            >
              <option value="WEB">WEB</option>
              <option value="MCP">MCP</option>
              <option value="ADMIN">ADMIN</option>
              <option value="INTERNAL">INTERNAL</option>
            </select>
          </div>
          <div>
            <label class="block text-xs text-gray-400 mb-1">Tier</label>
            <select
              v-model="previewContext.tier"
              class="w-full bg-gray-900 border border-gray-700 rounded px-3 py-1.5 text-sm text-gray-200"
            >
              <option value="FREE">Free</option>
              <option value="BASIC">Basic</option>
              <option value="STANDARD">Standard</option>
              <option value="PROFESSIONAL">Professional</option>
              <option value="ENTERPRISE">Enterprise</option>
            </select>
          </div>
        </div>

        <div>
          <label class="block text-xs text-gray-400 mb-1">Roles (comma-separated)</label>
          <input
            v-model="rolesInput"
            type="text"
            class="w-full bg-gray-900 border border-gray-700 rounded px-3 py-1.5 text-sm text-gray-200"
            placeholder="e.g. ADMIN, TENANT_ADMIN"
          />
        </div>

        <div>
          <label class="block text-xs text-gray-400 mb-1">Permissions (comma-separated)</label>
          <input
            v-model="permissionsInput"
            type="text"
            class="w-full bg-gray-900 border border-gray-700 rounded px-3 py-1.5 text-sm text-gray-200"
            placeholder="e.g. config:read, tenant:read"
          />
        </div>

        <div>
          <label class="block text-xs text-gray-400 mb-1">Features (comma-separated)</label>
          <input
            v-model="featuresInput"
            type="text"
            class="w-full bg-gray-900 border border-gray-700 rounded px-3 py-1.5 text-sm text-gray-200"
            placeholder="e.g. ai-features, advanced-export"
          />
        </div>

        <button
          class="w-full py-2 text-sm bg-blue-600 hover:bg-blue-500 text-white rounded-lg transition-colors disabled:opacity-50"
          :disabled="previewLoading"
          @click="runPreview"
        >
          {{ previewLoading ? 'Running preview...' : 'Run Preview' }}
        </button>

        <div v-if="previewError" class="p-3 bg-red-900/30 border border-red-700 rounded-lg text-red-300 text-sm">
          {{ previewError }}
        </div>

        <div v-if="previewResult" class="space-y-3">
          <h3 class="text-sm font-medium text-gray-300">Preview Result</h3>

          <div v-for="(routes, group) in previewResult.menuGroups" :key="group" class="bg-gray-900 border border-gray-700 rounded-lg p-3">
            <div class="text-xs font-medium text-gray-400 mb-2 uppercase">{{ group }}</div>
            <div class="space-y-1">
              <div
                v-for="r in routes"
                :key="r.routeKey"
                class="flex items-center justify-between text-sm"
              >
                <div class="flex items-center gap-2">
                  <span
                    class="w-2 h-2 rounded-full"
                    :class="{
                      'bg-green-400': r.visible && r.enabled,
                      'bg-red-400': r.visible && !r.enabled,
                      'bg-gray-600': !r.visible
                    }"
                  />
                  <span :class="!r.visible ? 'text-gray-600' : r.enabled ? 'text-gray-200' : 'text-gray-400'">
                    {{ r.title }}
                  </span>
                  <span class="text-xs font-mono text-gray-500">{{ r.path }}</span>
                </div>
                <span v-if="!r.enabled && r.reasonCode" class="text-xs text-yellow-400">
                  {{ r.reasonCode }}
                </span>
              </div>
              <div v-if="routes.length === 0" class="text-xs text-gray-600">No visible routes</div>
            </div>
          </div>

          <div v-if="Object.keys(previewResult.menuGroups).length === 0" class="text-sm text-gray-500">
            No visible routes for this context.
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
