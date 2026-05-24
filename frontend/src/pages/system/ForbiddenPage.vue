<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import PageHeader from '@/components/ui/PageHeader.vue'
import { useI18nError } from '@/utils/i18n'

const route = useRoute()
const router = useRouter()
const { t } = useI18nError()

const reasonCode = computed(() => route.query.reasonCode?.toString() || route.query.reason?.toString() || 'NAV-404-HIDDEN')
const requiredPermission = computed(() => route.query.requiredPermission?.toString() || '')
const requiredEntitlement = computed(() => route.query.requiredEntitlement?.toString() || '')
const requiredFeatureFlag = computed(() => route.query.requiredFeatureFlag?.toString() || '')
const userMessage = computed(() => route.query.message?.toString() || '')

const diagnosticInfo = computed(() => {
  return JSON.stringify({
    reasonCode: reasonCode.value,
    requiredPermission: requiredPermission.value || undefined,
    requiredEntitlement: requiredEntitlement.value || undefined,
    requiredFeatureFlag: requiredFeatureFlag.value || undefined,
    path: route.path,
    timestamp: new Date().toISOString(),
  }, null, 2)
})

const requirements = computed(() => {
  const items: string[] = []
  if (requiredPermission.value) items.push(`Permission: ${requiredPermission.value}`)
  if (requiredEntitlement.value) items.push(`Entitlement: ${requiredEntitlement.value}`)
  if (requiredFeatureFlag.value) items.push(`Feature Flag: ${requiredFeatureFlag.value}`)
  return items
})

function goToDashboard() {
  router.push('/me')
}

function contactAdmin() {
  window.location.href = 'mailto:admin@media-platform.local?subject=Access Request'
}

function copyDiagnostic() {
  navigator.clipboard.writeText(diagnosticInfo.value).catch(() => {})
}
</script>

<template>
  <div class="flex-1 overflow-y-auto layout-content-padded">
    <PageHeader title="Access Denied" />

    <div class="max-w-lg mx-auto mt-xl">
      <div class="c-card" role="alert">
        <div class="c-card-body flex flex-col items-center text-center py-xl">
          <div class="text-5xl mb-lg" aria-hidden="true">🚫</div>

          <h1 class="text-xl font-semibold text-text-primary mb-sm">Access Denied</h1>

          <p class="text-sm text-text-secondary mb-md max-w-sm">
            {{ userMessage || t(reasonCode, 'You do not have access to this page.') }}
          </p>

          <div v-if="requirements.length > 0" class="w-full max-w-xs mb-md">
            <div class="text-xs font-medium text-text-muted mb-sm">Required to access:</div>
            <ul class="space-y-xs text-left">
              <li v-for="(req, i) in requirements" :key="i" class="flex items-center gap-xs text-sm text-text-secondary">
                <span class="w-1.5 h-1.5 rounded-full bg-danger-500 flex-shrink-0" aria-hidden="true" />
                {{ req }}
              </li>
            </ul>
          </div>

          <div class="flex items-center gap-sm mb-md">
            <span class="text-xs text-text-muted">Error Code:</span>
            <code class="text-xs font-mono bg-bg-surface px-sm py-xs rounded text-text-primary">{{ reasonCode }}</code>
          </div>

          <div class="flex items-center gap-md pt-md border-t border-default">
            <button class="theme-btn theme-btn-primary" @click="goToDashboard">
              Go to Dashboard
            </button>
            <button class="theme-btn theme-btn-secondary" @click="contactAdmin">
              Contact Admin
            </button>
            <button class="theme-btn theme-btn-ghost" title="Copy diagnostic info" @click="copyDiagnostic">
              clipboard Copy Info
            </button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
