<script setup lang="ts">
import { ref, watch, computed } from 'vue'
import type { FrontendRouteDefinition } from '@/types/routing'

const props = defineProps<{
  route: FrontendRouteDefinition | null
}>()

const emit = defineEmits<{
  save: [data: Partial<FrontendRouteDefinition>]
  close: []
}>()

const form = ref({
  routeKey: '',
  path: '',
  componentKey: '',
  title: '',
  description: '',
  menuGroup: '',
  icon: '',
  order: 0,
  parentRouteKey: '',
  requiredPermissions: '',
  requiredRoles: '',
  requiredEntitlements: '',
  requiredTier: '',
  requiredFeatures: '',
  requiredFeatureFlags: '',
  supportedSources: '',
  visible: true,
  enabled: true
})

const isEditing = computed(() => props.route != null)

watch(() => props.route, (route) => {
  if (route) {
    form.value = {
      routeKey: route.routeKey,
      path: route.path,
      componentKey: route.componentKey,
      title: route.title,
      description: route.description ?? '',
      menuGroup: route.menuGroup ?? '',
      icon: route.icon ?? '',
      order: route.order ?? 0,
      parentRouteKey: route.parentRouteKey ?? '',
      requiredPermissions: route.requiredPermissions?.join(', ') ?? '',
      requiredRoles: route.requiredRoles?.join(', ') ?? '',
      requiredEntitlements: route.requiredEntitlements?.join(', ') ?? '',
      requiredTier: route.requiredTier ?? '',
      requiredFeatures: route.requiredFeatures?.join(', ') ?? '',
      requiredFeatureFlags: (route as any).requiredFeatureFlags?.join(', ') ?? '',
      supportedSources: route.supportedSources?.join(', ') ?? '',
      visible: route.visible !== false,
      enabled: route.enabled !== false,
    }
  } else {
    form.value = {
      routeKey: '', path: '', componentKey: '', title: '', description: '',
      menuGroup: '', icon: '', order: 0, parentRouteKey: '',
      requiredPermissions: '', requiredRoles: '', requiredEntitlements: '',
      requiredTier: '', requiredFeatures: '', requiredFeatureFlags: '',
      supportedSources: '', visible: true, enabled: true,
    }
  }
}, { immediate: true })

function parseList(value: string): string[] | undefined {
  const items = value.split(',').map(s => s.trim()).filter(Boolean)
  return items.length > 0 ? items : undefined
}

function handleSave() {
  const data: Record<string, unknown> = {
    path: form.value.path,
    componentKey: form.value.componentKey,
    title: form.value.title,
    visible: form.value.visible,
    enabled: form.value.enabled
  }

  if (form.value.routeKey && !isEditing.value) data.routeKey = form.value.routeKey
  if (form.value.description) data.description = form.value.description
  if (form.value.menuGroup) data.menuGroup = form.value.menuGroup
  if (form.value.icon) data.icon = form.value.icon
  data.order = form.value.order
  if (form.value.parentRouteKey) data.parentRouteKey = form.value.parentRouteKey
  if (form.value.requiredPermissions) data.requiredPermissions = parseList(form.value.requiredPermissions)
  if (form.value.requiredRoles) data.requiredRoles = parseList(form.value.requiredRoles)
  if (form.value.requiredEntitlements) data.requiredEntitlements = parseList(form.value.requiredEntitlements)
  if (form.value.requiredTier) data.requiredTier = form.value.requiredTier
  if (form.value.requiredFeatures) data.requiredFeatures = parseList(form.value.requiredFeatures)
  if (form.value.requiredFeatureFlags) (data as any).requiredFeatureFlags = parseList(form.value.requiredFeatureFlags)
  if (form.value.supportedSources) data.supportedSources = parseList(form.value.supportedSources)

  emit('save', data)
}
</script>

