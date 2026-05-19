<script setup lang="ts">
import { ref, onMounted, computed, reactive } from 'vue'
import { PolicyAdminAPI } from '@/api/admin/policy-admin'
import type { ABACPolicy, PolicyRule } from '@/api/admin/policy-admin'
import PolicyRuleEditorVue from './PolicyRuleEditor.vue'
import PolicySimulationPanelVue from './PolicySimulationPanel.vue'
import LoadingState from '@/components/ui/LoadingState.vue'
import EmptyState from '@/components/ui/EmptyState.vue'
import StatusBadge from '@/components/ui/StatusBadge.vue'

type Tab = 'policies' | 'simulation'

const loading = ref(true)
const policies = ref<ABACPolicy[]>([])
const error = ref<string | null>(null)
const activeTab = ref<Tab>('policies')
const showEditor = ref(false)
const editingRule = ref<{ policyId: string; rule: PolicyRule | null } | null>(null)

const searchQuery = ref('')
const filterStatus = ref<string>('ALL')

const editorForm = reactive({
  policyId: '',
  name: '',
  code: '',
  description: '',
  status: 'DRAFT' as ABACPolicy['status'],
  rules: [] as PolicyRule[],
  versionCount: 0,
})

const isEditingPolicy = computed(() => !!editorForm.policyId)

const filteredPolicies = computed(() => {
  let result = policies.value
  if (searchQuery.value) {
    const q = searchQuery.value.toLowerCase()
    result = result.filter(p =>
      p.name.toLowerCase().includes(q) ||
      p.code.toLowerCase().includes(q)
    )
  }
  if (filterStatus.value !== 'ALL') {
    result = result.filter(p => p.status === filterStatus.value)
  }
  return result
})

async function loadPolicies() {
  loading.value = true
  error.value = null
  try {
    policies.value = await PolicyAdminAPI.listPolicies()
  } catch (err) {
    const msg = err instanceof Error ? err.message : String(err)
    error.value = msg
  } finally {
    loading.value = false
  }
}

function createPolicy() {
  editorForm.policyId = ''
  editorForm.name = ''
  editorForm.code = ''
  editorForm.description = ''
  editorForm.status = 'DRAFT'
  editorForm.rules = []
  editorForm.versionCount = 0
  showEditor.value = true
}

function editPolicy(policy: ABACPolicy) {
  editorForm.policyId = policy.policyId
  editorForm.name = policy.name
  editorForm.code = policy.code
  editorForm.description = policy.description
  editorForm.status = policy.status
  editorForm.rules = policy.rules.map(r => ({ ...r }))
  editorForm.versionCount = policy.versionCount
  showEditor.value = true
}

async function savePolicy() {
  try {
    if (isEditingPolicy.value) {
      await PolicyAdminAPI.updatePolicy(editorForm.policyId, {
        name: editorForm.name,
        code: editorForm.code,
        description: editorForm.description,
        status: editorForm.status,
        rules: editorForm.rules,
        versionCount: editorForm.versionCount,
      })
    } else {
      await PolicyAdminAPI.createPolicy({
        name: editorForm.name,
        code: editorForm.code,
        description: editorForm.description,
        status: editorForm.status,
        rules: editorForm.rules,
        versionCount: editorForm.versionCount,
      })
    }
    showEditor.value = false
    await loadPolicies()
  } catch (err) {
    console.error('[PolicyManagement] Failed to save policy:', err)
  }
}

function closeEditor() {
  showEditor.value = false
}

async function archivePolicy(policy: ABACPolicy) {
  try {
    await PolicyAdminAPI.archivePolicy(policy.policyId)
    await loadPolicies()
  } catch (err) {
    console.error('[PolicyManagement] Failed to archive policy:', err)
  }
}

function addRule(policy: ABACPolicy) {
  editingRule.value = { policyId: policy.policyId, rule: null }
}

function editRule(policy: ABACPolicy, rule: PolicyRule) {
  editingRule.value = {
    policyId: policy.policyId,
    rule: {
      ...rule,
      conditions: [...rule.conditions],
      featureFlagConditions: [...rule.featureFlagConditions],
    },
  }
}

function onRuleSaved(rule: PolicyRule) {
  if (editingRule.value) {
    const policy = policies.value.find(p => p.policyId === editingRule.value!.policyId)
    if (policy) {
      const idx = policy.rules.findIndex(r => r.ruleId === rule.ruleId)
      if (idx >= 0) {
        policy.rules[idx] = rule
      } else {
        policy.rules.push(rule)
      }
    }
  }
  editingRule.value = null
}

