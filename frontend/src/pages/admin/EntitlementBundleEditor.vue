<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { EntitlementAdminAPI } from '@/api/admin/entitlement-admin'
import type { EntitlementBundle } from '@/types'

const props = defineProps<{
  bundle: EntitlementBundle | null
}>()

const emit = defineEmits<{
  (e: 'close'): void
  (e: 'saved'): void
}>()

const form = ref({
  name: '',
  description: '',
  tier: 'PRO',
  features: [] as string[],
  quota: {} as Record<string, number>,
  status: 'DRAFT' as 'ACTIVE' | 'ARCHIVED' | 'DRAFT'
})
const saving = ref(false)
const newFeature = ref('')
const newQuotaKey = ref('')
const newQuotaValue = ref(0)

const tiers = ['FREE', 'PRO', 'TEAM', 'ENTERPRISE']

onMounted(() => {
  if (props.bundle) {
    form.value = {
      name: props.bundle.name,
      description: props.bundle.description,
      tier: props.bundle.tier,
      features: [...props.bundle.features],
      quota: { ...props.bundle.quota },
      status: props.bundle.status
    }
  }
})

function addFeature() {
  if (newFeature.value && !form.value.features.includes(newFeature.value)) {
    form.value.features.push(newFeature.value)
    newFeature.value = ''
  }
}

function removeFeature(idx: number) {
  form.value.features.splice(idx, 1)
}

function addQuota() {
  if (newQuotaKey.value) {
    form.value.quota[newQuotaKey.value] = newQuotaValue.value
    newQuotaKey.value = ''
    newQuotaValue.value = 0
  }
}

function removeQuota(key: string) {
  delete form.value.quota[key]
}

async function save() {
  if (!form.value.name) return
  saving.value = true
  try {
    if (props.bundle) {
      await EntitlementAdminAPI.updateBundle(props.bundle.bundleId, form.value)
    } else {
      await EntitlementAdminAPI.createBundle(form.value)
    }
    emit('saved')
  } catch { /* handle error */ }
  saving.value = false
}
</script>

<template>
  <div class="fixed inset-0 bg-black/50 flex items-center justify-center z-50">
    <div class="bg-gray-800 border border-gray-700 rounded-lg p-6 w-[600px] max-h-[80vh] overflow-y-auto space-y-4">
      <div class="flex items-center justify-between">
        <h2 class="text-lg font-semibold text-white">{{ bundle ? 'Edit Bundle' : 'New Bundle' }}</h2>
        <button class="text-gray-400 hover:text-white" @click="emit('close')">✕</button>
      </div>

      <div class="space-y-3">
        <div>
          <label class="text-xs text-gray-400 block mb-1">Name</label>
          <input v-model="form.name" class="w-full bg-gray-700 border border-gray-600 rounded px-2 py-1.5 text-sm text-white" />
        </div>
        <div>
          <label class="text-xs text-gray-400 block mb-1">Description</label>
          <textarea v-model="form.description" rows="2" class="w-full bg-gray-700 border border-gray-600 rounded px-2 py-1.5 text-sm text-white" />
        </div>
        <div class="grid grid-cols-2 gap-3">
          <div>
            <label class="text-xs text-gray-400 block mb-1">Tier</label>
            <select v-model="form.tier" class="w-full bg-gray-700 border border-gray-600 rounded px-2 py-1.5 text-sm text-white">
              <option v-for="t in tiers" :key="t" :value="t">{{ t }}</option>
            </select>
          </div>
          <div>
            <label class="text-xs text-gray-400 block mb-1">Status</label>
            <select v-model="form.status" class="w-full bg-gray-700 border border-gray-600 rounded px-2 py-1.5 text-sm text-white">
              <option value="DRAFT">DRAFT</option>
              <option value="ACTIVE">ACTIVE</option>
              <option value="ARCHIVED">ARCHIVED</option>
            </select>
          </div>
        </div>

        <div>
          <label class="text-xs text-gray-400 block mb-1">Features</label>
          <div class="flex flex-wrap gap-1 mb-2">
            <span v-for="(feat, idx) in form.features" :key="idx" class="flex items-center gap-1 px-2 py-0.5 rounded bg-blue-600/20 text-blue-300 text-xs">
              {{ feat }}
              <button class="hover:text-white" @click="removeFeature(idx)">×</button>
            </span>
          </div>
          <div class="flex gap-2">
            <input v-model="newFeature" placeholder="Add feature..." class="flex-1 bg-gray-700 border border-gray-600 rounded px-2 py-1 text-xs text-white" @keyup.enter="addFeature" />
            <button class="px-2 py-1 bg-gray-600 hover:bg-gray-500 text-white text-xs rounded" @click="addFeature">Add</button>
          </div>
        </div>

        <div>
          <label class="text-xs text-gray-400 block mb-1">Quota</label>
          <div class="space-y-1 mb-2">
            <div v-for="(val, key) in form.quota" :key="key" class="flex items-center justify-between p-1.5 rounded bg-gray-700/30 text-xs">
              <span class="text-gray-300">{{ key }}</span>
              <div class="flex items-center gap-2">
                <span class="text-white">{{ val }}</span>
                <button class="text-red-400 hover:text-red-300" @click="removeQuota(key)">×</button>
              </div>
            </div>
          </div>
          <div class="flex gap-2">
            <input v-model="newQuotaKey" placeholder="Key" class="flex-1 bg-gray-700 border border-gray-600 rounded px-2 py-1 text-xs text-white" />
            <input v-model.number="newQuotaValue" type="number" placeholder="Value" class="w-20 bg-gray-700 border border-gray-600 rounded px-2 py-1 text-xs text-white" />
            <button class="px-2 py-1 bg-gray-600 hover:bg-gray-500 text-white text-xs rounded" @click="addQuota">Add</button>
          </div>
        </div>
      </div>

      <div class="flex gap-2 pt-2 border-t border-gray-700">
        <button class="px-4 py-2 bg-blue-600 hover:bg-blue-500 text-white text-sm rounded" :disabled="saving || !form.name" @click="save">
          {{ saving ? 'Saving...' : 'Save' }}
        </button>
        <button class="px-4 py-2 bg-gray-600 hover:bg-gray-500 text-white text-sm rounded" @click="emit('close')">Cancel</button>
      </div>
    </div>
  </div>
</template>