<template>
  <div class="fixed inset-0 z-50 flex items-center justify-center bg-black/60" @click.self="emit('close')">
    <div class="bg-surface-2 border border-border-subtle rounded-xl shadow-2xl w-full max-w-2xl max-h-[90vh] overflow-y-auto">
      <div class="flex items-center justify-between px-6 py-4 border-b border-border-subtle">
        <h2 class="text-lg font-semibold text-white">
          {{ isEditing ? 'Edit Route' : 'Create Route' }}
        </h2>
        <button class="text-text-secondary hover:text-white text-xl leading-none" @click="emit('close')">×</button>
      </div>

      <div class="px-6 py-4 space-y-4">
        <div class="grid grid-cols-2 gap-4">
          <div>
            <label class="block text-xs text-text-secondary mb-1">Route Key *</label>
            <input
              v-model="form.routeKey"
              type="text"
              :disabled="isEditing"
              class="w-full bg-surface-0 border border-border-subtle rounded px-3 py-1.5 text-sm text-text-primary disabled:opacity-50"
              placeholder="e.g. my-route"
            />
          </div>
          <div>
            <label class="block text-xs text-text-secondary mb-1">Path *</label>
            <input
              v-model="form.path"
              type="text"
              class="w-full bg-surface-0 border border-border-subtle rounded px-3 py-1.5 text-sm text-text-primary"
              placeholder="e.g. /my-route"
            />
          </div>
        </div>

        <div class="grid grid-cols-2 gap-4">
          <div>
            <label class="block text-xs text-text-secondary mb-1">Component Key *</label>
            <input
              v-model="form.componentKey"
              type="text"
              class="w-full bg-surface-0 border border-border-subtle rounded px-3 py-1.5 text-sm text-text-primary"
              placeholder="e.g. MyPage"
            />
          </div>
          <div>
            <label class="block text-xs text-text-secondary mb-1">Title *</label>
            <input
              v-model="form.title"
              type="text"
              class="w-full bg-surface-0 border border-border-subtle rounded px-3 py-1.5 text-sm text-text-primary"
              placeholder="e.g. My Page"
            />
          </div>
        </div>

        <div>
          <label class="block text-xs text-text-secondary mb-1">Description</label>
          <input
            v-model="form.description"
            type="text"
            class="w-full bg-surface-0 border border-border-subtle rounded px-3 py-1.5 text-sm text-text-primary"
            placeholder="Optional description"
          />
        </div>

        <div class="grid grid-cols-3 gap-4">
          <div>
            <label class="block text-xs text-text-secondary mb-1">Menu Group</label>
            <input
              v-model="form.menuGroup"
              type="text"
              class="w-full bg-surface-0 border border-border-subtle rounded px-3 py-1.5 text-sm text-text-primary"
              placeholder="e.g. main, admin"
            />
          </div>
          <div>
            <label class="block text-xs text-text-secondary mb-1">Icon</label>
            <input
              v-model="form.icon"
              type="text"
              class="w-full bg-surface-0 border border-border-subtle rounded px-3 py-1.5 text-sm text-text-primary"
              placeholder="e.g. 📊"
            />
          </div>
          <div>
            <label class="block text-xs text-text-secondary mb-1">Order</label>
            <input
              v-model.number="form.order"
              type="number"
              class="w-full bg-surface-0 border border-border-subtle rounded px-3 py-1.5 text-sm text-text-primary"
            />
          </div>
        </div>

        <div>
          <label class="block text-xs text-text-secondary mb-1">Parent Route Key</label>
          <input
            v-model="form.parentRouteKey"
            type="text"
            class="w-full bg-surface-0 border border-border-subtle rounded px-3 py-1.5 text-sm text-text-primary"
            placeholder="Optional parent route"
          />
        </div>

        <div class="border-t border-border-subtle pt-4">
          <h3 class="text-sm font-medium text-text-primary mb-3">Access Control</h3>
          <div class="space-y-3">
            <div>
              <label class="block text-xs text-text-secondary mb-1">Required Permissions (comma-separated)</label>
              <input
                v-model="form.requiredPermissions"
                type="text"
                class="w-full bg-surface-0 border border-border-subtle rounded px-3 py-1.5 text-sm text-text-primary"
                placeholder="e.g. config:read, config:write"
              />
            </div>
            <div>
              <label class="block text-xs text-text-secondary mb-1">Required Roles (comma-separated)</label>
              <input
                v-model="form.requiredRoles"
                type="text"
                class="w-full bg-surface-0 border border-border-subtle rounded px-3 py-1.5 text-sm text-text-primary"
                placeholder="e.g. ADMIN, TENANT_ADMIN"
              />
            </div>
            <div class="grid grid-cols-2 gap-4">
              <div>
                <label class="block text-xs text-text-secondary mb-1">Required Tier</label>
                <select
                  v-model="form.requiredTier"
                  class="w-full bg-surface-0 border border-border-subtle rounded px-3 py-1.5 text-sm text-text-primary"
                >
                  <option value="">Any</option>
                  <option value="FREE">Free</option>
                  <option value="BASIC">Basic</option>
                  <option value="STANDARD">Standard</option>
                  <option value="PROFESSIONAL">Professional</option>
                  <option value="ENTERPRISE">Enterprise</option>
                </select>
              </div>
             <div>
               <label class="block text-xs text-text-secondary mb-1">Supported Sources</label>
               <input
                 v-model="form.supportedSources"
                 type="text"
                 class="w-full bg-surface-0 border border-border-subtle rounded px-3 py-1.5 text-sm text-text-primary"
                 placeholder="e.g. WEB, ADMIN"
               />
             </div>
             <div>
               <label class="block text-xs text-text-secondary mb-1">Required Feature Flags (comma-separated)</label>
               <input
                 v-model="form.requiredFeatureFlags"
                 type="text"
                 class="w-full bg-surface-0 border border-border-subtle rounded px-3 py-1.5 text-sm text-text-primary"
                 placeholder="e.g. new-dashboard-v2, beta-ui"
               />
             </div>
           </div>
         </div>
       </div>

       <div class="border-t border-border-subtle pt-4">
         <h3 class="text-sm font-medium text-text-primary mb-3">Visibility</h3>
          <div class="flex items-center gap-6">
            <label class="flex items-center gap-2 text-sm text-text-primary cursor-pointer">
              <input v-model="form.visible" type="checkbox" class="rounded bg-surface-0 border-border-subtle" />
              Visible
            </label>
            <label class="flex items-center gap-2 text-sm text-text-primary cursor-pointer">
              <input v-model="form.enabled" type="checkbox" class="rounded bg-surface-0 border-border-subtle" />
              Enabled
            </label>
          </div>
        </div>
      </div>

      <div class="flex items-center justify-end gap-3 px-6 py-4 border-t border-border-subtle">
        <button
          class="px-4 py-1.5 text-sm text-text-secondary hover:text-white border border-border-default rounded-lg transition-colors"
          @click="emit('close')"
        >
          Cancel
        </button>
        <button
          class="px-4 py-1.5 text-sm bg-blue-600 hover:bg-blue-500 text-white rounded-lg transition-colors"
          @click="handleSave"
        >
          {{ isEditing ? 'Save Changes' : 'Create Route' }}
        </button>
      </div>
    </div>
  </div>
</template>