function onRuleClosed() {
  editingRule.value = null
}

function effectVariant(effect: string): 'success' | 'warning' | 'danger' | 'info' | 'neutral' {
  switch (effect) {
    case 'ALLOW': return 'success'
    case 'DENY': return 'danger'
    case 'REQUIRE_REVIEW': return 'warning'
    case 'DEGRADE': return 'info'
    case 'WARN': return 'warning'
    default: return 'neutral'
  }
}

onMounted(loadPolicies)
</script>

<template>
  <div class="flex-1 overflow-y-auto p-6">
    <div class="flex items-center justify-between mb-6">
      <div>
        <h1 class="text-xl font-bold">Policy / ABAC Management</h1>
        <p class="text-sm text-gray-400 mt-1">Manage attribute-based access control policies and rules</p>
      </div>
      <div class="flex gap-2">
        <button class="px-3 py-1.5 bg-blue-600 hover:bg-blue-500 text-sm rounded text-white" @click="createPolicy">
          + New Policy
        </button>
        <button class="px-3 py-1.5 bg-gray-700 hover:bg-gray-600 text-sm rounded text-white" @click="loadPolicies">
          Refresh
        </button>
      </div>
    </div>

    <div class="flex border-b border-gray-700 mb-4">
      <button
        class="px-4 py-2 text-sm"
        :class="activeTab === 'policies' ? 'text-blue-400 border-b-2 border-blue-400' : 'text-gray-400 hover:text-white'"
        @click="activeTab = 'policies'"
      >
        Policies ({{ policies.length }})
      </button>
      <button
        class="px-4 py-2 text-sm"
        :class="activeTab === 'simulation' ? 'text-blue-400 border-b-2 border-blue-400' : 'text-gray-400 hover:text-white'"
        @click="activeTab = 'simulation'"
      >
        Simulation
      </button>
    </div>

    <div v-if="error" class="mb-4 p-3 bg-red-900/30 border border-red-700 rounded-lg text-red-300 text-sm">
      {{ error }}
    </div>

    <template v-if="activeTab === 'policies'">
      <LoadingState v-if="loading" message="Loading policies..." />

      <template v-else>
        <div class="flex items-center gap-4 mb-4">
          <input
            v-model="searchQuery"
            type="text"
            class="flex-1 bg-gray-800 border border-gray-700 rounded px-3 py-1.5 text-sm text-gray-200"
            placeholder="Search by name or code..."
          />
          <select v-model="filterStatus" class="bg-gray-800 border border-gray-700 rounded px-2 py-1.5 text-sm text-gray-200">
            <option value="ALL">All Status</option>
            <option value="ACTIVE">Active</option>
            <option value="DRAFT">Draft</option>
            <option value="ARCHIVED">Archived</option>
          </select>
          <span class="text-xs text-gray-500">{{ filteredPolicies.length }} policies</span>
        </div>

        <EmptyState
          v-if="filteredPolicies.length === 0"
          icon="📜"
          title="No policies found"
          description="Create your first ABAC policy to get started."
        >
          <template #action>
            <button class="px-3 py-1.5 bg-blue-600 hover:bg-blue-500 text-sm rounded text-white" @click="createPolicy">
              + New Policy
            </button>
          </template>
        </EmptyState>

        <div v-else class="space-y-4">
          <div
            v-for="policy in filteredPolicies"
            :key="policy.policyId"
            class="bg-gray-800 border border-gray-700 rounded-lg p-4"
          >
            <div class="flex items-center justify-between mb-3">
              <div class="flex items-center gap-3">
                <h3 class="text-sm font-semibold text-white">{{ policy.name }}</h3>
                <span class="text-xs font-mono text-blue-300">{{ policy.code }}</span>
                <StatusBadge
                  :variant="policy.status === 'ACTIVE' ? 'success' : policy.status === 'DRAFT' ? 'warning' : 'neutral'"
                  :label="policy.status"
                />
                <span class="text-xs text-gray-500">{{ policy.versionCount }} versions</span>
              </div>
              <div class="flex gap-1">
                <button class="text-[10px] text-blue-400 hover:text-blue-300 px-1" @click="editPolicy(policy)">Edit</button>
                <button class="text-[10px] text-green-400 hover:text-green-300 px-1" @click="addRule(policy)">+ Rule</button>
                <button v-if="policy.status !== 'ARCHIVED'" class="text-[10px] text-red-400 hover:text-red-300 px-1" @click="archivePolicy(policy)">Archive</button>
              </div>
            </div>
            <p class="text-xs text-gray-400 mb-3">{{ policy.description }}</p>
            <div v-if="policy.rules.length > 0" class="space-y-1.5">
              <div v-for="(rule, idx) in policy.rules" :key="rule.ruleId || idx" class="flex items-center justify-between p-2 bg-gray-900/50 rounded">
                <div class="flex items-center gap-2">
                  <span class="text-xs text-gray-500 font-mono">#{{ rule.priority }}</span>
                  <span class="text-xs text-gray-200">{{ rule.name }}</span>
                  <StatusBadge :variant="effectVariant(rule.effect)" :label="rule.effect" />
                  <span class="text-xs text-gray-500">{{ rule.conditions.length }} conditions</span>
                  <span v-if="rule.featureFlagConditions.length > 0" class="text-xs px-1 py-0.5 rounded bg-purple-600/20 text-purple-300">
                    {{ rule.featureFlagConditions.length }} flag refs
                  </span>
                </div>
                <button class="text-[10px] text-blue-400 hover:text-blue-300" @click="editRule(policy, rule)">Edit</button>
              </div>
            </div>
            <div v-else class="text-xs text-gray-500">No rules defined</div>
          </div>
        </div>
      </template>
    </template>

    <PolicySimulationPanelVue v-else-if="activeTab === 'simulation'" :policies="policies" />

    <!-- Policy Editor Modal -->
    <div v-if="showEditor" class="fixed inset-0 z-50 flex items-center justify-center bg-black/60" @click.self="closeEditor">
      <div class="bg-gray-800 border border-gray-700 rounded-xl shadow-2xl w-full max-w-lg max-h-[90vh] overflow-y-auto">
        <div class="flex items-center justify-between px-6 py-4 border-b border-gray-700">
          <h2 class="text-lg font-semibold text-white">{{ isEditingPolicy ? 'Edit Policy' : 'New Policy' }}</h2>
          <button class="text-gray-400 hover:text-white text-xl leading-none" @click="closeEditor">×</button>
        </div>
        <div class="px-6 py-4 space-y-4">
          <div>
            <label class="block text-xs text-gray-400 mb-1">Name *</label>
            <input
              v-model="editorForm.name"
              type="text"
              class="w-full bg-gray-900 border border-gray-700 rounded px-3 py-1.5 text-sm text-gray-200"
              placeholder="e.g. Enterprise Access Policy"
            />
          </div>
          <div>
            <label class="block text-xs text-gray-400 mb-1">Code *</label>
            <input
              v-model="editorForm.code"
              type="text"
              class="w-full bg-gray-900 border border-gray-700 rounded px-3 py-1.5 text-sm text-gray-200"
              placeholder="e.g. enterprise-access"
            />
          </div>
          <div>
            <label class="block text-xs text-gray-400 mb-1">Description</label>
            <textarea
              v-model="editorForm.description"
              rows="2"
              class="w-full bg-gray-900 border border-gray-700 rounded px-3 py-1.5 text-sm text-gray-200 resize-none"
            />
          </div>
          <div>
            <label class="block text-xs text-gray-400 mb-1">Status</label>
            <select v-model="editorForm.status" class="w-full bg-gray-900 border border-gray-700 rounded px-3 py-1.5 text-sm text-gray-200">
              <option value="DRAFT">Draft</option>
              <option value="ACTIVE">Active</option>
              <option value="ARCHIVED">Archived</option>
            </select>
          </div>
        </div>
        <div class="flex items-center justify-end gap-3 px-6 py-4 border-t border-gray-700">
          <button class="px-4 py-1.5 text-sm text-gray-400 hover:text-white border border-gray-600 rounded-lg" @click="closeEditor">
            Cancel
          </button>
          <button
            class="px-4 py-1.5 text-sm bg-blue-600 hover:bg-blue-500 text-white rounded-lg disabled:opacity-50"
            :disabled="!editorForm.name || !editorForm.code"
            @click="savePolicy"
          >
            {{ isEditingPolicy ? 'Save Changes' : 'Create Policy' }}
          </button>
        </div>
      </div>
    </div>

    <!-- Rule Editor -->
    <PolicyRuleEditorVue
      v-if="editingRule"
      :rule="editingRule.rule"
      @save="onRuleSaved"
      @close="onRuleClosed"
    />
  </div>
</template>
