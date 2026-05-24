<script setup lang="ts">
import { ref, watch, computed } from 'vue'
import { FeatureFlagAPI } from '@/api/admin/feature-flags'
import type { FeatureFlagDefinition, FeatureFlagVariant, FeatureFlagTargetingRule } from '@/api/admin/feature-flags'
import FeatureFlagRuleEditorVue from './FeatureFlagRuleEditor.vue'

const props = defineProps<{
  flag: FeatureFlagDefinition | null
}>()

const emit = defineEmits<{
  save: []
  close: []
}>()

const form = ref({
  flagKey: '',
  name: '',
  description: '',
  type: 'BOOLEAN' as FeatureFlagDefinition['type'],
  defaultValue: '',
  owner: '',
  tags: '',
  enabled: true,
})

const variants = ref<FeatureFlagVariant[]>([])
const targetingRules = ref<FeatureFlagTargetingRule[]>([])
const showRuleEditor = ref(false)
const editingRule = ref<FeatureFlagTargetingRule | null>(null)
const saving = ref(false)
const saveError = ref<string | null>(null)

const isEditing = computed(() => props.flag != null)

watch(() => props.flag, (flag) => {
  if (flag) {
    form.value = {
      flagKey: flag.flagKey,
      name: flag.name,
      description: flag.description,
      type: flag.type,
      defaultValue: flag.defaultValue,
      owner: flag.owner,
      tags: flag.tags?.join(', ') || '',
      enabled: flag.enabled,
    }
    variants.value = [...(flag.variants || [])]
    targetingRules.value = [...(flag.targetingRules || [])]
  } else {
    form.value = {
      flagKey: '', name: '', description: '', type: 'BOOLEAN',
      defaultValue: 'false', owner: '', tags: '', enabled: true,
    }
    variants.value = [{ key: 'control', value: 'false' }]
    targetingRules.value = []
  }
}, { immediate: true })

function addVariant() {
  variants.value.push({ key: '', value: '' })
}

function removeVariant(index: number) {
  variants.value.splice(index, 1)
}

function addRule() {
  editingRule.value = {
    priority: targetingRules.value.length + 1,
    name: '',
    conditions: [],
    percentage: 100,
  }
  showRuleEditor.value = true
}

function editRule(rule: FeatureFlagTargetingRule) {
  editingRule.value = { ...rule, conditions: [...rule.conditions] }
  showRuleEditor.value = true
}

function removeRule(index: number) {
  targetingRules.value.splice(index, 1)
}

function onRuleSave(rule: FeatureFlagTargetingRule) {
  const idx = targetingRules.value.findIndex(r => r.ruleId === rule.ruleId)
  if (idx >= 0) {
    targetingRules.value[idx] = rule
  } else {
    targetingRules.value.push(rule)
  }
  targetingRules.value.sort((a, b) => a.priority - b.priority)
  showRuleEditor.value = false
  editingRule.value = null
}

function onRuleClose() {
  showRuleEditor.value = false
  editingRule.value = null
}

