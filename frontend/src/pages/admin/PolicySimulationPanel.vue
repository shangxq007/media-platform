<script setup lang="ts">
import { ref } from 'vue'
import { PolicyAdminAPI } from '@/api/admin/policy-admin'
import type { ABACPolicy, PolicySimulationContext, PolicySimulationResult } from '@/api/admin/policy-admin'
import EmptyState from '@/components/ui/EmptyState.vue'
import StatusBadge from '@/components/ui/StatusBadge.vue'

const props = defineProps<{
  policies: ABACPolicy[]
}>()

const selectedPolicyCode = ref('')
const context = ref<PolicySimulationContext>({
  user: '',
  role: '',
  tenant: '',
  workspace: '',
  resource: '',
  action: '',
  tier: '',
  region: '',
})

const simulating = ref(false)
const result = ref<PolicySimulationResult | null>(null)
const error = ref<string | null>(null)

function parseContext(): PolicySimulationContext {
  const parsed: PolicySimulationContext = {}
  for (const [key, value] of Object.entries(context.value)) {
    if (value && String(value).trim()) {
      (parsed as Record<string, string>)[key] = String(value).trim()
    }
  }
  return parsed
}

async function simulate() {
  if (!selectedPolicyCode.value) return
  simulating.value = true
  error.value = null
  result.value = null
  try {
    result.value = await PolicyAdminAPI.simulatePolicy({
      policyCode: selectedPolicyCode.value,
      context: parseContext(),
    })
  } catch (err) {
    error.value = err instanceof Error ? err.message : String(err)
  } finally {
    simulating.value = false
  }
}

function resetContext() {
  context.value = { user: '', role: '', tenant: '', workspace: '', resource: '', action: '', tier: '', region: '' }
}

function decisionVariant(decision: string): 'success' | 'warning' | 'danger' | 'info' | 'neutral' {
  switch (decision) {
    case 'ALLOW': return 'success'
    case 'DENY': return 'danger'
    case 'REQUIRE_REVIEW': return 'warning'
    case 'DEGRADE': return 'info'
    case 'WARN': return 'warning'
    default: return 'neutral'
  }
}
</script>

