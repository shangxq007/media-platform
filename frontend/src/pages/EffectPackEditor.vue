<script setup lang="ts">
import { ref, computed, reactive, onMounted } from 'vue'
import { useEffectPackStore } from '@/stores/effectPack'
import type { EffectPack, EffectPackEffect, EffectParameterDef } from '@/types'
import PortalPageHeader from '@/components/ui/PortalPageHeader.vue'
import AppIcon from '@/components/ui/AppIcon.vue'

const effectPackStore = useEffectPackStore()

const showEditor = ref(false)
const editingPackId = ref<string | null>(null)
const activeEffectIdx = ref<number | null>(null)

const packForm = reactive({
  packId: '',
  version: '1.0.0',
  name: '',
  description: '',
  author: '',
  compatibility: '2.0',
  allowedTiers: ['FREE', 'PRO', 'TEAM', 'ENTERPRISE'] as string[],
})

const effectForm = reactive({
  effectKey: '',
  displayName: '',
  category: 'transition' as 'transition' | 'video' | 'audio' | 'text',
  description: '',
  providerMappings: ['javacv'],
  allowedTiers: ['FREE', 'PRO', 'TEAM', 'ENTERPRISE'] as string[],
  parameterSchema: {} as Record<string, EffectParameterDef>,
  defaultValues: {} as Record<string, unknown>,
})

const currentEffects = ref<EffectPackEffect[]>([])
const paramKey = ref('')
const paramType = ref<EffectParameterDef['type']>('float')

const allPacks = computed(() => effectPackStore.allPacks)

const tierOptions = ['FREE', 'PRO', 'TEAM', 'ENTERPRISE']
const categoryOptions = ['transition', 'video', 'audio', 'text'] as const
const providerOptions = ['javacv', 'ffmpeg', 'natron', 'ofx', 'gpu']
const paramTypeOptions: EffectParameterDef['type'][] = ['int', 'float', 'string', 'boolean', 'color']

function resetPackForm() {
  packForm.packId = ''
  packForm.version = '1.0.0'
  packForm.name = ''
  packForm.description = ''
  packForm.author = ''
  packForm.compatibility = '2.0'
  packForm.allowedTiers = ['FREE', 'PRO', 'TEAM', 'ENTERPRISE']
  currentEffects.value = []
  editingPackId.value = null
  showEditor.value = false
}

function resetEffectForm() {
  effectForm.effectKey = ''
  effectForm.displayName = ''
  effectForm.category = 'transition'
  effectForm.description = ''
  effectForm.providerMappings = ['javacv']
  effectForm.allowedTiers = ['FREE', 'PRO', 'TEAM', 'ENTERPRISE']
  effectForm.parameterSchema = {}
  effectForm.defaultValues = {}
  activeEffectIdx.value = null
}

function startCreate() {
  resetPackForm()
  resetEffectForm()
  showEditor.value = true
}

function startEdit(pack: EffectPack) {
  packForm.packId = pack.packId
  packForm.version = pack.version
  packForm.name = pack.name
  packForm.description = pack.description
  packForm.author = pack.author
  packForm.compatibility = pack.compatibility
  packForm.allowedTiers = [...pack.allowedTiers]
  currentEffects.value = pack.effects.map(e => ({ ...e, parameterSchema: { ...e.parameterSchema }, defaultValues: { ...e.defaultValues } }))
  editingPackId.value = pack.packId
  resetEffectForm()
  showEditor.value = true
}

function cancelEdit() {
  resetPackForm()
}

function addParameter() {
  if (!paramKey.value.trim()) return
  const key = paramKey.value.trim()
  const def: EffectParameterDef = {
    type: paramType.value,
    defaultValue: paramType.value === 'boolean' ? false : paramType.value === 'float' || paramType.value === 'int' ? 0 : '',
    description: '',
  }
  effectForm.parameterSchema[key] = def
  effectForm.defaultValues[key] = def.defaultValue
  paramKey.value = ''
}

