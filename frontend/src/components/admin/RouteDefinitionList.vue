<script setup lang="ts">
import type { FrontendRouteDefinition } from '@/types/routing'

defineProps<{
  routes: FrontendRouteDefinition[]
}>()

const emit = defineEmits<{
  edit: [route: FrontendRouteDefinition]
  disable: [route: FrontendRouteDefinition]
  enable: [route: FrontendRouteDefinition]
  preview: [route: FrontendRouteDefinition]
}>()

function statusBadge(route: FrontendRouteDefinition): { label: string; cls: string } {
  if (route.visible === false) return { label: 'Hidden', cls: 'bg-surface-4/20 text-text-secondary' }
  if (route.enabled === false) return { label: 'Disabled', cls: 'bg-danger-muted text-danger' }
  return { label: 'Active', cls: 'bg-success-muted text-success' }
}
</script>

<template>
  <div class="bg-surface-2 border border-border-subtle rounded-lg overflow-hidden">
    <table class="w-full text-sm">
      <thead>
        <tr class="border-b border-border-subtle text-text-secondary text-left">
          <th class="px-4 py-2.5 font-medium">Route Key</th>
          <th class="px-4 py-2.5 font-medium">Path</th>
          <th class="px-4 py-2.5 font-medium">Component</th>
          <th class="px-4 py-2.5 font-medium">Group</th>
          <th class="px-4 py-2.5 font-medium">Title</th>
           <th class="px-4 py-2.5 font-medium">Status</th>
           <th class="px-4 py-2.5 font-medium">Feature Flags</th>
           <th class="px-4 py-2.5 font-medium text-right">Actions</th>
        </tr>
      </thead>
      <tbody>
        <tr
          v-for="route in routes"
          :key="route.routeKey"
          class="border-b border-border-subtle/50 hover:bg-surface-3/30 transition-colors"
        >
          <td class="px-4 py-2 font-mono text-xs text-info">{{ route.routeKey }}</td>
          <td class="px-4 py-2 font-mono text-xs text-text-primary">{{ route.path }}</td>
          <td class="px-4 py-2 text-xs text-text-secondary">{{ route.componentKey }}</td>
          <td class="px-4 py-2 text-xs text-text-secondary">{{ route.menuGroup ?? '—' }}</td>
          <td class="px-4 py-2 text-text-primary">{{ route.title }}</td>
           <td class="px-4 py-2">
             <span
               class="text-xs px-1.5 py-0.5 rounded"
               :class="statusBadge(route).cls"
             >
               {{ statusBadge(route).label }}
             </span>
           </td>
           <td class="px-4 py-2">
             <div v-if="(route as unknown)?.requiredFeatureFlags?.length" class="flex flex-wrap gap-1">
               <span
                 v-for="flag in (route as unknown).requiredFeatureFlags"
                 :key="flag"
                 class="text-[10px] px-1.5 py-0.5 rounded bg-accent-500/10 text-accent-300 font-mono"
               >
                 {{ flag }}
               </span>
             </div>
             <span v-else class="text-xs text-text-tertiary">—</span>
           </td>
           <td class="px-4 py-2 text-right">
            <div class="flex items-center justify-end gap-1">
              <button
                class="text-xs px-2 py-1 text-text-secondary hover:text-white hover:bg-surface-3 rounded transition-colors"
                title="Preview navigation"
                @click="emit('preview', route)"
              >
                👁
              </button>
              <button
                class="text-xs px-2 py-1 text-text-secondary hover:text-white hover:bg-surface-3 rounded transition-colors"
                title="Edit route"
                @click="emit('edit', route)"
              >
                ✏️
              </button>
              <button
                v-if="route.enabled !== false"
                class="text-xs px-2 py-1 text-text-secondary hover:text-danger hover:bg-surface-3 rounded transition-colors"
                title="Disable route"
                @click="emit('disable', route)"
              >
                🔴
              </button>
              <button
                v-else
                class="text-xs px-2 py-1 text-text-secondary hover:text-success hover:bg-surface-3 rounded transition-colors"
                title="Enable route"
                @click="emit('enable', route)"
              >
                🟢
              </button>
            </div>
          </td>
        </tr>
         <tr v-if="routes.length === 0">
           <td colspan="8" class="px-4 py-8 text-center text-text-tertiary text-sm">
             No routes found
           </td>
         </tr>
      </tbody>
    </table>
  </div>
</template>
