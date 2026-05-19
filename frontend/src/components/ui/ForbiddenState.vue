<script setup lang="ts">
import { computed } from 'vue'

const props = withDefaults(defineProps<{
  title?: string
  description?: string
  requiredPermission?: string
  requiredRole?: string
  requiredEntitlement?: string
  errorCode?: string | number
  showUpgrade?: boolean
  showContactAdmin?: boolean
}>(), {
  title: 'Access Denied',
  description: 'You do not have permission to access this page.',
  showUpgrade: true,
  showContactAdmin: true,
})

defineEmits<{
  upgrade: []
  contactAdmin: []
}>()

const requirements = computed(() => {
  const items: string[] = []
  if (props.requiredPermission) items.push(`Permission: ${props.requiredPermission}`)
  if (props.requiredRole) items.push(`Role: ${props.requiredRole}`)
  if (props.requiredEntitlement) items.push(`Entitlement: ${props.requiredEntitlement}`)
  return items
})
</script>

<template>
  <div class="c-state-page" role="alert" aria-labelledby="forbidden-title">
    <div class="c-state-icon" aria-hidden="true">
      <svg class="w-16 h-16 text-danger-500" fill="none" stroke="currentColor" viewBox="0 0 24 24">
        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5"
          d="M18.364 18.364A9 9 0 005.636 5.636m12.728 12.728A9 9 0 015.636 5.636m12.728 12.728L5.636 5.636" />
      </svg>
    </div>
    <h2 id="forbidden-title" class="c-state-title">{{ title }}</h2>
    <p class="c-state-description">{{ description }}</p>

    <div v-if="requirements.length > 0" class="c-state-requirements">
      <div class="text-xs font-medium text-text-muted mb-sm">Required to access:</div>
      <ul class="space-y-xs">
        <li v-for="(req, i) in requirements" :key="i" class="flex items-center gap-xs text-sm text-text-secondary">
          <span class="w-1.5 h-1.5 rounded-full bg-danger-500 flex-shrink-0" aria-hidden="true" />
          {{ req }}
        </li>
      </ul>
    </div>

    <div v-if="errorCode" class="mt-md flex items-center gap-sm">
      <span class="text-xs text-text-muted">Error Code:</span>
      <code class="text-xs font-mono bg-bg-surface px-sm py-xs rounded text-text-primary">{{ errorCode }}</code>
    </div>

    <div class="c-state-actions">
      <button v-if="showUpgrade" class="theme-btn theme-btn-primary" @click="$emit('upgrade')">
        Upgrade Plan
      </button>
      <button v-if="showContactAdmin" class="theme-btn theme-btn-secondary" @click="$emit('contactAdmin')">
        Contact Admin
      </button>
      <router-link to="/" class="theme-btn theme-btn-ghost">
        Go Home
      </router-link>
    </div>
  </div>
</template>
