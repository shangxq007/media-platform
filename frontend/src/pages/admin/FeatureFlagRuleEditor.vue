<script setup lang="ts">
import { ref, watch } from 'vue'
import type { FeatureFlagTargetingRule, FeatureFlagCondition } from '@/api/admin/feature-flags'

const props = defineProps<{
  rule: FeatureFlagTargetingRule | null
}>()

const emit = defineEmits<{
  save: [rule: FeatureFlagTargetingRule]
  close: []
}>()

type ConditionAttribute = FeatureFlagCondition['attribute']
type ConditionOperator = FeatureFlagCondition['operator']

const ATTRIBUTES: ConditionAttribute[] = [
  'tenant', 'workspace', 'user', 'role', 'group', 'tier', 'region', 'requestSource', 'environment'
]

const OPERATORS: ConditionOperator[] = [
  'EQUALS', 'IN', 'NOT_IN', 'GT', 'LT', 'GTE', 'LTE', 'CONTAINS'
]

const form = ref({
  name: '',
  priority: 1,
  percentage: 100,
  variantKey: '',
  startAt: '',
  endAt: '',
})

const conditions = ref<FeatureFlagCondition[]>([])

watch(() => props.rule, (rule) => {
  if (rule) {
    form.value = {
      name: rule.name,
      priority: rule.priority,
      percentage: rule.percentage,
      variantKey: rule.variantKey || '',
      startAt: rule.startAt || '',
      endAt: rule.endAt || '',
    }
    conditions.value = [...(rule.conditions || [])]
  } else {
    form.value = { name: '', priority: 1, percentage: 100, variantKey: '', startAt: '', endAt: '' }
    conditions.value = []
  }
}, { immediate: true })

function addCondition() {
  conditions.value.push({ attribute: 'tenant', operator: 'EQUALS', value: '' })
}

function removeCondition(index: number) {
  conditions.value.splice(index, 1)
}

function handleSave() {
  const rule: FeatureFlagTargetingRule = {
    ruleId: props.rule?.ruleId,
    name: form.value.name,
    priority: form.value.priority,
    percentage: form.value.percentage,
    conditions: conditions.value.filter(c => c.attribute && c.value),
    variantKey: form.value.variantKey || undefined,
    startAt: form.value.startAt || undefined,
    endAt: form.value.endAt || undefined,
  }
  emit('save', rule)
}
</script>

<template>
  <div class="fixed inset-0 z-[60] flex items-center justify-center bg-black/60" @click.self="emit('close')">
    <div class="bg-gray-800 border border-gray-700 rounded-xl shadow-2xl w-full max-w-2xl max-h-[90vh] overflow-y-auto">
      <div class="flex items-center justify-between px-6 py-4 border-b border-gray-700">
        <h2 class="text-lg font-semibold text-white">{{ rule ? 'Edit Targeting Rule' : 'New Targeting Rule' }}</h2>
        <button class="text-gray-400 hover:text-white text-xl leading-none" @click="emit('close')">×</button>
      </div>

      <div class="px-6 py-4 space-y-4">
        <div class="grid grid-cols-3 gap-4">
          <div>
            <label class="block text-xs text-gray-400 mb-1">Rule Name *</label>
            <input
              v-model="form.name"
              type="text"
              class="w-full bg-gray-900 border border-gray-700 rounded px-3 py-1.5 text-sm text-gray-200"
              placeholder="e.g. Enterprise rollout"
            />
          </div>
          <div>
            <label class="block text-xs text-gray-400 mb-1">Priority *</label>
            <input
              v-model.number="form.priority"
              type="number"
              min="1"
              class="w-full bg-gray-900 border border-gray-700 rounded px-3 py-1.5 text-sm text-gray-200"
            />
          </div>
          <div>
            <label class="block text-xs text-gray-400 mb-1">Percentage: {{ form.percentage }}%</label>
            <input
              v-model.number="form.percentage"
              type="range"
              min="0"
              max="100"
              class="w-full mt-2"
            />
          </div>
        </div>

        <div class="grid grid-cols-3 gap-4">
          <div>
            <label class="block text-xs text-gray-400 mb-1">Variant Assignment</label>
            <input
              v-model="form.variantKey"
              type="text"
              class="w-full bg-gray-900 border border-gray-700 rounded px-3 py-1.5 text-sm text-gray-200"
              placeholder="Optional variant key"
            />
          </div>
          <div>
            <label class="block text-xs text-gray-400 mb-1">Start At</label>
            <input
              v-model="form.startAt"
              type="datetime-local"
              class="w-full bg-gray-900 border border-gray-700 rounded px-3 py-1.5 text-sm text-gray-200"
            />
          </div>
          <div>
            <label class="block text-xs text-gray-400 mb-1">End At</label>
            <input
              v-model="form.endAt"
              type="datetime-local"
              class="w-full bg-gray-900 border border-gray-700 rounded px-3 py-1.5 text-sm text-gray-200"
            />
          </div>
        </div>

        <!-- Conditions -->
        <div class="border-t border-gray-700 pt-4">
          <div class="flex items-center justify-between mb-3">
            <h3 class="text-sm font-medium text-gray-300">Conditions ({{ conditions.length }})</h3>
            <button class="text-xs text-blue-400 hover:text-blue-300" @click="addCondition">+ Add Condition</button>
          </div>
          <div v-if="conditions.length === 0" class="text-xs text-gray-500">No conditions — rule applies to entire percentage</div>
          <div v-else class="space-y-2">
            <div v-for="(cond, idx) in conditions" :key="idx" class="flex items-center gap-2">
              <select
                v-model="cond.attribute"
                class="bg-gray-900 border border-gray-700 rounded px-2 py-1.5 text-sm text-gray-200"
              >
                <option v-for="attr in ATTRIBUTES" :key="attr" :value="attr">{{ attr }}</option>
              </select>
              <select
                v-model="cond.operator"
                class="bg-gray-900 border border-gray-700 rounded px-2 py-1.5 text-sm text-gray-200"
              >
                <option v-for="op in OPERATORS" :key="op" :value="op">{{ op }}</option>
              </select>
              <input
                v-model="cond.value"
                type="text"
                class="flex-1 bg-gray-900 border border-gray-700 rounded px-3 py-1.5 text-sm text-gray-200"
                placeholder="Value (comma-separated for IN/NOT_IN)"
              />
              <button class="text-red-400 hover:text-red-300 text-sm px-2" @click="removeCondition(idx)">✕</button>
            </div>
          </div>
        </div>
      </div>

      <div class="flex items-center justify-end gap-3 px-6 py-4 border-t border-gray-700">
        <button
          class="px-4 py-1.5 text-sm text-gray-400 hover:text-white border border-gray-600 rounded-lg transition-colors"
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
