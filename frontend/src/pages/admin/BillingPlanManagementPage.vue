<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { BillingAdminAPI } from '@/api/admin/billing-admin'
import type { BillingPlan } from '@/types'

const loading = ref(true)
const plans = ref<BillingPlan[]>([])
const showEditor = ref(false)
const editingPlan = ref<BillingPlan | null>(null)
const flaggedPlans = ref<Map<string, string[]>>(new Map())

onMounted(async () => {
  await loadPlans()
  try {
    const map = new Map<string, string[]>()
    for (const plan of plans.value) {
      const flagKeys = plan.featureFlagKeys
      if (flagKeys?.length) {
        map.set(plan.planId, flagKeys)
      }
    }
    flaggedPlans.value = map
  } catch { /* backend may not be running */ }
})

async function loadPlans() {
  loading.value = true
  try {
    plans.value = await BillingAdminAPI.getBillingPlans()
  } catch { /* backend may not be running */ }
  loading.value = false
}

function newPlan() {
  editingPlan.value = null
  showEditor.value = true
}

function editPlan(plan: BillingPlan) {
  editingPlan.value = plan
  showEditor.value = true
}

async function archivePlan(planId: string) {
  await BillingAdminAPI.archiveBillingPlan(planId)
  await loadPlans()
}

function tierBadgeClass(tier: string): string {
  switch (tier) {
    case 'FREE': return 'bg-surface-4'
    case 'PRO': return 'bg-blue-600'
    case 'TEAM': return 'bg-purple-600'
    case 'ENTERPRISE': return 'bg-amber-600'
    default: return 'bg-surface-4'
  }
}
</script>

<template>
  <div class="flex-1 overflow-y-auto p-6 space-y-6">
    <div class="flex items-center justify-between">
      <h1 class="text-xl font-bold text-white">Billing Plans</h1>
      <div class="flex gap-2">
        <button class="px-3 py-1.5 bg-blue-600 hover:bg-blue-500 text-sm rounded text-white" @click="newPlan">+ New Plan</button>
        <button class="px-3 py-1.5 bg-surface-3 hover:bg-surface-4 text-sm rounded text-white" @click="loadPlans">Refresh</button>
      </div>
    </div>

    <div v-if="loading" class="text-text-secondary text-sm">Loading...</div>
    <div v-else-if="plans.length === 0" class="text-text-tertiary text-sm">No billing plans</div>
    <div v-else class="grid grid-cols-2 gap-6">
      <div v-for="plan in plans" :key="plan.planId" class="bg-surface-2 border border-border-subtle rounded-lg p-4">
        <div class="flex items-center justify-between mb-2">
          <div class="flex items-center gap-2">
            <span class="px-2 py-0.5 rounded text-[10px] font-medium text-white" :class="tierBadgeClass(plan.tier)">{{ plan.tier }}</span>
            <h3 class="text-sm font-semibold text-white">{{ plan.name }}</h3>
          </div>
          <div class="flex items-center gap-1.5">
            <span v-if="plan.isActive" class="px-1.5 py-0.5 rounded bg-success-muted text-success text-[10px]">Active</span>
            <span v-if="flaggedPlans.has(plan.planId)" class="px-1.5 py-0.5 rounded bg-accent-500/10 text-accent-300 text-[10px]">
              🚩 {{ flaggedPlans.get(plan.planId)!.length }}
            </span>
          </div>
         </div>
        <p class="text-xs text-text-secondary mb-3">{{ plan.description }}</p>
        <div class="text-lg font-bold text-white mb-3">
          ${{ plan.monthlyPrice.toFixed(2) }}<span class="text-xs text-text-tertiary font-normal">/mo</span>
          <span class="text-sm text-text-tertiary ml-2">${{ plan.annualPrice.toFixed(2) }}/yr</span>
        </div>
        <div class="grid grid-cols-2 gap-2 text-xs mb-3">
          <div class="flex justify-between"><span class="text-text-secondary">Render Min</span><span class="text-white">{{ plan.quota.renderMinutes }}</span></div>
          <div class="flex justify-between"><span class="text-text-secondary">Storage</span><span class="text-white">{{ plan.quota.storageGb }} GB</span></div>
          <div class="flex justify-between"><span class="text-text-secondary">Resolution</span><span class="text-white">{{ plan.quota.maxResolution }}</span></div>
          <div class="flex justify-between"><span class="text-text-secondary">GPU</span><span :class="plan.quota.gpuAllowed ? 'text-success' : 'text-danger'">{{ plan.quota.gpuAllowed ? 'Yes' : 'No' }}</span></div>
        </div>
        <div class="flex flex-wrap gap-1 mb-3">
          <span v-for="feat in plan.features.slice(0, 4)" :key="feat" class="px-1 py-0.5 rounded bg-surface-3 text-[10px] text-text-secondary">{{ feat }}</span>
        </div>
        <div class="flex gap-2">
          <button class="text-[10px] text-info hover:text-info" @click="editPlan(plan)">Edit</button>
          <button v-if="plan.isActive" class="text-[10px] text-warning hover:text-warning" @click="archivePlan(plan.planId)">Archive</button>
        </div>
      </div>
    </div>

    <div v-if="showEditor" class="fixed inset-0 bg-black/50 flex items-center justify-center z-50">
      <div class="bg-surface-2 border border-border-subtle rounded-lg p-6 w-[500px] space-y-4">
        <h2 class="text-lg font-semibold text-white">{{ editingPlan ? 'Edit Plan' : 'New Plan' }}</h2>
        <div class="text-xs text-text-tertiary">Plan editor form would go here (name, tier, prices, quota, features)</div>
        <div class="flex gap-2 pt-2 border-t border-border-subtle">
          <button class="px-4 py-2 bg-blue-600 hover:bg-blue-500 text-white text-sm rounded" @click="showEditor = false">Close</button>
        </div>
      </div>
    </div>
  </div>
</template>
