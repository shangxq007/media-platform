<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import PageHeader from '@/components/ui/PageHeader.vue'

const route = useRoute()
const router = useRouter()

const routeKey = computed(() => route.query.routeKey?.toString() || '')
const pageName = computed(() => route.query.pageName?.toString() || routeKey.value || 'This feature')
const currentTier = computed(() => route.query.currentTier?.toString() || 'Free')
const requiredTier = computed(() => route.query.requiredTier?.toString() || route.query.upgrade?.toString() || '')
const reasonCode = computed(() => route.query.reasonCode?.toString() || 'NAV-403-TIER')
const upgradeOptions = computed(() => {
  const raw = route.query.upgradeOptions?.toString() || ''
  return raw ? raw.split(',').filter(Boolean) : []
})

const diagnosticInfo = computed(() => {
  return JSON.stringify({
    routeKey: routeKey.value || undefined,
    currentTier: currentTier.value || undefined,
    requiredTier: requiredTier.value || undefined,
    reasonCode: reasonCode.value,
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
    <PageHeader title="Upgrade Required" />

    <div class="max-w-lg mx-auto mt-xl">
      <div class="c-card" role="alert">
        <div class="c-card-body flex flex-col items-center text-center py-xl">
          <div class="text-5xl mb-lg" aria-hidden="true">⬆️</div>

          <h1 class="text-xl font-semibold text-text-primary mb-sm">Upgrade Required</h1>

          <p class="text-sm text-text-secondary mb-md max-w-sm">
            <strong class="text-text-primary">{{ pageName }}</strong> requires a higher subscription tier.
          </p>

          <div class="flex items-center gap-lg mb-md">
            <div class="text-center">
              <div class="text-[10px] uppercase tracking-wider text-text-muted mb-xs">Current Plan</div>
              <div class="c-card bg-bg-surface px-lg py-sm">
                <span class="text-sm font-semibold text-text-primary">{{ currentTier }}</span>
              </div>
            </div>
            <div class="text-2xl text-text-muted" aria-hidden="true">→</div>
            <div class="text-center">
              <div class="text-[10px] uppercase tracking-wider text-text-muted mb-xs">Required Plan</div>
              <div class="c-card bg-primary-500/10 border-primary-200 px-lg py-sm">
                <span class="text-sm font-semibold text-primary-400">{{ requiredTier || 'Higher Tier' }}</span>
              </div>
            </div>
          </div>

          <div v-if="upgradeOptions.length > 0" class="w-full max-w-xs mb-md">
            <div class="text-xs font-medium text-text-muted mb-sm">Available upgrade paths:</div>
            <div class="space-y-xs">
              <div v-for="opt in upgradeOptions" :key="opt" class="c-card bg-bg-surface">
                <div class="c-card-body py-sm flex items-center justify-between">
                  <span class="text-sm text-text-primary font-medium">{{ opt }}</span>
                  <button class="theme-btn theme-btn-primary theme-btn-sm" @click="upgradePlan">
                    Select
                  </button>
                </div>
              </div>
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
            <button class="theme-btn theme-btn-secondary" @click="upgradePlan">
              Upgrade Plan
            </button>
            <button class="theme-btn theme-btn-ghost" title="Copy diagnostic info" @click="copyDiagnostic">
              📋 Copy Info
            </button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