async function handleSave() {
  saving.value = true
  saveError.value = null
  try {
    const payload: FeatureFlagDefinition = {
      flagKey: form.value.flagKey,
      name: form.value.name,
      description: form.value.description,
      type: form.value.type,
      defaultValue: form.value.defaultValue,
      variants: variants.value.filter(v => v.key),
      targetingRules: targetingRules.value,
      owner: form.value.owner,
      tags: form.value.tags.split(',').map(t => t.trim()).filter(Boolean),
      enabled: form.value.enabled,
    }

    if (isEditing.value) {
      await FeatureFlagAPI.updateFeatureFlag(form.value.flagKey, payload)
    } else {
      await FeatureFlagAPI.createFeatureFlag(payload)
    }
    emit('save')
  } catch (err) {
    saveError.value = err instanceof Error ? err.message : String(err)
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <div class="fixed inset-0 z-50 flex items-center justify-center bg-black/60" @click.self="emit('close')">
    <div class="bg-surface-2 border border-border-subtle rounded-xl shadow-2xl w-full max-w-3xl max-h-[90vh] overflow-y-auto">
      <div class="flex items-center justify-between px-6 py-4 border-b border-border-subtle">
        <h2 class="text-lg font-semibold text-white">{{ isEditing ? 'Edit Feature Flag' : 'New Feature Flag' }}</h2>
        <button class="text-text-secondary hover:text-white text-xl leading-none" @click="emit('close')">×</button>
      </div>

      <div v-if="saveError" class="mx-6 mt-4 p-3 bg-danger-muted border border-danger rounded-lg text-danger text-sm">
        {{ saveError }}
      </div>

      <div class="px-6 py-4 space-y-4">
        <div class="grid grid-cols-2 gap-4">
          <div>
            <label class="block text-xs text-text-secondary mb-1">Flag Key *</label>
            <input
              v-model="form.flagKey"
              type="text"
              :disabled="isEditing"
              class="w-full bg-surface-0 border border-border-subtle rounded px-3 py-1.5 text-sm text-text-primary disabled:opacity-50"
              placeholder="e.g. new-dashboard-v2"
            />
          </div>
          <div>
            <label class="block text-xs text-text-secondary mb-1">Name *</label>
            <input
              v-model="form.name"
              type="text"
              class="w-full bg-surface-0 border border-border-subtle rounded px-3 py-1.5 text-sm text-text-primary"
              placeholder="e.g. New Dashboard V2"
            />
          </div>
        </div>

        <div>
          <label class="block text-xs text-text-secondary mb-1">Description</label>
          <textarea
            v-model="form.description"
            rows="2"
            class="w-full bg-surface-0 border border-border-subtle rounded px-3 py-1.5 text-sm text-text-primary resize-none"
            placeholder="What does this flag control?"
          />
        </div>

        <div class="grid grid-cols-3 gap-4">
          <div>
            <label class="block text-xs text-text-secondary mb-1">Type *</label>
            <select v-model="form.type" class="w-full bg-surface-0 border border-border-subtle rounded px-3 py-1.5 text-sm text-text-primary">
              <option value="BOOLEAN">Boolean</option>
              <option value="STRING">String</option>
              <option value="NUMBER">Number</option>
              <option value="JSON">JSON</option>
            </select>
          </div>
          <div>
            <label class="block text-xs text-text-secondary mb-1">Default Value *</label>
            <input
              v-model="form.defaultValue"
              type="text"
              class="w-full bg-surface-0 border border-border-subtle rounded px-3 py-1.5 text-sm text-text-primary"
              :placeholder="form.type === 'BOOLEAN' ? 'false' : ''"
            />
          </div>
          <div>
            <label class="block text-xs text-text-secondary mb-1">Owner *</label>
            <input
              v-model="form.owner"
              type="text"
              class="w-full bg-surface-0 border border-border-subtle rounded px-3 py-1.5 text-sm text-text-primary"
              placeholder="team or person"
            />
          </div>
        </div>

        <div class="grid grid-cols-2 gap-4">
          <div>
            <label class="block text-xs text-text-secondary mb-1">Tags (comma-separated)</label>
            <input
              v-model="form.tags"
              type="text"
              class="w-full bg-surface-0 border border-border-subtle rounded px-3 py-1.5 text-sm text-text-primary"
              placeholder="e.g. ui, beta, experiment"
            />
          </div>
          <div class="flex items-end pb-1">
            <label class="flex items-center gap-2 text-sm text-text-primary cursor-pointer">
              <input v-model="form.enabled" type="checkbox" class="rounded bg-surface-0 border-border-subtle" />
              Enabled
            </label>
          </div>
        </div>

        <!-- Variants -->
        <div class="border-t border-border-subtle pt-4">
          <div class="flex items-center justify-between mb-3">
            <h3 class="text-sm font-medium text-text-primary">Variants</h3>
            <button class="text-xs text-info hover:text-info" @click="addVariant">+ Add Variant</button>
          </div>
          <div v-if="variants.length === 0" class="text-xs text-text-tertiary">No variants defined</div>
          <div v-else class="space-y-2">
            <div v-for="(variant, idx) in variants" :key="idx" class="flex items-center gap-2">
              <input
                v-model="variant.key"
                type="text"
                class="flex-1 bg-surface-0 border border-border-subtle rounded px-3 py-1.5 text-sm text-text-primary"
                placeholder="Variant key"
              />
              <input
                v-model="variant.value"
                type="text"
                class="flex-1 bg-surface-0 border border-border-subtle rounded px-3 py-1.5 text-sm text-text-primary"
                placeholder="Variant value"
              />
              <button class="text-danger hover:text-danger text-sm px-2" @click="removeVariant(idx)">✕</button>
            </div>
          </div>
        </div>

        <!-- Targeting Rules -->
        <div class="border-t border-border-subtle pt-4">
          <div class="flex items-center justify-between mb-3">
            <h3 class="text-sm font-medium text-text-primary">Targeting Rules ({{ targetingRules.length }})</h3>
            <button class="text-xs text-info hover:text-info" @click="addRule">+ Add Rule</button>
          </div>
          <div v-if="targetingRules.length === 0" class="text-xs text-text-tertiary">No targeting rules — flag uses default value</div>
          <div v-else class="space-y-2">
            <div
              v-for="(rule, idx) in targetingRules"
              :key="rule.ruleId || idx"
              class="flex items-center justify-between p-3 bg-surface-0/50 rounded border border-border-subtle"
            >
              <div class="flex items-center gap-3">
                <span class="text-xs text-text-tertiary font-mono">#{{ rule.priority }}</span>
                <span class="text-xs text-text-primary">{{ rule.name || 'Unnamed rule' }}</span>
                <span class="text-xs px-1.5 py-0.5 rounded bg-info-muted text-info">{{ rule.percentage }}%</span>
                <span class="text-xs text-text-tertiary">{{ rule.conditions.length }} conditions</span>
              </div>
              <div class="flex gap-1">
                <button class="text-[10px] text-info hover:text-info px-1" @click="editRule(rule)">Edit</button>
                <button class="text-[10px] text-danger hover:text-danger px-1" @click="removeRule(idx)">Remove</button>
              </div>
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
          class="px-4 py-1.5 text-sm bg-blue-600 hover:bg-blue-500 text-white rounded-lg transition-colors disabled:opacity-50"
          :disabled="saving"
          @click="handleSave"
        >
          {{ saving ? 'Saving...' : isEditing ? 'Save Changes' : 'Create Flag' }}
        </button>
      </div>
    </div>
  </div>

  <FeatureFlagRuleEditorVue
    v-if="showRuleEditor"
    :rule="editingRule"
    @save="onRuleSave"
    @close="onRuleClose"
  />
</template>