function removeParameter(key: string) {
  delete effectForm.parameterSchema[key]
  delete effectForm.defaultValues[key]
}

function updateDefaultValue(key: string, value: string) {
  const schema = effectForm.parameterSchema[key]
  if (!schema) return
  if (schema.type === 'int') {
    effectForm.defaultValues[key] = parseInt(value, 10) || 0
  } else if (schema.type === 'float') {
    effectForm.defaultValues[key] = parseFloat(value) || 0
  } else if (schema.type === 'boolean') {
    effectForm.defaultValues[key] = value === 'true' || value === '1'
  } else {
    effectForm.defaultValues[key] = value
  }
}

function addEffect() {
  if (!effectForm.effectKey.trim() || !effectForm.displayName.trim()) return
  currentEffects.value.push({
    effectKey: effectForm.effectKey.trim(),
    displayName: effectForm.displayName.trim(),
    category: effectForm.category,
    description: effectForm.description,
    parameterSchema: { ...effectForm.parameterSchema },
    defaultValues: { ...effectForm.defaultValues },
    providerMappings: [...effectForm.providerMappings],
    allowedTiers: [...effectForm.allowedTiers],
  })
  resetEffectForm()
}

function editEffect(idx: number) {
  const eff = currentEffects.value[idx]
  effectForm.effectKey = eff.effectKey
  effectForm.displayName = eff.displayName
  effectForm.category = eff.category
  effectForm.description = eff.description
  effectForm.providerMappings = [...eff.providerMappings]
  effectForm.allowedTiers = [...eff.allowedTiers]
  effectForm.parameterSchema = { ...eff.parameterSchema }
  effectForm.defaultValues = { ...eff.defaultValues }
  activeEffectIdx.value = idx
}

function updateEffect() {
  if (activeEffectIdx.value === null) return
  const idx = activeEffectIdx.value
  currentEffects.value[idx] = {
    effectKey: effectForm.effectKey.trim(),
    displayName: effectForm.displayName.trim(),
    category: effectForm.category,
    description: effectForm.description,
    parameterSchema: { ...effectForm.parameterSchema },
    defaultValues: { ...effectForm.defaultValues },
    providerMappings: [...effectForm.providerMappings],
    allowedTiers: [...effectForm.allowedTiers],
  }
  resetEffectForm()
}

function removeEffect(idx: number) {
  currentEffects.value.splice(idx, 1)
  if (activeEffectIdx.value === idx) resetEffectForm()
}

function toggleTier(tier: string, list: string[]) {
  const idx = list.indexOf(tier)
  if (idx >= 0) list.splice(idx, 1)
  else list.push(tier)
}

function toggleProvider(p: string) {
  const idx = effectForm.providerMappings.indexOf(p)
  if (idx >= 0) effectForm.providerMappings.splice(idx, 1)
  else effectForm.providerMappings.push(p)
}

async function savePack() {
  if (!packForm.packId.trim() || !packForm.name.trim()) return
  const pack: EffectPack = {
    packId: packForm.packId.trim(),
    version: packForm.version,
    name: packForm.name.trim(),
    description: packForm.description,
    author: packForm.author,
    compatibility: packForm.compatibility,
    allowedTiers: [...packForm.allowedTiers],
    effects: currentEffects.value,
  }
  try {
    if (editingPackId.value) {
      await effectPackStore.updateCustomPack(pack.packId, pack.version, pack)
    } else {
      await effectPackStore.saveCustomPack(pack)
    }
    resetPackForm()
  } catch (e) {
    console.error('Failed to save effect pack', e)
  }
}

async function deletePack(packId: string, version: string) {
  try {
    await effectPackStore.deleteCustomPack(packId, version)
    if (editingPackId.value === packId) resetPackForm()
  } catch (e) {
    console.error('Failed to delete effect pack', e)
  }
}

