<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'

const props = defineProps<{
  title: string
  subtitle?: string
  /** When true, always show the Dashboard back link (tools pages). When false, never show. When unset, auto from route. */
  showDashboardBack?: boolean
}>()

defineSlots<{
  breadcrumb?: () => unknown
  actions?: () => unknown
  extra?: () => unknown
}>()

const route = useRoute()
const router = useRouter()

const showBack = computed(() => {
  if (props.showDashboardBack === true) return true
  if (props.showDashboardBack === false) return false
  const path = route.path
  if (path.startsWith('/admin')) return false
  if (path === '/me') return false
  return path.startsWith('/me/') || path.startsWith('/workspace/') || path.startsWith('/prompts') || path.startsWith('/effect-packs')
})

function goDashboard() {
  router.push('/me')
}
</script>

<template>
  <div class="page-header">
    <div class="flex-1 min-w-0">
      <slot name="breadcrumb" />
      <button
        v-if="showBack"
        type="button"
        class="text-xs text-text-muted hover:text-primary-500 mb-xs transition-colors"
        @click="goDashboard"
      >
        ← Dashboard
      </button>
      <h1 class="page-title">{{ title }}</h1>
      <p v-if="subtitle" class="page-subtitle">{{ subtitle }}</p>
    </div>
    <div class="page-actions">
      <slot name="actions" />
    </div>
    <slot name="extra" />
  </div>
</template>
