<script setup lang="ts">
import { ref, watch } from 'vue'
import type { PolicyRule, PolicyCondition, PolicyFeatureFlagCondition } from '@/api/admin/policy-admin'

const props = defineProps<{
  rule: PolicyRule | null
}>()

const emit = defineEmits<{
  save: [rule: PolicyRule]
  close: []
}>()

type ConditionOperator = PolicyCondition['operator']

const OPERATORS: ConditionOperator[] = [
  'EQUALS', 'IN', 'NOT_IN', 'GT', 'LT', 'GTE', 'LTE', 'CONTAINS', 'EXISTS'
]

const EFFECTS: PolicyRule['effect'][] = [
  'ALLOW', 'DENY', 'REQUIRE_REVIEW', 'DEGRADE', 'WARN'
]

const form = ref({
  name: '',
  effect: 'ALLOW' as PolicyRule['effect'],
  priority: 1,
  status: 'ACTIVE' as PolicyRule['status'],
})

const conditions = ref<PolicyCondition[]>([])
const featureFlagConditions = ref<PolicyFeatureFlagCondition[]>([])

watch(() => props.rule, (rule) => {
  if (rule) {
    form.value = {
      name: rule.name,
      effect: rule.effect,
      priority: rule.priority,
      status: rule.status,
    }
    conditions.value = rule.conditions ? [...rule.conditions] : []
    featureFlagConditions.value = rule.featureFlagConditions ? [...rule.featureFlagConditions] : []
  } else {
    form.value = { name: '', effect: 'ALLOW', priority: 1, status: 'ACTIVE' }
    conditions.value = []
    featureFlagConditions.value = []
  }
}, { immediate: true })

function addCondition() {
  conditions.value.push({ attribute: '', operator: 'EQUALS', value: '' })
}

function removeCondition(index: number) {
  conditions.value.splice(index, 1)
}

function addFlagCondition() {
  featureFlagConditions.value.push({ flagKey: '', expectedValue: '' })
}

function removeFlagCondition(index: number) {
  featureFlagConditions.value.splice(index, 1)
}

function handleSave() {
  const rule: PolicyRule = {
    ruleId: props.rule?.ruleId,
    name: form.value.name,
    effect: form.value.effect,
    priority: form.value.priority,
    status: form.value.status,
    conditions: conditions.value.filter(c => c.attribute && c.value),
    featureFlagConditions: featureFlagConditions.value.filter(f => f.flagKey),
  }
  emit('save', rule)
}
</script>

<template>
  <div class="fixed inset-0 z-[60] flex items-center justify-center bg-black/60" @click.self="emit('close')">
    <div class="bg-surface-2 border border-border-subtle rounded-xl shadow-2xl w-full max-w-2xl max-h-[90vh] overflow-y-auto">
      <div class="flex items-center justify-between px-6 py-4 border-b border-border-subtle">
        <h2 class="text-lg font-semibold text-white">{{ rule ? 'Edit Rule' : 'New Rule' }}</h2>
        <button class="text-text-secondary hover:text-white text-xl leading-none" @click="emit('close')">×</button>
      </div>

      <div class="px-6 py-4 space-y-4">
        <div class="grid grid-cols-3 gap-4">
          <div>
            <label class="block text-xs text-text-secondary mb-1">Rule Name *</label>
            <input
              v-model="form.name"
              type="text"
              class="w-full bg-surface-0 border border-border-subtle rounded px-3 py-1.5 text-sm text-text-primary"
              placeholder="e.g. Allow enterprise admins"
            />
          </div>
          <div>
            <label class="block text-xs text-text-secondary mb-1">Effect *</label>
            <select v-model="form.effect" class="w-full bg-surface-0 border border-border-subtle rounded px-3 py-1.5 text-sm text-text-primary">
              <option v-for="eff in EFFECTS" :key="eff" :value="eff">{{ eff }}</option>
            </select>
          </div>
          <div>
            <label class="block text-xs text-text-secondary mb-1">Priority *</label>
            <input
              v-model.number="form.priority"
              type="number"
              min="1"
              class="w-full bg-surface-0 border border-border-subtle rounded px-3 py-1.5 text-sm text-text-primary"
            />
          </div>
        </div>

        <div>
          <label class="block text-xs text-text-secondary mb-1">Status</label>
          <select v-model="form.status" class="w-48 bg-surface-0 border border-border-subtle rounded px-3 py-1.5 text-sm text-text-primary">
            <option value="ACTIVE">Active</option>
            <option value="INACTIVE">Inactive</option>
          </select>
        </div>

        <!-- Attribute Conditions -->
        <div class="border-t border-border-subtle pt-4">
          <div class="flex items-center justify-between mb-3">
            <h3 class="text-sm font-medium text-text-primary">Attribute Conditions ({{ conditions.length }})</h3>
            <button class="text-xs text-info hover:text-info" @click="addCondition">+ Add Condition</button>
          </div>
          <div v-if="conditions.length === 0" class="text-xs text-text-tertiary">No conditions — rule always matches</div>
          <div v-else class="space-y-2">
            <div v-for="(cond, idx) in conditions" :key="idx" class="flex items-center gap-2">
              <input
                v-model="cond.attribute"
                type="text"
                class="flex-1 bg-surface-0 border border-border-subtle rounded px-3 py-1.5 text-sm text-text-primary"
                placeholder="Attribute (e.g. user.role)"
              />
              <select
                v-model="cond.operator"
                class="bg-surface-0 border border-border-subtle rounded px-2 py-1.5 text-sm text-text-primary"
              >
                <option v-for="op in OPERATORS" :key="op" :value="op">{{ op }}</option>
              </select>
              <input
                v-model="cond.value"
                type="text"
                class="flex-1 bg-surface-0 border border-border-subtle rounded px-3 py-1.5 text-sm text-text-primary"
                placeholder="Value"
              />
              <button class="text-danger hover:text-danger text-sm px-2" @click="removeCondition(idx)">✕</button>
            </div>
          </div>
        </div>

        <!-- Feature Flag Conditions -->
        <div class="border-t border-border-subtle pt-4">
          <div class="flex items-center justify-between mb-3">
            <h3 class="text-sm font-medium text-text-primary">Feature Flag Conditions ({{ featureFlagConditions.length }})</h3>
            <button class="text-xs text-info hover:text-info" @click="addFlagCondition">+ Add Flag Condition</button>
          </div>
          <div v-if="featureFlagConditions.length === 0" class="text-xs text-text-tertiary">No feature flag conditions</div>
          <div v-else class="space-y-2">
            <div v-for="(fc, idx) in featureFlagConditions" :key="idx" class="flex items-center gap-2">
              <input
                v-model="fc.flagKey"
                type="text"
                class="flex-1 bg-surface-0 border border-border-subtle rounded px-3 py-1.5 text-sm text-text-primary"
                placeholder="Flag key"
              />
              <input
                v-model="fc.expectedValue"
                type="text"
                class="flex-1 bg-surface-0 border border-border-subtle rounded px-3 py-1.5 text-sm text-text-primary"
                placeholder="Expected value (e.g. true)"
              />
              <button class="text-danger hover:text-danger text-sm px-2" @click="removeFlagCondition(idx)">✕</button>
            </div>
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
          Save Rule
        </button>
      </div>
    </div>
  </div>
</template>