onMounted(() => {
  effectPackStore.loadFromApi()
})

function togglePackTier(tier: string) {
  toggleTier(tier, packForm.allowedTiers)
}
</script>

<template>
  <div class="h-full flex flex-col bg-bg-base text-text-primary studio-page">
    <PortalPageHeader title="Effect Pack Studio" subtitle="Browse, create, and publish effect packs for your workspace">
      <template #actions>
        <button
          v-if="!showEditor"
          type="button"
          class="theme-btn theme-btn-primary theme-btn-sm inline-flex items-center gap-1"
          @click="startCreate"
        >
          <AppIcon name="plus" :size="16" />
          New pack
        </button>
      </template>
    </PortalPageHeader>

    <div class="flex-1 flex overflow-hidden min-h-0">
      <div class="flex-1 overflow-y-auto theme-scrollbar">
        <template v-if="!showEditor">
          <div v-if="effectPackStore.loading" class="text-center py-16 text-text-muted text-sm">Loading packs…</div>
          <div v-else-if="allPacks.length === 0" class="text-center py-16">
            <AppIcon name="sparkles" :size="48" class="mx-auto text-text-muted opacity-40 mb-4" />
            <p class="text-sm text-text-secondary">No effect packs yet</p>
            <button type="button" class="theme-btn theme-btn-primary theme-btn-sm mt-4" @click="startCreate">Create your first pack</button>
          </div>
          <div v-else class="studio-pack-grid">
            <article
              v-for="pack in allPacks"
              :key="`${pack.packId}-${pack.version}`"
              class="studio-pack-card"
            >
              <div class="studio-pack-card-header">
                <div class="flex items-start gap-3">
                  <div class="dashboard-action-icon flex-shrink-0">
                    <AppIcon name="sparkles" :size="20" />
                  </div>
                  <div class="min-w-0 flex-1">
                    <h3 class="text-base font-semibold text-text-primary">{{ pack.name }}</h3>
                    <p class="text-xs text-text-muted mt-1 line-clamp-2">{{ pack.description || 'No description' }}</p>
                  </div>
                </div>
                <div class="flex items-center gap-2 mt-3">
                  <span class="text-xs px-2 py-0.5 rounded-full bg-primary-500/15 text-primary-400 font-medium">
                    {{ pack.effects.length }} effects
                  </span>
                  <span class="text-xs text-text-muted font-mono">v{{ pack.version }}</span>
                  <span v-if="pack.packId === 'builtin-core'" class="text-xs text-text-muted">Built-in</span>
                </div>
              </div>
              <div class="p-4 space-y-3">
                <div class="flex flex-wrap gap-1.5">
                  <span
                    v-for="eff in pack.effects.slice(0, 8)"
                    :key="eff.effectKey"
                    class="text-[10px] px-2 py-0.5 rounded-md bg-bg-base border border-default text-text-secondary"
                  >
                    {{ eff.displayName }}
                  </span>
                  <span v-if="pack.effects.length > 8" class="text-[10px] text-text-muted">+{{ pack.effects.length - 8 }}</span>
                </div>
                <div class="flex items-center gap-2 pt-1 border-t border-default">
                  <button type="button" class="theme-btn theme-btn-secondary theme-btn-sm" @click="startEdit(pack)">Edit</button>
                  <button
                    v-if="pack.packId !== 'builtin-core'"
                    type="button"
                    class="theme-btn theme-btn-ghost theme-btn-sm text-danger-500"
                    @click="deletePack(pack.packId, pack.version)"
                  >
                    <AppIcon name="trash" :size="14" class="mr-1" />
                    Delete
                  </button>
                </div>
              </div>
            </article>
          </div>
        </template>

        <!-- ===== EDITOR VIEW ===== -->
        <template v-else>
          <div class="max-w-3xl space-y-6">
            <!-- Pack Info -->
            <section class="border border-border-subtle rounded-lg p-4">
              <h3 class="text-sm font-semibold mb-3 text-text-primary">Pack Info</h3>
              <div class="grid grid-cols-2 gap-3">
                <div>
                  <label class="text-xs text-text-secondary block mb-1">Pack ID *</label>
                  <input
                    v-model="packForm.packId"
                    type="text"
                    placeholder="my-effect-pack"
                    class="w-full bg-surface-2 border border-border-default rounded px-2 py-1.5 text-sm text-white"
                    :disabled="editingPackId !== null"
                  />
                </div>
                <div>
                  <label class="text-xs text-text-secondary block mb-1">Version</label>
                  <input
                    v-model="packForm.version"
                    type="text"
                    placeholder="1.0.0"
                    class="w-full bg-surface-2 border border-border-default rounded px-2 py-1.5 text-sm text-white"
                  />
                </div>
                <div class="col-span-2">
                  <label class="text-xs text-text-secondary block mb-1">Name *</label>
                  <input
                    v-model="packForm.name"
                    type="text"
                    placeholder="My Effect Pack"
                    class="w-full bg-surface-2 border border-border-default rounded px-2 py-1.5 text-sm text-white"
                  />
                </div>
                <div class="col-span-2">
                  <label class="text-xs text-text-secondary block mb-1">Description</label>
                  <textarea
                    v-model="packForm.description"
                    rows="2"
                    placeholder="Describe what this pack does..."
                    class="w-full bg-surface-2 border border-border-default rounded px-2 py-1.5 text-sm text-white resize-none"
                  />
                </div>
                <div>
                  <label class="text-xs text-text-secondary block mb-1">Author</label>
                  <input
                    v-model="packForm.author"
                    type="text"
                    placeholder="Your name"
                    class="w-full bg-surface-2 border border-border-default rounded px-2 py-1.5 text-sm text-white"
                  />
                </div>
                <div>
                  <label class="text-xs text-text-secondary block mb-1">Compatibility</label>
                  <input
                    v-model="packForm.compatibility"
                    type="text"
                    placeholder="2.0"
                    class="w-full bg-surface-2 border border-border-default rounded px-2 py-1.5 text-sm text-white"
                  />
                </div>
                <div class="col-span-2">
                  <label class="text-xs text-text-secondary block mb-1">Allowed Tiers</label>
                  <div class="flex gap-2">
                    <button
                      v-for="tier in tierOptions"
                      :key="tier"
                      class="text-xs px-2 py-1 rounded border"
                      :class="packForm.allowedTiers.includes(tier)
                        ? 'bg-blue-600/30 border-info text-info'
                        : 'bg-surface-2 border-border-default text-text-tertiary'"
                      @click="togglePackTier(tier)"
                    >
                      {{ tier }}
                    </button>
                  </div>
                </div>
              </div>
            </section>

            <!-- Effects List -->
            <section class="border border-border-subtle rounded-lg p-4">
              <h3 class="text-sm font-semibold mb-3 text-text-primary">
                Effects ({{ currentEffects.length }})
              </h3>

              <div v-if="currentEffects.length > 0" class="space-y-2 mb-4">
                <div
                  v-for="(eff, idx) in currentEffects"
                  :key="idx"
                  class="flex items-center gap-3 p-2 rounded border border-border-subtle bg-surface-2/30"
                >
                  <span
                    class="text-xs px-1.5 py-0.5 rounded shrink-0"
                    :class="{
                      'bg-info-muted text-info': eff.category === 'transition',
                      'bg-success-muted text-success': eff.category === 'video',
                      'bg-yellow-600/20 text-warning': eff.category === 'audio',
                      'bg-pink-600/20 text-pink-300': eff.category === 'text',
                    }"
                  >
                    {{ eff.category }}
                  </span>
                  <div class="flex-1 min-w-0">
                    <span class="text-sm text-white">{{ eff.displayName }}</span>
                    <span class="text-xs text-text-tertiary ml-2 font-mono">{{ eff.effectKey }}</span>
                  </div>
                  <div class="flex gap-1 shrink-0">
                    <span class="text-xs text-text-tertiary">{{ Object.keys(eff.parameterSchema).length }} params</span>
                    <button class="text-xs px-1.5 py-0.5 bg-surface-3 hover:bg-surface-4 rounded" @click="editEffect(idx)">Edit</button>
                    <button class="text-xs px-1.5 py-0.5 bg-red-900/40 hover:bg-red-800/60 text-danger rounded" @click="removeEffect(idx)">×</button>
                  </div>
                </div>
              </div>

              <!-- Add / Edit Effect Form -->
              <div class="border border-dashed border-border-default rounded-lg p-4 bg-surface-2/20">
                <h4 class="text-xs font-semibold text-text-secondary mb-3">
                  {{ activeEffectIdx !== null ? 'Edit Effect' : 'Add Effect' }}
                </h4>
                <div class="grid grid-cols-2 gap-3">
                  <div>
                    <label class="text-xs text-text-secondary block mb-1">Effect Key *</label>
                    <input
                      v-model="effectForm.effectKey"
                      type="text"
                      placeholder="video.my_effect"
                      class="w-full bg-surface-2 border border-border-default rounded px-2 py-1.5 text-sm text-white"
                    />
                  </div>
                  <div>
                    <label class="text-xs text-text-secondary block mb-1">Display Name *</label>
                    <input
                      v-model="effectForm.displayName"
                      type="text"
                      placeholder="My Effect"
                      class="w-full bg-surface-2 border border-border-default rounded px-2 py-1.5 text-sm text-white"
                    />
                  </div>
                  <div>
                    <label class="text-xs text-text-secondary block mb-1">Category</label>
                    <select
                      v-model="effectForm.category"
                      class="w-full bg-surface-2 border border-border-default rounded px-2 py-1.5 text-sm text-white"
                    >
                      <option v-for="c in categoryOptions" :key="c" :value="c">{{ c }}</option>
                    </select>
                  </div>
                  <div>
                    <label class="text-xs text-text-secondary block mb-1">Providers</label>
                    <div class="flex gap-2 flex-wrap">
                      <button
                        v-for="p in providerOptions"
                        :key="p"
                        class="text-xs px-2 py-1 rounded border"
                        :class="effectForm.providerMappings.includes(p)
                          ? 'bg-green-600/30 border-success text-success'
                          : 'bg-surface-2 border-border-default text-text-tertiary'"
                        @click="toggleProvider(p)"
                      >
                        {{ p }}
                      </button>
                    </div>
                  </div>
                  <div class="col-span-2">
                    <label class="text-xs text-text-secondary block mb-1">Description</label>
                    <input
                      v-model="effectForm.description"
                      type="text"
                      placeholder="What does this effect do?"
                      class="w-full bg-surface-2 border border-border-default rounded px-2 py-1.5 text-sm text-white"
                    />
                  </div>
                  <div class="col-span-2">
                    <label class="text-xs text-text-secondary block mb-1">Allowed Tiers</label>
                    <div class="flex gap-2">
                      <button
                        v-for="tier in tierOptions"
                        :key="tier"
                        class="text-xs px-2 py-1 rounded border"
                        :class="effectForm.allowedTiers.includes(tier)
                          ? 'bg-blue-600/30 border-info text-info'
                          : 'bg-surface-2 border-border-default text-text-tertiary'"
                        @click="toggleTier(tier, effectForm.allowedTiers)"
                      >
                        {{ tier }}
                      </button>
                    </div>
                  </div>
                </div>

                <!-- Parameters -->
                <div class="mt-4">
                  <label class="text-xs text-text-secondary block mb-2">Parameters</label>

                  <div v-if="Object.keys(effectForm.parameterSchema).length > 0" class="space-y-2 mb-3">
                    <div
                      v-for="(def, key) in effectForm.parameterSchema"
                      :key="key"
                      class="flex items-center gap-2 p-2 rounded bg-surface-2/50 border border-border-subtle"
                    >
                      <span class="text-xs font-mono text-accent-300 w-24 shrink-0">{{ key }}</span>
                      <span class="text-xs text-text-tertiary w-16 shrink-0">{{ def.type }}</span>
                      <input
                        v-if="def.type === 'string' || def.type === 'color'"
                        :value="String(effectForm.defaultValues[key] ?? '')"
                        type="text"
                        class="flex-1 bg-surface-2 border border-border-default rounded px-2 py-1 text-xs text-white"
                        @input="updateDefaultValue(key, ($event.target as HTMLInputElement).value)"
                      />
                      <input
                        v-else-if="def.type === 'int' || def.type === 'float'"
                        :value="String(effectForm.defaultValues[key] ?? 0)"
                        type="number"
                        :step="def.type === 'float' ? 0.1 : 1"
                        class="flex-1 bg-surface-2 border border-border-default rounded px-2 py-1 text-xs text-white"
                        @input="updateDefaultValue(key, ($event.target as HTMLInputElement).value)"
                      />
                      <input
                        v-else-if="def.type === 'boolean'"
                        :checked="Boolean(effectForm.defaultValues[key])"
                        type="checkbox"
                        class="rounded"
                        @change="updateDefaultValue(key, ($event.target as HTMLInputElement).checked ? 'true' : 'false')"
                      />
                      <button class="text-xs text-danger hover:text-danger shrink-0" @click="removeParameter(key)">×</button>
                    </div>
                  </div>

                  <div class="flex gap-2">
                    <input
                      v-model="paramKey"
                      type="text"
                      placeholder="Parameter name"
                      class="flex-1 bg-surface-2 border border-border-default rounded px-2 py-1.5 text-xs text-white"
                      @keyup.enter="addParameter"
                    />
                    <select
                      v-model="paramType"
                      class="bg-surface-2 border border-border-default rounded px-2 py-1.5 text-xs text-white"
                    >
                      <option v-for="t in paramTypeOptions" :key="t" :value="t">{{ t }}</option>
                    </select>
                    <button
                      class="text-xs px-2 py-1 bg-surface-3 hover:bg-surface-4 rounded"
                      @click="addParameter"
                    >
                      Add
                    </button>
                  </div>
                </div>

                <div class="flex gap-2 mt-4">
                  <button
                    v-if="activeEffectIdx === null"
                    class="px-3 py-1.5 bg-blue-600 hover:bg-blue-500 text-sm rounded"
                    @click="addEffect"
                  >
                    + Add Effect
                  </button>
                  <template v-else>
                    <button
                      class="px-3 py-1.5 bg-blue-600 hover:bg-blue-500 text-sm rounded"
                      @click="updateEffect"
                    >
                      Update Effect
                    </button>
                    <button
                      class="px-3 py-1.5 bg-surface-3 hover:bg-surface-4 text-sm rounded"
                      @click="resetEffectForm"
                    >
                      Cancel
                    </button>
                  </template>
                </div>
              </div>
            </section>

            <!-- Save / Cancel -->
            <div class="flex gap-2 justify-end">
              <button
                class="px-4 py-2 bg-surface-3 hover:bg-surface-4 text-sm rounded"
                @click="cancelEdit"
              >
                Cancel
              </button>
              <button
                class="px-4 py-2 bg-green-600 hover:bg-green-500 text-sm rounded disabled:opacity-40 disabled:cursor-not-allowed"
                :disabled="!packForm.packId.trim() || !packForm.name.trim()"
                @click="savePack"
              >
                {{ editingPackId ? 'Update Pack' : 'Save Pack' }}
              </button>
            </div>
          </div>
        </template>
      </div>
    </div>
  </div>
</template>
