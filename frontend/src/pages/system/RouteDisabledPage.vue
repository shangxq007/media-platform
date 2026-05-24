<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import PageHeader from '@/components/ui/PageHeader.vue'
import { useI18nError } from '@/utils/i18n'

const route = useRoute()
const router = useRouter()
const { t } = useI18nError()

const routeKey = computed(() => route.query.routeKey?.toString() || '')
const pageName = computed(() => route.query.pageName?.toString() || routeKey.value || 'This page')
const reasonCode = computed(() => route.query.reasonCode?.toString() || route.query.reason?.toString() || 'NAV-403-DISABLED')
const requiredPermission = computed(() => route.query.requiredPermission?.toString() || '')
const requiredEntitlement = computed(() => route.query.requiredEntitlement?.toString() || '')
const requiredFeatureFlag = computed(() => route.query.requiredFeatureFlag?.toString() || '')
const requiredUpgrade = computed(() => route.query.upgrade?.toString() || route.query.requiredUpgrade?.toString() || '')
const upgradeOptions = computed(() => {
  const raw = route.query.upgradeOptions?.toString() || ''
  return raw ? raw.split(',').filter(Boolean) : []
})

const diagnosticInfo = computed(() => {
  return JSON.stringify({
    routeKey: routeKey.value || undefined,
    reasonCode: reasonCode.value,
    requiredPermission: requiredPermission.value || undefined,
    requiredEntitlement: requiredEntitlement.value || undefined,
    requiredFeatureFlag: requiredFeatureFlag.value || undefined,
    requiredUpgrade: requiredUpgrade.value || undefined,
    upgradeOptions: upgradeOptions.value.length > 0 ? upgradeOptions.value : undefined,
    path: route.path,
    timestamp: new Date().toISOString(),
  }, null, 2)
})

function goToDashboard() {
  router.push('/me')
}

function upgradePlan() {
  router.push('/me/capabilities')
}

function copyDiagnostic() {
  navigator.clipboard.writeText(diagnosticInfo.value).catch(() => {})
}
</script>

<template>
  <div class="flex-1 overflow-y-auto layout-content-padded">
    <PageHeader title="Feature Unavailable" />

    <div class="max-w-lg mx-auto mt-xl">
      <div class="c-card" role="alert">
        <div class="c-card-body flex flex-col items-center text-center py-xl">
          <div class="text-5xl mb-lg" aria-hidden="true">lock</div>

          <h1 class="text-xl font-semibold text-text-primary mb-sm">{{ pageName }} is unavailable</h1>

          <p class="text-sm text-text-secondary mb-md max-w-sm">
            {{ t(reasonCode, 'This feature is currently disabled for your account.') }}
          </p>

          <div v-if="requiredUpgrade" class="w-full max-w-xs mb-md">
            <div class="c-card bg-warning-500/5 border-warning-500/20">
              <div class="c-card-body py-sm">
                <div class="text-xs text-warning-600 font-medium">
                  Requires: {{ requiredUpgrade }}
                </div>
              </div>
            </div>
          </div>

          <div v-if="upgradeOptions.length > 0" class="w-full max-w-xs mb-md">
            <div class="text-xs font-medium text-text-muted mb-sm">Available plans:</div>
            <div class="flex flex-wrap gap-xs justify-center">
              <span v-for="opt in upgradeOptions" :key="opt" class="theme-badge bg-primary-500/10 text-primary-400 text-xs">
                {{ opt }}
              </span>
            </div>
          </div>

          <div class="flex items-center gap-sm mb-md">
            <span class="text-xs text-text-muted">Error Code:</span>
            <code class="text-xs font-mono bg-bg-surface px-sm py-xs rounded text-text-primary">{{ reasonCode }}</code>
          </div>

          <div class="flex items-center gap-md pt-md border-t border-default">
            <button class="theme-btn theme-btn-primary" @click="goToDashboard">
              Go to Dashboard
            </button>
            <button v-if="requiredUpgrade || upgradeOptions.length > 0" class="theme-btn theme-btn-secondary" @click="upgradePlan">
              Upgrade Plan
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