<template>
  <div class="space-y-6">
    <EmptyState
      v-if="props.policies.length === 0"
      icon="file-text"
      title="No policies available"
      description="Create policies first to use the simulation panel."
    />

    <template v-else>
      <div class="bg-surface-2 border border-border-subtle rounded-lg p-4">
        <h2 class="text-sm font-semibold text-text-primary mb-3">Select Policy</h2>
        <select
          v-model="selectedPolicyCode"
          class="w-full bg-surface-0 border border-border-subtle rounded px-3 py-1.5 text-sm text-text-primary"
        >
          <option value="">Choose a policy...</option>
          <option v-for="policy in props.policies" :key="policy.policyId" :value="policy.code">
            {{ policy.name }} ({{ policy.code }}) — {{ policy.status }}
          </option>
        </select>
      </div>

      <div class="bg-surface-2 border border-border-subtle rounded-lg p-4">
        <div class="flex items-center justify-between mb-3">
          <h2 class="text-sm font-semibold text-text-primary">Simulation Context</h2>
          <button class="text-xs text-text-secondary hover:text-white" @click="resetContext">Reset</button>
        </div>
        <div class="grid grid-cols-4 gap-3">
          <div>
            <label class="block text-xs text-text-secondary mb-1">User</label>
            <input v-model="context.user" type="text" class="w-full bg-surface-0 border border-border-subtle rounded px-2 py-1.5 text-sm text-text-primary" placeholder="user-1" />
          </div>
          <div>
            <label class="block text-xs text-text-secondary mb-1">Role</label>
            <input v-model="context.role" type="text" class="w-full bg-surface-0 border border-border-subtle rounded px-2 py-1.5 text-sm text-text-primary" placeholder="ADMIN" />
          </div>
          <div>
            <label class="block text-xs text-text-secondary mb-1">Tenant</label>
            <input v-model="context.tenant" type="text" class="w-full bg-surface-0 border border-border-subtle rounded px-2 py-1.5 text-sm text-text-primary" placeholder="Tenant ID" />
          </div>
          <div>
            <label class="block text-xs text-text-secondary mb-1">Workspace</label>
            <input v-model="context.workspace" type="text" class="w-full bg-surface-0 border border-border-subtle rounded px-2 py-1.5 text-sm text-text-primary" placeholder="workspace-1" />
          </div>
          <div>
            <label class="block text-xs text-text-secondary mb-1">Resource</label>
            <input v-model="context.resource" type="text" class="w-full bg-surface-0 border border-border-subtle rounded px-2 py-1.5 text-sm text-text-primary" placeholder="render-job:123" />
          </div>
          <div>
            <label class="block text-xs text-text-secondary mb-1">Action</label>
            <input v-model="context.action" type="text" class="w-full bg-surface-0 border border-border-subtle rounded px-2 py-1.5 text-sm text-text-primary" placeholder="CREATE" />
          </div>
          <div>
            <label class="block text-xs text-text-secondary mb-1">Tier</label>
            <select v-model="context.tier" class="w-full bg-surface-0 border border-border-subtle rounded px-2 py-1.5 text-sm text-text-primary">
              <option value="">Any</option>
              <option value="FREE">Free</option>
              <option value="PRO">Pro</option>
              <option value="TEAM">Team</option>
              <option value="ENTERPRISE">Enterprise</option>
            </select>
          </div>
          <div>
            <label class="block text-xs text-text-secondary mb-1">Region</label>
            <input v-model="context.region" type="text" class="w-full bg-surface-0 border border-border-subtle rounded px-2 py-1.5 text-sm text-text-primary" placeholder="us-east-1" />
          </div>
        </div>
        <button
          class="mt-4 px-4 py-1.5 bg-blue-600 hover:bg-blue-500 text-white text-sm rounded transition-colors disabled:opacity-50"
          :disabled="!selectedPolicyCode || simulating"
          @click="simulate"
        >
          {{ simulating ? 'Simulating...' : 'Simulate' }}
        </button>
      </div>

      <div v-if="error" class="p-3 bg-danger-muted border border-danger rounded-lg text-danger text-sm">
        {{ error }}
      </div>

      <template v-if="result">
        <!-- Decision Result -->
        <div class="bg-surface-2 border rounded-lg p-4" :class="result.decision === 'ALLOW' ? 'border-success' : result.decision === 'DENY' ? 'border-danger' : 'border-warning'">
          <div class="flex items-center justify-between mb-3">
            <h2 class="text-sm font-semibold text-text-primary">Decision Result</h2>
            <StatusBadge :variant="decisionVariant(result.decision)" :label="result.decision" size="md" />
          </div>
          <p class="text-sm text-text-primary">{{ result.explanation }}</p>
        </div>

        <!-- Decision Chain -->
        <div v-if="result.decisionChain.length > 0" class="bg-surface-2 border border-border-subtle rounded-lg p-4">
          <h3 class="text-sm font-semibold text-text-primary mb-3">Decision Chain</h3>
          <div class="space-y-2">
            <div v-for="(step, idx) in result.decisionChain" :key="idx" class="flex items-center gap-3 p-2 bg-surface-0/50 rounded">
              <span class="text-xs text-text-tertiary font-mono w-6">{{ idx + 1 }}</span>
              <span class="text-xs text-text-primary flex-1">{{ step.step }}</span>
              <StatusBadge :variant="decisionVariant(step.decision)" :label="step.decision" />
              <span v-if="step.detail" class="text-xs text-text-tertiary">{{ step.detail }}</span>
            </div>
          </div>
        </div>

        <!-- Matched Rules -->
        <div v-if="result.matchedRules.length > 0" class="bg-surface-2 border border-border-subtle rounded-lg p-4">
          <h3 class="text-sm font-semibold text-text-primary mb-3">Matched Rules ({{ result.matchedRules.length }})</h3>
          <div class="space-y-2">
            <div v-for="mr in result.matchedRules" :key="mr.ruleId" class="p-3 bg-surface-0/50 rounded">
              <div class="flex items-center gap-2 mb-1">
                <span class="text-xs text-text-primary font-medium">{{ mr.ruleName }}</span>
                <StatusBadge :variant="decisionVariant(mr.effect)" :label="mr.effect" />
              </div>
              <div class="flex flex-wrap gap-1">
                <span v-for="(mc, idx) in mr.matchedConditions" :key="idx" class="text-[10px] px-1.5 py-0.5 rounded bg-success-muted text-success">
                  {{ mc }}
                </span>
              </div>
            </div>
          </div>
        </div>

        <!-- Feature Flag Results -->
        <div v-if="result.featureFlagResults.length > 0" class="bg-surface-2 border border-border-subtle rounded-lg p-4">
          <h3 class="text-sm font-semibold text-text-primary mb-3">Feature Flag Evaluations ({{ result.featureFlagResults.length }})</h3>
          <div class="space-y-1.5">
            <div v-for="ffr in result.featureFlagResults" :key="ffr.flagKey" class="flex items-center justify-between p-2 bg-surface-0/50 rounded">
              <div class="flex items-center gap-2">
                <span class="text-xs font-mono text-info">{{ ffr.flagKey }}</span>
                <span v-if="!ffr.evaluated" class="text-[10px] px-1 py-0.5 rounded bg-surface-4/20 text-text-secondary">Not evaluated</span>
              </div>
              <span
                class="text-xs px-1.5 py-0.5 rounded"
                :class="ffr.result === 'ENABLED' ? 'bg-success-muted text-success' : 'bg-danger-muted text-danger'"
              >
                {{ ffr.result }}
              </span>
            </div>
          </div>
        </div>
      </template>
    </template>
  </div>
</template>
