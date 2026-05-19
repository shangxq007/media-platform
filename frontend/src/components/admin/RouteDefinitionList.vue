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
  if (route.visible === false) return { label: 'Hidden', cls: 'bg-gray-600/20 text-gray-400' }
  if (route.enabled === false) return { label: 'Disabled', cls: 'bg-red-600/20 text-red-300' }
  return { label: 'Active', cls: 'bg-green-600/20 text-green-300' }
}
</script>

<template>
  <div class="bg-gray-800 border border-gray-700 rounded-lg overflow-hidden">
    <table class="w-full text-sm">
      <thead>
        <tr class="border-b border-gray-700 text-gray-400 text-left">
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
          class="border-b border-gray-700/50 hover:bg-gray-700/30 transition-colors"
        >
          <td class="px-4 py-2 font-mono text-xs text-blue-300">{{ route.routeKey }}</td>
          <td class="px-4 py-2 font-mono text-xs text-gray-300">{{ route.path }}</td>
          <td class="px-4 py-2 text-xs text-gray-400">{{ route.componentKey }}</td>
          <td class="px-4 py-2 text-xs text-gray-400">{{ route.menuGroup ?? '—' }}</td>
          <td class="px-4 py-2 text-gray-200">{{ route.title }}</td>
           <td class="px-4 py-2">
             <span
               class="text-xs px-1.5 py-0.5 rounded"
               :class="statusBadge(route).cls"
             >
               {{ statusBadge(route).label }}
             </span>
           </td>
           <td class="px-4 py-2">
             <div v-if="(route as any)?.requiredFeatureFlags?.length" class="flex flex-wrap gap-1">
               <span
                 v-for="flag in (route as any).requiredFeatureFlags"
                 :key="flag"
                 class="text-[10px] px-1.5 py-0.5 rounded bg-purple-600/20 text-purple-300 font-mono"
               >
                 {{ flag }}
               </span>
             </div>
             <span v-else class="text-xs text-gray-600">—</span>
           </td>
           <td class="px-4 py-2 text-right">
            <div class="flex items-center justify-end gap-1">
              <button
                class="text-xs px-2 py-1 text-gray-400 hover:text-white hover:bg-gray-700 rounded transition-colors"
                title="Preview navigation"
                @click="emit('preview', route)"
              >
                👁
              </button>
              <button
                class="text-xs px-2 py-1 text-gray-400 hover:text-white hover:bg-gray-700 rounded transition-colors"
                title="Edit route"
                @click="emit('edit', route)"
              >
                ✏️
              </button>
              <button
                v-if="route.enabled !== false"
                class="text-xs px-2 py-1 text-gray-400 hover:text-red-300 hover:bg-gray-700 rounded transition-colors"
                title="Disable route"
                @click="emit('disable', route)"
              >
                🔴
              </button>
              <button
                v-else
                class="text-xs px-2 py-1 text-gray-400 hover:text-green-300 hover:bg-gray-700 rounded transition-colors"
                title="Enable route"
                @click="emit('enable', route)"
              >
                🟢
              </button>
            </div>
          </td>
        </tr>
         <tr v-if="routes.length === 0">
           <td colspan="8" class="px-4 py-8 text-center text-gray-500 text-sm">
             No routes found
           </td>
         </tr>
      </tbody>
    </table>
  </div>
</template>
